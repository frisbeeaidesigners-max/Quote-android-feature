package com.example.template.core.ui.format

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val hhmm = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dayShort = SimpleDateFormat("EE", Locale("ru"))  // Пн, Вт, Ср ...
    private val dayMonth = SimpleDateFormat("d MMM", Locale("ru"))
    // Полный месяц в genitive (как этого требует русская языковая норма для конструкции «N месяца»):
    // «12 мая», «12 мая 2025». Java Russian locale выдаёт нужную форму через MMMM автоматически.
    private val dayMonthFull = SimpleDateFormat("d MMMM", Locale("ru"))
    private val dayMonthYearFull = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

    // Сокращённые формы месяца в родительном падеже («5 Мая», «8 Апр.», «30 Мар.»). Май/Июнь/Июль
    // короткие и в genitive не сокращаются — без точки. Остальные — до 3–4 букв + точка.
    private val RU_MONTH_GEN_ABBR = arrayOf(
        "Янв.", "Фев.", "Мар.", "Апр.", "Мая", "Июн.",
        "Июл.", "Авг.", "Сент.", "Окт.", "Нояб.", "Дек.",
    )

    /** HH:mm для подписи под bubble сообщения. */
    fun formatBubbleTime(timestamp: Long): String = hhmm.format(Date(timestamp))

    /**
     * Длительность голосового / видео-сообщения в баббле. Стиль iMessage/Telegram:
     * «0:07», «1:32», «12:05». Часов не поддерживаем — для прототипа voice-сообщения
     * длиннее часа не ожидаются, при необходимости расширить.
     */
    fun formatVoiceDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    /**
     * Длительность звонка для subtitle бабла `CallMeetView`. Короткая (не склоняемая) форма:
     * «30 сек» / «2 мин» / «1 ч» / «1 ч 5 мин». Для `durationMs ≤ 0` (отмена / NoAnswer) —
     * пустая строка, чтобы бабл показал только время без запятой.
     */
    fun formatCallDuration(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        val totalSec = durationMs / 1000L
        return when {
            totalSec < 60L -> "$totalSec сек"
            totalSec < 3600L -> "${totalSec / 60L} мин"
            else -> {
                val h = totalSec / 3600L
                val m = (totalSec % 3600L) / 60L
                if (m == 0L) "$h ч" else "$h ч $m мин"
            }
        }
    }

    /** Relative phrase like "час назад" / "только что" / "2 дня назад" / "12 мая". */
    fun formatLastSeen(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diffMs = now - timestamp
        if (diffMs < 60_000L) return "только что"
        val minutes = (diffMs / 60_000L).toInt()
        if (minutes < 60) return "$minutes ${ruPlural(minutes, "минуту", "минуты", "минут")} назад"
        val hours = (diffMs / 3_600_000L).toInt()
        if (hours < 24) return "$hours ${ruPlural(hours, "час", "часа", "часов")} назад"
        val days = (diffMs / 86_400_000L).toInt()
        if (days == 1) return "вчера"
        if (days < 7) return "$days ${ruPlural(days, "день", "дня", "дней")} назад"
        return dayMonth.format(Date(timestamp))
    }

    private fun ruPlural(n: Int, one: String, few: String, many: String): String {
        val n100 = n % 100
        if (n100 in 11..14) return many
        return when (n % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    /**
     * Лейбл для разделителя дат внутри ленты сообщений (стиль Telegram):
     *   • сегодня → «Сегодня»
     *   • вчера → «Вчера»
     *   • текущий год → «12 мая»
     *   • прошлый/старше → «12 мая 2025»
     */
    fun formatDateSeparator(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        if (msgCal.isSameDay(nowCal)) return "Сегодня"
        val yesterday = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (msgCal.isSameDay(yesterday)) return "Вчера"
        val sameYear = msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        val formatter = if (sameYear) dayMonthFull else dayMonthYearFull
        return formatter.format(Date(timestamp))
    }

    fun formatChatListTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        // 1. Сегодня → HH:mm
        if (msgCal.isSameDay(nowCal)) return hhmm.format(Date(timestamp))

        // 2. Вчера → «Вчера»
        val yesterday = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (msgCal.isSameDay(yesterday)) return "Вчера"

        // 3. Текущая календарная неделя (Пн–Вс), но раньше «вчера» → день недели (Пн, Вт, …).
        // Считаем неделю с понедельника независимо от системной локали — иначе на устройствах
        // с en-US воскресенье ушло бы в первый день недели, и в понедельник мы бы показывали
        // «Вс» вместо ожидаемого «5 Мая».
        val weekStart = (nowCal.clone() as Calendar).startOfMondayWeek()
        if (timestamp >= weekStart.timeInMillis) {
            return dayShort.format(Date(timestamp)).replaceFirstChar { it.uppercase() }
        }

        val day = msgCal.get(Calendar.DAY_OF_MONTH)
        val monthAbbr = RU_MONTH_GEN_ABBR[msgCal.get(Calendar.MONTH)]
        val msgYear = msgCal.get(Calendar.YEAR)

        // 4. Текущий год → «5 Мая», «8 Апр.»
        if (msgYear == nowCal.get(Calendar.YEAR)) return "$day $monthAbbr"

        // 5. Прошлый/более ранний год → «5 Дек. 2025 г.»
        return "$day $monthAbbr $msgYear г."
    }

    private fun Calendar.isSameDay(other: Calendar): Boolean =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    private fun Calendar.startOfMondayWeek(): Calendar = apply {
        // DAY_OF_WEEK: Вс=1, Пн=2, … Сб=7. Сдвиг от понедельника: (dow + 5) % 7.
        val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
