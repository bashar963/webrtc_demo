/*
 * Created by Bashar Alkaddah on 2020.
 * Copyright (c) $year, Apptech Ltd. All rights reserved.
 * balkaddah@apptech.com.tr
 */

package com.apptech.webrtcdemo


import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class MediaProjectionService:Service() {
    override fun onBind(intent: Intent?): IBinder? {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG_FOREGROUND_SERVICE, "My foreground service onCreate().")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent != null) {
            val action = intent.action
            if (action != null) when (action) {
                ACTION_START_FOREGROUND_SERVICE -> {
                    startForegroundService()
                    Toast.makeText(
                        applicationContext,
                        "Foreground service is started.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                ACTION_STOP_FOREGROUND_SERVICE -> {
                    stopForegroundService()
                    Toast.makeText(
                        applicationContext,
                        "Foreground service is stopped.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                ACTION_PLAY -> Toast.makeText(
                    applicationContext,
                    "You click Play button.",
                    Toast.LENGTH_LONG
                ).show()
                ACTION_PAUSE -> Toast.makeText(
                    applicationContext,
                    "You click Pause button.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /* Used to build and start foreground service. */
    private fun startForegroundService() {
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel("screen_sharing", "Screen Sharing Service")
        } else {
            // Create notification default intent.
            val intent = Intent()
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
            // Create notification builder.
            val builder = NotificationCompat.Builder(this,"screen_sharing")
            // Make notification show big text.
            val bigTextStyle = NotificationCompat.BigTextStyle()
            bigTextStyle.setBigContentTitle("Screen Sharing service.")
            bigTextStyle.bigText("you are now sharing your screen")
            // Set big text style.
            builder.setStyle(bigTextStyle)
            builder.setWhen(System.currentTimeMillis())
            builder.setSmallIcon(R.mipmap.ic_launcher)
            val largeIconBitmap =
                BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_background)
            builder.setLargeIcon(largeIconBitmap)
            // Make the notification max priority.
            builder.priority = Notification.PRIORITY_HIGH
            // Make head-up notification.
            builder.setFullScreenIntent(pendingIntent, true)

//            // Add Play button intent in notification.
//            val playIntent = Intent(this, MyService::class.java)
//            playIntent.action = ACTION_PLAY
//            val pendingPlayIntent = PendingIntent.getService(this, 0, playIntent, 0)
//            val playAction = NotificationCompat.Action(
//                R.drawable.ic_monitor,
//                "Play",
//                pendingPlayIntent
//            )
//            builder.addAction(playAction)
//
//            // Add Pause button intent in notification.
//            val pauseIntent = Intent(this, MyService::class.java)
//            pauseIntent.action = ACTION_PAUSE
//            val pendingPrevIntent = PendingIntent.getService(this, 0, pauseIntent, 0)
//            val prevAction = NotificationCompat.Action(
//                R.drawable.ic_end_call,
//                "Pause",
//                pendingPrevIntent
//            )
//            builder.addAction(prevAction)

            // Build the notification.
            val notification: Notification = builder.build()

            // Start foreground service.
            startForeground(1, notification)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String) {
        val resultIntent = Intent(this, MainActivity::class.java)
        // Create the TaskStackBuilder and add the intent, which inflates the back stack
        val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntentWithParentStack(resultIntent)
        val resultPendingIntent: PendingIntent =
            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        val chan =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager =
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("App is running in background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(resultPendingIntent) //intent
            .build()
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(1, notificationBuilder.build())
        startForeground(1, notification)
    }

    private fun stopForegroundService() {
        Log.d(TAG_FOREGROUND_SERVICE, "Stop foreground service.")

        // Stop foreground service and remove the notification.
        stopForeground(true)

        // Stop the foreground service.
        stopSelf()
    }
    companion object{
        private const val TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE"

        const val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"

        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"

        const val ACTION_PAUSE = "ACTION_PAUSE"

        const val ACTION_PLAY = "ACTION_PLAY"
    }
}