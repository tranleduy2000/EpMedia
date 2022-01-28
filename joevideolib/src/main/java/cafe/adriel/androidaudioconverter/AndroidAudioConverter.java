package cafe.adriel.androidaudioconverter;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;
import com.arthenica.ffmpegkit.Statistics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import cafe.adriel.androidaudioconverter.callback.IConvertCallback;
import cafe.adriel.androidaudioconverter.utils.StringUtils;
import ffmpeg.FfmpegLogParser;

public class AndroidAudioConverter {
    private static final String TAG = "AndroidAudioConverter";

    private File inputFile;
    private File outputFile;
    private IConvertCallback callback;
    private String bitRate;

    private AndroidAudioConverter() {
    }

    public static AndroidAudioConverter getInstance() {
        return new AndroidAudioConverter();
    }

    public AndroidAudioConverter setInputFile(File originalFile) {
        this.inputFile = originalFile;
        return this;
    }

    public AndroidAudioConverter setCallback(IConvertCallback callback) {
        this.callback = callback;
        return this;
    }

    public AndroidAudioConverter setBitRate(String bitRate) {
        this.bitRate = bitRate;
        return this;
    }

    public void convert() {
        if (inputFile == null || !inputFile.exists()) {
            callback.onFailure(new IOException("Input file not exists"));
            return;
        }
        if (!inputFile.canRead()) {
            callback.onFailure(new IOException("Can't read the file. Missing permission?"));
            return;
        }
        ArrayList<String> args = new ArrayList<>(
                Arrays.asList("-y", "-i", StringUtils.quote(inputFile.getAbsolutePath())));
        if (bitRate != null) {
            args.add("-b:a");
            args.add(bitRate);
        }

        // output file
        args.add(StringUtils.quote(outputFile.getAbsolutePath()));

        try {
            String cmd = StringUtils.join(args, " ");
            Log.d(TAG, "cmd = " + cmd);
            FFmpegKit.executeAsync(cmd,
                    (FFmpegSession session) -> {
                        SessionState state = session.getState();
                        ReturnCode returnCode = session.getReturnCode();
                        Log.d(TAG, String.format("FFmpeg process exited with state %s and rc %s.%s", state, returnCode, session.getFailStackTrace()));

                        if (ReturnCode.isSuccess(returnCode)) {
                            callback.onSuccess(outputFile);

                        } else {
                            callback.onFailure(new Exception(session.getFailStackTrace()));
                        }

                    }, (com.arthenica.ffmpegkit.Log log) -> {
                        Log.d(TAG, log.getMessage());

                        String message = log.getMessage();
                        Long duration = FfmpegLogParser.parseTime(message);
                        if (duration != null) {
                            callback.onProgress(duration);
                        }
                    }, (Statistics statistics) -> {

                    });
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    public AndroidAudioConverter setOutputFile(File outputFile) {
        this.outputFile = outputFile;
        return this;
    }
}