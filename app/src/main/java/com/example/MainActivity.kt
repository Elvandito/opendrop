package com.example

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.os.Environment
import android.os.StrictMode
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewModelScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch


class OpenDropViewModel(application: Application) : AndroidViewModel(application) {
    val networkTransfer = NetworkTransfer.getInstance(application)
    private val prefs = application.getSharedPreferences("opendrop_prefs", android.content.Context.MODE_PRIVATE)

    var onboardingCompleted by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("onboarding_completed", false))
        private set

    var serverPort by androidx.compose.runtime.mutableStateOf(prefs.getInt("server_port", 50505))
        private set

    var bulkAutoAccept by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("bulk_auto_accept", false))
        private set

    val callStatus = networkTransfer.callStatus

    fun completeOnboarding(name: String, port: Int) {
        updateDeviceName(name)
        updateServerPort(port)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        onboardingCompleted = true
    }

    fun updateServerPort(port: Int) {
        serverPort = port
        prefs.edit().putInt("server_port", port).apply()
        networkTransfer.updateServerPort(port)
    }

    fun updateBulkAutoAccept(value: Boolean) {
        bulkAutoAccept = value
        prefs.edit().putBoolean("bulk_auto_accept", value).apply()
    }

    fun initiateCall(device: DiscoveredDevice) {
        viewModelScope.launch {
            networkTransfer.initiateCall(device)
        }
    }

    fun endCall() {
        networkTransfer.endCall()
    }

    var deviceName by androidx.compose.runtime.mutableStateOf(prefs.getString("device_name", Build.MODEL) ?: Build.MODEL)
        private set

    var discoverable by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("discoverable", true))
        private set

    var autoAccept by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("auto_accept", false))
        private set

    var autoFullScreenCall by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("auto_full_screen_call", false))
        private set

    var autoAcceptDevices by androidx.compose.runtime.mutableStateOf(prefs.getStringSet("auto_accept_devices", emptySet()) ?: emptySet())
        private set

    var savePath by androidx.compose.runtime.mutableStateOf(prefs.getString("save_path", "OpenDrop") ?: "OpenDrop")
        private set

    fun toggleAutoAcceptDevice(deviceId: String) {
        val newList = autoAcceptDevices.toMutableSet()
        if (newList.contains(deviceId)) {
            newList.remove(deviceId)
        } else {
            newList.add(deviceId)
        }
        autoAcceptDevices = newList
        prefs.edit().putStringSet("auto_accept_devices", newList).apply()
    }

    var isBatteryOptimizationIgnored by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun checkBatteryOptimization() {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isBatteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback if target activity cannot be direct
                val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    var sharedUri by androidx.compose.runtime.mutableStateOf<Uri?>(null)
    var sharedFileName by androidx.compose.runtime.mutableStateOf("")
    var sharedFileSize by androidx.compose.runtime.mutableStateOf(0L)

    fun clearSharedFile() {
        sharedUri = null
        sharedFileName = ""
        sharedFileSize = 0L
    }

    fun handleSharedIntent(intent: Intent, contentResolver: android.content.ContentResolver) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
            if (uri != null) {
                setSharedFile(uri, contentResolver)
            }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (!uris.isNullOrEmpty()) {
                setSharedFile(uris[0], contentResolver)
            }
        }
    }

    private fun setSharedFile(uri: Uri, contentResolver: android.content.ContentResolver) {
        sharedUri = uri
        var name = "Shared file"
        var size = 0L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sharedFileName = name
        sharedFileSize = size
    }

    var followSystemTheme by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("follow_system_theme", true))
        private set

    var useDarkTheme by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("use_dark_theme", false))
        private set

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "discoverable" -> {
                discoverable = prefs.getBoolean("discoverable", true)
            }
            "device_name" -> {
                deviceName = prefs.getString("device_name", Build.MODEL) ?: Build.MODEL
            }
            "auto_accept" -> {
                autoAccept = prefs.getBoolean("auto_accept", false)
            }
            "auto_accept_devices" -> {
                autoAcceptDevices = prefs.getStringSet("auto_accept_devices", emptySet()) ?: emptySet()
            }
            "auto_full_screen_call" -> {
                autoFullScreenCall = prefs.getBoolean("auto_full_screen_call", false)
            }
            "save_path" -> {
                savePath = prefs.getString("save_path", "OpenDrop") ?: "OpenDrop"
            }
            "follow_system_theme" -> {
                followSystemTheme = prefs.getBoolean("follow_system_theme", true)
            }
            "use_dark_theme" -> {
                useDarkTheme = prefs.getBoolean("use_dark_theme", false)
            }
            "onboarding_completed" -> {
                onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
            }
            "server_port" -> {
                serverPort = prefs.getInt("server_port", 50505)
            }
            "bulk_auto_accept" -> {
                bulkAutoAccept = prefs.getBoolean("bulk_auto_accept", false)
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        networkTransfer.updateDeviceName(deviceName)
        if (discoverable) {
            startForegroundService()
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun startForegroundService() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, OpenDropService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundService() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, OpenDropService::class.java)
        context.stopService(intent)
    }

    fun updateDeviceName(name: String) {
        deviceName = name
        prefs.edit().putString("device_name", name).apply()
        networkTransfer.updateDeviceName(name)
    }

    fun updateDiscoverable(value: Boolean) {
        discoverable = value
        prefs.edit().putBoolean("discoverable", value).apply()
        if (value) {
            startForegroundService()
        } else {
            stopForegroundService()
        }
        try {
            android.service.quicksettings.TileService.requestListeningState(
                getApplication(),
                android.content.ComponentName(getApplication(), OpenDropTileService::class.java)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateAutoAccept(value: Boolean) {
        autoAccept = value
        prefs.edit().putBoolean("auto_accept", value).apply()
    }

    fun updateAutoFullScreenCall(value: Boolean) {
        autoFullScreenCall = value
        prefs.edit().putBoolean("auto_full_screen_call", value).apply()
    }

    fun updateSavePath(path: String) {
        savePath = path
        prefs.edit().putString("save_path", path).apply()
    }

    fun updateFollowSystemTheme(value: Boolean) {
        followSystemTheme = value
        prefs.edit().putBoolean("follow_system_theme", value).apply()
    }

    fun updateUseDarkTheme(value: Boolean) {
        useDarkTheme = value
        prefs.edit().putBoolean("use_dark_theme", value).apply()
    }

    fun sendMessage(device: DiscoveredDevice, message: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = networkTransfer.sendMessage(device, message)
            onResult(success)
        }
    }

    fun sendFile(device: DiscoveredDevice, uri: Uri, fileName: String, fileSize: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = networkTransfer.sendFile(device, uri, fileName, fileSize)
            onResult(success)
        }
    }

    val liveNote = networkTransfer.liveNote
    val syncedClipboard = networkTransfer.syncedClipboard
    val syncHistory = networkTransfer.syncHistory

    fun updateLiveNote(content: String) {
        viewModelScope.launch {
            networkTransfer.broadcastSyncData("live_note", content)
        }
    }

    fun syncClipboard(content: String) {
        viewModelScope.launch {
            networkTransfer.broadcastSyncData("clipboard", content)
        }
    }

    fun deleteSyncHistoryItem(item: SyncHistoryItem) {
        networkTransfer.deleteSyncHistoryItem(item)
    }

    fun clearSyncHistory() {
        networkTransfer.clearSyncHistory()
    }

    val isMicMuted = networkTransfer.isMicMuted
    val isSpeakerOn = networkTransfer.isSpeakerOn

    fun toggleMicMute() {
        networkTransfer.toggleMicMute()
    }

    fun toggleSpeaker() {
        networkTransfer.toggleSpeaker()
    }
}

class MainActivity : ComponentActivity() {
    private var viewModelInstance: OpenDropViewModel? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            System.gc()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        enableEdgeToEdge()

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }
        
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[OpenDropViewModel::class.java]
        viewModelInstance = viewModel
        intent?.let { 
            viewModel.handleSharedIntent(it, contentResolver) 
            if (it.action == "com.opendrop.ACTION_EXPAND_CALL") {
                viewModel.networkTransfer.expandCall()
            }
        }

        setContent {
            val darkTheme = when {
                viewModel.followSystemTheme -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> viewModel.useDarkTheme
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!viewModel.onboardingCompleted) {
                        OnboardingScreen(viewModel)
                    } else {
                        OpenDropApp(viewModel)
                    }

                    val callState by viewModel.callStatus.collectAsState()
                    val isMicMuted by viewModel.isMicMuted.collectAsState()
                    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
                    
                    LaunchedEffect(callState.state) {
                        when (callState.state) {
                            NetworkTransfer.CallState.RINGING -> this@MainActivity.volumeControlStream = android.media.AudioManager.STREAM_RING
                            NetworkTransfer.CallState.CONNECTED -> this@MainActivity.volumeControlStream = android.media.AudioManager.STREAM_VOICE_CALL
                            else -> this@MainActivity.volumeControlStream = android.media.AudioManager.USE_DEFAULT_STREAM_TYPE
                        }
                    }

                    if (callState.state != NetworkTransfer.CallState.IDLE && callState.showFullScreen) {
                        CallOverlay(
                            callStatus = callState,
                            isMicMuted = isMicMuted,
                            isSpeakerOn = isSpeakerOn,
                            onToggleMic = { viewModel.toggleMicMute() },
                            onToggleSpeaker = { viewModel.toggleSpeaker() },
                            onAnswer = { viewModelInstance?.networkTransfer?.onCallDecision?.invoke(true) },
                            onDecline = { viewModelInstance?.networkTransfer?.onCallDecision?.invoke(false) },
                            onHangup = { viewModel.endCall() }
                        )
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("discoverable", true)) {
                val intent = android.content.Intent(this, OpenDropService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelInstance?.handleSharedIntent(intent, contentResolver)
        if (intent.action == "com.opendrop.ACTION_EXPAND_CALL") {
            viewModelInstance?.networkTransfer?.expandCall()
        }
    }
}


enum class Tab { Home, Receive, History, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenDropApp(viewModel: OpenDropViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(Tab.Home) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("OpenDrop", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                },
                actions = {
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                NavigationBarItem(
                    selected = currentTab == Tab.Home,
                    onClick = { currentTab = Tab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontWeight = if (currentTab == Tab.Home) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Receive,
                    onClick = { currentTab = Tab.Receive },
                    icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Receive") },
                    label = { Text("Receive", fontWeight = if (currentTab == Tab.Receive) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = currentTab == Tab.History,
                    onClick = { currentTab = Tab.History },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontWeight = if (currentTab == Tab.History) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = currentTab == Tab.Settings,
                    onClick = { currentTab = Tab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontWeight = if (currentTab == Tab.Settings) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                Tab.Home -> HomeScreen(viewModel)
                Tab.Receive -> ReceiveScreen(viewModel)
                Tab.History -> HistoryScreen(viewModel)
                Tab.Settings -> SettingsScreen(viewModel)
            }
            
            val transferProgress by viewModel.networkTransfer.transferProgress.collectAsState()
            androidx.compose.animation.AnimatedVisibility(
                visible = transferProgress != null,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                transferProgress?.let { progress ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (progress.isSending) Icons.Default.Send else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (progress.isSending) "Sending..." else "Receiving...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        progress.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "${(progress.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { progress.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (progress.fileSize > 0) {
                                    Text(
                                        "${formatFileSize(progress.bytesTransferred)} of ${formatFileSize(progress.fileSize)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                } else {
                                    Text(
                                        "Transferring...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.networkTransfer.cancelActiveTransfer() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp).testTag("cancel_transfer_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            
            val incomingRequest by viewModel.networkTransfer.incomingRequest.collectAsState()
            incomingRequest?.let { request ->
                AlertDialog(
                    onDismissRequest = { request.onReject() },
                    title = { Text("Incoming File") },
                    text = { 
                        Text("${request.senderName} wants to send you '${request.fileName}' (${formatFileSize(request.fileSize)}). Do you want to receive it?")
                    },
                    confirmButton = {
                        Button(onClick = { request.onAccept() }) {
                            Text("Accept")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { request.onReject() }) {
                            Text("Reject")
                        }
                    }
                )
            }
        }
    }
}



@Composable
fun HomeScreen(viewModel: OpenDropViewModel) {
    val devices by viewModel.networkTransfer.devices.collectAsState()
    val distinctDevices = remember(devices) { devices.distinctBy { it.name } }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showSendSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shared File Ready Card
        androidx.compose.animation.AnimatedVisibility(
            visible = viewModel.sharedUri != null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Shared file",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Shared File Ready",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            viewModel.sharedFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeText = if (viewModel.sharedFileSize > 0) {
                            " (${formatFileSize(viewModel.sharedFileSize)})"
                        } else ""
                        Text(
                            "Tap a device below to send$sizeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearSharedFile() },
                        modifier = Modifier.testTag("clear_shared_file_btn")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear shared file",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Radar Animation Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "Radar")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 2.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RadarScale"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RadarAlpha"
            )

            // Pulse rings
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .border(2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = alpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(scale)
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = alpha * 0.5f), CircleShape)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Searching for nearby devices...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Make sure the recipient has OpenDrop open and visible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Available Devices Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Available Devices",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "${distinctDevices.size} found",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.networkTransfer.refreshDiscovery() },
                    modifier = Modifier.size(32.dp).testTag("refresh_discovery_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Devices List
        if (distinctDevices.isEmpty()) {
            Text(
                "No devices found yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                distinctDevices.forEach { device ->
                    DeviceCard(
                        device = device,
                        onCallClick = { viewModel.initiateCall(device) }
                    ) {
                        if (viewModel.sharedUri != null) {
                            val uri = viewModel.sharedUri!!
                            val name = viewModel.sharedFileName
                            val size = viewModel.sharedFileSize
                            viewModel.clearSharedFile()
                            Toast.makeText(context, "Sending $name to ${device.name}...", Toast.LENGTH_SHORT).show()
                            viewModel.sendFile(device, uri, name, size) { success ->
                                val msg = if (success) "Sent successfully" else "Failed to send file"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            selectedDevice = device
                            showSendSheet = true
                        }
                    }
                }
            }
        }
    }

    if (showSendSheet && selectedDevice != null) {
        SendDialog(
            device = selectedDevice!!,
            viewModel = viewModel,
            onDismiss = { showSendSheet = false }
        )
    }
}

@Composable
fun DeviceCard(device: DiscoveredDevice, onCallClick: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "IP: ${device.address.hostAddress}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366).copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call Device",
                    tint = Color(0xFF128C7E),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    .padding(6.dp)
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Select",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendDialog(
    device: DiscoveredDevice,
    viewModel: OpenDropViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }
    var showMessageDialog by remember { mutableStateOf(false) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            var fileName = "unknown_file"
            var fileSize = 0L
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
            
            Toast.makeText(context, "Sending $fileName...", Toast.LENGTH_SHORT).show()
            viewModel.sendFile(device, it, fileName, fileSize) { success ->
                val msg = if (success) "File sent successfully" else "Failed to send file"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Area
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(4.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                device.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "IP: ${device.address.hostAddress}:${device.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "This port is only accessible by OpenDrop and changes dynamically every time the service starts for security.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Select Content Grid
            Text(
                "Select Content",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CategoryItem(
                    icon = Icons.Default.Image,
                    label = "Photos",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    filePickerLauncher.launch(arrayOf("image/*"))
                }
                CategoryItem(
                    icon = Icons.Default.Movie,
                    label = "Videos",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    filePickerLauncher.launch(arrayOf("video/*"))
                }
                CategoryItem(
                    icon = Icons.Default.Folder,
                    label = "Files",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
                CategoryItem(
                    icon = Icons.Default.Message,
                    label = "Message",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    showMessageDialog = true
                }
            }
        }

        if (showMessageDialog) {
            AlertDialog(
                onDismissRequest = { showMessageDialog = false },
                title = { Text("Send Message") },
                text = {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type your message...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(device, messageText) { success ->
                                val status = if (success) "Message sent" else "Failed to send message"
                                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                            }
                            showMessageDialog = false
                            onDismiss()
                        }
                    }) {
                        Text("Send")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMessageDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun CategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReceiveScreen(viewModel: OpenDropViewModel) {
    val transferHistory by viewModel.networkTransfer.transferHistory.collectAsState()
    val receivedItems = remember(transferHistory) { transferHistory.filter { !it.isSent }.take(3) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Receive Files",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Device Discoverability Card
        Surface(
            color = if (viewModel.discoverable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.discoverable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (viewModel.discoverable) Icons.Default.WifiTethering else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (viewModel.discoverable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (viewModel.discoverable) "Discoverable" else "Not Discoverable",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (viewModel.discoverable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (viewModel.discoverable) {
                            val localIp = viewModel.networkTransfer.getLocalIpAddress() ?: "Checking..."
                            val localPort = viewModel.networkTransfer.getLocalPort() ?: "Dynamic"
                            "Visible as '${viewModel.deviceName}'\nIP: $localIp:$localPort"
                        } else {
                            "Tap below to enable discoverability to receive files"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (viewModel.discoverable) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.discoverable) {
            // Animated radar area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "Radar")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 2.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "RadarScale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "RadarAlpha"
                )

                // Pulse rings
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(scale)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f), CircleShape)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        } else {
            // Disabled view / Enable quick action
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Receiver is Offline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enable discoverability so senders can locate your device on the local network.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.updateDiscoverable(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("enable_discoverable_btn")
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Turn On Discoverable")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent received section
        Text(
            "Recent Received",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (receivedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No files received yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                receivedItems.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.type == "Message") Icons.Default.Message else Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeText = if (item.type == "File" && item.fileSize > 0) {
                                    " • ${formatFileSize(item.fileSize)}"
                                } else ""
                                Text(
                                    "From: ${item.deviceName}$sizeText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: OpenDropViewModel) {
    val transferHistory by viewModel.networkTransfer.transferHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            "Transfer History",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Review your recent shares and messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        if (transferHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No items found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(transferHistory) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.type == "Message") Icons.Default.Message else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.content,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeText = if (item.type == "File" && item.fileSize > 0) {
                                    " • ${formatFileSize(item.fileSize)}"
                                } else ""
                                Text(
                                    (if (item.isSent) "To: ${item.deviceName}" else "From: ${item.deviceName}") + sizeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (item.type == "File") {
                                    val context = LocalContext.current
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val file = if (item.filePath != null) File(item.filePath) else {
                                                    val prefs = context.getSharedPreferences("opendrop_prefs", android.content.Context.MODE_PRIVATE)
                                                    val saveFolder = prefs.getString("save_path", "OpenDrop") ?: "OpenDrop"
                                                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$saveFolder/${item.content}")
                                                }
                                                if (file.exists()) {
                                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        file
                                                    )
                                                    val mimeType = context.contentResolver.getType(fileUri) ?: "*/*"
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(fileUri, mimeType)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } else {
                                                    Toast.makeText(context, "File does not exist or was deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp).testTag("open_file_btn_${item.timestamp}")
                                    ) {
                                        Text("Open", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Success",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.networkTransfer.deleteHistoryItem(item)
                                    },
                                    modifier = Modifier.size(36.dp).testTag("delete_item_btn_${item.timestamp}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete from history",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: OpenDropViewModel) {
    LaunchedEffect(Unit) {
        viewModel.checkBatteryOptimization()
    }
    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(viewModel.deviceName) }
    var showPathDialog by remember { mutableStateOf(false) }
    var tempPath by remember { mutableStateOf(viewModel.savePath) }
    var showPortDialog by remember { mutableStateOf(false) }
    var tempPort by remember { mutableStateOf(viewModel.serverPort.toString()) }
    var portError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Text(
            "PROFILE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Smartphone,
                    iconColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "Device Name",
                    subtitle = viewModel.deviceName,
                    trailingIcon = Icons.Default.Edit,
                    onClick = {
                        tempName = viewModel.deviceName
                        showNameDialog = true
                    }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SettingsItem(
                    icon = Icons.Default.Radar,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Discoverable",
                    subtitle = "Allow others to find your device",
                    isSwitch = true,
                    switchState = viewModel.discoverable,
                    onSwitchChange = { viewModel.updateDiscoverable(it) }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SettingsItem(
                    icon = Icons.Default.Add,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Static Port",
                    subtitle = "${viewModel.serverPort} (P2P listening port)",
                    trailingIcon = Icons.Default.Edit,
                    onClick = {
                        tempPort = viewModel.serverPort.toString()
                        portError = ""
                        showPortDialog = true
                    }
                )
            }
        }

        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Device Name") },
                text = {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateDeviceName(tempName)
                        showNameDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "CALLING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Call,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Auto Full Screen Call (Without Answer)",
                    subtitle = "Automatically show call screen in full screen",
                    isSwitch = true,
                    switchState = viewModel.autoFullScreenCall,
                    onSwitchChange = { viewModel.updateAutoFullScreenCall(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "STORAGE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Save Path",
                    subtitle = "/Internal Storage/Downloads/${viewModel.savePath}",
                    trailingIcon = Icons.Default.ChevronRight,
                    onClick = {
                        tempPath = viewModel.savePath
                        showPathDialog = true
                    }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Bulk Session Auto-Acceptance",
                    subtitle = "Accept multiple files with 1-click (valid for 2 mins)",
                    isSwitch = true,
                    switchState = viewModel.bulkAutoAccept,
                    onSwitchChange = { viewModel.updateBulkAutoAccept(it) }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SettingsItem(
                    icon = Icons.Default.Schedule,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Auto-Accept",
                    subtitle = "From known devices only",
                    isSwitch = true,
                    switchState = viewModel.autoAccept,
                    onSwitchChange = { viewModel.updateAutoAccept(it) }
                )
                if (viewModel.autoAccept) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Column(modifier = Modifier.padding(16.dp)) {
                        var autoAcceptDevicesExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trusted Devices", style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { autoAcceptDevicesExpanded = !autoAcceptDevicesExpanded }) {
                                Icon(
                                    imageVector = if (autoAcceptDevicesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (autoAcceptDevicesExpanded) "Collapse" else "Expand"
                                )
                            }
                        }
                        
                        if (autoAcceptDevicesExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val history = viewModel.networkTransfer.transferHistory.collectAsState().value
                            val distinctDevices = history.map { it.deviceName }.distinct()
                            
                            if (distinctDevices.isEmpty()) {
                                Text("No trusted devices configured yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                distinctDevices.forEach { deviceName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleAutoAcceptDevice(deviceName) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = viewModel.autoAcceptDevices.contains(deviceName),
                                            onCheckedChange = { viewModel.toggleAutoAcceptDevice(deviceName) }
                                        )
                                        Text(deviceName, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPathDialog) {
            AlertDialog(
                onDismissRequest = { showPathDialog = false },
                title = { Text("Save Path Folder") },
                text = {
                    OutlinedTextField(
                        value = tempPath,
                        onValueChange = { tempPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Folder Name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateSavePath(tempPath)
                        showPathDialog = true
                        showPathDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPathDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "THEME",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "Follow System Theme",
                    subtitle = "Automatically switch between light and dark",
                    isSwitch = true,
                    switchState = viewModel.followSystemTheme,
                    onSwitchChange = { viewModel.updateFollowSystemTheme(it) }
                )
                if (!viewModel.followSystemTheme) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsItem(
                        icon = Icons.Default.Visibility,
                        iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        title = "Dark Theme",
                        subtitle = "Enable dark mode manually",
                        isSwitch = true,
                        switchState = viewModel.useDarkTheme,
                        onSwitchChange = { viewModel.updateUseDarkTheme(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "SECURITY",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    iconColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "Local Encryption",
                    subtitle = "All file transfers are secured using peer-to-peer end-to-end encryption. Keys are generated locally and never leave your device."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "BATTERY OPTIMIZATION",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                val context = LocalContext.current
                SettingsItem(
                    icon = Icons.Default.WifiTethering,
                    iconColor = if (viewModel.isBatteryOptimizationIgnored) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = if (viewModel.isBatteryOptimizationIgnored) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    title = "Battery Optimization",
                    subtitle = if (viewModel.isBatteryOptimizationIgnored) "Disabled (Transfer performance is fully optimized)" else "Enabled (Tap to disable to prevent background transfer issues)",
                    trailingIcon = if (viewModel.isBatteryOptimizationIgnored) null else Icons.Default.OpenInNew,
                    onClick = if (viewModel.isBatteryOptimizationIgnored) null else {
                        { viewModel.requestIgnoreBatteryOptimization(context) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "DEVELOPER",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                val context = LocalContext.current
                SettingsItem(
                    icon = Icons.Default.Person,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "GitHub",
                    subtitle = "https://github.com/Elvandito",
                    trailingIcon = Icons.Default.OpenInNew,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Elvandito"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open GitHub link", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Info,
                    iconColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = "About OpenDrop",
                    subtitle = "v1.0"
                )
            }
        }

        if (showPortDialog) {
            AlertDialog(
                onDismissRequest = { showPortDialog = false },
                title = { Text("Server Static Port") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tempPort,
                            onValueChange = { tempPort = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Port (1024 - 65535)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        if (portError.isNotEmpty()) {
                            Text(portError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val port = tempPort.toIntOrNull()
                        if (port == null || port < 1024 || port > 65535) {
                            portError = "Invalid port range!"
                        } else {
                            viewModel.updateServerPort(port)
                            showPortDialog = false
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPortDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isSwitch: Boolean = false,
    switchState: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null || (isSwitch && onSwitchChange != null)) {
                if (onClick != null) {
                    onClick.invoke()
                } else if (isSwitch && onSwitchChange != null) {
                    onSwitchChange(!switchState)
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
        if (isSwitch) {
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = switchState,
                onCheckedChange = onSwitchChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: OpenDropViewModel) {
    var devName by remember { mutableStateOf(Build.MODEL) }
    var portStr by remember { mutableStateOf("50505") }
    var errorText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Welcome to OpenDrop",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Setup your local file sharing preferences to begin.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = devName,
                onValueChange = { devName = it },
                label = { Text("Device Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = portStr,
                onValueChange = { portStr = it },
                label = { Text("Port (Static)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorText.isNotEmpty()) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val port = portStr.toIntOrNull()
                    if (port == null || port < 1024 || port > 65535) {
                        errorText = "Please enter a valid port between 1024 and 65535"
                    } else if (devName.isBlank()) {
                        errorText = "Device name cannot be blank"
                    } else {
                        viewModel.completeOnboarding(devName, port)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Sharing")
            }
        }
    }
}

@Composable
fun CallOverlay(
    callStatus: NetworkTransfer.CallStatus,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF075E54)) // WhatsApp green classic background
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Voice Call",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (callStatus.isIncoming) "Incoming local voice call..." else "Outgoing local voice call...",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = callStatus.peerName.ifEmpty { "Unknown Peer" },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (callStatus.state) {
                        NetworkTransfer.CallState.RINGING -> "Ringing..."
                        NetworkTransfer.CallState.CALLING -> "Calling..."
                        NetworkTransfer.CallState.CONNECTED -> "Connected"
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Call controls (Mic mute & Speakerphone toggles) shown when connected
            if (callStatus.state == NetworkTransfer.CallState.CONNECTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onToggleMic,
                            modifier = Modifier
                                .size(56.dp)
                                .background(if (isMicMuted) Color.Red else Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isMicMuted) "Muted" else "Mute Mic",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(56.dp)
                                .background(if (isSpeakerOn) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (isSpeakerOn) "Switch to Earpiece" else "Switch to Speaker",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSpeakerOn) "Speaker On" else "Earpiece On",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (callStatus.state == NetworkTransfer.CallState.RINGING && callStatus.isIncoming) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = onAnswer,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Green, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onHangup,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hangup",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: OpenDropViewModel) {
    val devices by viewModel.networkTransfer.devices.collectAsState()
    val distinctDevices = remember(devices) { devices.distinctBy { it.name } }

    val liveNoteRemote by viewModel.liveNote.collectAsState()
    val syncedClipboard by viewModel.syncedClipboard.collectAsState()
    val syncHistory by viewModel.syncHistory.collectAsState()

    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Smooth collaborative notepad typing state
    var localNoteText by remember { mutableStateOf(liveNoteRemote) }

    // Keep local note text in sync with remote updates
    LaunchedEffect(liveNoteRemote) {
        if (liveNoteRemote != localNoteText) {
            localNoteText = liveNoteRemote
        }
    }

    // Debounce typing broadcasts by 400ms
    LaunchedEffect(localNoteText) {
        if (localNoteText != liveNoteRemote) {
            kotlinx.coroutines.delay(400)
            viewModel.updateLiveNote(localNoteText)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            "Live Sync Board",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Synchronize text notes and clipboard in real-time with all nearby active devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Connected devices badge bar
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (distinctDevices.isNotEmpty()) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Gray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (distinctDevices.isNotEmpty()) {
                        "Connected to ${distinctDevices.size} nearby device(s) for real-time sync"
                    } else {
                        "Searching for nearby active devices..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Live Notepad Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Collaborative Notepad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (localNoteText.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                localNoteText = ""
                                viewModel.updateLiveNote("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear notepad",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = localNoteText,
                    onValueChange = { localNoteText = it },
                    placeholder = { Text("Start typing... updates will sync in real-time with nearby devices.") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (localNoteText.isNotEmpty()) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(localNoteText))
                                Toast.makeText(context, "Copied notepad content to system clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = localNoteText.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Note", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Shared Clipboard Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Shared Clipboard Content",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (syncedClipboard.isNotEmpty()) syncedClipboard else "No shared clipboard data yet. Share your clipboard or wait for a nearby device to broadcast.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (syncedClipboard.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = if (syncedClipboard.isNotEmpty()) androidx.compose.ui.text.font.FontStyle.Normal else androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrEmpty()) {
                                viewModel.syncClipboard(clip)
                                Toast.makeText(context, "Shared clipboard successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Your system clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share My Clip", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            if (syncedClipboard.isNotEmpty()) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(syncedClipboard))
                                Toast.makeText(context, "Copied shared clipboard to device", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = syncedClipboard.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Shared Clip", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Sync History Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Sync Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (syncHistory.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearSyncHistory() }) {
                    Text("Clear All", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (syncHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No sync logs available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                syncHistory.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "From: ${item.senderName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val timeStr = remember(item.timestamp) {
                                        val date = java.util.Date(item.timestamp)
                                        val format = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                        format.format(date)
                                    }
                                    Text(
                                        "Received at $timeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.content))
                                            Toast.makeText(context, "Copied log to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Copy text",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteSyncHistoryItem(item) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete from log",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
