package com.huiyang.quizlocktv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * 全屏锁屏出题页：遮住电视画面，必须连续答对才放行。
 * 防乱点：①需连对 requiredStreak 题，答错清零；②单选选项每次随机打乱。
 * 视觉：渐变背景 + 圆角卡片 + 连对进度点 + 按钮按压反馈。
 */
class QuizLockActivity : Activity() {

    companion object { const val EXTRA_FIRST = "first" }

    private lateinit var stemView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var dotsBox: LinearLayout
    private lateinit var hintView: TextView
    private lateinit var optionsBox: LinearLayout
    private lateinit var optionsCard: LinearLayout

    private var requiredStreak = 3
    private var correctCount = 0
    private var locked = false // 反馈动画期间锁定点击，防误触

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        requiredStreak = QuizStore.getStreak(this)
        correctCount = 0
        buildLayout()
        showNextQuestion()
    }

    private fun cardBg(radiusDp: Int, color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
            setStroke(dp(1), Color.parseColor("#2A3A55"))
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
            setPadding(dp(26), dp(22), dp(26), dp(22))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "🔒 答题解锁看电视"
            textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#27D67B"))
            gravity = Gravity.CENTER
        }
        hintView = TextView(this).apply {
            textSize = 14f; setTextColor(Color.parseColor("#8FA3C2")); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        }

        // 连对进度点
        dotsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

        // 题目卡片
        optionsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(18, Color.parseColor("#13223A"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        stemView = TextView(this).apply {
            textSize = 25f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE)
            setLineSpacing(dp(8).toFloat(), 1.0f)
        }
        optionsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, 0)
        }

        feedbackView = TextView(this).apply {
            textSize = 17f; setTextColor(Color.parseColor("#FF5B5B")); gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }

        col.addView(title)
        col.addView(hintView)
        col.addView(dotsBox)
        col.addView(optionsCard)
        optionsCard.addView(stemView)
        optionsCard.addView(optionsBox)
        col.addView(feedbackView)
        root.addView(col)
        setContentView(root)
        renderDots()
    }

    private fun renderDots() {
        dotsBox.removeAllViews()
        for (i in 0 until requiredStreak) {
            val d = View(this).apply {
                val size = dp(12)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    leftMargin = dp(5); rightMargin = dp(5)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i < correctCount) Color.parseColor("#27D67B") else Color.parseColor("#26364F"))
                }
            }
            dotsBox.addView(d)
        }
        hintView.text = "需连续答对 $requiredStreak 题 · 已连续答对 $correctCount 题"
    }

    private fun showNextQuestion() {
        val qs = QuizStore.questions(this)
        if (qs.isEmpty()) {
            Toast.makeText(this, "题库为空，请先导入题库", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val idx = QuizStore.takeNext(this)
        if (idx < 0 || idx >= qs.size) { finish(); return }
        render(qs[idx])
    }

    private fun render(q: com.huiyang.quizlocktv.Question) {
        stemView.text = if (q.isJudge) "【判断题】\n${q.stem}" else "【单选题】\n${q.stem}"
        feedbackView.text = ""; feedbackView.setTextColor(Color.parseColor("#FF5B5B"))
        renderDots()
        optionsBox.removeAllViews()
        locked = false

        if (q.isJudge) {
            optionsBox.addView(makeOptionButton("正确", q.correct == 1, q))
            optionsBox.addView(makeOptionButton("错误", q.correct == 0, q))
        } else {
            val items = ArrayList<Pair<String, Int>>()
            for (i in q.options.indices) items.add(q.options[i] to i)
            items.shuffle(Random.Default)
            items.forEachIndexed { pos, pair ->
                optionsBox.addView(makeOptionButton("${'A' + pos}. ${pair.first}", pair.second == q.correct, q))
            }
        }
    }

    private fun makeOptionButton(label: String, isCorrect: Boolean, q: com.huiyang.quizlocktv.Question): Button {
        val b = Button(this).apply {
            text = label; textSize = 19f; isAllCaps = false
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(11) }
            background = cardBg(14, Color.parseColor("#1B2C47"))
            setTextColor(Color.WHITE)
            stateListAnimator = null
        }
        b.setOnClickListener {
            if (locked) return@setOnClickListener
            locked = true
            Handler(Looper.getMainLooper()).postDelayed({ locked = false }, 350) // 防连点误触

            if (isCorrect) {
                b.setBackgroundColor(Color.parseColor("#1F7A46"))
                correctCount++
                renderDots()
                if (correctCount >= requiredStreak) {
                    feedbackView.setTextColor(Color.parseColor("#27D67B"))
                    feedbackView.text = "✅ 全部答对，继续看吧！"
                    LockService.scheduleNext(this)
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 850)
                } else {
                    feedbackView.setTextColor(Color.parseColor("#27D67B"))
                    feedbackView.text = "✅ 答对了！还需连对 ${requiredStreak - correctCount} 题"
                    Handler(Looper.getMainLooper()).postDelayed({ showNextQuestion() }, 550)
                }
            } else {
                b.setBackgroundColor(Color.parseColor("#7A2020"))
                correctCount = 0
                renderDots()
                val tip = if (q.analysis.isNotBlank())
                    "❌ 答错了，计数清零。${q.analysis}" else "❌ 答错了，计数清零"
                feedbackView.setTextColor(Color.parseColor("#FF5B5B"))
                feedbackView.text = tip
                Handler(Looper.getMainLooper()).postDelayed({ showNextQuestion() }, 900)
            }
        }
        return b
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() { /* 禁用 */ }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
