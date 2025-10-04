package fi.iki.pnr.kioskhelper

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDndGranted = nm.isNotificationPolicyAccessGranted

        // Fail-fast: only re-apply if all preconditions still hold. MVP: no-op.
        if (!isDeviceOwner || !isDndGranted) return

        // In a future iteration we could re-apply policy based on stored state.
    }
}
