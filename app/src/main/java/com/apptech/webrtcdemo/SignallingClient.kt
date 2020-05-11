/*
 * Created by Bashar Alkaddah on 2020.
 * Copyright (c) $year, Apptech Ltd. All rights reserved.
 * balkaddah@apptech.com.tr
 */

package com.apptech.webrtcdemo

import android.annotation.SuppressLint
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.net.URISyntaxException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal class SignallingClient {


    internal interface SignalingInterface {
         fun onRemoteHangUp(msg: String)

         fun onOfferReceived(data: JSONObject)

         fun onAnswerReceived(data: JSONObject)

         fun onIceCandidateReceived(data: JSONObject)

         fun onTryToStart()

         fun onCreatedRoom()

         fun onJoinedRoom()

         fun onNewPeerJoined()

    }
    internal interface SignalingRoomsInterface {

        fun onRoomsReceived(data: JSONObject)
        fun onCreatedRoom()
        fun onJoinedRoom()
        fun onRoomFull()
    }

    companion object {
        private var roomName: String? = null

        init {
            if (roomName == null) {
                roomName = "public"
            }
        }

        private var socket: Socket? = null
        var isChannelReady = false
        var isInitiator = false
        var isStarted = false
        var screenshare = false
        private var callback: SignalingInterface? = null
        private var callback2: SignalingRoomsInterface? = null

        //This piece of code should not go into production?
        //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
        private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            }
        })

        fun initRooms(signalingInterface: SignalingRoomsInterface){
            this.callback2 = signalingInterface
            try {
                val sslcontext = SSLContext.getInstance("TLS")
                sslcontext.init(null, trustAllCerts, null)
                //set the socket.io url here
                socket = IO.socket("http://18.157.69.48:1794")
                Log.d("SignallingClient", "init() called")
                socket?.on(Socket.EVENT_CONNECT){
                    getRooms()
                }

                socket?.on(Socket.EVENT_DISCONNECT){
                    Log.e("EVENT_DISCONNECT","EVENT_DISCONNECT")

                }
                socket?.on(Socket.EVENT_CONNECT_ERROR){
                    Log.e("error",it.toString())
                    Log.e("EVENT_CONNECT_ERROR","EVENT_CONNECT_ERROR")
                }
                socket?.on(Socket.EVENT_CONNECT_TIMEOUT){
                    Log.e("EVENT_CONNECT_TIMEOUT","EVENT_CONNECT_TIMEOUT")
                }
                socket?.on("joined") { args ->
                    Log.d("SignallingClient", "joined call() called with: args = [" + Arrays.toString(args) + "]")
                    isChannelReady = true
                    callback2?.onJoinedRoom()
                }

                socket?.on("created") { args ->
                    Log.d("SignallingClient", "created call() called with: args = [" + Arrays.toString(args) + "]")
                    isInitiator = true
                    callback2?.onCreatedRoom()
                }
                socket?.on("full") { args ->
                    Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]")
                    callback2?.onRoomFull()
                }

                socket?.on("rooms"){
                    val data = it[0] as JSONObject
                    callback2?.onRoomsReceived(data)
                }
                socket?.connect()

            } catch (e: URISyntaxException) {
                e.printStackTrace()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            } catch (e: KeyManagementException) {
                e.printStackTrace()
            }
        }

        fun init(signalingInterface: SignalingInterface) {
            this.callback = signalingInterface
            try {
                //room created event.
                if (isInitiator){
                    callback?.onCreatedRoom()
                }

                //peer joined event
                socket?.on("join") { args ->
                    Log.d("SignallingClient", "join call() called with: args = [" + Arrays.toString(args) + "]")
                    isChannelReady = true
                    callback?.onNewPeerJoined()
                }

                //when you joined a chat room successfully
                if (isChannelReady){
                    callback?.onJoinedRoom()
                }

                //log event
                socket?.on("log") { args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]") }

                //bye event
                socket?.on("bye") { args ->
                    isChannelReady = false
                    isInitiator = false
                    isStarted = false
                    socket?.disconnect()
                    socket?.close()
                    socket = null
                    callback?.onRemoteHangUp(args[0] as String)
                }

                //messages - SDP and ICE candidates are transferred through this
                socket?.on("message") { args ->
                    Log.d("SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]")
                    when (args[0]) {
//                        is String -> {
//                            Log.d("SignallingClient", "String received :: " + args[0])
//                            val data = args[0] as String
//                            if (!data.equals("bye", ignoreCase = true)) {
//                                callback?.onTryToStart()
//                            }
////                            if (data.equals("bye", ignoreCase = true)) {
////
////                            }
//                        }
                        is JSONObject -> try {
                            val data = args[0] as JSONObject
                            Log.d("SignallingClient", "Json Received :: $data")
                            val type = data.getString("type")
                            if (type.equals("offer", ignoreCase = true)) {
                                callback?.onOfferReceived(data)
                            } else if (type.equals("answer", ignoreCase = true) && isStarted) {
                                callback?.onAnswerReceived(data)
                            }
                            else if (type.equals("message", ignoreCase = true)) {
                                callback?.onTryToStart()
                            }
                            else if (type.equals("candidate", ignoreCase = true) && isStarted) {
                                callback?.onIceCandidateReceived(data)
                            }

                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            } catch (e: KeyManagementException) {
                e.printStackTrace()
            }

        }

        private fun emitInitStatement(message: String) {
            Log.d("SignallingClient", "emitInitStatement() called with: event = [create or join], message = [$message]")
            socket!!.emit("create or join", message)
        }
        fun creteOrJoinRoom(roomName: String, screenshare:Boolean = false){
            this.roomName = roomName
            this.screenshare = screenshare
            emitInitStatement(roomName)
        }
        fun emitMessage(message: String) {
            Log.d("SignallingClient", "emitMessage() called with: message = [$message]")
            val obj = JSONObject()
            obj.put("type", "message")
            obj.put("room", roomName)
            socket!!.emit("message", obj)
            callback?.onTryToStart()
        }

        fun emitMessage(message: SessionDescription) {
            try {
                Log.d("SignallingClient", "emitMessage() called with: message = [$message]")
                val obj = JSONObject()
                obj.put("type", message.type.canonicalForm())
                obj.put("sdp", message.description)
                obj.put("room", roomName)
                Log.d("emitMessage", obj.toString())
                socket!!.emit("message", obj)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

        }

        fun getRooms(){
            socket?.emit("rooms")
        }
        fun emitIceCandidate(iceCandidate: IceCandidate) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("type", "candidate")
                jsonObject.put("label", iceCandidate.sdpMLineIndex)
                jsonObject.put("id", iceCandidate.sdpMid)
                jsonObject.put("room", roomName)
                jsonObject.put("candidate", iceCandidate.sdp)
                socket!!.emit("message", jsonObject)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun close() {
            socket?.emit("bye", roomName)

        }
    }
}