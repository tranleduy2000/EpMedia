package VideoHandle

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import ffmpeg.Ffmpeg
import ffmpeg.OnFfmpegProcessCallback
import utils.CmdUtils
import utils.FileUtils
import utils.TrackUtils
import utils.VideoUitls
import java.io.File
import java.io.IOException

/**
 * 视频编辑器
 * Created by YangJie on 2017/5/18.
 */
object EpEditor {
    var DEBUG = false
    private const val TAG = "EpEditor"
    private const val DEFAULT_WIDTH = 480 //默认输出宽度
    private const val DEFAULT_HEIGHT = 360 //默认输出高度

    /**
     * 处理单个视频
     *
     * @param epVideo      需要处理的视频
     * @param outputOption 输出选项配置
     */
    fun exec(
        epVideo: EpVideo,
        outputOption: OutputOption,
        processCallback: OnFfmpegProcessCallback,
    ) {
        var isFilter = false
        val epDraws = epVideo.epDraws
        //开始处理
        val cmd = CmdList()
        cmd.append("-y")
        if (epVideo.videoClip) {
            cmd.append("-ss").append(epVideo.clipStart).append("-t").append(epVideo.clipDuration)
                .append("-accurate_seek")
        }
        cmd.append("-i").append(CmdUtils.quote(epVideo.videoPath))
        //添加图片或者动图
        if (epDraws.size > 0) {
            for (i in epDraws.indices) {
                if (epDraws[i].isAnimation) {
                    cmd.append("-ignore_loop")
                    cmd.append(0)
                }
                cmd.append("-i").append(epDraws[i].picPath)
            }
            cmd.append("-filter_complex")
            val filter_complex = StringBuilder()
            filter_complex.append("[0:v]")
                .append(if (epVideo.filters != null) epVideo.filters.toString() + "," else "")
                .append("scale=").append(if (outputOption.getWidth() == 0) "iw" else outputOption.getWidth())
                .append(":")
                .append(if (outputOption.getHeight() == 0) "ih" else outputOption.getHeight())
                .append(if (outputOption.getWidth() == 0) "" else ",setdar=" + outputOption.getSar())
                .append("[outv0];")
            for (i in epDraws.indices) {
                filter_complex.append("[").append(i + 1).append(":0]").append(epDraws[i].picFilter)
                    .append("scale=").append(
                        epDraws[i].picWidth).append(":")
                    .append(epDraws[i].picHeight).append("[outv").append(i + 1).append("];")
            }
            for (i in epDraws.indices) {
                if (i == 0) {
                    filter_complex.append("[outv").append(i).append("]").append("[outv")
                        .append(i + 1).append("]")
                } else {
                    filter_complex.append("[outo").append(i - 1).append("]").append("[outv")
                        .append(i + 1).append("]")
                }
                filter_complex.append("overlay=").append(epDraws[i].picX).append(":")
                    .append(epDraws[i].picY)
                    .append(epDraws[i].time)
                if (epDraws[i].isAnimation) {
                    filter_complex.append(":shortest=1")
                }
                if (i < epDraws.size - 1) {
                    filter_complex.append("[outo").append(i).append("];")
                }
            }
            cmd.append(filter_complex.toString())
            isFilter = true
        } else {
            val filter_complex = StringBuilder()
            if (epVideo.filters != null) {
                cmd.append("-filter_complex")
                filter_complex.append(epVideo.filters)
                isFilter = true
            }
            //设置输出分辨率
            if (outputOption.getWidth() != 0) {
                if (epVideo.filters != null) {
                    filter_complex.append(",scale=").append(outputOption.getWidth()).append(":")
                        .append(outputOption.getHeight())
                        .append(",setdar=").append(outputOption.getSar())
                } else {
                    cmd.append("-filter_complex")
                    filter_complex.append("scale=").append(outputOption.getWidth()).append(":")
                        .append(outputOption.getHeight())
                        .append(",setdar=").append(outputOption.getSar())
                    isFilter = true
                }
            }
            if (filter_complex.toString() != "") {
                cmd.append(filter_complex.toString())
            }
        }

        //输出选项
        cmd.append(outputOption.outputInfo.split(" ").toTypedArray())
        if (!isFilter && outputOption.outputInfo.isEmpty()) {
            cmd.append("-vcodec")
            cmd.append("copy")
            cmd.append("-acodec")
            cmd.append("copy")
        } else {
            cmd.append("-preset")
            cmd.append("superfast")
        }
        cmd.append(outputOption.outPath)
        Ffmpeg.execCmd(cmd, processCallback)
    }

    // https://ottverse.com/change-resolution-resize-scale-video-using-ffmpeg/
    fun resizeVideo(
        inputFile: String, outputFile: String,
        width: Int?, height: Int?,
        onFfmpegProcessCallback: OnFfmpegProcessCallback,
    ) {
        // ffmpeg -i input.mp4 -vf scale=320:-1 output.mp4
        val cmd = CmdList()
        cmd.append("-y").append("-i").append(CmdUtils.quote(inputFile))
        cmd.append("-vf")
        cmd.append("scale=${width ?: -1}:${height ?: -1}")
        cmd.append(CmdUtils.quote(outputFile))
        Ffmpeg.execCmd(cmd, onFfmpegProcessCallback)
    }

    /**
     * 合并多个视频
     *
     * @param epVideos     需要合并的视频集合
     * @param outputOption 输出选项配置
     */
    fun merge(
        epVideos: List<EpVideo>,
        outputOption: OutputOption,
        processCallback: OnFfmpegProcessCallback,
    ) {
        //检测是否有无音轨视频
        var isNoAudioTrack = false
        for (epVideo in epVideos) {
            val mediaExtractor = MediaExtractor()
            try {
                mediaExtractor.setDataSource(epVideo.videoPath)
            } catch (e: IOException) {
                e.printStackTrace()
                return
            }
            val at = TrackUtils.selectAudioTrack(mediaExtractor)
            if (at == -1) {
                isNoAudioTrack = true
                mediaExtractor.release()
                break
            }
            mediaExtractor.release()
        }
        //设置默认宽高
        outputOption.setWidth(if (outputOption.getWidth() == 0) DEFAULT_WIDTH else outputOption.getWidth())
        outputOption.setHeight(if (outputOption.getHeight() == 0) DEFAULT_HEIGHT else outputOption.getHeight())
        //判断数量
        if (epVideos.size > 1) {
            val cmd = CmdList()
            cmd.append("-y")
            //添加输入标示
            for (e in epVideos) {
                if (e.videoClip) {
                    cmd.append("-ss").append(e.clipStart).append("-t").append(e.clipDuration)
                        .append("-accurate_seek")
                }
                cmd.append("-i").append(CmdUtils.quote(e.videoPath))
            }
            for (e in epVideos) {
                val epDraws = e.epDraws
                if (epDraws.size > 0) {
                    for (ep in epDraws) {
                        if (ep.isAnimation) cmd.append("-ignore_loop").append(0)
                        cmd.append("-i").append(ep.picPath)
                    }
                }
            }
            //添加滤镜标识
            cmd.append("-filter_complex")
            val filter_complex = StringBuilder()
            for (i in epVideos.indices) {
                val filter =
                    if (epVideos[i].filters == null) StringBuilder("") else epVideos[i].filters.append(
                        ",")
                filter_complex.append("[").append(i).append(":v]").append(filter).append("scale=")
                    .append(outputOption.getWidth()).append(":").append(outputOption.getHeight())
                    .append(",setdar=").append(outputOption.getSar()).append("[outv").append(i)
                    .append("];")
            }
            //添加标记和处理宽高
            var drawNum = epVideos.size //图标计数器
            for (i in epVideos.indices) {
                for (j in epVideos[i].epDraws.indices) {
                    filter_complex.append("[").append(drawNum++).append(":0]")
                        .append(epVideos[i].epDraws[j].picFilter).append("scale=")
                        .append(epVideos[i].epDraws[j].picWidth).append(":")
                        .append(epVideos[i].epDraws[j]
                            .picHeight).append("[p").append(i).append("a").append(j).append("];")
                }
            }
            //添加图标操作
            for (i in epVideos.indices) {
                for (j in epVideos[i].epDraws.indices) {
                    filter_complex.append("[outv").append(i).append("][p").append(i).append("a")
                        .append(j).append("]overlay=")
                        .append(epVideos[i].epDraws[j].picX).append(":")
                        .append(epVideos[i].epDraws[j].picY)
                        .append(epVideos[i].epDraws[j].time)
                    if (epVideos[i].epDraws[j].isAnimation) {
                        filter_complex.append(":shortest=1")
                    }
                    filter_complex.append("[outv").append(i).append("];")
                }
            }
            //开始合成视频
            for (i in epVideos.indices) {
                filter_complex.append("[outv").append(i).append("]")
            }
            filter_complex.append("concat=n=").append(epVideos.size).append(":v=1:a=0[outv]")
            //是否添加音轨
            if (!isNoAudioTrack) {
                filter_complex.append(";")
                for (i in epVideos.indices) {
                    filter_complex.append("[").append(i).append(":a]")
                }
                filter_complex.append("concat=n=").append(epVideos.size).append(":v=0:a=1[outa]")
            }
            if (filter_complex.toString() != "") {
                cmd.append(filter_complex.toString())
            }
            cmd.append("-map").append("[outv]")
            if (!isNoAudioTrack) {
                cmd.append("-map").append("[outa]")
            }
            cmd.append(outputOption.outputInfo.split(" ").toTypedArray())
            cmd.append("-preset").append("superfast").append(outputOption.outPath)
            Ffmpeg.execCmd(cmd, processCallback)
        } else {
            throw RuntimeException("Need more than one video")
        }
    }

    /**
     * 无损合并多个视频
     *
     *
     * 注意：此方法要求视频格式非常严格，需要合并的视频必须分辨率相同，帧率和码率也得相同
     *
     * @param context         Context
     * @param epVideos        需要合并的视频的集合
     * @param outputOption    输出选项
     * @param processCallback 回调监听
     */
    fun mergeByLc(
        context: Context,
        epVideos: List<EpVideo>,
        outputOption: OutputOption,
        processCallback: OnFfmpegProcessCallback,
    ) {
        val appDir = context.cacheDir.absolutePath + "/EpVideos/"
        val fileName = "ffmpeg_concat.txt"
        val videos: MutableList<String> = ArrayList()
        for (e in epVideos) {
            videos.add(e.videoPath)
        }
        FileUtils.writeTxtToFile(videos, appDir, fileName)
        val cmd = CmdList()
        cmd.append("-y").append("-f").append("concat").append("-safe")
            .append("0").append("-i").append(appDir + fileName)
            .append("-c").append("copy").append(CmdUtils.quote(outputOption.outPath))
        var duration: Long = 0
        for (ep in epVideos) {
            val d = VideoUitls.getDuration(ep.videoPath)
            duration += if (d != 0L) {
                d
            } else {
                break
            }
        }
        Ffmpeg.execCmd(cmd, processCallback)
    }

    /**
     * 添加背景音乐
     *
     * @param inputVideo      视频文件
     * @param inputAudio      音频文件
     * @param outputFile      输出路径
     * @param videoVolume     视频原声音音量(例:0.7为70%)
     * @param audioVolume     背景音乐音量(例:1.5为150%)
     * @param processCallback 回调监听
     */
    fun music(
        inputVideo: String?,
        inputAudio: String?,
        outputFile: String?,
        videoVolume: Float,
        audioVolume: Float,
        processCallback: OnFfmpegProcessCallback,
    ) {
        val mediaExtractor = MediaExtractor()
        try {
            mediaExtractor.setDataSource(inputVideo!!)
        } catch (e: IOException) {
            processCallback.onFailure(null)
            e.printStackTrace()
            return
        }
        val at = TrackUtils.selectAudioTrack(mediaExtractor)
        val cmd = CmdList()
        cmd.append("-y")
            .append("-i").append(CmdUtils.quote(inputVideo))
        if (at == -1) {
            val vt = TrackUtils.selectVideoTrack(mediaExtractor)
            val duration = mediaExtractor.getTrackFormat(vt).getLong(MediaFormat.KEY_DURATION)
                .toFloat() / 1000f / 1000f
            cmd.append("-ss").append("0").append("-t").append(duration)
                .append("-i").append(CmdUtils.quote(inputAudio))
                .append("-acodec").append("copy")
                .append("-vcodec").append("copy")
        } else {
            cmd.append("-i").append(CmdUtils.quote(inputAudio)).append("-filter_complex")
                .append("[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=$videoVolume[a0];[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=$audioVolume[a1];[a0][a1]amix=inputs=2:duration=first[aout]")
                .append("-map").append("[aout]").append("-ac").append("2").append("-c:v")
                .append("copy").append("-map").append("0:v:0")
        }
        cmd.append(CmdUtils.quote(outputFile))
        mediaExtractor.release()
        Ffmpeg.execCmd(cmd, processCallback)
    }

    /**
     * 音视频分离
     *
     * @param inputFile       视频文件
     * @param outFile         输出文件路径
     * @param format          输出类型
     * @param processCallback 回调监听
     */
    fun demuxer(
        inputFile: String?,
        outFile: String?,
        format: Format?,
        processCallback: OnFfmpegProcessCallback,
    ) {
        // https://superuser.com/questions/609740/extracting-wav-from-mp4-while-preserving-the-highest-possible-quality
        val cmd = CmdList()
        cmd.append("-y")
            .append("-i").append(CmdUtils.quote(inputFile))
        when (format) {
            Format.MP3 -> {
                // To remove the video
                cmd.append("-vn")
                // Other -acodec options are mp3 flac m4a
                cmd.append("-acodec").append("libmp3lame")
                cmd.append("-async").append("1")
            }
            Format.AAC -> {
                // To remove the video
                cmd.append("-vn")
                // Other -acodec options are mp3 flac m4a
                cmd.append("-c:a aac")
                cmd.append("-async").append("1")
            }
            Format.WAV -> {
                // To remove the video
                cmd.append("-vn")
                // Other -acodec options are mp3 flac m4a
                cmd.append("-acodec").append("pcm_s16le")
                cmd.append("-async").append("1")
            }
            Format.MP4 -> cmd.append("-vcodec").append("copy").append("-an")
        }
        cmd.append(CmdUtils.quote(outFile))
        Ffmpeg.execCmd(cmd, processCallback)
    }

    /**
     * 音视频倒放
     *
     * @param inputFile       视频文件
     * @param outputFile      输出文件路径
     * @param vr              是否视频倒放
     * @param ar              是否音频倒放
     * @param processCallback 回调监听
     */
    fun reverse(
        inputFile: String?,
        outputFile: String?,
        vr: Boolean,
        ar: Boolean,
        processCallback: OnFfmpegProcessCallback,
    ) {
        if (!vr && !ar) {
            Log.e("ffmpeg", "parameter error")
            processCallback.onFailure(null)
            return
        }
        val cmd = CmdList()
        cmd.append("-y").append("-i").append(CmdUtils.quote(inputFile)).append("-filter_complex")
        var filter = ""
        if (vr) {
            filter += "[0:v]reverse[v];"
        }
        if (ar) {
            filter += "[0:a]areverse[a];"
        }
        cmd.append(filter.substring(0, filter.length - 1))
        if (vr) {
            cmd.append("-map").append("[v]")
        }
        if (ar) {
            cmd.append("-map").append("[a]")
        }
        if (ar && !vr) {
            cmd.append("-acodec").append("libmp3lame")
        }
        cmd.append("-preset").append("superfast").append(CmdUtils.quote(outputFile))
        Ffmpeg.execCmd(cmd, processCallback)
    }

    @Throws(IOException::class)
    fun changeSpeed(
        inputFile: String,
        outputFile: String?,
        times: Float,
        pts: PTS?,
        processCallback: OnFfmpegProcessCallback,
    ) {
        checkFileExist(inputFile)
        if (times < 0.25f || times > 4.0f) {
            Log.e("ffmpeg", "times can only be 0.25 to 4")
            processCallback.onFailure(null)
            return
        }
        val cmd = CmdList()
        cmd.append("-y") // force override ouput file
            .append("-i").append(CmdUtils.quote(inputFile))
        var t = "atempo=$times"
        if (times < 0.5f) {
            t = "atempo=0.5,atempo=" + times / 0.5f
        } else if (times > 2.0f) {
            t = "atempo=2.0,atempo=" + times / 2.0f
        }
        Log.v("ffmpeg", "atempo:$t")
        when (pts) {
            PTS.VIDEO -> cmd.append("-filter_complex").append("[0:v]setpts=" + 1 / times + "*PTS")
                .append("-an")
            PTS.AUDIO -> cmd.append("-filter:a").append(t)
            PTS.ALL -> cmd.append("-filter_complex")
                .append("[0:v]setpts=" + 1 / times + "*PTS[v];[0:a]" + t + "[a]")
                .append("-map").append("[v]").append("-map").append("[a]")
        }
        // Keep video quality
        // https://stackoverflow.com/questions/6503894/ffmpeg-convert-video-without-losing-resolution
        cmd.append("-q").append("1")

        // TODO fix cmd.append("-preset").append("superfast");
        cmd.append(CmdUtils.quote(outputFile))
        Ffmpeg.execCmd(cmd, processCallback)
    }

    @Throws(IOException::class)
    private fun checkFileExist(inputFile: String) {
        val file = File(inputFile)
        if (!file.exists()) {
            throw IOException("File not found $inputFile")
        }
        if (!file.canRead()) {
            throw IOException("Cannot read file $inputFile")
        }
        if (file.isDirectory) {
            throw IOException("File is directory $inputFile")
        }
    }

    /**
     * 视频转图片
     *
     * @param videoin         音视频文件
     * @param out             输出路径
     * @param w               输出图片宽度
     * @param h               输出图片高度
     * @param rate            每秒视频生成图片数
     * @param processCallback 回调接口
     */
    fun video2pic(
        videoin: String?,
        out: String?,
        w: Int,
        h: Int,
        rate: Float,
        processCallback: OnFfmpegProcessCallback,
    ) {
        if (w <= 0 || h <= 0) {
            Log.e("ffmpeg", "width and height must greater than 0")
            processCallback.onFailure(null)
            return
        }
        if (rate <= 0) {
            Log.e("ffmpeg", "rate must greater than 0")
            processCallback.onFailure(null)
            return
        }
        val cmd = CmdList()
        cmd.append("-y").append("-i").append(videoin)
            .append("-r").append(rate).append("-s").append(w.toString() + "x" + h).append("-q:v")
            .append(2)
            .append("-f").append("image2").append("-preset").append("superfast").append(out)
        val d = VideoUitls.getDuration(videoin)
        Ffmpeg.execCmd(cmd, processCallback)
    }

    /**
     * 图片转视频
     *
     * @param videoin         视频文件
     * @param out             输出路径
     * @param w               输出视频宽度
     * @param h               输出视频高度
     * @param rate            输出视频帧率
     * @param processCallback 回调接口
     */
    fun pic2video(
        videoin: String?,
        out: String?,
        w: Int,
        h: Int,
        rate: Float,
        processCallback: OnFfmpegProcessCallback,
    ) {
        if (w < 0 || h < 0) {
            Log.e("ffmpeg", "width and height must greater than 0")
            processCallback.onFailure(null)
            return
        }
        if (rate <= 0) {
            Log.e("ffmpeg", "rate must greater than 0")
            processCallback.onFailure(null)
            return
        }
        val cmd = CmdList()
        cmd.append("-y").append("-f").append("image2").append("-i").append(videoin)
            .append("-vcodec").append("libx264")
            .append("-r").append(rate)
        //				.append("-b").append("10M");
        if (w > 0 && h > 0) {
            cmd.append("-s").append(w.toString() + "x" + h)
        }
        cmd.append(out)
        val d = VideoUitls.getDuration(videoin)
        Ffmpeg.execCmd(cmd, processCallback)
    }

    enum class Format {
        MP3, MP4, WAV, AAC
    }

    enum class PTS {
        VIDEO, AUDIO, ALL
    }

    /**
     * 输出选项设置
     */
    class OutputOption(
        //输出路径
        var outPath: String,
    ) {
        var frameRate = 0 //帧率
        var bitRate = 0 //比特率(一般设置10M)
        var outFormat = "" //输出格式(目前暂时只支持mp4,x264,mp3,gif)
        private var width = 0 //输出宽度
        private var height = 0 //输出高度
        private var sar = 6 //输出宽高比

        /**
         * 获取宽高比
         *
         * @return 1
         */
        fun getSar(): String {
            val res: String
            res = when (sar) {
                ONE_TO_ONE -> "1/1"
                FOUR_TO_THREE -> "4/3"
                THREE_TO_FOUR -> "3/4"
                SIXTEEN_TO_NINE -> "16/9"
                NINE_TO_SIXTEEN -> "9/16"
                else -> "$width/$height"
            }
            return res
        }

        fun setSar(sar: Int) {
            this.sar = sar
        }

        /**
         * 获取输出信息
         *
         * @return 1
         */
        val outputInfo: String
            get() {
                val res = StringBuilder()
                if (frameRate != 0) {
                    res.append(" -r ").append(frameRate)
                }
                if (bitRate != 0) {
                    res.append(" -b ").append(bitRate).append("M")
                }
                if (!outFormat.isEmpty()) {
                    res.append(" -f ").append(outFormat)
                }
                return res.toString()
            }

        /**
         * 设置宽度
         *
         * @param width 宽
         */
        fun setWidth(width: Int) {
            var width = width
            if (width % 2 != 0) width -= 1
            this.width = width
        }

        fun getWidth(): Int {
            return width;
        }

        /**
         * 设置高度
         *
         * @param height 高
         */
        fun setHeight(height: Int) {
            var height = height
            if (height % 2 != 0) height -= 1
            this.height = height
        }

        fun getHeight(): Int {
            return height;
        }

        companion object {
            const val ONE_TO_ONE = 1 // 1:1
            const val FOUR_TO_THREE = 2 // 4:3
            const val SIXTEEN_TO_NINE = 3 // 16:9
            const val NINE_TO_SIXTEEN = 4 // 9:16
            const val THREE_TO_FOUR = 5 // 3:4
        }
    }
}