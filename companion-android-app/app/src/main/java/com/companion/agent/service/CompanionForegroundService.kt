package com.companion.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.companion.agent.MainActivity
import com.companion.agent.data.network.GeminiLiveListener
import com.companion.agent.data.network.GeminiLiveService
import com.companion.agent.utils.DeviceToolExecutor
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class CompanionForegroundService : Service(), GeminiLiveListener {
    private val TAG = "CompanionForegroundService"
    private val NOTIFICATION_CHANNEL_ID = "companion_foreground_monitoring"
    private val NOTIFICATION_ID = 4096

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Live parameters
    private var geminiService: GeminiLiveService? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Audio Capture Properties
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    // Audio Playback Properties
    private var audioTrack: AudioTrack? = null
    private val pcmPlaybackBuffer = ByteArrayOutputStream()

    // Service Listener to push updates to the UI ViewModels
    interface ServiceStateListener {
        fun onServiceConnected()
        fun onServiceDisconnected()
        fun onAudioReceived(audio: ByteArray)
        fun onTranscriptUpdate(text: String)
        fun onInterrupted()
        fun onToolCall(toolName: String, params: Map<String, Any>)
    }
    private var stateListener: ServiceStateListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): CompanionForegroundService = this@CompanionForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Foreground Service created.")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Waking up your Companion Agent..."))
        acquireWakeLock()
        initAudioTrack()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apiKey = intent?.getStringExtra("gemini_api_key") ?: ""
        val workspace = intent?.getStringExtra("workspace_name") ?: "hermes_default"

        if (apiKey.isNotEmpty()) {
            val toolExecutor = DeviceToolExecutor(applicationContext)
            geminiService = GeminiLiveService(apiKey, this, toolExecutor)
            geminiService?.connect()
        } else {
            Log.w(TAG, "No API key found in startup parameters. Service started in Demo/Simulation Mode.")
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setStateListener(listener: ServiceStateListener?) {
        this.stateListener = listener
    }

    /**
     * Toggles recording state on or off
     */
    fun toggleRecording(enable: Boolean) {
        if (enable) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    fun isRecordingActive(): Boolean = isRecording

    /**
     * Gathers raw microphone signals and streams base64 chunk packets to the Live WebSocket.
     */
    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        Log.d(TAG, "Starting Audio recording stream...")

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()

            recordingJob = serviceScope.launch {
                val buffer = ByteArray(bufferSize)
                while (isRecording && activeContext()) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        val chunk = buffer.copyOfRange(0, readBytes)
                        geminiService?.sendAudioChunk(chunk)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing RECORD_AUDIO permission to start recording", e)
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord stream", e)
            isRecording = false
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Recording stream released successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down recording resources", e)
        }
    }

    /**
     * Initializes Android's AudioTrack for streaming PCM 16-bit 16kHz playback
     */
    private fun initAudioTrack() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    /**
     * Streams direct text message to the live connection
     */
    fun sendTextMessage(text: String) {
        geminiService?.sendTextMessage(text)
    }

    // --- Gemini Live Callbacks ---

    override fun onConnected() {
        Log.d(TAG, "Gemini session connected!")
        updateNotification("Connected to your Companion Agent")
        stateListener?.onServiceConnected()
    }

    override fun onDisconnected(reason: String) {
        Log.d(TAG, "Gemini session disconnected: $reason")
        updateNotification("Companion Session inactive.")
        stateListener?.onServiceDisconnected()
    }

    override fun onAudioReceived(audioBytes: ByteArray) {
        // Enqueue PCM frame into the playback stream
        try {
            audioTrack?.write(audioBytes, 0, audioBytes.size)
            stateListener?.onAudioReceived(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play back pcm frame", e)
        }
    }

    override fun onTextReceived(text: String) {
        Log.d(TAG, "Received Transcript fragment: $text")
        stateListener?.onTranscriptUpdate(text)
    }

    override fun onInterrupted() {
        Log.d(TAG, "Interrupted playback due to user barge-in.")
        audioTrack?.flush() // Flush queued audio immediately
        stateListener?.onInterrupted()
    }

    override fun onToolTriggered(toolName: String, arguments: Map<String, Any>) {
        stateListener?.onToolCall(toolName, arguments)
    }

    // --- System Lifecycle Management ---

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HermesCompanion::AgentWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L) // Limit to 24 hours
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Companion Background Monitor"
            val descriptionText = "Ensures continuous ambient agent processing and audio mapping is alive."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Companion Agent Alive")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
    }

    private fun activeContext(): Boolean {
        return isRecording
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground Service shutting down.")
        stopRecording()
        geminiService?.disconnect()
        serviceScope.cancel()
        releaseWakeLock()
        
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up playback track", e)
        }
    }
}
