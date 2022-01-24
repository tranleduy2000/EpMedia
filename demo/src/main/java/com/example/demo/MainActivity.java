package com.example.demo;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import VideoHandle.EpEditor;
import VideoHandle.EpVideo;
import VideoHandle.OnEditorListener;

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
            EpEditor.exec(epVideo,
                    outputOption, new OnEditorListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "onSuccess() called");
                        }

                        @Override
                        public void onFailure() {
                            Log.d(TAG, "onFailure() called");
                        }

                        @Override
                        public void onProgress(float progress) {
                            Log.d(TAG, "onProgress() called with: progress = [" + progress + "]");
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}