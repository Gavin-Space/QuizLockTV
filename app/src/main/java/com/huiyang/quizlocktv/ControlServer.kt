package com.huiyang.quizlocktv

import android.content.Context
import android.content.Intent
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * 内置局域网 HTTP 控制服务器：让同一 WiFi 下的手机/电脑用浏览器控制电视。
 * 路由：
 *   GET  /            -> 手机控制台网页(assets/console.html)
 *   GET  /api/status  -> 当前题库数/间隔/连对/IP 的 JSON
 *   POST /api/bank    -> body 为题库 JSON（UTF-8 字节流），校验后整库替换
 *   POST /api/config  -> body: interval=10&streak=3，改弹窗间隔与连对数
 *   POST /api/popup   -> 立即拉起全屏锁屏出题
 *
 * 关键修复：请求体按"字节(Content-Length 是字节数)"读取再整体 UTF-8 解码，
 * 不能用 reader 按字符读，否则中文题库(汉字 UTF-8 占 3 字节)会被截断报格式错误。
 * 纯 JDK ServerSocket 实现，不依赖第三方库。
 */
class ControlServer(private val ctx: Context, private val port: Int = NetworkInfo.PORT) {

    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        thread(isDaemon = true, name = "QuizControl") {
            try {
                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                while (running) {
                    val socket = try { server?.accept() ?: break }
                    catch (e: Exception) { if (!running) break else continue }
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
                s.soTimeout = 10000
                val input = s.getInputStream()
                val output = s.getOutputStream()

                // 1) 读请求头（按字节，直到 \r\n\r\n）
                val headerBytes = readUntilHeader(input) ?: return
                val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
                val lines = headerText.split("\r\n")
                val requestLine = lines.firstOrNull() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                var contentLength = 0
                for (line in lines.drop(1)) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                // 2) 按字节读取 body，再整体 UTF-8 解码（修复中文截断）
                val body = if (contentLength > 0) {
                    val bytes = readNBytes(input, contentLength)
                    String(bytes, StandardCharsets.UTF_8)
                } else ""

                route(method, path, body, output)
            }
        } catch (e: Exception) {
            // 单次请求出错不影响服务
        }
    }

    /** 读到 \r\n\r\n 为止的请求头字节；限制最大 16KB 防异常请求。 */
    private fun readUntilHeader(input: InputStream): ByteArray? {
        val buf = ByteArrayOutputStream()
        var c = input.read()
        var count = 0
        while (c != -1) {
            buf.write(c)
            count++
            if (count > 16384) return buf.toByteArray() // 头过大，直接返回已读部分
            val size = buf.size()
            if (size >= 4) {
                val b = buf.toByteArray()
                if (b[size - 4] == '\r'.code.toByte() && b[size - 3] == '\n'.code.toByte() &&
                    b[size - 2] == '\r'.code.toByte() && b[size - 1] == '\n'.code.toByte()) {
                    return b
                }
            }
            c = input.read()
        }
        return if (buf.size() > 0) buf.toByteArray() else null
    }

    /** 精确读取 n 个字节。 */
    private fun readNBytes(input: InputStream, n: Int): ByteArray {
        val result = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(result, read, n - read)
            if (r == -1) break
            read += r
        }
        return if (read == n) result else result.copyOf(read)
    }

    private fun route(method: String, path: String, body: String, out: OutputStream) {
        when {
            method == "GET" && (path == "/" || path.startsWith("/index") || path.startsWith("/console")) -> {
                send(out, 200, "text/html; charset=utf-8", readAsset("console.html"))
            }
            method == "GET" && path.startsWith("/api/status") -> {
                send(out, 200, "application/json; charset=utf-8", statusJson())
            }
            method == "POST" && path.startsWith("/api/bank") -> handleBank(body, out)
            method == "POST" && path.startsWith("/api/config") -> handleConfig(body, out)
            method == "POST" && path.startsWith("/api/popup") -> handlePopup(out)
            else -> send(out, 404, "text/plain; charset=utf-8", "not found")
        }
    }

    private fun statusJson(): String {
        val count = QuizStore.questions(ctx).size
        val interval = QuizStore.getInterval(ctx)
        val streak = QuizStore.getStreak(ctx)
        val hasBank = QuizStore.hasBank(ctx)
        return """{"count":$count,"interval":$interval,"streak":$streak,"hasBank":$hasBank,"ip":"${NetworkInfo.localIp()}","port":$port}"""
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
        if (!QuizStore.hasBank(ctx)) {
            send(out, 200, "application/json; charset=utf-8", """{"ok":false,"msg":"题库为空，请先推送题库"}""")
            return
        }
        val i = Intent(ctx, QuizLockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
        send(out, 200, "application/json; charset=utf-8", """{"ok":true}""")
    }

    private fun send(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val status = when (code) {
            200 -> "200 OK"
            404 -> "404 Not Found"
            else -> "$code Status"
        }
        val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: $type\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun readAsset(name: String): String = try {
        ctx.assets.open(name).use { it.readBytes().toString(StandardCharsets.UTF_8) }
    } catch (e: Exception) {
        "<html><body style='background:#0B1220;color:#fff;padding:24px;font-family:sans-serif'>控制台加载失败：${e.message}</body></html>"
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

    private fun jsonEsc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
