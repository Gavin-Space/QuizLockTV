package com.huiyang.quizlocktv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 管理主页 = 题库入口。
 * 入口A：粘贴导入（把 AI 生成的题库 JSON 粘进来）。
 * 入口B：URL 自动同步（填一个公开 .json 地址，电视定时/手动拉取更新）。
 * 另：设置出题间隔、测试锁屏。
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var intervalLabel: TextView
    private lateinit var streakLabel: TextView
    private lateinit var pasteInput: EditText
    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        refreshStatus()
        startServiceAndSchedule()
    }

    private fun startServiceAndSchedule() {
        val svc = Intent(this, LockService::class.java)
        runCatching { startForegroundService(svc) }
            .onFailure { runCatching { startService(svc) } }
        LockService.scheduleNext(this)
    }

    private fun buildLayout() {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1220"))
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        col.addView(header("答题锁 TV"))
        statusView = TextView(this).apply {
            textSize = 16f; setTextColor(Color.parseColor("#9FB0CC"))
            setPadding(0, dp(6), 0, dp(16))
        }
        col.addView(statusView)

        // 间隔设置
        col.addView(subhead("每看几分钟弹一题："))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("-1", 1f) { bumpInterval(-1) })
        intervalLabel = labelView(QuizStore.getInterval(this).toString(), 1f)
        row.addView(intervalLabel)
        row.addView(btn("+1", 1f) { bumpInterval(1) })
        row.addView(btn("设为10", 1.5f) { setInterval(10) })
        col.addView(row)

        // 防乱点：需连续答对几题才解锁
        col.addView(subhead("需连续答对几题才解锁（越大越难乱点）："))
        val rowS = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowS.addView(btn("-1", 1f) { bumpStreak(-1) })
        streakLabel = labelView(QuizStore.getStreak(this).toString(), 1f)
        rowS.addView(streakLabel)
        rowS.addView(btn("+1", 1f) { bumpStreak(1) })
        rowS.addView(btn("设为3", 1.5f) { setStreak(3) })
        col.addView(rowS)

        // 入口A
        col.addView(subhead("【入口A】粘贴导入题库 JSON："))
        pasteInput = EditText(this).apply {
            hint = "把 AI 生成的题库 JSON 粘贴到这里（需含 questions 数组）"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7A93"))
            setBackgroundColor(Color.parseColor("#16243B"))
            minHeight = dp(120)
            gravity = Gravity.TOP or Gravity.START
        }
        col.addView(pasteInput)
        col.addView(btnFull("保存并导入") { importFromPaste() })

        // 入口B
        col.addView(subhead("【入口B】题库链接自动同步："))
        urlInput = EditText(this).apply {
            hint = "填公开可读的 .json 地址，如 https://…/题库.json"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7A93"))
            setBackgroundColor(Color.parseColor("#16243B"))
            setText(QuizStore.getSyncUrl(this@MainActivity))
        }
        col.addView(urlInput)
        val rowB = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowB.addView(btn("保存地址", 1f) { saveUrl() })
        rowB.addView(btn("立即同步", 1f) { doSync() })
        col.addView(rowB)

        // 测试
        col.addView(btnFull("测试：立即弹一道题") {
            if (QuizStore.hasBank(this)) {
                startActivity(Intent(this, QuizLockActivity::class.java))
            } else {
                Toast.makeText(this, "还没有题库，先用入口A/B放题", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(col)
        setContentView(root)
    }

    // ---- 业务动作 ----
    private fun bumpInterval(delta: Int) {
        val v = (QuizStore.getInterval(this) + delta).coerceIn(1, 1440)
        setInterval(v)
    }

    private fun setInterval(v: Int) {
        QuizStore.setInterval(this, v)
        LockService.scheduleNext(this)
        refreshStatus()
    }

    private fun bumpStreak(delta: Int) {
        setStreak((QuizStore.getStreak(this) + delta).coerceIn(1, 10))
    }

    private fun setStreak(v: Int) {
        QuizStore.setStreak(this, v)
        streakLabel.text = v.toString()
        refreshStatus()
    }

    private fun importFromPaste() {
        val text = pasteInput.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "请先粘贴内容", Toast.LENGTH_SHORT).show(); return }
        try {
            val n = QuizParser.parse(text).size
            QuizStore.saveRaw(this, text)
            Toast.makeText(this, "导入成功，共 $n 题", Toast.LENGTH_LONG).show()
            pasteInput.setText("")
            refreshStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "格式有误：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveUrl() {
        QuizStore.setSyncUrl(this, urlInput.text.toString())
        Toast.makeText(this, "同步地址已保存", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun doSync() {
        QuizStore.setSyncUrl(this, urlInput.text.toString())
        QuizSync.fetchNow(this, object : QuizSync.Result {
            override fun onDone(ok: Boolean, msg: String) {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                if (ok) refreshStatus()
            }
        })
    }

    private fun refreshStatus() {
        val count = QuizStore.questions(this).size
        intervalLabel.text = QuizStore.getInterval(this).toString()
        val last = QuizStore.getLastSync(this)
        val lastStr = if (last == 0L) "从未同步"
        else "上次同步 ${android.text.format.DateFormat.getDateFormat(this).format(java.util.Date(last))}"
        statusView.text = "当前题库：$count 题　|　间隔 ${QuizStore.getInterval(this)} 分钟　|　连对 ${QuizStore.getStreak(this)} 题解锁　|　$lastStr"
    }

    // ---- 小工具 ----
    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 26f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#2BB673"))
    }

    private fun subhead(t: String) = TextView(this).apply {
        text = t; textSize = 17f; setTextColor(Color.WHITE)
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun labelView(t: String, w: Float) = TextView(this).apply {
        text = t; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(48), w)
    }

    private fun btn(t: String, w: Float, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 15f
        layoutParams = LinearLayout.LayoutParams(0, dp(48), w)
        setBackgroundColor(Color.parseColor("#16243B")); setTextColor(Color.WHITE)
        setOnClickListener { onClick() }
    }

    private fun btnFull(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 18f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { topMargin = dp(12) }
        setBackgroundColor(Color.parseColor("#0F8A4F")); setTextColor(Color.WHITE)
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
