package utils;

public class CmdUtils {
    public static String join(String[] cmds) {
        StringBuilder s = new StringBuilder();
        for (String cmd : cmds) {
            if (s.length() != 0) {
                s.append(" ");
            }
            s.append(cmd);
        }
        return s.toString();
    }

    public static String quote(String path) {
        // replace " with \" add quote
        return "\"" + path.replace("\"", "\\\"") + "\"";
    }
}
