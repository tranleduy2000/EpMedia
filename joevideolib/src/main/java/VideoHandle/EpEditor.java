package VideoHandle;

import static ffmpeg.Ffmpeg.execCmd;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ffmpeg.OnFfmpegProcessCallback;
import utils.CmdUtils;
import utils.FileUtils;
import utils.TrackUtils;
import utils.VideoUitls;

/**
 * 视频编辑器
 * Created by YangJie on 2017/5/18.
 */

public class EpEditor {

    public static boolean DEBUG = false;

    private static final String TAG = "EpEditor";
    private static final int DEFAULT_WIDTH = 480;//默认输出宽度
    private static final int DEFAULT_HEIGHT = 360;//默认输出高度

    public enum Format {
        MP3, MP4, WAV, AAC;
    }

    public enum PTS {
        VIDEO, AUDIO, ALL
    }

    private EpEditor() {
    }

    /**
     * 处理单个视频
     *
     * @param epVideo      需要处理的视频
     * @param outputOption 输出选项配置
     */
    public static void exec(EpVideo epVideo, OutputOption outputOption, OnFfmpegProcessCallback processCallback) {
        boolean isFilter = false;
        ArrayList<EpDraw> epDraws = epVideo.getEpDraws();
        //开始处理
        CmdList cmd = new CmdList();
        cmd.append("-y");
        if (epVideo.getVideoClip()) {
            cmd.append("-ss").append(epVideo.getClipStart()).append("-t").append(epVideo.getClipDuration()).append("-accurate_seek");
        }
        cmd.append("-i").append(CmdUtils.quote(epVideo.getVideoPath()));
        //添加图片或者动图
        if (epDraws.size() > 0) {
            for (int i = 0; i < epDraws.size(); i++) {
                if (epDraws.get(i).isAnimation()) {
                    cmd.append("-ignore_loop");
                    cmd.append(0);
                }
                cmd.append("-i").append(epDraws.get(i).getPicPath());
            }
            cmd.append("-filter_complex");
            StringBuilder filter_complex = new StringBuilder();
            filter_complex.append("[0:v]").append(epVideo.getFilters() != null ? epVideo.getFilters() + "," : "")
                    .append("scale=").append(outputOption.width == 0 ? "iw" : outputOption.width).append(":")
                    .append(outputOption.height == 0 ? "ih" : outputOption.height)
                    .append(outputOption.width == 0 ? "" : ",setdar=" + outputOption.getSar()).append("[outv0];");
            for (int i = 0; i < epDraws.size(); i++) {
                filter_complex.append("[").append(i + 1).append(":0]").append(epDraws.get(i).getPicFilter()).append("scale=").append(epDraws.get(i).getPicWidth()).append(":")
                        .append(epDraws.get(i).getPicHeight()).append("[outv").append(i + 1).append("];");
            }
            for (int i = 0; i < epDraws.size(); i++) {
                if (i == 0) {
                    filter_complex.append("[outv").append(i).append("]").append("[outv").append(i + 1).append("]");
                } else {
                    filter_complex.append("[outo").append(i - 1).append("]").append("[outv").append(i + 1).append("]");
                }
                filter_complex.append("overlay=").append(epDraws.get(i).getPicX()).append(":").append(epDraws.get(i).getPicY())
                        .append(epDraws.get(i).getTime());
                if (epDraws.get(i).isAnimation()) {
                    filter_complex.append(":shortest=1");
                }
                if (i < epDraws.size() - 1) {
                    filter_complex.append("[outo").append(i).append("];");
                }
            }
            cmd.append(filter_complex.toString());
            isFilter = true;
        } else {
            StringBuilder filter_complex = new StringBuilder();
            if (epVideo.getFilters() != null) {
                cmd.append("-filter_complex");
                filter_complex.append(epVideo.getFilters());
                isFilter = true;
            }
            //设置输出分辨率
            if (outputOption.width != 0) {
                if (epVideo.getFilters() != null) {
                    filter_complex.append(",scale=").append(outputOption.width).append(":").append(outputOption.height)
                            .append(",setdar=").append(outputOption.getSar());
                } else {
                    cmd.append("-filter_complex");
                    filter_complex.append("scale=").append(outputOption.width).append(":").append(outputOption.height)
                            .append(",setdar=").append(outputOption.getSar());
                    isFilter = true;
                }
            }
            if (!filter_complex.toString().equals("")) {
                cmd.append(filter_complex.toString());
            }
        }

        //输出选项
        cmd.append(outputOption.getOutputInfo().split(" "));
        if (!isFilter && outputOption.getOutputInfo().isEmpty()) {
            cmd.append("-vcodec");
            cmd.append("copy");
            cmd.append("-acodec");
            cmd.append("copy");
        } else {
            cmd.append("-preset");
            cmd.append("superfast");
        }
        cmd.append(outputOption.outPath);
        execCmd(cmd, processCallback);
    }

    /**
     * 合并多个视频
     *
     * @param epVideos     需要合并的视频集合
     * @param outputOption 输出选项配置
     */
    public static void merge(List<EpVideo> epVideos, OutputOption outputOption, OnFfmpegProcessCallback processCallback) {
        //检测是否有无音轨视频
        boolean isNoAudioTrack = false;
        for (EpVideo epVideo : epVideos) {
            MediaExtractor mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(epVideo.getVideoPath());
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            int at = TrackUtils.selectAudioTrack(mediaExtractor);
            if (at == -1) {
                isNoAudioTrack = true;
                mediaExtractor.release();
                break;
            }
            mediaExtractor.release();
        }
        //设置默认宽高
        outputOption.width = outputOption.width == 0 ? DEFAULT_WIDTH : outputOption.width;
        outputOption.height = outputOption.height == 0 ? DEFAULT_HEIGHT : outputOption.height;
        //判断数量
        if (epVideos.size() > 1) {
            CmdList cmd = new CmdList();
            cmd.append("-y");
            //添加输入标示
            for (EpVideo e : epVideos) {
                if (e.getVideoClip()) {
                    cmd.append("-ss").append(e.getClipStart()).append("-t").append(e.getClipDuration()).append("-accurate_seek");
                }
                cmd.append("-i").append(CmdUtils.quote(e.getVideoPath()));
            }
            for (EpVideo e : epVideos) {
                ArrayList<EpDraw> epDraws = e.getEpDraws();
                if (epDraws.size() > 0) {
                    for (EpDraw ep : epDraws) {
                        if (ep.isAnimation()) cmd.append("-ignore_loop").append(0);
                        cmd.append("-i").append(ep.getPicPath());
                    }
                }
            }
            //添加滤镜标识
            cmd.append("-filter_complex");
            StringBuilder filter_complex = new StringBuilder();
            for (int i = 0; i < epVideos.size(); i++) {
                StringBuilder filter = epVideos.get(i).getFilters() == null ? new StringBuilder("") : epVideos.get(i).getFilters().append(",");
                filter_complex.append("[").append(i).append(":v]").append(filter).append("scale=").append(outputOption.width).append(":").append(outputOption.height)
                        .append(",setdar=").append(outputOption.getSar()).append("[outv").append(i).append("];");
            }
            //添加标记和处理宽高
            int drawNum = epVideos.size();//图标计数器
            for (int i = 0; i < epVideos.size(); i++) {
                for (int j = 0; j < epVideos.get(i).getEpDraws().size(); j++) {
                    filter_complex.append("[").append(drawNum++).append(":0]").append(epVideos.get(i).getEpDraws().get(j).getPicFilter()).append("scale=")
                            .append(epVideos.get(i).getEpDraws().get(j).getPicWidth()).append(":").append(epVideos.get(i).getEpDraws().get(j)
                            .getPicHeight()).append("[p").append(i).append("a").append(j).append("];");
                }
            }
            //添加图标操作
            for (int i = 0; i < epVideos.size(); i++) {
                for (int j = 0; j < epVideos.get(i).getEpDraws().size(); j++) {
                    filter_complex.append("[outv").append(i).append("][p").append(i).append("a").append(j).append("]overlay=")
                            .append(epVideos.get(i).getEpDraws().get(j).getPicX()).append(":")
                            .append(epVideos.get(i).getEpDraws().get(j).getPicY())
                            .append(epVideos.get(i).getEpDraws().get(j).getTime());
                    if (epVideos.get(i).getEpDraws().get(j).isAnimation()) {
                        filter_complex.append(":shortest=1");
                    }
                    filter_complex.append("[outv").append(i).append("];");
                }
            }
            //开始合成视频
            for (int i = 0; i < epVideos.size(); i++) {
                filter_complex.append("[outv").append(i).append("]");
            }
            filter_complex.append("concat=n=").append(epVideos.size()).append(":v=1:a=0[outv]");
            //是否添加音轨
            if (!isNoAudioTrack) {
                filter_complex.append(";");
                for (int i = 0; i < epVideos.size(); i++) {
                    filter_complex.append("[").append(i).append(":a]");
                }
                filter_complex.append("concat=n=").append(epVideos.size()).append(":v=0:a=1[outa]");
            }
            if (!filter_complex.toString().equals("")) {
                cmd.append(filter_complex.toString());
            }
            cmd.append("-map").append("[outv]");
            if (!isNoAudioTrack) {
                cmd.append("-map").append("[outa]");
            }
            cmd.append(outputOption.getOutputInfo().split(" "));
            cmd.append("-preset").append("superfast").append(outputOption.outPath);
            execCmd(cmd, processCallback);
        } else {
            throw new RuntimeException("Need more than one video");
        }
    }

    /**
     * 无损合并多个视频
     * <p>
     * 注意：此方法要求视频格式非常严格，需要合并的视频必须分辨率相同，帧率和码率也得相同
     *
     * @param context          Context
     * @param epVideos         需要合并的视频的集合
     * @param outputOption     输出选项
     * @param processCallback 回调监听
     */
    public static void mergeByLc(Context context, List<EpVideo> epVideos, OutputOption outputOption, final OnFfmpegProcessCallback processCallback) {
        String appDir = context.getCacheDir().getAbsolutePath() + "/EpVideos/";
        String fileName = "ffmpeg_concat.txt";
        List<String> videos = new ArrayList<>();
        for (EpVideo e : epVideos) {
            videos.add(e.getVideoPath());
        }
        FileUtils.writeTxtToFile(videos, appDir, fileName);
        CmdList cmd = new CmdList();
        cmd.append("-y").append("-f").append("concat").append("-safe")
                .append("0").append("-i").append(appDir + fileName)
                .append("-c").append("copy").append(CmdUtils.quote(outputOption.outPath));
        long duration = 0;
        for (EpVideo ep : epVideos) {
            long d = VideoUitls.getDuration(ep.getVideoPath());
            if (d != 0) {
                duration += d;
            } else {
                break;
            }
        }
        execCmd(cmd, processCallback);
    }

    /**
     * 添加背景音乐
     *
     * @param inputVideo       视频文件
     * @param inputAudio       音频文件
     * @param outputFile       输出路径
     * @param videoVolume      视频原声音音量(例:0.7为70%)
     * @param audioVolume      背景音乐音量(例:1.5为150%)
     * @param processCallback 回调监听
     */
    public static void music(String inputVideo, String inputAudio, String outputFile, float videoVolume, float audioVolume, OnFfmpegProcessCallback processCallback) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(inputVideo);
        } catch (IOException e) {
            processCallback.onFailure(null);
            e.printStackTrace();
            return;
        }
        int at = TrackUtils.selectAudioTrack(mediaExtractor);
        CmdList cmd = new CmdList();
        cmd.append("-y")
                .append("-i").append(CmdUtils.quote(inputVideo));
        if (at == -1) {
            int vt = TrackUtils.selectVideoTrack(mediaExtractor);
            float duration = (float) mediaExtractor.getTrackFormat(vt).getLong(MediaFormat.KEY_DURATION) / 1000f / 1000f;
            cmd.append("-ss").append("0").append("-t").append(duration)
                    .append("-i").append(CmdUtils.quote(inputAudio))
                    .append("-acodec").append("copy")
                    .append("-vcodec").append("copy");
        } else {
            cmd.append("-i").append(CmdUtils.quote(inputAudio)).append("-filter_complex")
                    .append("[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=" + videoVolume + "[a0];[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=" + audioVolume + "[a1];[a0][a1]amix=inputs=2:duration=first[aout]")
                    .append("-map").append("[aout]").append("-ac").append("2").append("-c:v")
                    .append("copy").append("-map").append("0:v:0");
        }
        cmd.append(CmdUtils.quote(outputFile));
        mediaExtractor.release();
        execCmd(cmd, processCallback);
    }

    /**
     * 音视频分离
     *
     * @param inputFile        视频文件
     * @param outFile          输出文件路径
     * @param format           输出类型
     * @param processCallback 回调监听
     */
    public static void demuxer(String inputFile, String outFile, Format format, OnFfmpegProcessCallback processCallback) {
        // https://superuser.com/questions/609740/extracting-wav-from-mp4-while-preserving-the-highest-possible-quality
        CmdList cmd = new CmdList();
        cmd.append("-y")
                .append("-i").append(CmdUtils.quote(inputFile));
        switch (format) {
            case MP3:
                // To remove the video
                cmd.append("-vn");
                // Other -acodec options are mp3 flac m4a
                cmd.append("-acodec").append("libmp3lame");
                cmd.append("-async").append("1");
                break;
            case AAC:
                // To remove the video
                cmd.append("-vn");
                // Other -acodec options are mp3 flac m4a
                cmd.append("-c:a aac");
                cmd.append("-async").append("1");
                break;
            case WAV:
                // To remove the video
                cmd.append("-vn");
                // Other -acodec options are mp3 flac m4a
                cmd.append("-acodec").append("pcm_s16le");
                cmd.append("-async").append("1");
                break;
            case MP4:
                cmd.append("-vcodec").append("copy").append("-an");
                break;
        }
        cmd.append(CmdUtils.quote(outFile));
        execCmd(cmd, processCallback);
    }

    /**
     * 音视频倒放
     *
     * @param inputFile        视频文件
     * @param outputFile       输出文件路径
     * @param vr               是否视频倒放
     * @param ar               是否音频倒放
     * @param processCallback 回调监听
     */
    public static void reverse(String inputFile, String outputFile, boolean vr, boolean ar, OnFfmpegProcessCallback processCallback) {
        if (!vr && !ar) {
            Log.e("ffmpeg", "parameter error");
            processCallback.onFailure(null);
            return;
        }
        CmdList cmd = new CmdList();
        cmd.append("-y").append("-i").append(CmdUtils.quote(inputFile)).append("-filter_complex");
        String filter = "";
        if (vr) {
            filter += "[0:v]reverse[v];";
        }
        if (ar) {
            filter += "[0:a]areverse[a];";
        }
        cmd.append(filter.substring(0, filter.length() - 1));
        if (vr) {
            cmd.append("-map").append("[v]");
        }
        if (ar) {
            cmd.append("-map").append("[a]");
        }
        if (ar && !vr) {
            cmd.append("-acodec").append("libmp3lame");
        }
        cmd.append("-preset").append("superfast").append(CmdUtils.quote(outputFile));
        execCmd(cmd, processCallback);
    }

    public static void changeSpeed(String inputFile, String outputFile, float times, PTS pts, OnFfmpegProcessCallback processCallback) throws IOException {
        checkFileExist(inputFile);
        if (times < 0.25f || times > 4.0f) {
            Log.e("ffmpeg", "times can only be 0.25 to 4");
            processCallback.onFailure(null);
            return;
        }

        MediaInformationSession session = FFprobeKit.getMediaInformation(inputFile);
        MediaInformation mediaInformation = session.getMediaInformation();
        String bitRate = mediaInformation.getBitrate();

        Log.d(TAG, "changeSpeed: " + mediaInformation);
        Log.d(TAG, "changeSpeed: " + bitRate);

        CmdList cmd = new CmdList();
        cmd.append("-y") // force override ouput file
                .append("-i").append(CmdUtils.quote(inputFile));
        String t = "atempo=" + times;
        if (times < 0.5f) {
            t = "atempo=0.5,atempo=" + (times / 0.5f);
        } else if (times > 2.0f) {
            t = "atempo=2.0,atempo=" + (times / 2.0f);
        }
        Log.v("ffmpeg", "atempo:" + t);
        switch (pts) {
            case VIDEO:
                cmd.append("-filter_complex").append("[0:v]setpts=" + (1 / times) + "*PTS").append("-an");
                break;
            case AUDIO:
                cmd.append("-filter:a").append(t);
                break;
            case ALL:
                cmd.append("-filter_complex").append("[0:v]setpts=" + (1 / times) + "*PTS[v];[0:a]" + t + "[a]")
                        .append("-map").append("[v]").append("-map").append("[a]");
                break;
        }
        if (bitRate != null && !bitRate.isEmpty()) {
            // https://superuser.com/questions/1100073/increase-video-and-audio-playback-speed-without-losing-quality
//            cmd.append("-b:v").append("1500k");
        }
        cmd.append("-crf 0");
        // TODO fix cmd.append("-preset").append("superfast");
        cmd.append(CmdUtils.quote(outputFile));
        execCmd(cmd, processCallback);
    }

    private static void checkFileExist(String inputFile) throws IOException {
        File file = new File(inputFile);
        if (!file.exists()) {
            throw new IOException("File not found " + inputFile);
        }
        if (!file.canRead()) {
            throw new IOException("Cannot read file " + inputFile);
        }
        if (file.isDirectory()) {
            throw new IOException("File is directory " + inputFile);
        }
    }

    /**
     * 视频转图片
     *
     * @param videoin          音视频文件
     * @param out              输出路径
     * @param w                输出图片宽度
     * @param h                输出图片高度
     * @param rate             每秒视频生成图片数
     * @param processCallback 回调接口
     */
    public static void video2pic(String videoin, String out, int w, int h, float rate, OnFfmpegProcessCallback processCallback) {
        if (w <= 0 || h <= 0) {
            Log.e("ffmpeg", "width and height must greater than 0");
            processCallback.onFailure(null);
            return;
        }
        if (rate <= 0) {
            Log.e("ffmpeg", "rate must greater than 0");
            processCallback.onFailure(null);
            return;
        }
        CmdList cmd = new CmdList();
        cmd.append("-y").append("-i").append(videoin)
                .append("-r").append(rate).append("-s").append(w + "x" + h).append("-q:v").append(2)
                .append("-f").append("image2").append("-preset").append("superfast").append(out);
        long d = VideoUitls.getDuration(videoin);
        execCmd(cmd, processCallback);
    }

    /**
     * 图片转视频
     *
     * @param videoin          视频文件
     * @param out              输出路径
     * @param w                输出视频宽度
     * @param h                输出视频高度
     * @param rate             输出视频帧率
     * @param processCallback 回调接口
     */
    public static void pic2video(String videoin, String out, int w, int h, float rate, OnFfmpegProcessCallback processCallback) {
        if (w < 0 || h < 0) {
            Log.e("ffmpeg", "width and height must greater than 0");
            processCallback.onFailure(null);
            return;
        }
        if (rate <= 0) {
            Log.e("ffmpeg", "rate must greater than 0");
            processCallback.onFailure(null);
            return;
        }
        CmdList cmd = new CmdList();
        cmd.append("-y").append("-f").append("image2").append("-i").append(videoin)
                .append("-vcodec").append("libx264")
                .append("-r").append(rate);
//				.append("-b").append("10M");
        if (w > 0 && h > 0) {
            cmd.append("-s").append(w + "x" + h);
        }
        cmd.append(out);
        long d = VideoUitls.getDuration(videoin);
        execCmd(cmd, processCallback);
    }


    /**
     * 输出选项设置
     */
    public static class OutputOption {
        static final int ONE_TO_ONE = 1;// 1:1
        static final int FOUR_TO_THREE = 2;// 4:3
        static final int SIXTEEN_TO_NINE = 3;// 16:9
        static final int NINE_TO_SIXTEEN = 4;// 9:16
        static final int THREE_TO_FOUR = 5;// 3:4

        String outPath;//输出路径
        public int frameRate = 0;//帧率
        public int bitRate = 0;//比特率(一般设置10M)
        public String outFormat = "";//输出格式(目前暂时只支持mp4,x264,mp3,gif)
        private int width = 0;//输出宽度
        private int height = 0;//输出高度
        private int sar = 6;//输出宽高比

        public OutputOption(String outPath) {
            this.outPath = outPath;
        }

        /**
         * 获取宽高比
         *
         * @return 1
         */
        public String getSar() {
            String res;
            switch (sar) {
                case ONE_TO_ONE:
                    res = "1/1";
                    break;
                case FOUR_TO_THREE:
                    res = "4/3";
                    break;
                case THREE_TO_FOUR:
                    res = "3/4";
                    break;
                case SIXTEEN_TO_NINE:
                    res = "16/9";
                    break;
                case NINE_TO_SIXTEEN:
                    res = "9/16";
                    break;
                default:
                    res = width + "/" + height;
                    break;
            }
            return res;
        }

        public void setSar(int sar) {
            this.sar = sar;
        }

        /**
         * 获取输出信息
         *
         * @return 1
         */
        String getOutputInfo() {
            StringBuilder res = new StringBuilder();
            if (frameRate != 0) {
                res.append(" -r ").append(frameRate);
            }
            if (bitRate != 0) {
                res.append(" -b ").append(bitRate).append("M");
            }
            if (!outFormat.isEmpty()) {
                res.append(" -f ").append(outFormat);
            }
            return res.toString();
        }

        /**
         * 设置宽度
         *
         * @param width 宽
         */
        public void setWidth(int width) {
            if (width % 2 != 0) width -= 1;
            this.width = width;
        }

        /**
         * 设置高度
         *
         * @param height 高
         */
        public void setHeight(int height) {
            if (height % 2 != 0) height -= 1;
            this.height = height;
        }
    }

}
