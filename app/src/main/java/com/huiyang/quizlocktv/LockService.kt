package com.huiyang.quizlocktv

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * 前台常驻服务：保证进程不被电视待机清理，并提供"答对解锁后重新计时 N 分钟"的闹钟调度。
 * 计时起点 = 上一次答对解锁的时刻，符合"每满 N 分钟弹一题"。
 */
class LockService : Service() {

    private var controlServer: ControlServer? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        // 启动内置局域网控制服务器：同一 WiFi 下手机/电脑可远程放题与弹窗
        controlServer = ControlServer(applicationContext).also { it.start() }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { controlServer?.stop() }
        controlServer = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        scheduleNext(this)
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "quiz_lock_svc"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "答题锁运行中", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        val notification = builder
            .setContentTitle("答题锁 TV")
            .setContentText("运行中：答题后才能看电视")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val REQ = 1001

        /** 安排 interval 分钟后弹出下一题。 */
        fun scheduleNext(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pending(ctx)
            val intervalMin = QuizStore.getInterval(ctx)
            val triggerAt = System.currentTimeMillis() + intervalMin * 60_000L
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        private fun pending(ctx: Context): PendingIntent {
            val i = Intent(ctx, AlarmReceiver::class.java)
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(ctx, REQ, i, flag)
        }
    }
}
