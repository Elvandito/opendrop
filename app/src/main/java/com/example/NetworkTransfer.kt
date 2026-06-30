package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.MediaRecorder
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val address: InetAddress,
    val port: Int
)

data class TransferHistoryItem(
    val deviceName: String,
    val type: String, // "Message" or "File"
    val content: String,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val fileSize: Long = 0L
)

data class SyncHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val type: String, // "live_note" or "clipboard"
    val content: String,
    val timestamp: Long
)

fun syncHistoryListToJson(list: List<SyncHistoryItem>): String {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject().apply {
            put("id", item.id)
            put("senderName", item.senderName)
            put("type", item.type)
            put("content", item.content)
            put("timestamp", item.timestamp)
        }
        array.put(obj)
    }
    return array.toString()
}

fun jsonToSyncHistoryList(jsonStr: String?): List<SyncHistoryItem> {
    if (jsonStr.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<SyncHistoryItem>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                SyncHistoryItem(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    senderName = obj.optString("senderName", ""),
                    type = obj.optString("type", ""),
                    content = obj.optString("content", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups < 0 || digitGroups >= units.size) return "$size B"
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun transferHistoryListToJson(list: List<TransferHistoryItem>): String {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject().apply {
            put("deviceName", item.deviceName)
            put("type", item.type)
            put("content", item.content)
            put("isSent", item.isSent)
            put("timestamp", item.timestamp)
            put("filePath", item.filePath ?: "")
            put("fileSize", item.fileSize)
        }
        array.put(obj)
    }
    return array.toString()
}

fun jsonToTransferHistoryList(jsonStr: String?): List<TransferHistoryItem> {
    if (jsonStr.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<TransferHistoryItem>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val path = obj.optString("filePath")
            list.add(
                TransferHistoryItem(
                    deviceName = obj.getString("deviceName"),
                    type = obj.getString("type"),
                    content = obj.getString("content"),
                    isSent = obj.getBoolean("isSent"),
                    timestamp = obj.getLong("timestamp"),
                    filePath = if (path.isEmpty()) null else path,
                    fileSize = obj.optLong("fileSize", 0L)
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

data class TransferProgress(
    val fileName: String,
    val progress: Float, // 0.0 to 1.0
    val isSending: Boolean,
    val bytesTransferred: Long = 0L,
    val fileSize: Long = 0L
)

data class IncomingTransferRequest(
    val senderName: String,
    val fileName: String,
    val fileSize: Long,
    val onAccept: () -> Unit,
    val onReject: () -> Unit
)

class NetworkTransfer private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: NetworkTransfer? = null

        fun getInstance(context: Context): NetworkTransfer {
            return INSTANCE ?: synchronized(this) {
                val instance = NetworkTransfer(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val TAG = "NetworkTransfer"
    private val SERVICE_TYPE = "_opendrop._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferHistoryItem>>(emptyList())
    val transferHistory: StateFlow<List<TransferHistoryItem>> = _transferHistory.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    private val _liveNote = MutableStateFlow<String>("")
    val liveNote: StateFlow<String> = _liveNote.asStateFlow()
    private var liveNoteLastUpdated = 0L

    private val _syncedClipboard = MutableStateFlow<String>("")
    val syncedClipboard: StateFlow<String> = _syncedClipboard.asStateFlow()
    private var syncedClipboardLastUpdated = 0L

    private val _syncHistory = MutableStateFlow<List<SyncHistoryItem>>(emptyList())
    val syncHistory: StateFlow<List<SyncHistoryItem>> = _syncHistory.asStateFlow()

    val myDeviceId: String
    var myDeviceName: String
    private var myServiceName: String

    var serverPort: Int = 50505
        private set

    enum class CallState { IDLE, CALLING, RINGING, CONNECTED }

    data class CallStatus(
        val state: CallState = CallState.IDLE,
        val peerName: String = "",
        val peerIp: String = "",
        val peerPort: Int = -1,
        val isIncoming: Boolean = false,
        val showFullScreen: Boolean = false
    )

    private val _callStatus = MutableStateFlow<CallStatus>(CallStatus())
    val callStatus: StateFlow<CallStatus> = _callStatus.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var ringtonePlayer: android.media.Ringtone? = null

    fun expandCall() {
        _callStatus.update { it.copy(showFullScreen = true) }
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
    }

    fun toggleSpeaker() {
        val nextState = !_isSpeakerOn.value
        _isSpeakerOn.value = nextState
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audioManager.isSpeakerphoneOn = nextState
            Log.d(TAG, "Speakerphone toggled to $nextState")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle speakerphone", e)
        }
    }

    @Volatile
    var onCallDecision: ((Boolean) -> Unit)? = null

    @Volatile
    var activeCallLatch: CountDownLatch? = null

    @Volatile
    var tempAcceptedSender: String? = null
    @Volatile
    var tempAcceptedTimestamp: Long = 0L

    fun updateServerPort(port: Int) {
        serverPort = port
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("server_port", port).apply()
        if (isServerRunning) {
            stopServer()
            startServer()
        }
    }

    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var activeSocket: java.net.Socket? = null

    @Volatile
    private var isTransferCancelled = false

    fun cancelActiveTransfer() {
        isTransferCancelled = true
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeSocket = null
    }

    fun getLocalPort(): Int? = serverSocket?.localPort

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    init {
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        var savedId = prefs.getString("device_id", null)
        if (savedId == null) {
            savedId = UUID.randomUUID().toString().take(6)
            prefs.edit().putString("device_id", savedId).apply()
        }
        myDeviceId = savedId
        myDeviceName = prefs.getString("device_name", Build.MODEL) ?: Build.MODEL
        myServiceName = "OpenDrop-$myDeviceName-$myDeviceId"
        serverPort = prefs.getInt("server_port", 50505)

        createNotificationChannel()
        loadHistoryFromPrefs()
        loadSyncFromPrefs()
    }

    private fun loadHistoryFromPrefs() {
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("transfer_history_json", null)
        val list = jsonToTransferHistoryList(jsonStr)
        _transferHistory.value = list
    }

    fun saveHistoryToPrefs(list: List<TransferHistoryItem>) {
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val jsonStr = transferHistoryListToJson(list)
        prefs.edit().putString("transfer_history_json", jsonStr).apply()
    }

    private fun loadSyncFromPrefs() {
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        _liveNote.value = prefs.getString("sync_live_note", "") ?: ""
        _syncedClipboard.value = prefs.getString("synced_clipboard", "") ?: ""
        val jsonStr = prefs.getString("sync_history_json", null)
        _syncHistory.value = jsonToSyncHistoryList(jsonStr)
    }

    fun updateLocalSyncState(type: String, content: String, timestamp: Long, senderName: String) {
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        if (type == "live_note") {
            if (timestamp > liveNoteLastUpdated) {
                liveNoteLastUpdated = timestamp
                _liveNote.value = content
                prefs.edit().putString("sync_live_note", content).apply()
            }
        } else if (type == "clipboard") {
            if (timestamp > syncedClipboardLastUpdated) {
                syncedClipboardLastUpdated = timestamp
                _syncedClipboard.value = content
                prefs.edit().putString("synced_clipboard", content).apply()

                if (senderName != "Me" && senderName != myDeviceName) {
                    // Save to history only if received from others or newly copied locally
                    val newItem = SyncHistoryItem(
                        senderName = senderName,
                        type = type,
                        content = content,
                        timestamp = timestamp
                    )
                    _syncHistory.update { current ->
                        val newList = (listOf(newItem) + current).take(50)
                        val jsonStr = syncHistoryListToJson(newList)
                        prefs.edit().putString("sync_history_json", jsonStr).apply()
                        newList
                    }

                    showCompletionNotification(
                        title = "Synced Clipboard",
                        message = "From $senderName: $content",
                        filePath = null,
                        isMessage = true,
                        senderIp = null,
                        senderPort = 0,
                        senderName = senderName
                    )
                }
            }
        }
    }

    fun deleteSyncHistoryItem(item: SyncHistoryItem) {
        _syncHistory.update { current ->
            val newList = current.filter { it.id != item.id }
            val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("sync_history_json", syncHistoryListToJson(newList)).apply()
            newList
        }
    }

    fun clearSyncHistory() {
        _syncHistory.value = emptyList()
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("sync_history_json").apply()
    }

    suspend fun broadcastSyncData(syncDataType: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val activeDevices = _devices.value.distinctBy { it.name }
        var anySuccess = false
        val timestamp = System.currentTimeMillis()

        // Update local state first
        updateLocalSyncState(syncDataType, content, timestamp, "Me")

        activeDevices.forEach { device ->
            try {
                Socket(device.address, device.port).use { s ->
                    s.tcpNoDelay = true
                    s.soTimeout = 5000
                    val out = DataOutputStream(java.io.BufferedOutputStream(s.getOutputStream(), 16384))
                    out.writeInt(0x4F504450)
                    out.writeByte(4) // Real-time Sync type
                    out.writeUTF(myDeviceName)
                    out.writeUTF(syncDataType)
                    out.writeUTF(content)
                    out.writeLong(timestamp)
                    out.flush()
                }
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send sync data to ${device.name}", e)
            }
        }
        anySuccess
    }

    fun deleteHistoryItem(item: TransferHistoryItem) {
        _transferHistory.update { currentList ->
            val newList = currentList.filter { it.timestamp != item.timestamp || it.content != item.content }
            saveHistoryToPrefs(newList)
            newList
        }
        if (!item.isSent && item.type == "File" && item.filePath != null) {
            try {
                val file = File(item.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun acceptIncomingRequest() {
        _incomingRequest.value?.onAccept?.invoke()
    }

    fun rejectIncomingRequest() {
        _incomingRequest.value?.onReject?.invoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "OpenDrop Transfers"
            val descriptionText = "Notifications for incoming and completed file transfers"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("opendrop_channel", name, importance).apply {
                description = descriptionText
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val callChannelName = "Incoming Calls"
            val callChannel = NotificationChannel("opendrop_call_channel", callChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for incoming calls"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                setSound(ringtoneUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    private fun showCompletionNotification(
        title: String, 
        message: String, 
        filePath: String?, 
        isMessage: Boolean,
        senderIp: String? = null,
        senderPort: Int? = null,
        senderName: String? = null
    ) {
        if (isMessage) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val wl = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "OpenDrop:IncomingMessageWake"
                )
                wl.acquire(10000) // 10 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire wake lock for message", e)
            }
        }

        val intent = if (!isMessage && filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                try {
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val mimeType = context.contentResolver.getType(fileUri) ?: "*/*"
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mimeType)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } catch (e: Exception) {
                    android.content.Intent(context, MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                }
            } else {
                android.content.Intent(context, MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
        } else {
            android.content.Intent(context, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "opendrop_channel")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (isMessage) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (isMessage) {
            builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (senderIp != null && senderPort != null && senderName != null) {
                val replyLabel = "Reply to $senderName"
                val remoteInput = androidx.core.app.RemoteInput.Builder("extra_reply_text")
                    .setLabel(replyLabel)
                    .build()

                val replyIntent = android.content.Intent(context, TransferReceiver::class.java).apply {
                    action = "com.opendrop.ACTION_REPLY_MESSAGE"
                    putExtra("sender_ip", senderIp)
                    putExtra("sender_port", senderPort)
                    putExtra("sender_name", senderName)
                    putExtra("notification_id", senderIp.hashCode())
                }
                val replyPendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    senderIp.hashCode(),
                    replyIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )

                val replyAction = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Reply",
                    replyPendingIntent
                ).addRemoteInput(remoteInput).build()

                builder.addAction(replyAction)
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (isMessage && senderIp != null) senderIp.hashCode() else System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, builder.build())
    }

    private fun showNotification(title: String, message: String) {
        showCompletionNotification(title, message, null, true, null, null, null)
    }

    private fun showIncomingRequestNotification(senderName: String, fileName: String, fileSize: Long) {
        val notificationId = 1001
        
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "OpenDrop:IncomingRequestWake"
            )
            wl.acquire(10000) // 10 seconds
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }

        val acceptIntent = android.content.Intent(context, TransferReceiver::class.java).apply {
            action = "com.example.ACTION_ACCEPT"
            putExtra("notification_id", notificationId)
        }
        val acceptPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            1,
            acceptIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = android.content.Intent(context, TransferReceiver::class.java).apply {
            action = "com.example.ACTION_REJECT"
            putExtra("notification_id", notificationId)
        }
        val rejectPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            2,
            rejectIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "opendrop_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Incoming File")
            .setContentText("$senderName wants to send $fileName (${formatFileSize(fileSize)})")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_add, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Reject", rejectPendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun showProgressNotification(fileName: String, progress: Float, isSending: Boolean, fileSize: Long, bytesTransferred: Long) {
        val notificationId = 1002
        val progressPercent = (progress * 100).toInt()
        val title = if (isSending) "Sending file..." else "Receiving file..."
        val contentText = if (fileSize > 0) {
            "${formatFileSize(bytesTransferred)} of ${formatFileSize(fileSize)} ($progressPercent%)"
        } else {
            "$fileName ($progressPercent%)"
        }
        
        val builder = NotificationCompat.Builder(context, "opendrop_channel")
            .setSmallIcon(if (isSending) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(fileName)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progressPercent, false)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun dismissProgressNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1002)
    }

    private var isUdpRunning = false
    private var udpBroadcasterThread: Thread? = null
    private var udpListenerThread: Thread? = null

    fun start() {
        acquireLocks()
        startServer()
        startUdpBroadcast()
    }

    fun stop() {
        stopDiscovery()
        stopUdpBroadcast()
        stopServer()
        releaseLocks()
    }

    private fun startUdpBroadcast() {
        if (isUdpRunning) return
        isUdpRunning = true

        udpBroadcasterThread = Thread {
            try {
                val socket = java.net.DatagramSocket()
                socket.broadcast = true
                while (isUdpRunning) {
                    try {
                        val json = org.json.JSONObject().apply {
                            put("id", myDeviceId)
                            put("name", myDeviceName)
                            put("port", serverPort)
                        }
                        val data = json.toString().toByteArray(Charsets.UTF_8)
                        val address = InetAddress.getByName("255.255.255.255")
                        val packet = java.net.DatagramPacket(data, data.size, address, 50506)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.e(TAG, "UDP broadcast send error", e)
                    }
                    Thread.sleep(3000)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "UDP broadcaster failed", e)
            }
        }

        udpListenerThread = Thread {
            try {
                val socket = java.net.DatagramSocket(null)
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(50506))
                val buffer = ByteArray(1024)
                while (isUdpRunning) {
                    val packet = java.net.DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val dataStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = org.json.JSONObject(dataStr)
                        val id = json.optString("id")
                        val name = json.optString("name")
                        val port = json.optInt("port")
                        
                        val myIp = getLocalIpAddress()
                        val senderIp = packet.address.hostAddress
                        
                        if (id == myDeviceId || (myIp != null && senderIp == myIp)) {
                            continue
                        }
                        
                        if (id.isNotEmpty() && name.isNotEmpty() && port > 0) {
                            val device = DiscoveredDevice(id, name, packet.address, port)
                            _devices.update { current ->
                                val newList = current.toMutableList()
                                newList.removeAll { 
                                    it.id == device.id || 
                                    it.address.hostAddress == device.address.hostAddress 
                                }
                                newList.add(device)
                                newList.take(50)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "UDP receive error", e)
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener failed", e)
            }
        }

        udpBroadcasterThread?.start()
        udpListenerThread?.start()
    }

    private fun stopUdpBroadcast() {
        isUdpRunning = false
        udpBroadcasterThread?.interrupt()
        udpListenerThread?.interrupt()
    }

    private fun acquireLocks() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("OpenDrop:MulticastLock").apply {
                    setReferenceCounted(false)
                }
            }
            multicastLock?.acquire()
            Log.d(TAG, "MulticastLock acquired successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (wifiLock == null) {
                wifiLock = wifiManager.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    } else {
                        @Suppress("DEPRECATION")
                        android.net.wifi.WifiManager.WIFI_MODE_FULL
                    },
                    "OpenDrop:WifiLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wifiLock?.acquire()
            Log.d(TAG, "WifiLock acquired successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wifi lock", e)
        }

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "OpenDrop:WakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock acquired successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseLocks() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "MulticastLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release multicast lock", e)
        }

        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WifiLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wifi lock", e)
        }

        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    fun updateDeviceName(name: String) {
        myDeviceName = name
        myServiceName = "OpenDrop-$myDeviceName-$myDeviceId"
        if (isServerRunning) {
            unregisterService()
            serverSocket?.localPort?.let { registerService(it) }
        }
    }

    private fun startServer() {
        Thread {
            try {
                val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
                val configuredPort = prefs.getInt("server_port", 50505)
                
                try {
                    serverSocket = ServerSocket(configuredPort)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to bind to configured port $configuredPort, trying dynamic port", e)
                    serverSocket = ServerSocket(0)
                }
                
                serverPort = serverSocket!!.localPort
                prefs.edit().putInt("server_port", serverPort).apply()

                val port = serverPort
                isServerRunning = true
                
                registerService(port)
                discoverServices()

                while (isServerRunning) {
                    val client = serverSocket!!.accept()
                    Thread { handleClient(client) }.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }.start()
    }

    private fun stopServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        unregisterService()
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = myServiceName
            serviceType = SERVICE_TYPE
            this.port = port
            getLocalIpAddress()?.let { ip ->
                setAttribute("ip", ip)
            }
            setAttribute("port", port.toString())
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD service", e)
        }
    }

    private fun unregisterService() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (e: Exception) {}
        }
    }

    fun refreshDiscovery() {
        _devices.value = emptyList()
        discoverServices()
    }

    private fun discoverServices() {
        stopDiscovery()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_opendrop")) {
                    if (service.serviceName.startsWith("OpenDrop-")) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (serviceInfo.serviceName.contains(myDeviceId)) {
                                    // It's me, ignore by name
                                    return
                                }
                                val myIp = getLocalIpAddress()

                                val nameParts = serviceInfo.serviceName.split("-")
                                val displayName = if (nameParts.size >= 2) nameParts[1] else serviceInfo.serviceName
                                
                                val ipBytes = serviceInfo.attributes["ip"]
                                val portBytes = serviceInfo.attributes["port"]
                                
                                val realIpStr = if (ipBytes != null) String(ipBytes) else null
                                val realPortInt = if (portBytes != null) String(portBytes).toIntOrNull() else null
                                
                                val resolvedAddressStr = realIpStr ?: serviceInfo.host?.hostAddress
                                val resolvedPort = realPortInt ?: serviceInfo.port
                                
                                if (resolvedAddressStr == null) {
                                    Log.w(TAG, "Resolved service has no host address: ${serviceInfo.serviceName}")
                                    return
                                }
                                
                                val resolvedAddress = try {
                                    InetAddress.getByName(resolvedAddressStr)
                                } catch (e: Exception) {
                                    serviceInfo.host ?: return
                                }

                                if (myIp != null && resolvedAddress.hostAddress == myIp) {
                                    return // It's me, ignore by IP
                                }

                                val device = DiscoveredDevice(
                                    id = serviceInfo.serviceName,
                                    name = displayName,
                                    address = resolvedAddress,
                                    port = resolvedPort
                                )
                                _devices.update { current ->
                                    val newList = current.toMutableList()
                                    newList.removeAll { 
                                        it.id == device.id || 
                                        it.address.hostAddress == device.address.hostAddress || 
                                        it.name == device.name 
                                    }
                                    newList.add(device)
                                    newList.take(50)
                                }
                            }
                        })
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                if (_transferProgress.value != null || _callStatus.value.state != CallState.IDLE) {
                    Log.d(TAG, "Ignoring onServiceLost during active transfer or call")
                    return
                }
                _devices.update { current -> 
                    current.filter { 
                        it.id != service.serviceName && 
                        !it.id.startsWith(service.serviceName) && 
                        !service.serviceName.startsWith(it.id)
                    } 
                }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
        }
        
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) {}
        }
    }

    private fun handleClient(socket: Socket) = runBlocking(Dispatchers.IO) {
        try {
            socket.use { s ->
                activeSocket = s
                isTransferCancelled = false
                s.tcpNoDelay = true
                s.sendBufferSize = 131072
                s.receiveBufferSize = 131072
                s.soTimeout = 30000 // 30 seconds read timeout
                val input = DataInputStream(java.io.BufferedInputStream(s.getInputStream(), 131072))
                val output = DataOutputStream(java.io.BufferedOutputStream(s.getOutputStream(), 131072))
                val magic = input.readInt()
                if (magic != 0x4F504450) return@runBlocking // "OPDP"

                val type = input.readByte().toInt()
                val senderName = input.readUTF()

                if (type == 1) { // Message
                    val senderPort = input.readInt()
                    val message = input.readUTF()
                    val item = TransferHistoryItem(senderName, "Message", message, false)
                    _transferHistory.update { currentList ->
                        val newList = (listOf(item) + currentList).take(50)
                        saveHistoryToPrefs(newList)
                        newList
                    }
                    showCompletionNotification(
                        title = "New Message",
                        message = "From $senderName: $message",
                        filePath = null,
                        isMessage = true,
                        senderIp = s.inetAddress.hostAddress,
                        senderPort = senderPort,
                        senderName = senderName
                    )
                } else if (type == 4) { // Real-time Sync Data
                    val syncDataType = input.readUTF()
                    val content = input.readUTF()
                    val timestamp = input.readLong()
                    updateLocalSyncState(syncDataType, content, timestamp, senderName)
                } else if (type == 2) { // File
                    val fileName = input.readUTF()
                    val fileSize = input.readLong()
                    
                    val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
                    val autoAccept = prefs.getBoolean("auto_accept", false)
                    val autoAcceptDevices = prefs.getStringSet("auto_accept_devices", emptySet()) ?: emptySet()
                    val bulkAutoAcceptEnabled = prefs.getBoolean("bulk_auto_accept", false)
                    
                    val isTemporarilyAccepted = bulkAutoAcceptEnabled && 
                        tempAcceptedSender == senderName && 
                        (System.currentTimeMillis() - tempAcceptedTimestamp < 120000)
                    
                    val isAutoAccepted = (autoAccept && autoAcceptDevices.contains(senderName)) || isTemporarilyAccepted
                    
                    val latch = CountDownLatch(1)
                    var accepted = false

                    if (isAutoAccepted) {
                        accepted = true
                        if (isTemporarilyAccepted) {
                            tempAcceptedTimestamp = System.currentTimeMillis()
                        }
                        latch.countDown()
                    } else {
                        showIncomingRequestNotification(senderName, fileName, fileSize)

                        _incomingRequest.update {
                            IncomingTransferRequest(
                                senderName = senderName,
                                fileName = fileName,
                                fileSize = fileSize,
                                onAccept = {
                                    accepted = true
                                    if (bulkAutoAcceptEnabled) {
                                        tempAcceptedSender = senderName
                                        tempAcceptedTimestamp = System.currentTimeMillis()
                                    }
                                    latch.countDown()
                                },
                                onReject = {
                                    accepted = false
                                    latch.countDown()
                                }
                            )
                        }
                    }

                    latch.await()
                    _incomingRequest.update { null }

                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(1001)
                    } catch (e: Exception) {}

                    if (!accepted) {
                        output.writeBoolean(false)
                        output.flush()
                        return@runBlocking
                    }

                    output.writeBoolean(true)
                    output.flush()

                    val savePrefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
                    val saveFolder = savePrefs.getString("save_path", "OpenDrop") ?: "OpenDrop"
                    val opendropDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), saveFolder)
                    if (!opendropDir.exists()) opendropDir.mkdirs()
                    val file = File(opendropDir, fileName)
                    val outputStream = FileOutputStream(file)

                    _transferProgress.update { TransferProgress(fileName, 0f, false, 0L, fileSize) }
                    showProgressNotification(fileName, 0f, false, fileSize, 0L)

                    var success = false
                    try {
                        coroutineScope {
                            val channel = Channel<ByteArray>(capacity = 4)
                            
                            // Concurrent background network reader coroutine
                            val readerJob = launch(Dispatchers.IO) {
                                try {
                                    val buffer = ByteArray(65536)
                                    var remaining = fileSize
                                    while (remaining > 0 && isActive) {
                                        val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                                        val read = input.read(buffer, 0, toRead)
                                        if (read == -1) break
                                        channel.send(buffer.copyOfRange(0, read))
                                        remaining -= read
                                    }
                                } catch (e: Exception) {
                                    channel.close(e)
                                } finally {
                                    channel.close()
                                }
                            }

                            // Concurrent file writer loop
                            try {
                                outputStream.use { out ->
                                    var downloaded = 0L
                                    var lastUpdatePercent = -1
                                    var lastUiTime = 0L
                                    var lastNotificationTime = 0L
                                    var lastNotificationPercent = -1
                                    
                                    for (chunk in channel) {
                                        out.write(chunk, 0, chunk.size)
                                        downloaded += chunk.size
                                        if (fileSize > 0) {
                                            val currentProgress = downloaded.toFloat() / fileSize
                                            val percent = (currentProgress * 100).toInt()
                                            val now = System.currentTimeMillis()
                                            if (percent != lastUpdatePercent && (now - lastUiTime > 150 || percent == 100)) {
                                                lastUpdatePercent = percent
                                                lastUiTime = now
                                                _transferProgress.update { TransferProgress(fileName, currentProgress, false, downloaded, fileSize) }
                                            }
                                            if (percent == 100 || (now - lastNotificationTime > 1000 && percent - lastNotificationPercent >= 3)) {
                                                lastNotificationPercent = percent
                                                lastNotificationTime = now
                                                showProgressNotification(fileName, currentProgress, false, fileSize, downloaded)
                                            }
                                        }
                                    }
                                    out.flush()
                                    readerJob.join()
                                    success = (downloaded == fileSize)
                                }
                            } catch (e: Exception) {
                                readerJob.cancel()
                                throw e
                            }
                        }
                    } finally {
                        activeSocket = null
                        _transferProgress.update { null }
                        dismissProgressNotification()
                        if (!success) {
                            try { file.delete() } catch (e: Exception) {}
                        }
                    }
                    if (success) {
                        showCompletionNotification("Transfer Complete", "Received $fileName (${formatFileSize(fileSize)}) from $senderName", file.absolutePath, false)
                        val item = TransferHistoryItem(senderName, "File", fileName, false, filePath = file.absolutePath, fileSize = fileSize)
                        _transferHistory.update { currentList ->
                            val newList = (listOf(item) + currentList).take(50)
                            saveHistoryToPrefs(newList)
                            newList
                        }
                    } else {
                        val reason = if (isTransferCancelled) "Transfer Cancelled" else "Transfer Failed"
                        showCompletionNotification(reason, "Transfer of $fileName from $senderName failed or was cancelled", null, false)
                    }
                } else if (type == 3) { // Call Request
                    val senderPort = input.readInt()
                    val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
                    val autoFullScreen = prefs.getBoolean("auto_full_screen_call", false)
                    _callStatus.update { CallStatus(CallState.RINGING, senderName, s.inetAddress.hostAddress, senderPort, isIncoming = true, showFullScreen = autoFullScreen) }
                    showIncomingCallNotification(senderName, s.inetAddress.hostAddress, senderPort)

                    val callLatch = java.util.concurrent.CountDownLatch(1)
                    var callAccepted = false
                    
                    activeCallLatch = callLatch
                    onCallDecision = { accepted ->
                        callAccepted = accepted
                        callLatch.countDown()
                    }

                    val pushbackInputStream = java.io.PushbackInputStream(s.getInputStream(), 1)
                    
                    // Start a thread to monitor if caller disconnects while ringing
                    var callerDisconnected = false
                    val monitorThread = Thread {
                        try {
                            s.soTimeout = 1000
                            while (callLatch.count > 0) {
                                try {
                                    val checkByte = pushbackInputStream.read()
                                    if (checkByte == -1) {
                                        callerDisconnected = true
                                        callLatch.countDown()
                                        break
                                    } else {
                                        pushbackInputStream.unread(checkByte)
                                        break
                                    }
                                } catch (e: java.net.SocketTimeoutException) {
                                    continue
                                }
                            }
                        } catch (e: Exception) {
                            callerDisconnected = true
                            callLatch.countDown()
                        }
                    }
                    monitorThread.start()

                    // 60 second timeout for answering
                    var answeredInTime = false
                    try {
                        answeredInTime = callLatch.await(60, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Call latch interrupted", e)
                    }

                    try {
                        monitorThread.join(2000)
                    } catch (e: Exception) {}


                    // Cancel call notification and stop ringtone
                    try {
                        ringtonePlayer?.stop()
                        ringtonePlayer = null
                    } catch (e: Exception) {}

                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(1002)
                    } catch (e: Exception) {}

                    if (callerDisconnected || !answeredInTime || !callAccepted) {
                        try {
                            output.writeByte(2) // Rejected
                            output.flush()
                        } catch (e: Exception) {}
                        _callStatus.update { CallStatus() }
                        return@runBlocking
                    }

                    output.writeByte(1) // Accepted
                    output.flush()

                    val currentStatus = _callStatus.value
                    _callStatus.update { it.copy(state = CallState.CONNECTED) }
                    showOngoingCallNotification(currentStatus.peerName)
                    runAudioLoop(s, pushbackInputStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
            if (isTransferCancelled) {
                Log.d(TAG, "Client transfer cancelled by user")
            }
        } finally {
            activeSocket = null
        }
    }

    suspend fun sendMessage(device: DiscoveredDevice, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket(device.address, device.port).use { s ->
                s.tcpNoDelay = true
                s.sendBufferSize = 65536
                s.receiveBufferSize = 65536
                val out = DataOutputStream(java.io.BufferedOutputStream(s.getOutputStream(), 65536))
                out.writeInt(0x4F504450)
                out.writeByte(1)
                out.writeUTF(myDeviceName)
                out.writeInt(getLocalPort() ?: 0)
                out.writeUTF(message)
                out.flush()
            }
            val item = TransferHistoryItem(device.name, "Message", message, true)
            _transferHistory.update { currentList ->
                val newList = (listOf(item) + currentList).take(50)
                saveHistoryToPrefs(newList)
                newList
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            false
        }
    }

    suspend fun sendFile(device: DiscoveredDevice, uri: Uri, fileName: String, fileSize: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket(device.address, device.port).use { s ->
                activeSocket = s
                isTransferCancelled = false
                s.tcpNoDelay = true
                s.sendBufferSize = 131072
                s.receiveBufferSize = 131072
                s.soTimeout = 30000 // 30 seconds timeout
                val out = DataOutputStream(java.io.BufferedOutputStream(s.getOutputStream(), 131072))
                val input = DataInputStream(java.io.BufferedInputStream(s.getInputStream(), 131072))
                out.writeInt(0x4F504450)
                out.writeByte(2)
                out.writeUTF(myDeviceName)
                out.writeUTF(fileName)
                out.writeLong(fileSize)
                out.flush()
                
                val accepted = input.readBoolean()
                if (!accepted) {
                    return@withContext false
                }

                _transferProgress.update { TransferProgress(fileName, 0f, true, 0L, fileSize) }
                showProgressNotification(fileName, 0f, true, fileSize, 0L)

                var success = false
                try {
                    coroutineScope {
                        val channel = Channel<ByteArray>(capacity = 4)
                        
                        // Concurrent background file reader coroutine
                        val readerJob = launch(Dispatchers.IO) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { fileInput ->
                                    val buffer = ByteArray(65536)
                                    while (isActive) {
                                        val read = fileInput.read(buffer)
                                        if (read == -1) break
                                        channel.send(buffer.copyOfRange(0, read))
                                    }
                                }
                            } catch (e: Exception) {
                                channel.close(e)
                            } finally {
                                channel.close()
                            }
                        }

                        // Concurrent socket writer loop
                        try {
                            var uploaded = 0L
                            var lastUpdatePercent = -1
                            var lastUiTime = 0L
                            var lastNotificationTime = 0L
                            var lastNotificationPercent = -1
                            
                            for (chunk in channel) {
                                out.write(chunk, 0, chunk.size)
                                uploaded += chunk.size
                                if (fileSize > 0) {
                                    val currentProgress = uploaded.toFloat() / fileSize
                                    val percent = (currentProgress * 100).toInt()
                                    val now = System.currentTimeMillis()
                                    if (percent != lastUpdatePercent && (now - lastUiTime > 150 || percent == 100)) {
                                        lastUpdatePercent = percent
                                        lastUiTime = now
                                        _transferProgress.update { TransferProgress(fileName, currentProgress, true, uploaded, fileSize) }
                                    }
                                    if (percent == 100 || (now - lastNotificationTime > 1000 && percent - lastNotificationPercent >= 3)) {
                                        lastNotificationPercent = percent
                                        lastNotificationTime = now
                                        showProgressNotification(fileName, currentProgress, true, fileSize, uploaded)
                                    }
                                }
                            }
                            out.flush()
                            readerJob.join()
                            success = true
                        } catch (e: Exception) {
                            readerJob.cancel()
                            throw e
                        }
                    }
                } finally {
                    activeSocket = null
                    _transferProgress.update { null }
                    dismissProgressNotification()
                }
            }
            val item = TransferHistoryItem(device.name, "File", fileName, true, fileSize = fileSize)
            _transferHistory.update { currentList ->
                val newList = (listOf(item) + currentList).take(50)
                saveHistoryToPrefs(newList)
                newList
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file", e)
            _transferProgress.update { null }
            false
        } finally {
            activeSocket = null
        }
    }

    fun endCall() {
        if (_callStatus.value.state != CallState.IDLE) {
            _callStatus.update { CallStatus() }
            try {
                activeSocket?.close()
            } catch (e: Exception) {}
            activeSocket = null
            activeCallLatch?.countDown()
            try {
                ringtonePlayer?.stop()
                ringtonePlayer = null
            } catch (e: Exception) {}
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(1004)
                nm.cancel(1002)
            } catch (e: Exception) {}
        }
    }

    suspend fun initiateCall(device: DiscoveredDevice) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val autoFullScreen = prefs.getBoolean("auto_full_screen_call", false)
        _callStatus.update { CallStatus(CallState.CALLING, device.name, device.address.hostAddress, device.port, isIncoming = false, showFullScreen = autoFullScreen) }
        try {
            val s = Socket(device.address, device.port)
            activeSocket = s
            s.tcpNoDelay = true
            val out = DataOutputStream(java.io.BufferedOutputStream(s.getOutputStream()))
            val input = DataInputStream(java.io.BufferedInputStream(s.getInputStream()))

            out.writeInt(0x4F504450)
            out.writeByte(3) // Call request
            out.writeUTF(myDeviceName)
            out.writeInt(getLocalPort() ?: 0)
            out.flush()

            // Wait for answer (with a timeout, e.g., 60s)
            s.soTimeout = 65000
            val response = input.readByte().toInt()
            if (response == 1) { // Accepted
                val currentStatus = _callStatus.value
                _callStatus.update { it.copy(state = CallState.CONNECTED) }
                showOngoingCallNotification(currentStatus.peerName)
                runAudioLoop(s)
            } else {
                s.close()
                _callStatus.update { CallStatus() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call failed", e)
            _callStatus.update { CallStatus() }
        } finally {
            activeSocket = null
        }
    }

    private fun runAudioLoop(socket: Socket, customInputStream: java.io.InputStream? = null) {
        val sampleRate = 16000
        val channelIn = AudioFormat.CHANNEL_IN_MONO
        val channelOut = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeRecord = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)
        val bufferSizeTrack = AudioTrack.getMinBufferSize(sampleRate, channelOut, audioFormat)

        var audioRecord: AudioRecord? = null
        var audioTrack: AudioTrack? = null
        var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
        var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalSpeakerphoneState = audioManager.isSpeakerphoneOn
        val originalMode = audioManager.mode

        // Reset state flows for the new call session
        _isMicMuted.value = false
        _isSpeakerOn.value = true

        try {
            socket.soTimeout = 0
        } catch (e: Exception) {}

        try {
            // Configure speakerphone for high quality local communication
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = _isSpeakerOn.value
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure speakerphone", e)
            }

            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Use VOICE_COMMUNICATION for better audio processing/echo cancellation
                    sampleRate,
                    channelIn,
                    audioFormat,
                    if (bufferSizeRecord > 0) bufferSizeRecord else 2048
                )

                // Initialize Acoustic Echo Canceler and Noise Suppressor
                val sessionId = audioRecord.audioSessionId
                try {
                    if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(sessionId)
                        echoCanceler?.enabled = true
                        Log.d(TAG, "AcousticEchoCanceler enabled successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable AcousticEchoCanceler", e)
                }

                try {
                    if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(sessionId)
                        noiseSuppressor?.enabled = true
                        Log.d(TAG, "NoiseSuppressor enabled successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable NoiseSuppressor", e)
                }
            } else {
                Log.w(TAG, "RECORD_AUDIO permission not granted, mic streaming will be disabled.")
            }

            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                channelOut,
                audioFormat,
                if (bufferSizeTrack > 0) bufferSizeTrack else 2048,
                AudioTrack.MODE_STREAM
            )

            audioTrack.play()
            try { audioRecord?.startRecording() } catch (e: Exception) { e.printStackTrace() }

            val readThread = Thread {
                val buffer = ByteArray(2048)
                try {
                    val input = customInputStream ?: socket.getInputStream()
                    while (_callStatus.value.state == CallState.CONNECTED) {
                        var totalRead = 0
                        while (totalRead < buffer.size && _callStatus.value.state == CallState.CONNECTED) {
                            val read = input.read(buffer, totalRead, buffer.size - totalRead)
                            if (read == -1) break
                            totalRead += read
                        }
                        if (totalRead > 0) {
                            audioTrack.write(buffer, 0, totalRead)
                        } else {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Audio read thread error/closed", e)
                } finally {
                    try { audioTrack.stop() } catch (e: Exception) {}
                    try { audioTrack.release() } catch (e: Exception) {}
                    endCall()
                }
            }

            val writeThread = Thread {
                val buffer = ByteArray(2048)
                try {
                    val output = socket.getOutputStream()
                    while (_callStatus.value.state == CallState.CONNECTED) {
                        if (audioRecord != null) {
                            val read = audioRecord.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                if (_isMicMuted.value) {
                                    buffer.fill(0)
                                }
                                output.write(buffer, 0, read)
                                output.flush()
                            }
                        } else {
                            Thread.sleep(100)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Audio write thread error/closed", e)
                } finally {
                    try { audioRecord?.stop() } catch (e: Exception) {}
                    try { audioRecord?.release() } catch (e: Exception) {}
                    endCall()
                }
            }

            readThread.start()
            writeThread.start()

            readThread.join()
            writeThread.join()

        } catch (e: Exception) {
            Log.e(TAG, "Audio loop error", e)
        } finally {
            // Restore original audio configurations
            try {
                audioManager.isSpeakerphoneOn = originalSpeakerphoneState
                audioManager.mode = originalMode
            } catch (e: Exception) {}

            try { echoCanceler?.release() } catch (e: Exception) {}
            try { noiseSuppressor?.release() } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
            _callStatus.update { CallStatus() }
        }
    }

    private fun showIncomingCallNotification(senderName: String, senderIp: String, senderPort: Int) {
        val notificationId = 1002
        val prefs = context.getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val autoFullScreen = prefs.getBoolean("auto_full_screen_call", false)
        
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "OpenDrop:IncomingCallWake"
            )
            wl.acquire(60000)
        } catch (e: Exception) {}

        val answerIntent = android.content.Intent(context, TransferReceiver::class.java).apply {
            action = "com.opendrop.ACTION_ANSWER_CALL"
            putExtra("sender_ip", senderIp)
            putExtra("sender_port", senderPort)
            putExtra("notification_id", notificationId)
        }
        val answerPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            1,
            answerIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = android.content.Intent(context, TransferReceiver::class.java).apply {
            action = "com.opendrop.ACTION_DECLINE_CALL"
            putExtra("sender_ip", senderIp)
            putExtra("sender_port", senderPort)
            putExtra("notification_id", notificationId)
        }
        val declinePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            2,
            declineIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = android.content.Intent(context, MainActivity::class.java).apply {
            action = "com.opendrop.ACTION_EXPAND_CALL"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
            context,
            3,
            fullScreenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "opendrop_call_channel")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Voice Call")
            .setContentText("Incoming local call from $senderName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Decline", declinePendingIntent)

        if (autoFullScreen) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            try {
                fullScreenPendingIntent.send()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forcefully start full screen activity via PendingIntent", e)
                try {
                    context.startActivity(fullScreenIntent)
                } catch (e2: Exception) {}
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())

        try {
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = android.media.RingtoneManager.getRingtone(context, ringtoneUri)
            ringtonePlayer?.play()
        } catch (e: Exception) {}
    }

    private fun showOngoingCallNotification(peerName: String) {
        val notificationId = 1004
        
        val fullScreenIntent = android.content.Intent(context, MainActivity::class.java).apply {
            action = "com.opendrop.ACTION_EXPAND_CALL"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
            context,
            4,
            fullScreenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = android.content.Intent(context, TransferReceiver::class.java).apply {
            action = "com.opendrop.ACTION_HANGUP_CALL"
        }
        val hangupPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            5,
            hangupIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "opendrop_service_channel")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Ongoing Call")
            .setContentText("Call with $peerName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hang Up", hangupPendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }
}
