package com.taxiapp.driver.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Єдиний утилітний клас парсингу та форматування дат для історії поїздок та гаманця.
 */
object DateFormatter {

    private val ISO_PATTERNS = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    )

    private val MONTHS_SHORT_UK = arrayOf(
        "січ.", "лют.", "бер.", "квіт.", "трав.", "черв.",
        "лип.", "серп.", "вер.", "жовт.", "лист.", "груд."
    )

    private val TIME_REGEX = Regex("(\\d{2}:\\d{2})")

    fun parseIsoDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        for (pattern in ISO_PATTERNS) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(clean.take(26))
                if (parsed != null) return parsed
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Форматує дату у стилі "31 серп. 19:47" для карток і деталей історії замовлень.
     */
    fun formatShortDateWithTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "---"
        val clean = raw.trim()
        val parsed = parseIsoDate(clean) ?: return clean.take(16).replace('T', ' ')

        val cal = Calendar.getInstance().apply { time = parsed }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(parsed)
        val monthName = MONTHS_SHORT_UK.getOrElse(monthIdx) { "" }
        return "$day $monthName $timeStr"
    }

    /**
     * Форматує дату заголовка транзакцій у стилі "31 серп. 2026" для гаманця.
     */
    fun formatDateHeaderWithYear(date: Date?, rawFallback: String): String {
        if (date == null) return rawFallback.take(10)
        val cal = Calendar.getInstance().apply { time = date }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        val monthName = MONTHS_SHORT_UK.getOrElse(monthIdx) { "" }
        return "$day $monthName $year"
    }

    fun extractTimeFallback(raw: String): String {
        val match = TIME_REGEX.find(raw)
        return match?.groupValues?.get(1) ?: raw
    }
}
