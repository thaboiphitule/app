package com.companion.agent.data.network

import android.util.Log
import com.companion.agent.data.model.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class HonchoClient(
    private var apiKey: String,
    private var workspaceName: String,
    baseUrl: String = "https://api.honcho.dev/"
) {
    private val TAG = "HonchoClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val apiService: HonchoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HonchoApiService::class.java)
    }

    private fun getAuthHeader(): String = "Bearer $apiKey"

    /**
     * Updates client configuration settings
     */
    fun updateConfig(newApiKey: String, newWorkspace: String) {
        this.apiKey = newApiKey
        this.workspaceName = newWorkspace
    }

    /**
     * Initializes workspace, user peer, agent peer, and active session
     */
    suspend fun initializeWorkspaceSession(
        userPeerName: String = "user_human",
        companionPeerName: String = "hermes_companion"
    ): Triple<String, String, String>? {
        try {
            Log.d(TAG, "Initializing Honcho workspace '$workspaceName'")
            
            // 1. Create Workspace if needed (normally managed via developer console, but can register via API)
            val workspaceRes = apiService.createWorkspace(getAuthHeader(), WorkspaceRequest(workspaceName))
            if (workspaceRes.isSuccessful) {
                Log.d(TAG, "Workspace initialized successfully: ${workspaceRes.body()?.id}")
            }

            // 2. Create/Resolve User Peer
            val userPeerRes = apiService.createPeer(getAuthHeader(), workspaceName, PeerRequest(userPeerName))
            val userPeerId = if (userPeerRes.isSuccessful) {
                userPeerRes.body()?.id ?: "peer_$userPeerName"
            } else {
                Log.w(TAG, "Peer User request failed, using fallback ID.")
                "peer_$userPeerName"
            }

            // 3. Create/Resolve Companion Peer
            val compPeerRes = apiService.createPeer(getAuthHeader(), workspaceName, PeerRequest(companionPeerName))
            val companionPeerId = if (compPeerRes.isSuccessful) {
                compPeerRes.body()?.id ?: "peer_$companionPeerName"
            } else {
                Log.w(TAG, "Peer Companion request failed, using fallback ID.")
                "peer_$companionPeerName"
            }

            // 4. Create conversational session for these peers
            val sessionRes = apiService.createSession(
                getAuthHeader(),
                workspaceName,
                SessionRequest("session_live_agent", listOf(userPeerId, companionPeerId))
            )
            val sessionId = if (sessionRes.isSuccessful) {
                sessionRes.body()?.id ?: "session_companion_live"
            } else {
                Log.w(TAG, "Session request failed, using fallback ID.")
                "session_companion_live"
            }

            return Triple(userPeerId, companionPeerId, sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Honcho workspace session structure", e)
            return null
        }
    }

    /**
     * Appends a message to the active session history
     */
    suspend fun logMessage(sessionId: String, content: String, senderId: String): MessageResponse? {
        try {
            val response = apiService.addMessage(
                getAuthHeader(),
                workspaceName,
                sessionId,
                MessageRequest(content, senderId)
            )
            if (response.isSuccessful) {
                return response.body()
            } else {
                Log.e(TAG, "Failed to log message: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception logging message to Honcho session", e)
        }
        return null
    }

    /**
     * Queries the Dialectic Agent for an intelligent retrieval grounded in user memory.
     */
    suspend fun queryDialectic(peerId: String, query: String, sessionId: String? = null): String? {
        try {
            val response = apiService.chatDialectic(
                getAuthHeader(),
                workspaceName,
                peerId,
                DialecticChatRequest(query, sessionId)
            )
            if (response.isSuccessful) {
                return response.body()?.response
            } else {
                Log.e(TAG, "Failed querying dialectic agent: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception querying Honcho dialectic", e)
        }
        return null
    }

    /**
     * Retrieves the learned habits, observations, and conclusions compiled about a peer.
     */
    suspend fun getHabitsAndConclusions(peerId: String): List<ConclusionResponse>? {
        try {
            val response = apiService.getConclusions(getAuthHeader(), workspaceName, peerId)
            if (response.isSuccessful) {
                return response.body()
            } else {
                Log.e(TAG, "Failed fetching peer conclusions: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching conclusions from Honcho", e)
        }
        return null
    }
}
