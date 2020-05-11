/*
 * Created by Bashar Alkaddah on 2020.
 * Copyright (c) $year, Apptech Ltd. All rights reserved.
 * balkaddah@apptech.com.tr
 */

package com.apptech.webrtcdemo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.user_card.view.*


class UsersAdapter(private val context:Context,private val list: List<User>,private val me:String) :RecyclerView.Adapter<UsersAdapter.ViewHolder>(){

    inner class ViewHolder(view:View):RecyclerView.ViewHolder(view){
        private val username = view.txt_username
        private val call = view.btn_call
        private val btn_screen_share = view.btn_screen_share
        @SuppressLint("SetTextI18n")
        fun bind(user:User){
            if (user.username == me)
            username.text= user.username +"(you)"
            else
                username.text= user.username
            call.setOnClickListener {
                SignallingClient.creteOrJoinRoom(user.username)
            }
            btn_screen_share.setOnClickListener {
                SignallingClient.creteOrJoinRoom(user.username,screenshare = true)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(context).inflate(R.layout.user_card,parent,false))
    override fun getItemCount(): Int = list.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(list[position])
}