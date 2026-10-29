package app.northvane.weather.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Time formatting in the forecast location's own time zone. */
class TimeFormat(private val zone: ZoneId, is24Hour: Boolean) {
    private val locale = Locale.getDefault()
    private val hourPattern = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h a", locale)
    private val timePattern = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
    private val dayTimePattern = DateTimeFormatter.ofPattern(if (is24Hour) "EEE HH:mm" else "EEE h:mm a", locale)

    fun hour(instant: Instant): String = hourPattern.format(instant.atZone(zone))
    fun time(instant: Instant): String = timePattern.format(instant.atZone(zone))
    fun dayTime(instant: Instant): String = dayTimePattern.format(instant.atZone(zone))
    fun today(): LocalDate = LocalDate.now(zone)

    fun dayName(date: LocalDate): String = when (date) {
        today() -> "Today"
        today().minusDays(1) -> "Tonight"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    }

    /** Short weekday name, never "Today" (for compact day rows). */
    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun monthDay(date: LocalDate): String = DateTimeFormatter.ofPattern("MMM d", locale).format(date)

    /** Shows the time with a day prefix only when it isn't today. */
    fun smart(instant: Instant): String =
        if (instant.atZone(zone).toLocalDate() == today()) time(instant) else dayTime(instant)

    companion object {
        fun ago(then: Instant, now: Instant = Instant.now()): String {
            val minutes = Duration.between(then, now).toMinutes()
            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "$minutes min ago"
                minutes < 48 * 60 -> "${minutes / 60} h ago"
                else -> "${minutes / (24 * 60)} days ago"
            }
        }
    }
}
