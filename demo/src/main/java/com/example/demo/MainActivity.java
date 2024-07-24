package com.example.demo;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import VideoHandle.EpEditor;
import VideoHandle.EpVideo;
import ffmpeg.OnFfmpegProcessCallback;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        try {
            File inputFile = new File(getFilesDir(), "video.mp4");
            FileUtils.copyToFile(getAssets().open("video.mp4"), inputFile);

            File outputFile = new File(getFilesDir(), "video.out.mp4");

            EpVideo epVideo = new EpVideo(inputFile.getAbsolutePath());
            epVideo.rotation(90,true);

            EpEditor.OutputOption outputOption = new EpEditor.OutputOption(outputFile.getAbsolutePath());
            EpEditor.INSTANCE.exec(epVideo,
                    outputOption, new OnFfmpegProcessCallback() {
                        @Override
                        public void onProgress(long duration) {
                            Log.d(TAG, "onProgress() called with: progress = [" + duration + "]");
                        }

                        @Override
                        public void onFailure(@Nullable Exception error) {
                            Log.d(TAG, "onFailure() called");

                        }

                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "onSuccess() called");
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}