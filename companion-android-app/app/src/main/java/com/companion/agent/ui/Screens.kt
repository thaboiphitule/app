package com.companion.agent.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Color Palette
val SlateDark = Color(0xFF12131C)
val CardBackground = Color(0xFF1E1F2E)
val PurpleAccent = Color(0xFF884DFF)
val CoralAccent = Color(0xFFFF5252)
val SoftCyan = Color(0xFF1AD1D1)
val TextWhite = Color(0xFFF1F1F7)
val TextGray = Color(0xFF9EA0B0)

/**
 * 1. Companion Avatar Dashboard Screen (Kindroid Inspired Visuals)
 */
@Composable
fun CompanionDashboardScreen(
    state: UiState,
    onToggleMic: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .padding(16.dp)
    ) {
        // Core Visualizer & Bio Card
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Bio Info Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.companionName,
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.companionTone,
                        color = SoftCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.companionBackstory,
                        color = TextGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            // Kindroid Inspired Beautiful Visual Element
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PurpleAccent.copy(alpha = 0.5f), Color.Transparent),
                            radius = 350f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing outer glowing aura
                Box(
                    modifier = Modifier
                        .size(180.dp * if (state.isRecording) pulseScale else 1f)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (state.isRecording) CoralAccent else PurpleAccent,
                            CircleShape
                        )
                        .background(CardBackground.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Default.PlayArrow else Icons.Default.Face,
                        contentDescription = "Avatar State",
                        tint = if (state.isRecording) CoralAccent else PurpleAccent,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Waveform overlay
                if (state.isRecording) {
                    LiveWaveformCanvas(volume = state.recordingVolume)
                }
            }

            // Interactive Listening state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = if (state.isRecording) "COMPANION IS WATCHING AND LISTENING" else "COMPANION ASLEEP",
                    color = if (state.isRecording) CoralAccent else TextGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                if (state.isRecording && state.liveTranscript.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${state.liveTranscript}\"",
                        color = TextWhite,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Microphone activation toggle button
                Button(
                    onClick = onToggleMic,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRecording) CoralAccent else PurpleAccent
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Default.Close else Icons.Default.CheckCircle,
                        contentDescription = "Mic Trigger",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * Pulsing visual audio wave canvas
 */
@Composable
fun LiveWaveformCanvas(volume: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barCount = 12
        val space = 8.dp.toPx()
        val totalBarWidth = 6.dp.toPx()
        val startX = (width - (barCount * (totalBarWidth + space))) / 2

        for (i in 0 until barCount) {
            val factor = 1f - Math.abs((i - barCount / 2f) / (barCount / 2f))
            val barHeight = (40.dp.toPx() + (volume * 120.dp.toPx() * factor))
            val x = startX + i * (totalBarWidth + space)
            val y1 = (height - barHeight) / 2
            val y2 = (height + barHeight) / 2

            drawLine(
                color = CoralAccent.copy(alpha = 0.3f + 0.7f * factor),
                start = Offset(x, y1),
                end = Offset(x, y2),
                strokeWidth = totalBarWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 2. Conversational Message History Screen (Chat Screen)
 */
@Composable
fun ChatScreen(
    state: UiState,
    onSendMessage: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll on new chats
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(state.messages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        // Chat Logs lazy column
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages) { message ->
                ChatBubble(message = message)
            }
        }

        // Horizontal partition line
        Divider(color = Color.White.copy(alpha = 0.08f))

        // Message Dispatch input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Write message here...", color = TextGray) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(PurpleAccent, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = when (message.sender) {
        "user" -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    val containerColor = when (message.sender) {
        "user" -> PurpleAccent
        "system" -> Color.DarkGray.copy(alpha = 0.4f)
        else -> CardBackground
    }

    val bubbleShape = when (message.sender) {
        "user" -> RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
        else -> RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = if (message.sender == "system") TextGray else TextWhite,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = message.timestamp,
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * 3. Hermes Agent Dashboard Screen (Habits and Tasks Panel)
 */
@Composable
fun AgentDashboardScreen(state: UiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section header
        item {
            Text(
                text = "HERMES AGENT ENGINE",
                color = SoftCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Active scheduled device alarms / reminders
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Reminders & Alerts",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alerts",
                            tint = CoralAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.activeReminders.isEmpty()) {
                        Text(
                            text = "No active reminders. Ask your agent to set a reminder or alarm!",
                            color = TextGray,
                            fontSize = 13.sp
                        )
                    } else {
                        state.activeReminders.forEach { reminder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = reminder.name, color = TextWhite, fontSize = 14.sp)
                                Text(text = "Fires at ${reminder.fireTime}", color = CoralAccent, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Honcho Persistent Memory Habit Logs card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Honcho Learned Habits",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Memory",
                            tint = PurpleAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    state.habits.forEach { habit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Tick",
                                tint = SoftCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = habit,
                                color = TextWhite,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        // Permissions Checklist Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device System Integrations",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem("Always-Listening Microphone", true)
                    PermissionItem("System Notification Spawning", true)
                    PermissionItem("Precise Location Mapping", true)
                    PermissionItem("Schedule Alarms & Wake Timers", true)
                }
            }
        }
    }
}

@Composable
fun PermissionItem(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, color = TextGray, fontSize = 13.sp)
        Text(
            text = if (granted) "Active" else "Inactive",
            color = if (granted) SoftCyan else CoralAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 4. Configuration and Settings View
 */
@Composable
fun SettingsScreen(
    state: UiState,
    onSaveSettings: (String, String, String, String, String, String, Boolean) -> Unit
) {
    var isDemo by remember { mutableStateOf(state.isDemoMode) }
    var geminiKey by remember { mutableStateOf(state.geminiApiKey) }
    var honchoKey by remember { mutableStateOf(state.honchoApiKey) }
    var workspace by remember { mutableStateOf(state.honchoWorkspace) }
    
    var name by remember { mutableStateOf(state.companionName) }
    var backstory by remember { mutableStateOf(state.companionBackstory) }
    var voiceTone by remember { mutableStateOf(state.companionTone) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Switch
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Demo / Simulation Mode", color = TextWhite, fontWeight = FontWeight.Bold)
                    Text("Runs app visually without needing key files", color = TextGray, fontSize = 12.sp)
                }
                Switch(
                    checked = isDemo,
                    onCheckedChange = { isDemo = it }
                )
            }
        }

        // Companion Customize Info
        item {
            Text("COMPANION DETAILS (KINDROID INSTINCTS)", color = PurpleAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Companion Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        unfocusedContainerColor = CardBackground,
                        focusedContainerColor = CardBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = backstory,
                    onValueChange = { backstory = it },
                    label = { Text("Personality Backstory") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        unfocusedContainerColor = CardBackground,
                        focusedContainerColor = CardBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = voiceTone,
                    onValueChange = { voiceTone = it },
                    label = { Text("Vocal Tone Preset") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        unfocusedContainerColor = CardBackground,
                        focusedContainerColor = CardBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Credentials Card
        if (!isDemo) {
            item {
                Text("API CREDENTIALS (PRODUCTION)", color = SoftCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = { Text("Gemini Live API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            unfocusedContainerColor = CardBackground,
                            focusedContainerColor = CardBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = honchoKey,
                        onValueChange = { honchoKey = it },
                        label = { Text("Honcho API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            unfocusedContainerColor = CardBackground,
                            focusedContainerColor = CardBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = workspace,
                        onValueChange = { workspace = it },
                        label = { Text("Honcho Workspace ID") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            unfocusedContainerColor = CardBackground,
                            focusedContainerColor = CardBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Save Config trigger
        item {
            Button(
                onClick = {
                    onSaveSettings(geminiKey, honchoKey, workspace, name, backstory, voiceTone, isDemo)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
            ) {
                Text("Save Configuration", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
