package com.example.bbdlimitter.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bbdlimitter.repository.InventoryRepository
import java.time.LocalDate

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = InventoryRepository(applicationContext)
        val due = repository.getDueProducts(LocalDate.now())
        if (due.isEmpty()) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "expiry_reminder"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "期限リマインダ", NotificationManager.IMPORTANCE_DEFAULT)
        )

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        due.take(3).forEachIndexed { index, product ->
            val remaining = product.expiryDateEpochDay - LocalDate.now().toEpochDay()
            manager.notify(
                product.id.toInt() + index,
                NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("期限が近い商品があります")
                    .setContentText("${product.name}: 残り${remaining}日")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            )
        }
        return Result.success()
    }
}
