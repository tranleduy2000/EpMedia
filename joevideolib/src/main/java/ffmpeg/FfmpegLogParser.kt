package ffmpeg

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FfmpegLogParser {
    //size=    2560kB time=00:02:48.32 bitrate= 124.6kbits/s speed=37.4x
    private val timePattern = Pattern.compile("time=([0-9]{2}):([0-9]{2}):([0-9]{2}).([0-9]{2})")
    private const val TAG = "LogParser"

    @JvmStatic
    fun parseTime(message: String): Long? {
        val matcher = timePattern.matcher(message)
        if (matcher.find()) {
            // Log.d(TAG, "parseTime: " + matcher.group());
            val hours = matcher.group(1)?.toInt()
            val minutes = matcher.group(2)?.toInt()
            val seconds = matcher.group(3)?.toInt()
            val millis = matcher.group(4)?.toInt()
            if (hours != null && minutes != null && seconds != null && millis != null) {
                return (millis + TimeUnit.SECONDS.toMillis(seconds.toLong()) + TimeUnit.MINUTES.toMillis(minutes.toLong()) + TimeUnit.HOURS.toMillis(
                        hours.toLong()
                ))
            }
        }
        return null
    }
}