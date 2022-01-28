package cafe.adriel.androidaudioconverter.utils;

import java.util.List;

public class StringUtils {
    public static String join(String[] args, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            if (builder.length() != 0) {
                builder.append(delimiter);
            }
            builder.append(arg);
        }
        return builder.toString();
    }

    public static String join(List<String> args, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            if (builder.length() != 0) {
                builder.append(delimiter);
            }
            builder.append(arg);
        }
        return builder.toString();
    }

    public static String quote(String arg) {
        return "\"" + arg + "\"";
    }
}
