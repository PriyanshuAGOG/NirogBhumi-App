package com.nirogbhumi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class NirogBhumiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel("health_reminders", "Health reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Quiet health, consultation, program and order reminders"
                }
            )
            // Separate from health_reminders so a member can mute one without
            // muting the other - download progress is low-importance (silent,
            // no sound/heads-up) since DownloadManager already shows its own
            // system progress notification; this channel is only used for the
            // "update ready to install" / "download failed" follow-up.
            manager.createNotificationChannel(
                NotificationChannel("app_updates", "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "New app version available, download progress, and install prompts"
                }
            )
        }
        com.nirogbhumi.app.notifications.ReminderScheduler.rescheduleAllEnabled(this)
        com.nirogbhumi.app.update.UpdateManager.schedulePeriodicCheck(this)
        val app = FirebaseApp.initializeApp(this) ?: return
        FirebaseFirestore.getInstance(app).firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build()
            )
            .build()
        if (!BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } else {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        }
    }
}
