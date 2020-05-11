
package com.apptech.webrtcdemo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_users.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class UsersActivity : AppCompatActivity(),SignallingClient.SignalingRoomsInterface {

    private lateinit var me: String
    private lateinit var viewAdapter:UsersAdapter
    private lateinit var viewLayoutManager: LinearLayoutManager
    private val userList: MutableList<User> = mutableListOf()
    private val myCoroutineScope = CoroutineScope(Dispatchers.IO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)
        me = UUID.randomUUID().toString()
        viewAdapter = UsersAdapter(this,userList,me)
        viewLayoutManager = LinearLayoutManager(this)
        user_list.apply {
            adapter = viewAdapter
            layoutManager = viewLayoutManager
            setHasFixedSize(true)
        }
        //SignallingClient.initRooms(this,"test1")
        create_room.setOnClickListener {
            SignallingClient.creteOrJoinRoom()
        }
    }

    override fun onResume() {
        super.onResume()
        SignallingClient.initRooms(this,"test1")
    }
    companion object{
        private var TAG: String = "appTech.WebRTC.UsersActivity"
    }

    override fun onRoomsReceived(data: JSONObject) {
        myCoroutineScope.launch (Main){
            userList.clear()
            val rooms = data.getJSONArray("rooms")
            for (i in 0 until rooms.length()){
                val room = rooms[i] as JSONObject
                userList.add(User(room.getString("roomId")))
            }
            viewAdapter.notifyDataSetChanged()
        }

    }

    override fun onCreatedRoom() {
        val intent = Intent(this,MainActivity::class.java)
        //intent.putExtra("to")
        intent.putExtra("me",me)
        intent.putExtra("screen_share",false)
        startActivity(intent)
    }

    override fun onJoinedRoom() {
        val intent = Intent(this,MainActivity::class.java)
        //intent.putExtra("to")
        intent.putExtra("me",me)
        intent.putExtra("screen_share",false)
        startActivity(intent)
    }

    override fun onRoomFull() {
        Log.e("fullRoom","full")
    }
}
