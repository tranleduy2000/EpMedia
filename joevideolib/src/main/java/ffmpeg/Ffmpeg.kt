package ffmpeg

import VideoHandle.CmdList
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.joe.joevideolib.BuildConfig
import ffmpeg.FfmpegLogParser.parseTime
import utils.CmdUtils

object Ffmpeg {
    var DEBUG = BuildConfig.DEBUG
    private const val TAG = "Ffmpeg"

    /**
     * 开始处理
     *
     * @param cmd              命令
     * @param onEditorListener 回调接口
     */
    fun execCmd(cmd: CmdList, onEditorListener: OnFfmpegProcessCallback) {
        val cmds = cmd.toTypedArray()
        val command = CmdUtils.join(cmds)
        if (DEBUG) {
            Log.d(TAG, "FFmpeg command: $command")
        }
        FFmpegKit.executeAsync(command, { session: FFmpegSession ->
            if (DEBUG) {
                Log.d(TAG, "completeCallback $session")
                val logs = StringBuilder()
                for (x in session.allLogs) {
                    val s = x.message
                    logs.append(s)
                }
                Log.d(TAG, logs.toString())
            }
            val state = session.state
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                onEditorListener.onSuccess()
            } else {
                onEditorListener.onFailure(null)
            }
        }, { log: com.arthenica.ffmpegkit.Log ->
            if (DEBUG) {
                Log.d(TAG, log.message)
            }
            val duration = parseTime(log.message)
            if (duration != null) {
                onEditorListener.onProgress(duration)
            }
        }) { statistics: Statistics? -> }
    }
}