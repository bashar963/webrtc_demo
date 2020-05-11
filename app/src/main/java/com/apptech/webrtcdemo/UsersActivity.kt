
package com.apptech.webrtcdemo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import kotlinx.android.synthetic.main.activity_users.*
import kotlinx.android.synthetic.main.create_room_dialog.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
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
        create_room.setOnClickListener {
            MaterialDialog(this).show {
                customView(R.layout.create_room_dialog)
                positiveButton {
                    val ed_room = getCustomView().ed_room
                    val shareScreen = getCustomView().checkBox_shareScreen
                    if (ed_room.editText!!.text.toString().isEmpty() || ed_room.editText!!.text.toString().isBlank()){
                        ed_room.error = "Please type a room name"
                        return@positiveButton
                    }
                    val roomName = ed_room.editText!!.text.toString()
                    SignallingClient.creteOrJoinRoom(roomName,shareScreen.isChecked)
                    it.dismiss()
                }
                negativeButton {
                    it.dismiss()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SignallingClient.initRooms(this)
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
        startActivity(intent)
    }

    override fun onJoinedRoom() {
        val intent = Intent(this,MainActivity::class.java)
        startActivity(intent)
    }

    override fun onRoomFull() {
        Toast.makeText(this,"room is full",Toast.LENGTH_LONG).show()
    }
}
