package com.companion.agent.data.network

import com.companion.agent.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface HonchoApiService {

    @POST("v3/workspaces")
    suspend fun createWorkspace(
        @Header("Authorization") authHeader: String,
        @Body request: WorkspaceRequest
    ): Response<WorkspaceResponse>

    @POST("v3/peers")
    suspend fun createPeer(
        @Header("Authorization") authHeader: String,
        @Header("X-Workspace-Name") workspaceName: String,
        @Body request: PeerRequest
    ): Response<PeerResponse>

    @POST("v3/sessions")
    suspend fun createSession(
        @Header("Authorization") authHeader: String,
        @Header("X-Workspace-Name") workspaceName: String,
        @Body request: SessionRequest
    ): Response<SessionResponse>

    @POST("v3/sessions/{sessionId}/messages")
    suspend fun addMessage(
        @Header("Authorization") authHeader: String,
        @Header("X-Workspace-Name") workspaceName: String,
        @Path("sessionId") sessionId: String,
        @Body request: MessageRequest
    ): Response<MessageResponse>

    @POST("v3/peers/{peerId}/chat")
    suspend fun chatDialectic(
        @Header("Authorization") authHeader: String,
        @Header("X-Workspace-Name") workspaceName: String,
        @Path("peerId") peerId: String,
        @Body request: DialecticChatRequest
    ): Response<DialecticChatResponse>

    @GET("v3/peers/{peerId}/conclusions")
    suspend fun getConclusions(
        @Header("Authorization") authHeader: String,
        @Header("X-Workspace-Name") workspaceName: String,
        @Path("peerId") peerId: String
    ): Response<List<ConclusionResponse>>
}
