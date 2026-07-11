package com.companion.agent.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.companion.agent.MainActivity
import com.companion.agent.R
import java.text.SimpleDateFormat
import java.util.*

class DeviceToolExecutor(private val context: Context) {
    private val TAG = "DeviceToolExecutor"
    private val CHANNEL_ID = "hermes_agent_reminders"

    init {
        createNotificationChannel()
    }

    /**
     * Entrypoint mapping agentic ToolCall names to concrete native Android operations
     */
    fun executeTool(toolName: String, arguments: Map<String, Any>): Map<String, Any> {
        Log.d(TAG, "Executing native tool: $toolName with arguments $arguments")
        return when (toolName) {
            "trigger_system_notification" -> {
                val title = arguments["title"]?.toString() ?: "Reminder"
                val message = arguments["message"]?.toString() ?: ""
                triggerNotification(title, message)
            }
            "fetch_device_status" -> {
                fetchDeviceStatus()
            }
            "schedule_alarm_reminder" -> {
                val taskName = arguments["task_name"]?.toString() ?: "Agent Task"
                val delayMinutes = (arguments["delay_minutes"] as? Number)?.toInt() ?: 1
                scheduleAlarm(taskName, delayMinutes)
            }
            else -> mapOf("error" to "Unsupported tool execution: $toolName")
        }
    }

    /**
     * Tool 1: Posts a high-priority system notification to surface suggestions or warnings
     */
    private fun triggerNotification(title: String, message: String): Map<String, Any> {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notification)

            Log.d(TAG, "Notification fired: $title - $message")
            return mapOf(
                "status" to "success",
                "message" to "Notification with ID $notificationId posted to device successfully."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error posting system notification tool", e)
            return mapOf("status" to "error", "error_message" to (e.message ?: "Unknown error"))
        }
    }

    /**
     * Tool 2: Gathers Android environment status context (Time, battery, network, location coordinates if permitted)
     */
    private fun fetchDeviceStatus(): Map<String, Any> {
        val status = mutableMapOf<String, Any>()
        try {
            // Time
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            status["current_time"] = sdf.format(Date())

            // Battery
            val batteryStatus: Intent? = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            status["battery_percentage"] = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else "unknown"

            // Location coordinates
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                try {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                } catch (securityEx: SecurityException) {
                    // Permission not granted
                }
            }

            if (bestLocation != null) {
                status["location"] = mapOf(
                    "latitude" to bestLocation.latitude,
                    "longitude" to bestLocation.longitude,
                    "accuracy_meters" to bestLocation.accuracy
                )
            } else {
                status["location"] = "unavailable or permission denied"
            }

            Log.d(TAG, "Device Status context compiled: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device status parameters", e)
            status["error"] = e.message ?: "Unknown error"
        }
        return status
    }

    /**
     * Tool 3: Schedules an alarm / reminder event via Android AlarmManager
     */
    private fun scheduleAlarm(taskName: String, delayMinutes: Int): Map<String, Any> {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("action_reminder_task", taskName)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                taskName.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + (delayMinutes * 60 * 1000)
            
            // Set exact alarm (requires SCHEDULE_EXACT_ALARM or fallback to inexact on modern androids)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            val scheduledTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(System.currentTimeMillis() + (delayMinutes * 60 * 1000)))
            Log.d(TAG, "Alarm registered for '$taskName' at $scheduledTime")
            return mapOf(
                "status" to "success",
                "task" to taskName,
                "trigger_time" to scheduledTime,
                "message" to "Successfully registered alarm event for user in $delayMinutes minute(s)."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Device Alarm event", e)
            return mapOf("status" to "error", "error_message" to (e.message ?: "Unknown error"))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Hermes Companion Alerts"
            val descriptionText = "Notifications triggered by your AI Companion Agent"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
