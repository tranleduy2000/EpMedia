package ffmpeg

import java.lang.Exception

/**
 * Created by YangJie on 2017/5/18.
 */
interface OnFfmpegProcessCallback {
    fun onSuccess()
    fun onFailure(error: Exception?)
    fun onProgress(duration: Long)
}