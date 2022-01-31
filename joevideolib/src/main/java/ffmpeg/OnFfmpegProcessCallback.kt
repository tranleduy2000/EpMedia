package ffmpeg;

/**
 * Created by YangJie on 2017/5/18.
 */
public interface OnFfmpegProcessCallback {
	void onSuccess();

	void onFailure(Exception e);

	void onProgress(long duration);
}
