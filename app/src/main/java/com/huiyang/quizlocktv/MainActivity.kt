package com.huiyang.quizlocktv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
 * 管理主页 = 题库入口（电视遥控器也可操作，但日常用「手机控制台」更顺手）。
 * 入口A：粘贴导入；入口B：URL 自动同步；并可设间隔/连对数、测试弹窗。
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

    private fun card(radiusDp: Int = 16, color: Int = Color.parseColor("#13223A")) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat(); setColor(color); setStroke(dp(1), Color.parseColor("#243650"))
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    private fun buildLayout() {
        val root = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0A1220"), Color.parseColor("#0E1A2E"))
            )
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
        }

        // 顶部标题卡
        val head = card(18, Color.parseColor("#0F8A4F"))
        head.addView(TextView(this).apply {
            text = "🔒 答题锁 TV"; textSize = 28f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        head.addView(TextView(this).apply {
            text = "📱 手机控制台（同 WiFi 浏览器打开）\n" + NetworkInfo.consoleUrl()
            textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#EAFBEE")); setPadding(0, dp(8), 0, 0)
            setTypeface(typeface, Typeface.BOLD)
        })
        col.addView(head)

        statusView = TextView(this).apply {
            textSize = 14f; setTextColor(Color.parseColor("#9FB0CC"))
            setPadding(dp(4), 0, dp(4), dp(12)); gravity = Gravity.CENTER
        }
        col.addView(statusView)

        // 规则卡
        val cfgCard = card()
        cfgCard.addView(subhead("⏱ 弹窗间隔（分钟）"))
        cfgCard.addView(stepperRow("interval", QuizStore.getInterval(this), 1440, "min"))
        cfgCard.addView(subhead("✅ 需连续答对几题解锁", topPad = 14))
        cfgCard.addView(stepperRow("streak", QuizStore.getStreak(this), 10, "streak"))
        cfgCard.addView(TextView(this).apply {
            text = "连对数越大，越难靠乱点蒙混过关。"
            textSize = 12f; setTextColor(Color.parseColor("#8FA3C2")); setPadding(0, dp(10), 0, 0)
        })
        col.addView(cfgCard)

        // 入口A 卡
        val aCard = card()
        aCard.addView(subhead("📥 入口A · 粘贴导入题库 JSON"))
        pasteInput = EditText(this).apply {
            hint = "把 AI 生成的题库 JSON 粘贴到这里（需含 questions 数组）"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#5C6F8C"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#0B1626"))
                setStroke(dp(1), Color.parseColor("#243650"))
            }
            minHeight = dp(110); gravity = Gravity.TOP or Gravity.START; setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        aCard.addView(pasteInput)
        aCard.addView(btnFull("保存并导入", Color.parseColor("#0F8A4F")) { importFromPaste() })
        col.addView(aCard)

        // 入口B 卡
        val bCard = card()
        bCard.addView(subhead("🔗 入口B · 题库链接自动同步"))
        urlInput = EditText(this).apply {
            hint = "填公开可读的 .json 地址，如 https://…/题库.json"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#5C6F8C"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#0B1626"))
                setStroke(dp(1), Color.parseColor("#243650"))
            }
            setSingleLine(); setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(QuizStore.getSyncUrl(this@MainActivity))
        }
        bCard.addView(urlInput)
        val rowB = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowB.addView(btn("保存地址", 1f) { saveUrl() })
        val sp = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10), 0) }
        rowB.addView(sp)
        rowB.addView(btn("立即同步", 1f) { doSync() })
        rowB.setPadding(0, dp(8), 0, 0)
        bCard.addView(rowB)
        col.addView(bCard)

        col.addView(btnFull("⚡ 测试：立即弹一道题", Color.parseColor("#1F6FEB")) {
            if (QuizStore.hasBank(this)) startActivity(Intent(this, QuizLockActivity::class.java))
            else Toast.makeText(this, "还没有题库，先用入口A/B放题", Toast.LENGTH_SHORT).show()
        })

        root.addView(col)
        setContentView(root)
    }

    private fun stepperRow(key: String, init: Int, max: Int, which: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        row.addView(roundBtn("−") { if (which == "min") bumpInterval(-1) else bumpStreak(-1) })
        val label = TextView(this).apply {
            text = init.toString(); textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        }
        if (which == "min") intervalLabel = label else streakLabel = label
        row.addView(label)
        row.addView(roundBtn("＋") { if (which == "min") bumpInterval(1) else bumpStreak(1) })
        return row
    }

    // ---- 业务动作 ----
    private fun bumpInterval(delta: Int) {
        val v = (QuizStore.getInterval(this) + delta).coerceIn(1, 1440)
        QuizStore.setInterval(this, v); LockService.scheduleNext(this); refreshStatus()
    }
    private fun bumpStreak(delta: Int) {
        val v = (QuizStore.getStreak(this) + delta).coerceIn(1, 10)
        QuizStore.setStreak(this, v); streakLabel.text = v.toString(); refreshStatus()
    }

    private fun importFromPaste() {
        val text = pasteInput.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "请先粘贴内容", Toast.LENGTH_SHORT).show(); return }
        try {
            val n = QuizParser.parse(text).size
            QuizStore.saveRaw(this, text)
            Toast.makeText(this, "导入成功，共 $n 题", Toast.LENGTH_LONG).show()
            pasteInput.setText(""); refreshStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "格式有误：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveUrl() {
        QuizStore.setSyncUrl(this, urlInput.text.toString())
        Toast.makeText(this, "同步地址已保存", Toast.LENGTH_SHORT).show(); refreshStatus()
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
        streakLabel.text = QuizStore.getStreak(this).toString()
        statusView.text = "当前题库 $count 题　·　间隔 ${QuizStore.getInterval(this)} 分　·　连对 ${QuizStore.getStreak(this)} 题解锁"
    }

    // ---- 小工具 ----
    private fun subhead(t: String, topPad: Int = 0) = TextView(this).apply {
        text = t; textSize = 16f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(topPad), 0, dp(4))
    }

    private fun roundBtn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 20f
        layoutParams = LinearLayout.LayoutParams(dp(50), dp(46))
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B2C47")) }
        setTextColor(Color.WHITE); stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun btn(t: String, w: Float, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 15f
        layoutParams = LinearLayout.LayoutParams(0, dp(48), w)
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#1B2C47")) }
        setTextColor(Color.WHITE); stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun btnFull(t: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(8) }
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(color) }
        setTextColor(Color.WHITE); stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
