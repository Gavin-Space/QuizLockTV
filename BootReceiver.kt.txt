package com.huiyang.quizlocktv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 开机完成：启动前台服务（负责安排每 N 分钟弹窗），并立即弹"开机首题"。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val svc = Intent(ctx, LockService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
        } catch (e: Exception) { /* 个别 ROM 限制，忽略 */ }

        // 开机先做一道题：从后台拉起锁屏页
        val lock = Intent(ctx, QuizLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(QuizLockActivity.EXTRA_FIRST, true)
        }
        try { ctx.startActivity(lock) } catch (e: Exception) {}
    }
}
