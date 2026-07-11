package com.companion.agent.data.network

import android.util.Base64
import android.util.Log
import com.companion.agent.utils.DeviceToolExecutor
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

interface GeminiLiveListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onAudioReceived(audioBytes: ByteArray)
    fun onTextReceived(text: String)
    fun onInterrupted()
    fun onToolTriggered(toolName: String, arguments: Map<String, Any>)
}

class GeminiLiveService(
    private var apiKey: String,
    private val listener: GeminiLiveListener,
    private val toolExecutor: DeviceToolExecutor
) {
    private val TAG = "GeminiLiveService"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSockets
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var isConnected = false

    fun updateApiKey(newKey: String) {
        this.apiKey = newKey
    }

    /**
     * Connects to the Gemini Live WebSockets endpoint
     */
    fun connect() {
        if (isConnected) return

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        Log.d(TAG, "Connecting to Gemini Live API: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "WebSocket Opened successfully!")
                sendSetupConfig()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason (code: $code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "WebSocket closed: $reason")
                listener.onDisconnected(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "WebSocket connection failed: ${t.message}", t)
                listener.onDisconnected(t.message ?: "Connection Failure")
            }
        })
    }

    /**
     * Disconnects the live session
     */
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
    }

    /**
     * Sends the initial BidiGenerateContentSetup structure
     */
    private fun sendSetupConfig() {
        val setupMsg = mapOf(
            "setup" to mapOf(
                "model" to "models/gemini-2.0-flash-exp",
                "generationConfig" to mapOf(
                    "responseModalities" to listOf("AUDIO"),
                    "speechConfig" to mapOf(
                        "voiceConfig" to mapOf(
                            "prebuiltVoiceConfig" to mapOf(
                                "voiceName" to "Aoede" // Warm, expressive companion voice
                            )
                        )
                    )
                ),
                // Declaring Device Action tools available for Gemini agentic execution ("Hermes" capabilities)
                "tools" to listOf(
                    mapOf(
                        "functionDeclarations" to listOf(
                            mapOf(
                                "name" to "trigger_system_notification",
                                "description" to "Triggers a high-priority push notification with companion reminders, notes, or tips for the user on their device.",
                                "parameters" to mapOf(
                                    "type" to "OBJECT",
                                    "properties" to mapOf(
                                        "title" to mapOf("type" to "STRING", "description" to "The bold heading of the notification"),
                                        "message" to mapOf("type" to "STRING", "description" to "The main description text of the alert")
                                    ),
                                    "required" to listOf("title", "message")
                                )
                            ),
                            mapOf(
                                "name" to "fetch_device_status",
                                "description" to "Retrieves real-time system and environmental parameters of the user's phone, including time, network connectivity, and location data.",
                                "parameters" to mapOf(
                                    "type" to "OBJECT",
                                    "properties" to emptyMap<String, Any>()
                                )
                            ),
                            mapOf(
                                "name" to "schedule_alarm_reminder",
                                "description" to "Schedules an alarm, habit tracker task, or calendar reminder to prompt the user later.",
                                "parameters" to mapOf(
                                    "type" to "OBJECT",
                                    "properties" to mapOf(
                                        "task_name" to mapOf("type" to "STRING", "description" to "The description of the event or prompt"),
                                        "delay_minutes" to mapOf("type" to "INTEGER", "description" to "Minutes from now when this event should fire")
                                    ),
                                    "required" to listOf("task_name", "delay_minutes")
                                )
                            )
                        )
                    )
                )
            )
        )
        val json = gson.toJson(setupMsg)
        Log.d(TAG, "Sending setup frame: $json")
        webSocket?.send(json)
    }

    /**
     * Streams an audio chunk of raw 16kHz PCM audio bytes
     */
    fun sendAudioChunk(pcmData: ByteArray) {
        if (!isConnected) return
        val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val audioMsg = mapOf(
            "realtimeInput" to mapOf(
                "audio" to mapOf(
                    "data" to base64Data,
                    "mimeType" to "audio/pcm;rate=16000"
                )
            )
        )
        webSocket?.send(gson.toJson(audioMsg))
    }

    /**
     * Sends a direct text message turn to Gemini Live
     */
    fun sendTextMessage(text: String) {
        if (!isConnected) return
        val textMsg = mapOf(
            "clientContent" to mapOf(
                "turns" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(
                            mapOf("text" to text)
                        )
                    )
                ),
                "turnComplete" to true
            )
        )
        webSocket?.send(gson.toJson(textMsg))
    }

    /**
     * Parses messages incoming from the Gemini Live Server API
     */
    private fun parseServerMessage(jsonString: String) {
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java) ?: return

            // 1. Check for serverContent / generation model data
            if (root.has("serverContent")) {
                val serverContent = root.getAsJsonObject("serverContent")

                // Handle barge-in interruption signals
                if (serverContent.has("interrupted") && serverContent.get("interrupted").asBoolean) {
                    Log.d(TAG, "User interrupted companion's speech (barge-in event).")
                    listener.onInterrupted()
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getAsJsonObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getAsJsonArray("parts")
                        for (element in parts) {
                            val part = element.asJsonObject
                            
                            // Extract audio output bytes
                            if (part.has("inlineData")) {
                                val inlineData = part.getAsJsonObject("inlineData")
                                if (inlineData.has("data")) {
                                    val base64Audio = inlineData.get("data").asString
                                    val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    listener.onAudioReceived(audioBytes)
                                }
                            }

                            // Extract text transcript updates
                            if (part.has("text")) {
                                val text = part.get("text").asString
                                listener.onTextReceived(text)
                            }
                        }
                    }
                }
            }

            // 2. Handle Agent Tool Calls / function calls
            if (root.has("toolCall")) {
                val toolCall = root.getAsJsonObject("toolCall")
                if (toolCall.has("functionCalls")) {
                    val functionCalls = toolCall.getAsJsonArray("functionCalls")
                    for (element in functionCalls) {
                        val call = element.asJsonObject
                        val id = call.get("id")?.asString ?: ""
                        val name = call.get("name")?.asString ?: ""
                        
                        val argsMap = mutableMapOf<String, Any>()
                        if (call.has("args")) {
                            val args = call.getAsJsonObject("args")
                            for (key in args.keySet()) {
                                val value = args.get(key)
                                if (value.isJsonPrimitive) {
                                    val primitive = value.asJsonPrimitive
                                    if (primitive.isBoolean) argsMap[key] = primitive.asBoolean
                                    else if (primitive.isNumber) argsMap[key] = primitive.asNumber
                                    else argsMap[key] = primitive.asString
                                }
                            }
                        }

                        Log.d(TAG, "Model invoked tool call: $name with parameters: $argsMap")
                        listener.onToolTriggered(name, argsMap)

                        // Execute the local Android function and return tool results to maintain the WebSocket loop
                        val executionResult = toolExecutor.executeTool(name, argsMap)
                        sendToolResponse(id, name, executionResult)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding server message json", e)
        }
    }

    /**
     * Sends execution results back to Gemini Live API to continue the session
     */
    private fun sendToolResponse(callId: String, functionName: String, output: Map<String, Any>) {
        if (!isConnected) return
        val responseMsg = mapOf(
            "clientContent" to mapOf(
                "turns" to listOf(
                    mapOf(
                        "role" to "tool",
                        "parts" to listOf(
                            mapOf(
                                "functionResponse" to mapOf(
                                    "name" to functionName,
                                    "id" to callId,
                                    "response" to mapOf("output" to output)
                                )
                            )
                        )
                    )
                ),
                "turnComplete" to true
            )
        )
        val json = gson.toJson(responseMsg)
        Log.d(TAG, "Sending tool response frame: $json")
        webSocket?.send(json)
    }
}
