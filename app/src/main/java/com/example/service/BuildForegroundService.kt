package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.util.NotificationHelper
import java.io.File

class BuildForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_UPDATE = "com.example.service.action.UPDATE"
        const val ACTION_STOP = "com.example.service.action.STOP"

        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_STEP_NUMBER = "extra_step_number"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
        const val EXTRA_IS_DONE = "extra_is_done"
        const val EXTRA_IS_SUCCESS = "extra_is_success"
        const val EXTRA_APK_PATH = "extra_apk_path"

        fun startService(
            context: Context,
            projectName: String = "Projeto Android",
            stepNumber: Int = 1,
            statusMessage: String = "Iniciando preparação..."
        ) {
            NotificationHelper.setupChannels(context)
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_STEP_NUMBER, stepNumber)
                putExtra(EXTRA_STATUS, statusMessage)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateStatus(
            context: Context,
            stepNumber: Int,
            statusMessage: String,
            progressPercent: Int? = null
        ) {
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_STEP_NUMBER, stepNumber)
                putExtra(EXTRA_STATUS, statusMessage)
                if (progressPercent != null) {
                    putExtra(EXTRA_PROGRESS_PERCENT, progressPercent)
                }
            }
            context.startService(intent)
        }

        fun finishService(
            context: Context,
            isSuccess: Boolean,
            projectName: String,
            message: String,
            apkFile: File? = null
        ) {
            val intent = Intent(context, BuildForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_STATUS, message)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_IS_DONE, true)
                putExtra(EXTRA_IS_SUCCESS, isSuccess)
                apkFile?.let { putExtra(EXTRA_APK_PATH, it.absolutePath) }
            }
            context.startService(intent)
        }
    }

    private var currentProjectName = "Projeto Android"
    private var lastUpdateTime = 0L
    private var lastStepNumber = -1
    private var lastStatusMessage = ""
    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.setupChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: currentProjectName
        currentProjectName = projectName
        val statusMessage = intent?.getStringExtra(EXTRA_STATUS) ?: "Compilando..."
        val stepNumber = intent?.getIntExtra(EXTRA_STEP_NUMBER, 1) ?: 1
        val hasPercent = intent?.hasExtra(EXTRA_PROGRESS_PERCENT) == true
        val progressPercent = if (hasPercent) intent.getIntExtra(EXTRA_PROGRESS_PERCENT, 0) else null

        val isDone = intent?.getBooleanExtra(EXTRA_IS_DONE, false) ?: false
        val isSuccess = intent?.getBooleanExtra(EXTRA_IS_SUCCESS, false) ?: false
        val apkPath = intent?.getStringExtra(EXTRA_APK_PATH)
        val apkFile = apkPath?.let { File(it) }

        when (action) {
            ACTION_START -> {
                lastStepNumber = stepNumber
                lastStatusMessage = statusMessage
                lastUpdateTime = System.currentTimeMillis()

                val notification = buildProgressNotification(stepNumber, statusMessage, projectName, progressPercent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NotificationHelper.PROGRESS_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
                }
            }

            ACTION_UPDATE -> {
                val now = System.currentTimeMillis()
                val isStepChanged = stepNumber != lastStepNumber
                val isPercentChanged = progressPercent != null && progressPercent != lastPercent
                val timePassed = (now - lastUpdateTime) >= 800L

                // Super optimized: only update notification when step changes, percent changes, or 800ms+ passed
                if (isStepChanged || isPercentChanged || (timePassed && statusMessage != lastStatusMessage)) {
                    lastStepNumber = stepNumber
                    lastStatusMessage = statusMessage
                    if (progressPercent != null) lastPercent = progressPercent
                    lastUpdateTime = now

                    val notification = buildProgressNotification(stepNumber, statusMessage, currentProjectName, progressPercent)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
                }
            }

            ACTION_STOP -> {
                if (isDone) {
                    NotificationHelper.showCompletionAlert(
                        context = this,
                        isSuccess = isSuccess,
                        projectName = projectName,
                        message = statusMessage,
                        apkFile = apkFile
                    )
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildProgressNotification(
        stepNumber: Int,
        message: String,
        projectName: String,
        progressPercent: Int?
    ): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Super clean and direct text
        val title = "GitBuilder • $projectName"
        val contentText = if (stepNumber in 1..6) {
            "Etapa $stepNumber/6 • $message"
        } else {
            message
        }

        val builder = NotificationCompat.Builder(this, NotificationHelper.PROGRESS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(pendingIntent)

        if (progressPercent != null && progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        } else if (stepNumber in 1..6) {
            builder.setProgress(6, stepNumber, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }
}
