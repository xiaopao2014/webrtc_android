package com.dds.nodejs;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.dds.webrtc.R;
import com.dds.webrtclib.WebRTCManager;

import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;


/**
 * Created by dds on 2018/11/7.
 * android_shuai@163.com
 */
public class NodejsActivity extends AppCompatActivity {
    private static final String TAG = "llbeing";
    private EditText et_signal;
    private EditText et_room;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodejs);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        initView();
        initVar();
        startScreenCapture();

    }

    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1116;

    @TargetApi(21)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }


    private void initView() {
        et_signal = findViewById(R.id.et_signal);
        et_room = findViewById(R.id.et_room);
    }

    private void initVar() {
        et_signal.setText("ws://172.20.122.199:3000");
        et_room.setText("__default");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        WebRTCManager.getInstance().screenCapture = createScreenCapturer(resultCode, data);
    }

    @TargetApi(21)
    private VideoCapturer createScreenCapturer(int resultCode, Intent mMediaProjectionPermissionResultData) {
        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "User didn't give permission to capture the screen.");
            return null;
        }
        return new ScreenCapturerAndroid(
                mMediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.e(TAG, "User revoked permission to capture the screen.");
            }
        });
    }

    /*-------------------------- nodejs版本服务器测试--------------------------------------------*/
    public void JoinRoomSingleVideo(View view) {
        WebrtcUtil.callSingle(this,
                et_signal.getText().toString(),
                et_room.getText().toString().trim(),
                false, true);
    }

    public void JoinRoom(View view) {
        WebrtcUtil.call(this, et_signal.getText().toString(), et_room.getText().toString().trim());

    }


}
