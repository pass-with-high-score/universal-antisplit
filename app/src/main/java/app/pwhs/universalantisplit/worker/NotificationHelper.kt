package app.pwhs.universalantisplit.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import app.pwhs.universalantisplit.MainActivity
import app.pwhs.universalantisplit.R
import java.io.File

object NotificationHelper {

    const val CHANNEL_ID_PROGRESS = "channel_apk_merger_progress"
    const val CHANNEL_ID_COMPLETED = "channel_apk_merger_completed"
    const val NOTIFICATION_ID_PROGRESS = 1001
    const val NOTIFICATION_ID_COMPLETED = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Progress channel (silent)
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(progressChannel)

            // Completed channel (high importance alert)
            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    fun buildProgressNotification(
        context: Context,
        appName: String,
        progress: Int,
        statusText: String? = null
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_merging_title, appName)
        val content = statusText ?: context.getString(R.string.notification_merging_desc, progress)

        return NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun showSuccessNotification(
        context: Context,
        appName: String,
        outputApkFile: File
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                outputApkFile
            )
        } catch (e: Exception) {
            Uri.fromFile(outputApkFile)
        }

        // Action 1: Install APK
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val installPendingIntent = PendingIntent.getActivity(
            context,
            1,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Share APK
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            2,
            Intent.createChooser(shareIntent, context.getString(R.string.notification_action_share)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notification_merge_success_title)
        val content = context.getString(R.string.notification_merge_success_desc, appName)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETED)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(installPendingIntent)
            .addAction(
                android.R.drawable.stat_sys_download_done,
                context.getString(R.string.notification_action_install),
                installPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_share,
                context.getString(R.string.notification_action_share),
                sharePendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
    }

    fun showErrorNotification(
        context: Context,
        appName: String,
        errorMessage: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = context.getString(R.string.notification_merge_failed_title)
        val content = context.getString(R.string.notification_merge_failed_desc, appName, errorMessage)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETED)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
    }
}
