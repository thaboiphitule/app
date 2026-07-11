package com.companion.agent

import com.companion.agent.data.model.WorkspaceRequest
import com.companion.agent.data.model.DialecticChatRequest
import com.companion.agent.ui.UiState
import com.companion.agent.ui.ChatMessage
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CompanionUnitTest {

    private val gson = Gson()

    @Test
    fun testWorkspaceRequestSerialization() {
        val request = WorkspaceRequest(name = "hermes_test_workspace")
        val json = gson.toJson(request)
        
        assertTrue(json.contains("\"name\":\"hermes_test_workspace\""))
    }

    @Test
    fun testDialecticChatRequestSerialization() {
        val request = DialecticChatRequest(
            message = "Tell me my schedule",
            sessionId = "session_abc_123",
            level = "high"
        )
        val json = gson.toJson(request)
        
        assertTrue(json.contains("\"message\":\"Tell me my schedule\""))
        assertTrue(json.contains("\"session_id\":\"session_abc_123\""))
        assertTrue(json.contains("\"level\":\"high\""))
    }

    @Test
    fun testUiStateDefaults() {
        val state = UiState()
        
        assertTrue(state.isDemoMode)
        assertFalse(state.isConnected)
        assertFalse(state.isRecording)
        assertEquals("Hermes v1", state.companionName)
        assertEquals(1, state.messages.size)
        assertEquals("companion", state.messages.first().sender)
    }

    @Test
    fun testAddChatMessage() {
        var state = UiState()
        val originalMessageCount = state.messages.size
        
        val newMsg = ChatMessage(text = "Hello Hermes", sender = "user")
        state = state.copy(messages = state.messages + newMsg)
        
        assertEquals(originalMessageCount + 1, state.messages.size)
        assertEquals("Hello Hermes", state.messages.last().text)
        assertEquals("user", state.messages.last().sender)
    }
}
