package com.huiyang.quizlocktv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 全屏锁屏出题页：遮住电视画面，必须答对才放行。
 * 规则：答对 → finish + 重新计时 N 分钟；答错 → 追加一道新题，仍锁屏。
 */
class QuizLockActivity : Activity() {

    companion object {
        const val EXTRA_FIRST = "first"
    }

    private lateinit var stemView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var optionsBox: LinearLayout
    private var current: Question? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏 + 防截屏 + 保持亮屏
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        buildLayout()
        showNextQuestion()
    }

    private fun buildLayout() {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1220"))
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "🔒 答题解锁看电视"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#2BB673"))
            gravity = Gravity.CENTER
        }

        stemView = TextView(this).apply {
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(20), 0, dp(16))
        }

        optionsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        feedbackView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#FF5B5B"))
            setPadding(0, dp(16), 0, dp(8))
        }

        col.addView(title)
        col.addView(stemView)
        col.addView(optionsBox)
        col.addView(feedbackView)
        root.addView(col)
        setContentView(root)
    }

    private fun showNextQuestion() {
        val qs = QuizStore.questions(this)
        if (qs.isEmpty()) {
            Toast.makeText(this, "题库为空，请先在「答题锁 TV」中导入题库", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val idx = QuizStore.takeNext(this)
        if (idx < 0 || idx >= qs.size) { finish(); return }
        val q = qs[idx]
        current = q
        render(q)
    }

    private fun render(q: Question) {
        stemView.text = if (q.isJudge) "【判断题】${q.stem}" else "【单选题】${q.stem}"
        feedbackView.text = ""
        optionsBox.removeAllViews()

        if (q.isJudge) {
            optionsBox.addView(makeOptionButton("正确", 1, q))
            optionsBox.addView(makeOptionButton("错误", 0, q))
        } else {
            q.options.forEachIndexed { i, opt ->
                optionsBox.addView(makeOptionButton("${('A' + i)}. $opt", i, q))
            }
        }
    }

    private fun makeOptionButton(label: String, pick: Int, q: Question): Button {
        val b = Button(this).apply {
            text = label
            textSize = 20f
            isAllCaps = false
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            setBackgroundColor(Color.parseColor("#16243B"))
            setTextColor(Color.WHITE)
        }
        b.setOnClickListener {
            if (pick == q.correct) {
                // 答对：放行 + 重新计时
                feedbackView.setTextColor(Color.parseColor("#2BB673"))
                feedbackView.text = "✅ 答对了，继续看吧！"
                LockService.scheduleNext(this)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 700)
            } else {
                // 答错：提示 + 追加一道新题，仍锁屏
                feedbackView.setTextColor(Color.parseColor("#FF5B5B"))
                val tip = if (q.analysis.isNotBlank()) "❌ 答错了。${q.analysis}  再来一题" else "❌ 答错了，再来一题"
                feedbackView.text = tip
                // 答错：追加一道不同的新题（takeNext 保证不重复），仍锁屏
                showNextQuestion()
            }
        }
        return b
    }

    // 屏蔽返回键，无法绕开锁屏
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() { /* 禁用 */ }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
