package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress

class TransferReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", -1)
        
        // Clear the request notification for normal actions immediately
        if (notificationId != -1 && action != "com.opendrop.ACTION_REPLY_MESSAGE") {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        val transfer = NetworkTransfer.getInstance(context)
        Log.d("TransferReceiver", "Received action: $action")
        
        when (action) {
            "com.example.ACTION_ACCEPT" -> {
                transfer.acceptIncomingRequest()
            }
            "com.example.ACTION_REJECT" -> {
                transfer.rejectIncomingRequest()
            }
            "com.opendrop.ACTION_ANSWER_CALL" -> {
                transfer.onCallDecision?.invoke(true)
            }
            "com.opendrop.ACTION_DECLINE_CALL" -> {
                transfer.onCallDecision?.invoke(false)
            }
            "com.opendrop.ACTION_HANGUP_CALL" -> {
                transfer.endCall()
            }
            "com.opendrop.ACTION_REPLY_MESSAGE" -> {
                val remoteInputResults = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
                if (remoteInputResults != null) {
                    val replyText = remoteInputResults.getCharSequence("extra_reply_text")?.toString()
                    val senderIp = intent.getStringExtra("sender_ip")
                    val senderPort = intent.getIntExtra("sender_port", -1)
                    val senderName = intent.getStringExtra("sender_name") ?: "Unknown"

                    if (!replyText.isNullOrBlank() && senderIp != null && senderPort != -1) {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val address = InetAddress.getByName(senderIp)
                                val device = DiscoveredDevice(id = "temp-$senderName", name = senderName, address = address, port = senderPort)
                                
                                // Show "Sending reply..." status in notification
                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                if (notificationId != -1) {
                                    val sendingNotification = androidx.core.app.NotificationCompat.Builder(context, "opendrop_channel")
                                        .setSmallIcon(android.R.drawable.ic_menu_share)
                                        .setContentTitle("Sending reply...")
                                        .setContentText(replyText)
                                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                                        .setProgress(0, 0, true) // Indeterminate progress spinner
                                        .build()
                                    notificationManager.notify(notificationId, sendingNotification)
                                }

                                val success = transfer.sendMessage(device, replyText)
                                Log.d("TransferReceiver", "Reply sent: $success")

                                if (notificationId != -1) {
                                    if (success) {
                                        // Show success confirmation
                                        val successNotification = androidx.core.app.NotificationCompat.Builder(context, "opendrop_channel")
                                            .setSmallIcon(android.R.drawable.ic_menu_share)
                                            .setContentTitle("Reply sent to $senderName")
                                            .setContentText("You: $replyText")
                                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                                            .setAutoCancel(true)
                                            .build()
                                        notificationManager.notify(notificationId, successNotification)
                                        
                                        // Auto-dismiss after 2 seconds
                                        kotlinx.coroutines.delay(2000)
                                        notificationManager.cancel(notificationId)
                                    } else {
                                        // Show failure confirmation
                                        val failureNotification = androidx.core.app.NotificationCompat.Builder(context, "opendrop_channel")
                                            .setSmallIcon(android.R.drawable.ic_menu_share)
                                            .setContentTitle("Failed to send reply")
                                            .setContentText("Could not reach $senderName")
                                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                                            .setAutoCancel(true)
                                            .build()
                                        notificationManager.notify(notificationId, failureNotification)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("TransferReceiver", "Error sending reply", e)
                                if (notificationId != -1) {
                                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    val errorNotification = androidx.core.app.NotificationCompat.Builder(context, "opendrop_channel")
                                        .setSmallIcon(android.R.drawable.ic_menu_share)
                                        .setContentTitle("Failed to send reply")
                                        .setContentText(e.message ?: "Unknown error")
                                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                                        .setAutoCancel(true)
                                        .build()
                                    notificationManager.notify(notificationId, errorNotification)
                                }
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }
        }
    }
}
