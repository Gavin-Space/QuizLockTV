package com.huiyang.quizlocktv

import android.content.Context
import android.content.Intent
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * 内置局域网 HTTP 控制服务器：让同一 WiFi 下的手机/电脑用浏览器控制电视。
 * 路由：
 *   GET  /             -> 手机控制台网页(assets/console.html)
 *   GET  /api/status   -> 当前题库数/间隔/连对/IP 的 JSON
 *   POST /api/bank     -> body 为题库 JSON，校验后整库替换
 *   POST /api/config   -> body: interval=10&streak=3，改弹窗间隔与连对数
 *   POST /api/popup    -> 立即拉起全屏锁屏出题
 * 纯 JDK ServerSocket 实现，不依赖第三方库，targetSdk30 无 cleartext 限制问题。
 */
class ControlServer(private val ctx: Context, private val port: Int = NetworkInfo.PORT) {

    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        thread(isDaemon = true) {
            try {
                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                while (running) {
                    val socket = try { server?.accept() ?: break } catch (e: Exception) { if (!running) break else continue }
                    thread(isDaemon = true) { handle(socket) }
                }
            } catch (e: Exception) {
                // 端口占用等，忽略
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 8000
                val input = s.getInputStream()
                val output = s.getOutputStream()
                val reader = input.reader(StandardCharsets.UTF_8).buffered()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                // 读头部，拿 Content-Length
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                val body = if (contentLength > 0) readNChars(reader, contentLength) else ""

                route(method, path, body, output)
            }
        } catch (e: Exception) {
            // 单次请求出错不影响服务
        }
    }

    private fun readNChars(reader: BufferedReader, n: Int): String {
        val buf = CharArray(n)
        var read = 0
        while (read < n) {
            val r = reader.read(buf, read, n - read)
            if (r == -1) break
            read += r
        }
        return String(buf, 0, read)
    }

    private fun route(method: String, path: String, body: String, out: OutputStream) {
        when {
            method == "GET" && (path == "/" || path.startsWith("/index")) -> {
                val html = readAsset("console.html")
                send(out, 200, "text/html; charset=utf-8", html)
            }
            method == "GET" && path.startsWith("/api/status") -> {
                send(out, 200, "application/json; charset=utf-8", statusJson())
            }
            method == "POST" && path.startsWith("/api/bank") -> {
                handleBank(body, out)
            }
            method == "POST" && path.startsWith("/api/config") -> {
                handleConfig(body, out)
            }
            method == "POST" && path.startsWith("/api/popup") -> {
                handlePopup(out)
            }
            else -> send(out, 404, "text/plain; charset=utf-8", "not found")
        }
    }

    private fun statusJson(): String {
        val count = QuizStore.questions(ctx).size
        val interval = QuizStore.getInterval(ctx)
        val streak = QuizStore.getStreak(ctx)
        return """{"count":$count,"interval":$interval,"streak":$streak,"ip":"${NetworkInfo.localIp()}","port":$port}"""
    }

    private fun handleBank(body: String, out: OutputStream) {
        try {
            val n = QuizParser.parse(body).size
            QuizStore.saveRaw(ctx, body)
            send(out, 200, "application/json; charset=utf-8", """{"ok":true,"count":$n}""")
        } catch (e: Exception) {
            send(out, 200, "application/json; charset=utf-8", """{"ok":false,"msg":"${jsonEsc(e.message ?: "格式错误")}"}""")
        }
    }

    private fun handleConfig(body: String, out: OutputStream) {
        val map = parseForm(body)
        map["interval"]?.toIntOrNull()?.let { QuizStore.setInterval(ctx, it) }
        map["streak"]?.toIntOrNull()?.let { QuizStore.setStreak(ctx, it) }
        LockService.scheduleNext(ctx)
        send(out, 200, "application/json; charset=utf-8", statusJson())
    }

    private fun handlePopup(out: OutputStream) {
        val i = Intent(ctx, QuizLockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
        send(out, 200, "application/json; charset=utf-8", """{"ok":true}""")
    }

    // ---- 工具 ----
    private fun send(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val status = if (code == 200) "200 OK" else "$code ERR"
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $type\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun readAsset(name: String): String {
        return try {
            ctx.assets.open(name).use { it.readBytes().toString(StandardCharsets.UTF_8) }
        } catch (e: Exception) {
            "控制台加载失败：${e.message}"
        }
    }

    private fun parseForm(body: String): Map<String, String> {
        val m = HashMap<String, String>()
        body.split("&").forEach { kv ->
            if (kv.contains("=")) {
                val k = kv.substringBefore("=")
                val v = kv.substringAfter("=")
                m[k] = URLDecoder.decode(v, "UTF-8")
            }
        }
        return m
    }

    private fun jsonEsc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
