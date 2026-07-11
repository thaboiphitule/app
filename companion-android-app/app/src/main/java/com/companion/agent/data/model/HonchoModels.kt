package com.companion.agent.data.model

import com.google.gson.annotations.SerializedName

// Honcho API model classes

data class WorkspaceRequest(
    @SerializedName("name") val name: String
)

data class WorkspaceResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class PeerRequest(
    @SerializedName("name") val name: String,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null
)

data class PeerResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null
)

data class SessionRequest(
    @SerializedName("name") val name: String,
    @SerializedName("peer_ids") val peerIds: List<String>
)

data class SessionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("peer_ids") val peerIds: List<String>
)

data class MessageRequest(
    @SerializedName("content") val content: String,
    @SerializedName("sender_id") val senderId: String
)

data class MessageResponse(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DialecticChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("level") val level: String = "minimal"
)

data class DialecticChatResponse(
    @SerializedName("response") val response: String,
    @SerializedName("reasoning_chain") val reasoningChain: List<String>? = null
)

data class ConclusionResponse(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("observed_id") val observedId: String,
    @SerializedName("created_at") val createdAt: String? = null
)
