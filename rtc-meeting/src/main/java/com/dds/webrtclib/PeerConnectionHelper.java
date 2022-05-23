package com.dds.webrtclib;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.dds.webrtclib.ws.IWebSocket;

import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class PeerConnectionHelper {

    public final static String TAG = "dds_webRtcHelper";

    public static final int VIDEO_RESOLUTION_WIDTH = 1920;
    public static final int VIDEO_RESOLUTION_HEIGHT = 1080;
    public static final int FPS = 10;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public PeerConnectionFactory _factory;
    public MediaStream _localStream;
    public VideoTrack _localVideoTrack;
    public VideoCapturer captureAndroid;
    public VideoSource videoSource;
    public AudioSource audioSource;

    public ArrayList<String> _connectionIdArray;
    public Map<String, Peer> _connectionPeerDic;

    public String _myId;
    public IViewCallback viewCallback;

    public ArrayList<PeerConnection.IceServer> ICEServers;
    public boolean videoEnable;
    public int _mediaType;


    enum Role {Caller, Receiver,}

    private Role _role;

    private IWebSocket _webSocket;

    private Context _context;

    private EglBase _rootEglBase;

    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;

    public PeerConnectionHelper(IWebSocket webSocket) {
        this._connectionPeerDic = new HashMap<>();
        this._connectionIdArray = new ArrayList<>();
        this.ICEServers = new ArrayList<>();

        _webSocket = webSocket;
    }

    // 设置界面的回调
    public void setViewCallback(IViewCallback callback) {
        viewCallback = callback;
    }

    // ===================================webSocket回调信息=======================================

    public void initContext(Context context, EglBase eglBase) {
        _context = context;
        _rootEglBase = eglBase;
    }

    public void onJoinToRoom(ArrayList<String> connections, String myId, boolean isVideoEnable, int mediaType) {
        videoEnable = isVideoEnable;
        _mediaType = mediaType;
        _connectionIdArray.addAll(connections);
        _myId = myId;
        if (_factory == null) {
            _factory = createConnectionFactory();
        }
        if (_localStream == null) {
            createLocalStream();
        }

        createPeerConnections();
        addStreams();
        createOffers();

    }

    public void onRemoteJoinToRoom(String socketId) {
        if (_localStream == null) {
            createLocalStream();
        }
        try {
            Peer mPeer = new Peer(socketId);
            mPeer.pc.addStream(_localStream);
            _connectionIdArray.add(socketId);
            _connectionPeerDic.put(socketId, mPeer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        Peer peer = _connectionPeerDic.get(socketId);
        if (peer != null) {
            peer.pc.addIceCandidate(iceCandidate);
        }
    }


    public void onRemoteOutRoom(String socketId) {
        closePeerConnection(socketId);
    }

    public void onReceiveOffer(String socketId, String description) {

        _role = Role.Receiver;
        Peer mPeer = _connectionPeerDic.get(socketId);
        String sessionDescription = description;
        SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, sessionDescription);
        if (mPeer != null) {
            mPeer.pc.setRemoteDescription(mPeer, sdp);
        }

    }

    public void onReceiverAnswer(String socketId, String sdp) {

        Peer mPeer = _connectionPeerDic.get(socketId);
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        if (mPeer != null) {
            mPeer.pc.setRemoteDescription(mPeer, sessionDescription);
        }

    }

    private PeerConnectionFactory createConnectionFactory() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(_context)
                        .createInitializationOptions());

        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                _rootEglBase.getEglBaseContext(),
                true,
                true);
        decoderFactory = new DefaultVideoDecoderFactory(_rootEglBase.getEglBaseContext());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        return PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(_context).createAudioDeviceModule())
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    // 创建本地流
    private void createLocalStream() {
        _localStream = _factory.createLocalMediaStream("ARDAMS");

        if (videoEnable) {
            // 视频
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", _rootEglBase.getEglBaseContext());
            videoSource = _factory.createVideoSource(captureAndroid.isScreencast());
            captureAndroid.initialize(surfaceTextureHelper, _context, videoSource.getCapturerObserver());
            captureAndroid.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);
            _localVideoTrack = _factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            _localStream.addTrack(_localVideoTrack);
        }


    }

    // 创建所有连接
    private void createPeerConnections() {
        for (String str : _connectionIdArray) {
            Peer peer = new Peer(str);
            _connectionPeerDic.put(str, peer);
        }
    }

    // 为所有连接添加流
    private void addStreams() {
        Log.v(TAG, "为所有连接添加流:" + _connectionPeerDic.size());
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(_context, "addStreams:" + _connectionPeerDic.size(), Toast.LENGTH_LONG).show());
        for (Map.Entry<String, Peer> entry : _connectionPeerDic.entrySet()) {
            if (_localStream == null) {
                createLocalStream();
            }
            try {
                entry.getValue().pc.addStream(_localStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    // 为所有连接创建offer
    private void createOffers() {
        for (Map.Entry<String, Peer> entry : _connectionPeerDic.entrySet()) {
            _role = Role.Caller;
            Peer mPeer = entry.getValue();
            mPeer.pc.createOffer(mPeer, offerOrAnswerConstraint());
        }

    }

    // 关闭通道流
    private void closePeerConnection(String connectionId) {
        Peer mPeer = _connectionPeerDic.get(connectionId);
        if (mPeer != null) {
            mPeer.pc.close();
        }
        _connectionPeerDic.remove(connectionId);
        _connectionIdArray.remove(connectionId);
        if (viewCallback != null) {
            viewCallback.onCloseWithId(connectionId);
        }

    }


    // 退出房间
    public void exitRoom() {
        if (viewCallback != null) {
            viewCallback = null;
        }
        ArrayList myCopy;
        myCopy = (ArrayList) _connectionIdArray.clone();
        for (Object Id : myCopy) {
            closePeerConnection((String) Id);
        }
        if (_connectionIdArray != null) {
            _connectionIdArray.clear();
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (captureAndroid != null) {
            try {
                captureAndroid.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            captureAndroid.dispose();
            captureAndroid = null;
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }


        if (_factory != null) {
            _factory.dispose();
            _factory = null;
        }

        if (_webSocket != null) {
            _webSocket.close();
            _webSocket = null;
        }
    }


    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(videoEnable)));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    //**************************************内部类******************************************/
    private class Peer implements SdpObserver, PeerConnection.Observer {
        private final PeerConnection pc;
        private final String socketId;

        public Peer(String socketId) {
            this.pc = createPeerConnection();
            this.socketId = socketId;

        }


        //****************************PeerConnection.Observer****************************/
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange: " + iceConnectionState.toString());
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            Log.i(TAG, "onConnectionChange: " + newState.toString());
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionReceivingChange:" + b);

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "onIceGatheringChange:" + iceGatheringState.toString());

        }


        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            // 发送IceCandidate
            _webSocket.sendIceCandidate(socketId, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.i(TAG, "onIceCandidatesRemoved:");
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {

        }


        //****************************SdpObserver****************************/

        @Override
        public void onCreateSuccess(SessionDescription origSdp) {
            Log.v(TAG, "sdp创建成功       " + origSdp.type);
            //设置本地的SDP

            String sdpDescription = origSdp.description;
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);


            pc.setLocalDescription(Peer.this, sdp);
        }

        @Override
        public void onSetSuccess() {
            Log.v(TAG, "sdp连接成功        " + pc.signalingState().toString());

            if (pc.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                pc.createAnswer(Peer.this, offerOrAnswerConstraint());
            } else if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                //判断连接状态为本地发送offer
                if (_role == Role.Receiver) {
                    //接收者，发送Answer
                    _webSocket.sendAnswer(socketId, pc.getLocalDescription().description);

                } else if (_role == Role.Caller) {
                    //发送者,发送自己的offer
                    _webSocket.sendOffer(socketId, pc.getLocalDescription().description);
                }

            } else if (pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                // Stable 稳定的
                if (_role == Role.Receiver) {
                    _webSocket.sendAnswer(socketId, pc.getLocalDescription().description);

                }
            }

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }


        //初始化 RTCPeerConnection 连接管道
        private PeerConnection createPeerConnection() {
            if (_factory == null) {
                _factory = createConnectionFactory();
            }
            // 管道连接抽象类实现方法
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(ICEServers);
            return _factory.createPeerConnection(rtcConfig, this);
        }

    }


}



