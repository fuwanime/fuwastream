package com.fuwanime

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.notify.EpisodeNotifier
import com.fuwanime.data.notify.NotificationAccess
import com.fuwanime.data.notify.ensureChannel
import com.fuwanime.data.notify.notificationAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationAccessTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun missingPermissionIsReportedBeforeChannelState() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationAccess.PERMISSION_MISSING, notificationAccess(app))
    }

    @Test fun testNotificationIsRejectedWithoutPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(EpisodeNotifier(app).notifyTest())
    }

    @Test fun channelIsCreatedWhenPermissionIsGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(ensureChannel(app, manager, "access_test_created", "Test", "Test"))
        assertNotNull(manager.getNotificationChannel("access_test_created"))
    }

    @Test fun blockedChannelIsRejected() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("access_test_blocked", "Blocked", NotificationManager.IMPORTANCE_NONE),
        )
        assertFalse(ensureChannel(app, manager, "access_test_blocked", "Blocked", "Blocked"))
    }
}
