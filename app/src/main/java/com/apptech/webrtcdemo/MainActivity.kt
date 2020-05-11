package com.apptech.webrtcdemo


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import com.apptech.webrtcdemo.webrtc.WebRTCApplicationHelper
import io.github.hyuwah.draggableviewlib.Draggable
import io.github.hyuwah.draggableviewlib.makeDraggable
import kotlinx.android.synthetic.main.call_buttons.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers.IO as IOC


class MainActivity : AppCompatActivity(),SignallingClient.SignalingInterface {

    private val rootEglBase by lazy { EglBase.create() }
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext, /* enableIntelVp8Encoder */true, /* enableH264HighProfile */true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        val builder = PeerConnectionFactory.builder()
        builder.setOptions(options)
        .setVideoDecoderFactory(defaultVideoDecoderFactory)
        .setVideoEncoderFactory(defaultVideoEncoderFactory)
        .createPeerConnectionFactory()
    }
    private var audioConstraints: MediaConstraints? = null
    private var videoConstraints: MediaConstraints? = null
    private var sdpConstraints: MediaConstraints? = null
    private var videoCapturerAndroid:VideoCapturer? =null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoView: SurfaceViewRenderer? = null
    private var remoteVideoView: SurfaceViewRenderer? = null
    private var mediaStream:MediaStream? =null
    private var localPeer: PeerConnection? = null
    private var gotUserMedia: Boolean = false
    private lateinit var audioManager:AudioManager
    private var peerIceServers: MutableList<PeerConnection.IceServer> = mutableListOf()
    private lateinit var me: String
    private lateinit var other:String
    private var btnsShown = true
    private var isScreenSharing = false
    private var speakerOn = true
    private lateinit var currentCameraPosition: String
    private var isMute = false
    private val myCoroutineScope = CoroutineScope(IOC)



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (intent.hasExtra("to"))
            other = intent.getStringExtra("to")!!
        if (intent.hasExtra("me"))
            me = intent.getStringExtra("me")!!
        if (intent.hasExtra("screen_share"))
            isScreenSharing = intent.getBooleanExtra("screen_share",false)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                ),
                ALL_PERMISSIONS_CODE
            )
        } else {
            // all permissions already granted
            start()
        }
    }
    private fun initViews() {
        localVideoView = findViewById(R.id.localView)
        remoteVideoView = findViewById(R.id.remoteView)
        if (isScreenSharing){
            switch_camera.visibility = View.GONE
        }


        mute.setOnClickListener {
            mute()
        }
        switch_camera.setOnClickListener {
            switchCamera()
        }
        endCall.setOnClickListener {
            hangup()
        }
        sound_output.setOnClickListener {
            changeSoundOutput()
        }
        remoteVideoView?.setOnClickListener {
            switchViews()
        }
        localVideoView?.setOnClickListener {
            if (btnsShown){
                btnsShown = false
                endCall.hide()
                mute.hide()
                switch_camera.hide()
                sound_output.hide()
            }else{
                btnsShown = true
                endCall.show()
                mute.show()
                switch_camera.show()
                sound_output.show()
            }

        }
    }
    private fun initVideos() {
        localVideoView?.init(rootEglBase.eglBaseContext, null)
        remoteVideoView?.init(rootEglBase.eglBaseContext, null)
        localVideoView?.setZOrderMediaOverlay(true)
        remoteVideoView?.setZOrderMediaOverlay(true)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }
    private fun switchViews(){
        myCoroutineScope.launch(Main) {
            val params2 = FrameLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT
            )
            remoteVideoView!!.layoutParams = params2
            remoteVideoView!!.setOnClickListener {
                if (btnsShown){
                    btnsShown = false
                    endCall.hide()
                    mute.hide()
                    switch_camera.hide()
                    sound_output.hide()
                }else{
                    btnsShown = true
                    endCall.show()
                    mute.show()
                    switch_camera.show()
                    sound_output.show()
                }
            }
            val params = FrameLayout.LayoutParams(
                dpToPx(130),
                dpToPx(160)
            ).apply {
                this.setMargins(dpToPx(16))
                gravity =  Gravity.START or Gravity.TOP
            }
            localVideoView?.layoutParams = params
            localVideoView?.makeDraggable(
                stickyAxis = Draggable.STICKY.NONE
            )
        }
    }

    private fun getIceServers() {
        //get Ice servers
        peerIceServers.add(PeerConnection.IceServer.builder("turn:18.157.69.48:3478").setPassword("test").setUsername("test").createIceServer())
//        val servers = arrayOf(
//            "stun: stun.l.google.com:19302",
//            "stun: stun1.l.google.com:19302",
//            "stun: stun2.l.google.com:19302",
//            "stun: stun3.l.google.com:19302",
//            "stun: stun4.l.google.com:19302",
//            "stun: stun.ekiga.net",
//            "stun: stun.ideasip.com",
//            "stun: stun.schlund.de",
//            "stun: stun.stunprotocol.org:3478",
//            "stun: stun.voiparound.com",
//            "stun: stun.voipbuster.com",
//            "stun: stun.voipstunt.com",
//            "stun: stun.services.mozilla.com"
//        )
//        servers.forEach {
//            peerIceServers.add(PeerConnection.IceServer.builder(it).createIceServer())
//        }

    }

    private fun start() {
        // keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initViews()
        initVideos()
        getIceServers()

        //val roomName = me+other

        //Now create a VideoCapturer instance.
        if (isScreenSharing){
            val  mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            if (intent == null){
                showToast("Unknown error")
            }else{
                startActivityForResult(intent, CAPTURE_PERMISSION_REQUEST_CODE)
            }
        }else{
            videoCapturerAndroid = if (Camera2Enumerator.isSupported(this)){
                createCameraCapturer(Camera2Enumerator(this))
            }else{
                createCameraCapturer(Camera1Enumerator(false))
            }
            audioConstraints = MediaConstraints()
            videoConstraints = MediaConstraints()
            //Create MediaConstraints - Will be useful for specifying video and audio constraints.
            //Create a VideoSource instance
            if (videoCapturerAndroid != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
                videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid!!.isScreencast)
                videoCapturerAndroid?.initialize(surfaceTextureHelper, this, videoSource!!.capturerObserver)
                localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
            }
            //create an AudioSource instance
            audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

            videoCapturerAndroid?.startCapture(1024, 720, 30)

            localVideoView?.visibility = View.VISIBLE

            // And finally, with our VideoRenderer ready, we
            // can add our renderer to the VideoTrack.
            localVideoTrack?.addSink(localVideoView)

            localVideoView?.setMirror(true)
            gotUserMedia = true
            if (SignallingClient.isInitiator) {
                onTryToStart()
            }
            SignallingClient.init(this)
        }

    }


    private fun hangup(){
        try {
            if (localPeer != null) {
                localPeer!!.close()
            }
            localPeer = null
            SignallingClient.close()

        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onRemoteHangUp(msg: String) {
        showToast("Remote Peer hungup")
        myCoroutineScope.launch (Main){
            //hangup()
            finish()
        }
    }
    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    override fun onOfferReceived(data: JSONObject) {
        showToast("Received Offer")
        myCoroutineScope.launch {
            if (!SignallingClient.isInitiator && !SignallingClient.isStarted) {
                onTryToStart()
            }
            try {
                localPeer?.setRemoteDescription(
                    CustomSdpObserver("localSetRemote"),
                    SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp"))
                )
                doAnswer()
                switchViews()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun doAnswer() {
        localPeer?.createAnswer(object : CustomSdpObserver("localCreateAns") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    localPeer!!.setLocalDescription(
                        CustomSdpObserver("localSetLocal"),
                        sessionDescription
                    )
                    SignallingClient.emitMessage(sessionDescription)
                }
            }, MediaConstraints())


    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */
    override fun onAnswerReceived(data: JSONObject) {
        showToast("Received Answer")
        try {
            localPeer!!.setRemoteDescription(
                CustomSdpObserver("localSetRemote"),
                SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(
                        data.getString("type").toLowerCase(Locale.ROOT)
                    ), data.getString("sdp")
                )
            )
            switchViews()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
    /**
     * Remote IceCandidate received
     */
    override fun onIceCandidateReceived(data: JSONObject) {
        try {

            val t =localPeer!!.addIceCandidate(
                IceCandidate(
                    data.getString("id"),
                    data.getInt("label"),
                    data.getString("candidate")
                )
            )
            showToast("$t")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    private fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.emitIceCandidate(iceCandidate)
    }
    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    override fun onTryToStart() {
        myCoroutineScope.launch {
            if (!SignallingClient.isStarted && localVideoTrack != null && SignallingClient.isChannelReady) {
                createPeerConnection()
                SignallingClient.isStarted = true
                if (SignallingClient.isInitiator) {
                    doCall()
                }
            }
        }
    }
    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    override fun onCreatedRoom() {
        showToast("You created the room $gotUserMedia")
        if (gotUserMedia) {
            SignallingClient.emitMessage("got user media")
        }
    }
    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    override fun onJoinedRoom() {
        showToast("You joined the room $gotUserMedia")
        if (gotUserMedia) {
            SignallingClient.emitMessage("got user media")
        }
    }

    override fun onNewPeerJoined() {
        showToast("Remote Peer Joined")
    }



    /**
     * Creating the local peerconnection instance
     */
    private fun createPeerConnection(){
        val rtcConfig = RTCConfiguration(peerIceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        localPeer = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : CustomPeerConnectionObserver("localPeerCreation") {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    super.onIceCandidate(iceCandidate)
                    onIceCandidateReceived(iceCandidate)
                }
                override fun onAddStream(mediaStream: MediaStream) {
                    showToast("Received Remote stream")
                    super.onAddStream(mediaStream)
                    gotRemoteStream(mediaStream)
                }
            })
        addStreamToLocalPeer()
    }

    /**
     * Adding the stream to the localpeer
     */
    private fun addStreamToLocalPeer() {
        //creating local mediastream
            mediaStream = peerConnectionFactory.createLocalMediaStream("102")
            mediaStream!!.addTrack(localAudioTrack)
            mediaStream!!.addTrack(localVideoTrack)
            localPeer?.addStream(mediaStream)


    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private fun gotRemoteStream(stream: MediaStream) {
        //we have remote video stream. add to the renderer.
        val videoTrack = stream.videoTracks[0]
        myCoroutineScope.launch (Main){
            try {
                remoteVideoView!!.visibility = View.VISIBLE
                videoTrack.addSink(remoteVideoView)
                stream.audioTracks[0].setEnabled(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun doCall(){
        sdpConstraints = MediaConstraints()
        sdpConstraints?.mandatory?.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        localPeer!!.createOffer(object : CustomSdpObserver("localCreateOffer") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer!!.setLocalDescription(
                    CustomSdpObserver("localSetLocalDesc"),
                    sessionDescription
                )
                Log.d("onCreateSuccess", "SignallingClient emit ")
                SignallingClient.emitMessage(sessionDescription)
            }
        }, sdpConstraints)
    }

    private fun showToast(message:String){
        myCoroutineScope.launch (Main){
            Toast.makeText(this@MainActivity,message,Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        //find the front facing camera and return it.
        deviceNames.forEach {
            if (enumerator.isFrontFacing(it)){
                val videoCapturer = enumerator.createCapturer(it, null)
                if (videoCapturer != null) {
                    currentCameraPosition = "user"
                    return videoCapturer
                }
            }
        }
        deviceNames.forEach {
            if (enumerator.isBackFacing(it)){
                val videoCapturer = enumerator.createCapturer(it, null)
                if (videoCapturer != null) {
                    currentCameraPosition = "env"
                    return videoCapturer
                }
            }
        }

        return null
    }

    override fun onDestroy() {
        myCoroutineScope.cancel()
        SignallingClient.close()
        super.onDestroy()
        audioManager.mode = AudioManager.MODE_NORMAL
        videoCapturerAndroid?.dispose()
        localPeer?.close()
        localPeer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        videoSource?.dispose()
        audioSource?.dispose()
        localVideoView?.release()
        remoteVideoView?.release()
        mediaStream?.dispose()
    }

    private fun mute(){
        localAudioTrack?.let {
            if (isMute){
                isMute=false
                it.setEnabled(true)
                mute.setImageResource(R.drawable.ic_mice_on)
                mute.setColorFilter(ContextCompat.getColor(this,R.color.green))
            }else{
                isMute=true
                it.setEnabled(false)
                mute.setImageResource(R.drawable.ic_mute)
                mute.setColorFilter(ContextCompat.getColor(this,R.color.red))
            }
        }
    }

    private fun createScreenCapturer(): ScreenCapturerAndroid? {
        if (mMediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            Log.e("ShareScreen","User didn't give permission to capture the screen.")
            return null
        }
        return ScreenCapturerAndroid(
            mMediaProjectionPermissionResultData, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.e("ShareScreen","User revoked permission to capture the screen.")
                }
            })
    }

    private fun initScreenSharing(){
        videoCapturerAndroid = createScreenCapturer()
        audioConstraints = MediaConstraints()
        videoConstraints = MediaConstraints()
        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid!!.isScreencast)
            videoCapturerAndroid?.initialize(surfaceTextureHelper, this, videoSource!!.capturerObserver)
            localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
        }
        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        videoCapturerAndroid?.startCapture(1024, 720, 30)

        localVideoView?.visibility = View.VISIBLE

        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack?.addSink(localVideoView)

        localVideoView?.setMirror(false)
        gotUserMedia = true
        if (SignallingClient.isInitiator) {
            onTryToStart()
        }

    }

    private fun switchCamera(){
        myCoroutineScope.launch (Main){
            if (videoCapturerAndroid != null) {
                if (videoCapturerAndroid is CameraVideoCapturer) {

                    val cameraVideoCapturer = videoCapturerAndroid as CameraVideoCapturer
                    cameraVideoCapturer.switchCamera(object :CameraVideoCapturer.CameraSwitchHandler{
                        override fun onCameraSwitchDone(p0: Boolean) {
                            if (currentCameraPosition == "user"){
                                localVideoView?.setMirror(false)
                                currentCameraPosition = "env"
                            }else if(currentCameraPosition == "env"){
                                localVideoView?.setMirror(true)
                                currentCameraPosition = "user"
                            }
                        }

                        override fun onCameraSwitchError(p0: String?) {
                            Log.e("CAMERA SWITCH ERR",p0+"")
                        }

                    })

                } else {
                    // Will not switch camera, video capturer is not a camera
                    showToast("error switch camera")
                }
            }
        }
    }

    private fun changeSoundOutput(){
        if (speakerOn){
            speakerOn=false
            sound_output.setImageResource(R.drawable.ic_headset_sound)
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }else{
            speakerOn = true
            sound_output.setImageResource(R.drawable.ic_speaker_sound)
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
    }

    /**
     * Util Methods
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAPTURE_PERMISSION_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            mMediaProjectionPermissionResultCode = resultCode
            mMediaProjectionPermissionResultData = data
            initScreenSharing()
        }
        WebRTCApplicationHelper.getInstance().handleResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ALL_PERMISSIONS_CODE && grantResults.size == 2 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED){
            // all permissions granted
            start()
        } else {
            finish()
        }
    }

    private fun dpToPx(dp: Int): Int {
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }

    companion object{
        //private var TAG: String = "appTech.WebRTC.MainActivity"
        private const val ALL_PERMISSIONS_CODE = 1
        private const val CAPTURE_PERMISSION_REQUEST_CODE = 24132
        private var mMediaProjectionPermissionResultData : Intent? = null
        private var mMediaProjectionPermissionResultCode: Int? = null
    }
}
