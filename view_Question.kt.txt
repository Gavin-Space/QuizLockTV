package com.huiyang.quizlocktv

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一道题。
 * type = "single"(单选) 或 "judge"(判断)
 * correct: single=正确选项下标(0基)；judge=0表示错误/false, 1表示正确/true
 */
data class Question(
    val type: String,
    val stem: String,
    val options: List<String>,   // 判断题为空列表
    val correct: Int,
    val analysis: String
) {
    val isJudge get() = type == "judge"
}

object QuizParser {
    /** 解析题库 JSON，返回题目列表；格式错误时抛 IllegalArgumentException */
    fun parse(text: String): List<Question> {
        val root = JSONObject(text.trim())
        val arr = root.optJSONArray("questions")
            ?: throw IllegalArgumentException("缺少 questions 数组")
        val list = ArrayList<Question>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val type = o.optString("type")
            val stem = o.optString("stem")
            if (stem.isBlank()) continue
            when (type) {
                "single" -> {
                    val opts = ArrayList<String>()
                    val oa = o.optJSONArray("options") ?: JSONArray()
                    for (k in 0 until oa.length()) opts.add(oa.getString(k))
                    if (opts.isEmpty()) continue
                    val ans = o.optInt("answer", -1)
                    if (ans < 0 || ans >= opts.size) continue
                    list.add(Question(type, stem, opts, ans, o.optString("analysis")))
                }
                "judge" -> {
                    val ans = if (o.optBoolean("answer", false)) 1 else 0
                    list.add(Question(type, stem, emptyList(), ans, o.optString("analysis")))
                }
                else -> { /* 跳过未知题型 */ }
            }
        }
        if (list.isEmpty()) throw IllegalArgumentException("没有解析到任何有效题目，请检查 JSON 格式")
        return list
    }
}
