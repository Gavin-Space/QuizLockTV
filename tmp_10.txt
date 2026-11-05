package com.huiyang.quizlocktv

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/**
 * 入口B：从用户填的 URL 拉取题库 JSON。fetchNow 内部自起后台线程，回调在主线程。
 */
object QuizSync {
    interface Result {
        fun onDone(ok: Boolean, msg: String)
    }

    /** 同步：拉取 URL，成功且能解析则替换本地题库。 */
    fun fetchNow(ctx: Context, result: Result?) {
        val url = QuizStore.getSyncUrl(ctx)
        Thread {
            if (url.isBlank()) {
                post(result, false, "未设置同步地址")
                return@Thread
            }
            try {
                val text = httpGet(url)
                if (text == null) {
                    post(result, false, "无法访问地址（检查网络/链接是否公开可读）")
                    return@Thread
                }
                // 先验证能解析成题库，再入库
                QuizParser.parse(text)
                QuizStore.saveRaw(ctx, text)
                QuizStore.setLastSync(ctx, System.currentTimeMillis())
                post(result, true, "同步成功，题库已更新")
            } catch (e: Exception) {
                post(result, false, "同步失败：${e.message}")
            }
        }.start()
    }

    private fun httpGet(u: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun post(r: Result?, ok: Boolean, msg: String) {
        Handler(Looper.getMainLooper()).post { r?.onDone(ok, msg) }
    }
}

