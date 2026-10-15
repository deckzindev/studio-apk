package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import java.io.File

object NotificationHelper {

    const val PROGRESS_CHANNEL_ID = "gitbuilder_progress_channel"
    const val ALERT_CHANNEL_ID = "gitbuilder_alerts_channel"

    const val PROGRESS_NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID = 2002

    fun setupChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: Clean background compilation progress (Low importance, silent, smooth)
            val progressChannel = NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "Progresso da Compilação",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso em tempo real das etapas da compilação do APK"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(progressChannel)

            // Channel 2: Completion alerts (High importance, sound, vibration on completion)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Conclusão da Compilação",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de sucesso ou falha da compilação do APK"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    fun dismissProgressNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun showCompletionAlert(
        context: Context,
        isSuccess: Boolean,
        projectName: String,
        message: String,
        apkFile: File? = null
    ) {
        setupChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ALWAYS dismiss the ongoing progress notification immediately!
        manager.cancel(PROGRESS_NOTIFICATION_ID)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val title = if (isSuccess) {
            "🎉 APK Pronto para Instalar!"
        } else {
            "❌ Falha na Compilação"
        }

        val contentText = if (isSuccess) {
            "O APK do aplicativo '$projectName' foi gerado com sucesso."
        } else {
            "A compilação de '$projectName' não pôde ser concluída."
        }

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n$message"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingOpenApp)

        // If APK file is ready on device, add one-tap install button
        if (isSuccess && apkFile != null && apkFile.exists()) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pendingInstall = PendingIntent.getActivity(
                    context,
                    1,
                    installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.stat_sys_download_done, "Instalar APK", pendingInstall)
            } catch (_: Exception) {}
        }

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }
}
