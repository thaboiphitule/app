package com.companion.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.companion.agent.ui.*

class MainActivity : ComponentActivity() {

    private val viewModel: CompanionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions for Always-Listening and Native action tools
        requestDevicePermissions()

        setContent {
            CompanionAgentTheme {
                MainAppLayout(viewModel)
            }
        }
    }

    private fun requestDevicePermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(viewModel: CompanionViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HERMES COMPANION", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateDark,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardBackground,
                contentColor = TextGray
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Face, contentDescription = "Companion") },
                    label = { Text("Companion") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Send, contentDescription = "Text Chat") },
                    label = { Text("Chat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Agent Habits") },
                    label = { Text("Agent Habits") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuration") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PurpleAccent,
                        selectedTextColor = PurpleAccent,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SlateDark),
            color = SlateDark
        ) {
            when (selectedTab) {
                0 -> CompanionDashboardScreen(state = state, onToggleMic = { viewModel.toggleLiveSession() })
                1 -> ChatScreen(state = state, onSendMessage = { text -> viewModel.sendTextMessage(text) })
                2 -> AgentDashboardScreen(state = state)
                3 -> SettingsScreen(
                    state = state,
                    onSaveSettings = { api, honcho, work, name, bio, tone, demo ->
                        viewModel.updateSettings(api, honcho, work, name, bio, tone, demo)
                    }
                )
            }
        }
    }
}

@Composable
fun CompanionAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PurpleAccent,
            background = SlateDark,
            surface = CardBackground,
            onBackground = TextWhite,
            onSurface = TextWhite
        ),
        content = content
    )
}
