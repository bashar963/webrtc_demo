/*
 * Created by Bashar Alkaddah on 2020.
 * Copyright (c) $year, Apptech Ltd. All rights reserved.
 * balkaddah@apptech.com.tr
 */

package com.apptech.webrtcdemo



import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apptech.webrtcdemo.webrtc.*
import io.github.hyuwah.draggableviewlib.Draggable
import io.github.hyuwah.draggableviewlib.makeDraggable
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.call_buttons.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import kotlinx.coroutines.Dispatchers.IO as IOC


class MainActivityTest : AppCompatActivity() {
    private lateinit var connection: WebRTCPeerConnection

    private lateinit var socket: Socket
    private lateinit var me: String
    private lateinit var other:String
    private var btnsShown = true
    private lateinit var localStream: WebRTCMediaStream
    private var didAnswer = false
    private lateinit var currentCameraPosition: String
    //private val dataChannels: Map<String, WebRTCDataChannel> = HashMap<String, WebRTCDataChannel>()
    private lateinit var remoteIceCandidates: ArrayList<WebRTCIceCandidate>
    private var isMute = false
    private val myCoroutineScope = CoroutineScope(IOC)


    private var inCall = false
    private var isInitiator = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (intent.hasExtra("to"))
            other = intent.getStringExtra("to")!!
        if (intent.hasExtra("me"))
            me = intent.getStringExtra("me")!!


        remoteIceCandidates = ArrayList()
        localView.setMirror(true)


        remoteView.makeDraggable(
            stickyAxis = Draggable.STICKY.NONE
        )
        val servers = arrayOf(
            "stun: stun.l.google.com:19302",
            "stun: stun1.l.google.com:19302",
            "stun: stun2.l.google.com:19302",
            "stun: stun3.l.google.com:19302",
            "stun: stun4.l.google.com:19302",
            "stun: stun.ekiga.net",
            "stun: stun.ideasip.com",
            "stun: stun.schlund.de",
            "stun: stun.stunprotocol.org:3478",
            "stun: stun.voiparound.com",
            "stun: stun.voipbuster.com",
            "stun: stun.voipstunt.com",
            "stun: stun.services.mozilla.com"
        )
        val rtcIceServer =  WebRTCIceServer(servers)
        val list =  mutableListOf<WebRTCIceServer>()
        list.add(rtcIceServer)
        val configuration =  WebRTCConfiguration(list)

        // PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions())
        connection =  WebRTCPeerConnection(this, configuration)
        connection.setOnTrackListener { event ->
            event?.let { webRTCTrackEvent ->
                webRTCTrackEvent.streams?.let {
                    //remoteView.setSrcObject(it[0])
                }
            }
        }
        connection.setOnIceCandidateListener { candidate ->
            val jsonObject =  JSONObject()
            try {
                jsonObject.put("from", me)
                jsonObject.put("sdp", candidate!!.sdp)
                jsonObject.put("sdpMid", candidate.sdpMid)
                jsonObject.put("sdpMLineIndex", candidate.sdpMLineIndex)
                jsonObject.put("serverUrl", candidate.serverUrl)
                socket.emit("iceCandidate", jsonObject)
            } catch (e:JSONException) {
                e.printStackTrace()
            }
        }

        if (AppWebRTC.hasPermissions(this))
            setUpUserMedia()
        else
            AppWebRTC.requestPermissions(this)




        mute.setOnClickListener {
            mute()
        }
        switch_camera.setOnClickListener {
            switchCamera()
        }
        endCall.setOnClickListener {
            endCall()
        }
        localView.setOnClickListener {
            if (btnsShown){
                btnsShown = false
                endCall.hide()
                mute.hide()
                switch_camera.hide()

            }else{
                btnsShown = true
                endCall.show()
                mute.show()
                switch_camera.show()

            }

        }
    }

    private fun answer(session:String) {
        myCoroutineScope.launch {

            didAnswer=true
            val list: MutableList<String?> = ArrayList()
            list.add(localStream.id)
            for (track in localStream.videoTracks) {
                connection.addTrack(track, list)
            }
            for (track in localStream.audioTracks) {
                connection.addTrack(track, list)
            }
            val sdp = WebRTCSessionDescription(WebRTCSdpType.OFFER, session)
            createAnswerForOfferReceived(sdp)

        }
    }

    private fun socketInit(){
        try {
            val options =  IO.Options()
            options.forceNew = true
            options.secure = false
            options.port = 3001
            socket = IO.socket("http://18.157.69.48:3001", options)
            socket.on("call:incoming") {
                myCoroutineScope.launch {
                    val  jsonObject =  it[0] as JSONObject
                    //val from: String = jsonObject.getString("from")
                    val session: String = jsonObject.getString("sdp")
                    val to: String = jsonObject.getString("to")
                    if (to.contains(me)) {
                        val list: MutableList<String?> = ArrayList()
                        list.add(localStream.id)
                        localStream.videoTracks.forEach {
                            connection.addTrack(it, list)
                        }
                        localStream.audioTracks.forEach {
                            connection.addTrack(it,list)
                        }
                        val sdp = WebRTCSessionDescription(WebRTCSdpType.OFFER, session)
                        createAnswerForOfferReceived(sdp)
                    }

                }
            }
            socket.on("connection"){
                myCoroutineScope.launch {
                    Log.e(TAG,it.toString())
                    val `object` = it[0] as JSONObject
                    Log.e("test", it.toString())
                    //val from: String = `object`.getString("from")
                    val session: String = `object`.getString("sdp")
                    val to: String = `object`.getString("to")
                    if (to.contains(me)) {
                        val list: MutableList<String?> = ArrayList()
                        list.add(localStream.id)
                        for (track in localStream.videoTracks) {
                            connection.addTrack(track, list)
                        }
                        for (track in localStream.audioTracks) {
                            connection.addTrack(track, list)
                        }
                        val sdp = WebRTCSessionDescription(WebRTCSdpType.OFFER, session)
                        createAnswerForOfferReceived(sdp)
                    }
                }
            }
            socket.on("user joined"){
                myCoroutineScope.launch {
                    Log.e(TAG,it.toString())
                    val `object` = it[0] as JSONObject
                    Log.e("test", it.toString())


                }
            }
            socket.on("getUsers"){
                myCoroutineScope.launch {
                    Log.e(TAG,it.toString())
                    val `object` = it[0] as JSONArray
                    Log.e("test", `object`.getJSONObject(0).getString("username"))
                }
            }
            socket.on("login"){
                myCoroutineScope.launch {
                    Log.e(TAG,it.toString())
                    val `object` = it[0] as JSONObject
                    Log.e("test", it.toString())


                }
            }
            socket.on("call:answer"){
                myCoroutineScope.launch {
                    val `object` = it[0] as JSONObject
                    //val from = `object`.getString("from")
                    val session = `object`.getString("sdp")
                    val to = `object`.getString("to")
                    if (to.contains(me)) {
                        val sdp = WebRTCSessionDescription(WebRTCSdpType.OFFER, session)
                        createAnswerForOfferReceived(sdp)
                    }
                }
            }
            socket.on("call:answered"){
                myCoroutineScope.launch {
                    if (!inCall) {
                        val `object` = it[0] as JSONObject
                        //val from = `object`.getString("from")
                        val session = `object`.getString("sdp")
                        val to = `object`.getString("to")
                        if (to.contains(me)) {
                            val sdp = WebRTCSessionDescription(WebRTCSdpType.ANSWER, session)
                            handleAnswerReceived(sdp)
                            //dataChannelCreate("osei");
                            //dataChannelSend("osei", "Test", FancyWebRTC.DataChannelMessageType.TEXT);
                        }

                    }
                }
            }
            socket.on("call:iceCandidate"){
                myCoroutineScope.launch {
                    val `object` = it[0] as JSONObject
                    //val from = `object`.getString("from")
                    val session = `object`.getString("sdp")
                    val to = `object`.getString("to")
                    val sdpMid = `object`.getString("sdpMid")
                    val sdpMLineIndex = `object`.getInt("sdpMLineIndex")
                    //val serverUrl = `object`.getString("serverUrl")
                    if (to.contains(me)) {
                        val candidate = WebRTCIceCandidate(session, sdpMid, sdpMLineIndex)
                        connection.addIceCandidate(candidate)
                    }
                }
            }

            socket.on(Socket.EVENT_CONNECT){

//                val `object` = JSONObject()
//                `object`.put("username", me)
//                socket.emit("add user", `object`)
//                socket.emit("getUsers", `object`)
                if (!intent.hasExtra("session"))
                    makeCall()
                else
                    if (intent.hasExtra("session") && !didAnswer)
                        answer(intent.getStringExtra("session")!!)
            }
            socket.on(Socket.EVENT_DISCONNECT){
                Log.e("EVENT_DISCONNECT","EVENT_DISCONNECT")
            }
            socket.on(Socket.EVENT_CONNECT_ERROR){
                Log.e("error",it.toString())
                Log.e("EVENT_CONNECT_ERROR","EVENT_CONNECT_ERROR")
            }
            socket.on(Socket.EVENT_CONNECT_TIMEOUT){
                Log.e("EVENT_CONNECT_TIMEOUT","EVENT_CONNECT_TIMEOUT")
            }
            socket.connect()
        }catch (e:Exception){
            e.printStackTrace()
        }

    }

    private fun makeCall(){
        with(connection){
            isInitiator = true
            val list = mutableListOf<String>()
            list.add(localStream.id)
            localStream.videoTracks.forEach {
                addTrack(it,list)
            }
            localStream.audioTracks.forEach {
                addTrack(it,list)
            }
            createOffer(WebRTCMediaConstraints(),object :WebRTCPeerConnection.SdpCreateListener{
                override fun onSuccess(description: WebRTCSessionDescription) {
                    setInitiatorLocalSdp(description)
                }

                override fun onError(error: String) =didReceiveError(error)



            })

        }
    }
    private fun shareScreen(){
        WebRTCMediaDevices.getDisplayMedia(this, WebRTCMediaStreamConstraints( true, true),object :WebRTCMediaDevices.GetUserMediaListener{
            override fun onSuccess(mediaStream: WebRTCMediaStream) {
                localStream = mediaStream
                //localView.setSrcObject(localStream)
                localView.setMirror(false)
            }
            override fun onError(error: String) {
                didReceiveError(error)
            }
        })
    }

    private fun switchCamera(){
        if (this::localStream.isInitialized){
            localStream.videoTracks.forEach {
                val constraints = WebRTCMediaTrackConstraints(null)
                val nextPosition = if (currentCameraPosition == "user") "environment" else "user"
                constraints.facingMode = nextPosition
                it.applyConstraints(constraints,object : WebRTCMediaStreamTrack.FancyRTCMediaStreamTrackListener{
                    override fun onSuccess() {
                        if (nextPosition == "environment") {
                            localView.setMirror(false)
                        } else {
                            localView.setMirror(true)
                        }
                        currentCameraPosition = nextPosition
                    }
                    override fun onError(error: String) {
                        didReceiveError(error)
                    }

                })
            }
        }
    }

    private fun answerCall(){

    }

    private fun mute(){
        if (this::localStream.isInitialized){
            if (isMute){
                isMute=false
                localStream.audioTracks.forEach {
                    it.mute=false
                }
            }else{
                isMute=true
                localStream.audioTracks.forEach {
                    it.mute=true
                }
            }
        }
    }
    private fun endCall(){
        connection.close()
        connection.dispose()
    }

    private fun setInitiatorLocalSdp(sdp: WebRTCSessionDescription) {
        if (connection.localDescription != null && connection.localDescription!!.type === WebRTCSdpType.ANSWER && sdp.type === WebRTCSdpType.ANSWER) return
        connection.setLocalDescription(sdp,object :WebRTCPeerConnection.SdpSetListener{
            override fun onSuccess() = sendInitiatorSdp(sdp)
            override fun onError(error: String?) = didReceiveError(error)
        })
    }
    private fun sendInitiatorSdp(sdp: WebRTCSessionDescription) {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("from",me)
            jsonObject.put("to",other)
            jsonObject.put("sdp",sdp.description)
            socket.emit("call",jsonObject)
        }catch (e:JSONException){
            e.printStackTrace()
        }
    }
    private fun setUpUserMedia(){
        val video: MutableMap<String, Any> = HashMap()
        video["facingMode"] = "user"
        video["width"] = 960
        video["height"] = 720
        currentCameraPosition = "user"
        val constraints = WebRTCMediaStreamConstraints(true,video)
        WebRTCMediaDevices.getUserMedia(this,constraints,object :WebRTCMediaDevices.GetUserMediaListener{
            override fun onSuccess(mediaStream: WebRTCMediaStream) {
                localStream = mediaStream
                //localView.setSrcObject(localStream)
                socketInit()
            }
            override fun onError(error: String) {
                didReceiveError(error)
            }
        })
    }
    private fun handleAnswerReceived(sdp: WebRTCSessionDescription) {
        if  (inCall) return
        val newSdp =  WebRTCSessionDescription(WebRTCSdpType.ANSWER, sdp.description)
        connection.setRemoteDescription(newSdp,object :WebRTCPeerConnection.SdpSetListener{
            override fun onSuccess() {
                inCall=true
            }
            override fun onError(error: String?) {
                didReceiveError(error)
            }
        })
    }
    private fun createAnswerForOfferReceived(sdp: WebRTCSessionDescription) {
        connection.setRemoteDescription(sdp,object :WebRTCPeerConnection.SdpSetListener{
            override fun onSuccess() {
                handleRemoteDescriptionSet()
                connection.createAnswer(WebRTCMediaConstraints(),object :WebRTCPeerConnection.SdpCreateListener{
                    override fun onSuccess(description: WebRTCSessionDescription) {
                        setNonInitiatorLocalSdp(description)
                    }
                    override fun onError(error: String) = didReceiveError(error)
                })
            }
            override fun onError(error: String?) = didReceiveError(error)
        })
    }
    private fun setNonInitiatorLocalSdp(sdp: WebRTCSessionDescription) {
//        if (connection.localDescription == null)
//            return
//        if (connection.localDescription!!.type == WebRTCSdpType.ANSWER && sdp.type == WebRTCSdpType.ANSWER)
//            return
        connection.setLocalDescription(sdp,object :WebRTCPeerConnection.SdpSetListener{
            override fun onSuccess() = sendNonInitiatorSdp(sdp)
            override fun onError(error: String?) = didReceiveError(error)
        })
    }
    private fun sendNonInitiatorSdp(sdp: WebRTCSessionDescription) {
        val jsonObject =  JSONObject()
        try {
            jsonObject.put("from", me)
            jsonObject.put("to", other)
            jsonObject.put("sdp", sdp.description)
            handleAnswerReceived(sdp)  // ???
            socket.emit("answered", jsonObject)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
    private fun didReceiveError(error: String?) {
        error?.let {
            Log.e(TAG,error)
            Toast.makeText(this,error,Toast.LENGTH_SHORT).show()
        }
    }
    private fun handleRemoteDescriptionSet() {
        remoteIceCandidates.forEach {
            connection.addIceCandidate(it)
        }
        remoteIceCandidates.clear()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            WebRTCApplicationHelper.getInstance().handleResult(requestCode, resultCode, data)
        }catch (e:Exception){e.printStackTrace()}

    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppWebRTC.WEBRTC_PERMISSIONS_REQUEST_CODE) {
            if (AppWebRTC.hasPermissions(this)) {
                setUpUserMedia()
            }
        }else{
            didReceiveError("Permissions denied")
        }
    }
    override fun onStop() {
        myCoroutineScope.cancel()
        super.onStop()
    }
    companion object{
        private var TAG: String = "appTech.WebRTC.MainActivity"
    }
}
