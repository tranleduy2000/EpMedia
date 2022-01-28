package ffmpeg;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FfmpegLogParser {
    //size=    2560kB time=00:02:48.32 bitrate= 124.6kbits/s speed=37.4x
    private static final Pattern timePattern = Pattern.compile("time=([0-9]{2}):([0-9]{2}):([0-9]{2}).([0-9]{2})");
    private static final String TAG = "LogParser";

    public static Long parseTime(String message) {
        Matcher matcher = timePattern.matcher(message);
        if (matcher.find()) {
            // Log.d(TAG, "parseTime: " + matcher.group());
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            int seconds = Integer.parseInt(matcher.group(3));
            int millis = Integer.parseInt(matcher.group(4));
            return millis
                    + TimeUnit.SECONDS.toMillis(seconds)
                    + TimeUnit.MINUTES.toMillis(minutes)
                    + TimeUnit.HOURS.toMillis(hours);
        }
        return null;
    }
}
