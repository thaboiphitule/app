package com.companion.agent.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.companion.agent.data.model.ConclusionResponse
import com.companion.agent.data.network.HonchoClient
import com.companion.agent.service.CompanionForegroundService
import com.companion.agent.utils.DeviceToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Chat Bubble representation
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: String, // "user", "companion", or "system"
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

// Active local alarms/reminders scheduled by Agent
data class ScheduledReminder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fireTime: String
)

// Main UI state
data class UiState(
    val isDemoMode: Boolean = true,
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val companionName: String = "Hermes v1",
    val companionBackstory: String = "An empathetic companion and proactive agent inspired by Hermes and Kindroid.",
    val companionTone: String = "Aoede (Warm and Expressive)",
    val geminiApiKey: String = "",
    val honchoApiKey: String = "",
    val honchoWorkspace: String = "hermes_default",
    val messages: List<ChatMessage> = listOf(
        ChatMessage(text = "Hello! I am your companion and local agent. You can configure your Gemini and Honcho keys in settings or chat directly with me right here in demo mode!", sender = "companion")
    ),
    val habits: List<String> = listOf(
        "Learned: User prefers working on deep software tasks during the evening.",
        "Learned: User appreciates structured notifications for agenda scheduling."
    ),
    val activeReminders: List<ScheduledReminder> = emptyList(),
    val liveTranscript: String = "",
    val recordingVolume: Float = 0.0f
)

class CompanionViewModel(application: Application) : AndroidViewModel(application), CompanionForegroundService.ServiceStateListener {
    private val TAG = "CompanionViewModel"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var honchoClient: HonchoClient? = null
    private var boundService: CompanionForegroundService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CompanionForegroundService.LocalBinder
            boundService = binder.getService()
            boundService?.setStateListener(this@CompanionViewModel)
            isBound = true
            
            // Sync status with UI
            _uiState.update { it.copy(
                isConnected = true,
                isRecording = boundService?.isRecordingActive() ?: false
            )}
            Log.d(TAG, "Successfully bound UI to Foreground Service.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService?.setStateListener(null)
            boundService = null
            isBound = false
            _uiState.update { it.copy(isConnected = false, isRecording = false) }
        }
    }

    init {
        // Attempt to bind to service initially if already running
        bindToService()
        
        // Start simulation updater for demo waveform/habits
        startSimulationLoop()
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), CompanionForegroundService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromService() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
            boundService = null
        }
    }

    fun updateSettings(
        apiKey: String,
        honchoKey: String,
        workspace: String,
        name: String,
        backstory: String,
        tone: String,
        demoMode: Boolean
    ) {
        _uiState.update {
            it.copy(
                geminiApiKey = apiKey,
                honchoApiKey = honchoKey,
                honchoWorkspace = workspace,
                companionName = name,
                companionBackstory = backstory,
                companionTone = tone,
                isDemoMode = demoMode
            )
        }

        if (!demoMode && honchoKey.isNotEmpty() && workspace.isNotEmpty()) {
            honchoClient = HonchoClient(honchoKey, workspace)
            viewModelScope.launch(Dispatchers.IO) {
                val config = honchoClient?.initializeWorkspaceSession()
                if (config != null) {
                    val conclusions = honchoClient?.getHabitsAndConclusions(config.second)
                    if (conclusions != null) {
                        _uiState.update { state ->
                            state.copy(
                                habits = conclusions.map { "Learned: ${it.content}" }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Toggles live mic listening state
     */
    fun toggleLiveSession() {
        val state = _uiState.value
        if (state.isDemoMode) {
            val nextRecording = !state.isRecording
            _uiState.update { it.copy(isRecording = nextRecording) }
            if (nextRecording) {
                simulateLiveCompanionResponse("A companion's heart is always listening. Tell me anything.")
            }
            return
        }

        // Live API Production path
        if (!state.isConnected) {
            startServiceAndConnect()
        }

        viewModelScope.launch {
            delay(100) // Give service time to bind
            val targetRecordState = !state.isRecording
            boundService?.toggleRecording(targetRecordState)
            _uiState.update { it.copy(isRecording = targetRecordState) }
        }
    }

    private fun startServiceAndConnect() {
        val context = getApplication<Application>()
        val key = _uiState.value.geminiApiKey
        val ws = _uiState.value.honchoWorkspace
        val intent = Intent(context, CompanionForegroundService::class.java).apply {
            putExtra("gemini_api_key", key)
            putExtra("workspace_name", ws)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        bindToService()
    }

    fun stopServiceAndDisconnect() {
        unbindFromService()
        val context = getApplication<Application>()
        context.stopService(Intent(context, CompanionForegroundService::class.java))
        _uiState.update { it.copy(isConnected = false, isRecording = false) }
    }

    /**
     * Text message entrypoint
     */
    fun sendTextMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(text = text, sender = "user")
        _uiState.update { it.copy(messages = it.messages + userMsg) }

        if (_uiState.value.isDemoMode) {
            viewModelScope.launch {
                delay(1000)
                // Process mock trigger commands
                if (text.lowercase().contains("notify")) {
                    triggerMockNotification("Hermes Companion", "This is an agentic notification mock!")
                } else if (text.lowercase().contains("reminder") || text.lowercase().contains("schedule")) {
                    addMockReminder("Focus Session", 10)
                } else {
                    simulateLiveCompanionResponse("I received: '$text'. In active mode, I'll log this in Honcho and plan next habits!")
                }
            }
            return
        }

        // Production path
        if (isBound) {
            boundService?.sendTextMessage(text)
        }

        // Sync with Honcho conversational thread asynchronously
        val client = honchoClient
        if (client != null) {
            viewModelScope.launch(Dispatchers.IO) {
                client.logMessage("session_companion_live", text, "user_human")
                // Query dialectic or check updates
                val dialecticResponse = client.queryDialectic("peer_hermes_companion", text, "session_companion_live")
                if (dialecticResponse != null) {
                    viewModelScope.launch(Dispatchers.Main) {
                        val reply = ChatMessage(text = dialecticResponse, sender = "companion")
                        _uiState.update { it.copy(messages = it.messages + reply) }
                    }
                }
            }
        }
    }

    private fun simulateLiveCompanionResponse(text: String) {
        val reply = ChatMessage(text = text, sender = "companion")
        _uiState.update { it.copy(messages = it.messages + reply) }
    }

    private fun triggerMockNotification(title: String, msg: String) {
        val toolExecutor = DeviceToolExecutor(getApplication())
        toolExecutor.executeTool("trigger_system_notification", mapOf("title" to title, "message" to msg))
        
        val systemMsg = ChatMessage(text = "System: Simulated notification posted. Check your status bar!", sender = "system")
        _uiState.update { it.copy(messages = it.messages + systemMsg) }
    }

    private fun addMockReminder(taskName: String, minutes: Int) {
        val triggerHour = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis() + minutes * 60 * 1000))
        val reminder = ScheduledReminder(name = taskName, fireTime = triggerHour)
        _uiState.update { state ->
            state.copy(
                activeReminders = state.activeReminders + reminder,
                messages = state.messages + ChatMessage(text = "System: scheduled reminder for '$taskName' at $triggerHour", sender = "system")
            )
        }
    }

    private fun startSimulationLoop() {
        viewModelScope.launch {
            while (true) {
                delay(120)
                if (_uiState.value.isRecording) {
                    // Update mock sound wave visual value
                    _uiState.update {
                        it.copy(recordingVolume = (0.2f + Math.random().toFloat() * 0.8f))
                    }
                } else {
                    _uiState.update { it.copy(recordingVolume = 0.0f) }
                }
            }
        }
    }

    // --- Service callbacks mapped into UI streams ---

    override fun onServiceConnected() {
        _uiState.update { it.copy(isConnected = true) }
    }

    override fun onServiceDisconnected() {
        _uiState.update { it.copy(isConnected = false, isRecording = false) }
    }

    override fun onAudioReceived(audio: ByteArray) {
        // Feed live waveform meter for incoming companion voice
        _uiState.update {
            it.copy(recordingVolume = (0.4f + Math.random().toFloat() * 0.6f))
        }
    }

    override fun onTranscriptUpdate(text: String) {
        _uiState.update { state ->
            val updatedTranscript = state.liveTranscript + text
            
            // Check if turn completes
            val wordThreshold = updatedTranscript.length > 40
            state.copy(
                liveTranscript = if (wordThreshold) "" else updatedTranscript,
                messages = if (wordThreshold) {
                    state.messages + ChatMessage(text = updatedTranscript, sender = "companion")
                } else {
                    state.messages
                }
            )
        }
    }

    override fun onInterrupted() {
        _uiState.update { state ->
            state.copy(
                messages = state.messages + ChatMessage(text = "[Interrupted Companion Speech]", sender = "system")
            )
        }
    }

    override fun onToolCall(toolName: String, params: Map<String, Any>) {
        viewModelScope.launch(Dispatchers.Main) {
            val formatted = "Agent Tool Triggered: $toolName (Parameters: $params)"
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatMessage(text = formatted, sender = "system")
                )
            }
            if (toolName == "schedule_alarm_reminder") {
                val delay = (params["delay_minutes"] as? Number)?.toInt() ?: 1
                val task = params["task_name"]?.toString() ?: "Reminder"
                addMockReminder(task, delay)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }
}
