package com.huiyang.quizlocktv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 每 N 分钟闹钟触发：拉起全屏锁屏出题页。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val lock = Intent(ctx, QuizLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(lock) } catch (e: Exception) {}
    }
}
