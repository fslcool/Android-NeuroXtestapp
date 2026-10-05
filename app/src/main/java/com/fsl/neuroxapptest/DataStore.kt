package com.fsl.neuroxapptest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 范式 ID ↔ 名字 映射。
 */
object ParadigmMapping {

    private val idToName = mapOf(
        "1" to "问卷",
        "2" to "IAT",
        "3" to "快乐情绪诱发",
        "4" to "悲伤情绪诱发",
        "5" to "愤怒情绪诱发",
        "6" to "恐惧情绪诱发",
        "7" to "惊讶情绪诱发",
        "8" to "厌恶情绪诱发",
        "9" to "焦虑情绪诱发",
        "10" to "嫉妒情绪诱发",
        "11" to "羞愧情绪诱发"
    )

    /** ID → 名字 */
    fun getName(id: String): String = idToName[id] ?: id

    /** 名字 → ID */
    fun getId(name: String): String =
        idToName.entries.firstOrNull { it.value == name }?.key ?: ""
}

/**
 * 数据存储工具类。
 *
 * 用 SharedPreferences 存 JSON 数组。
 *
 * 两类数据：
 *   1. 当前会话的"被试信息"（键：current_subject）
 *   2. 历史"实验记录"列表（键：records）
 */
object DataStore {

    private const val PREF_NAME = "neurox_data"
    private const val KEY_CURRENT_SUBJECT = "current_subject"
    private const val KEY_RECORDS = "records"

    // ============ 当前会话被试信息 ============

    data class SubjectInfo(
        val name: String,
        val id: String,
        val age: String,
        val gender: String,
        val subjectId: String,
        val paradigm: String
    )

    /**
     * 保存"当前会话"的被试信息。
     */
    fun saveCurrentSubject(context: Context, info: SubjectInfo) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("name", info.name)
            put("id", info.id)
            put("age", info.age)
            put("gender", info.gender)
            put("subjectId", info.subjectId)
            put("paradigm", info.paradigm)
        }
        sp.edit().putString(KEY_CURRENT_SUBJECT, json.toString()).apply()
    }

    /**
     * 读取"当前会话"的被试信息。
     * 没有返回 null。
     */
    fun getCurrentSubject(context: Context): SubjectInfo? {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val str = sp.getString(KEY_CURRENT_SUBJECT, null) ?: return null
        return try {
            val json = JSONObject(str)
            SubjectInfo(
                json.optString("name", ""),
                json.optString("id", ""),
                json.optString("age", ""),
                json.optString("gender", ""),
                json.optString("subjectId", ""),
                json.optString("paradigm", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ============ 实验记录列表 ============

    data class Record(
        val name: String,
        val id: String,
        val age: String,
        val gender: String,
        val subjectId: String,
        val paradigm: String,
        val dateTime: String,   // 完整日期时间 yyyy-MM-dd HH:mm:ss
        val result: String      // "正常" 或 "需关注"
    )

    /**
     * 加一条实验记录。
     * 从"当前会话被试信息"拿数据，日期用当前时间，结果随机。
     */
    fun addRecord(context: Context, dateTime: String, result: String) {
        val info = getCurrentSubject(context) ?: return

        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val recordsStr = sp.getString(KEY_RECORDS, "[]") ?: "[]"
        val arr = try { JSONArray(recordsStr) } catch (e: Exception) { JSONArray() }

        val record = JSONObject().apply {
            put("name", info.name)
            put("id", info.id)
            put("age", info.age)
            put("gender", info.gender)
            put("subjectId", info.subjectId)
            put("paradigm", info.paradigm)
            put("dateTime", dateTime)
            put("result", result)
        }
        arr.put(record)

        sp.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    /**
     * 读取所有实验记录。
     */
    fun getAllRecords(context: Context): List<Record> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val str = sp.getString(KEY_RECORDS, "[]") ?: "[]"
        val list = mutableListOf<Record>()
        try {
            val arr = JSONArray(str)
            for (i in 0 until arr.length()) {
                val json = arr.getJSONObject(i)
                list.add(
                    Record(
                        json.optString("name", ""),
                        json.optString("id", ""),
                        json.optString("age", ""),
                        json.optString("gender", ""),
                        json.optString("subjectId", ""),
                        json.optString("paradigm", ""),
                        json.optString("dateTime", ""),
                        json.optString("result", "正常")
                    )
                )
            }
        } catch (e: Exception) {
            // 忽略
        }
        return list
    }

    /**
     * 按索引读一条记录。
     * 越界返回 null。
     */
    fun getRecordAt(context: Context, index: Int): Record? {
        val list = getAllRecords(context)
        return if (index in list.indices) list[index] else null
    }
}