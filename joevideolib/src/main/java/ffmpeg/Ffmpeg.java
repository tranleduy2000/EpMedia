package ffmpeg;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;
import com.joe.joevideolib.BuildConfig;

import VideoHandle.CmdList;
import utils.CmdUtils;

public class Ffmpeg {

    public static boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "Ffmpeg";

    /**
     * 开始处理
     *
     * @param cmd              命令
     * @param onEditorListener 回调接口
     */
    public static void execCmd(CmdList cmd, final OnFfmpegProcessCallback onEditorListener) {
        String[] cmds = cmd.toArray(new String[0]);
        String command = CmdUtils.join(cmds);
        if (DEBUG) {
            Log.d(TAG, "FFmpeg command: " + command);
        }
        FFmpegKit.executeAsync(command, session -> {
            if (DEBUG) {
                Log.d(TAG, "completeCallback " + session);
                StringBuilder logs = new StringBuilder();
                for (com.arthenica.ffmpegkit.Log x : session.getAllLogs()) {
                    String s = x.getMessage();
                    logs.append(s);
                }
                Log.d(TAG, logs.toString());
            }
            SessionState state = session.getState();
            ReturnCode returnCode = session.getReturnCode();

            if (ReturnCode.isSuccess(returnCode)) {
                onEditorListener.onSuccess();
            } else {
                onEditorListener.onFailure(null);
            }
        }, log -> {
            if (DEBUG) {
                Log.d(TAG, log.getMessage());
            }
            Long duration = FfmpegLogParser.parseTime(log.getMessage());
            if (duration != null) {
                onEditorListener.onProgress(duration);
            }

        }, statistics -> {

        });
    }
}
