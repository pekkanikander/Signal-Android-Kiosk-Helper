package fi.iki.pnr.kioskhelper

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "KioskHelper"
        private const val SIGNAL_PACKAGE_NAME = "org.thoughtcrime.securesms"
        private const val SIGNAL_ROUTING_ACTIVITY = "${SIGNAL_PACKAGE_NAME}.RoutingActivity"
        @Volatile private var launchedThisBoot: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (launchedThisBoot) { Log.i(TAG, "BootReceiver: already launched once; ignoring ${intent.action}"); return }
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> handleBoot(context, "LOCKED_BOOT_COMPLETED")
            Intent.ACTION_BOOT_COMPLETED        -> handleBoot(context, "BOOT_COMPLETED")
            Intent.ACTION_USER_UNLOCKED         -> handleBoot(context, "USER_UNLOCKED")
            else -> return
        }
    }

    private fun handleBoot(context: Context, reason: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val nm  = context.getSystemService(Context.NOTIFICATION_SERVICE)  as NotificationManager
        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        val isDndGranted  =  nm.isNotificationPolicyAccessGranted
        val prepared      = KioskController.isPrepared(context)

        Log.i(TAG, "BootReceiver($reason): do=$isDeviceOwner, dnd=$isDndGranted, prepared=$prepared")

        // Fail-fast: only auto-launch if we are still DO and kiosk was prepared.
        if (!isDeviceOwner || !prepared) return

        // Best-effort: bring Signal to foreground. We do not force lock task here;
        // Signal will pin itself when/if it enters Accessibility Mode.
        val signal = ComponentName(SIGNAL_PACKAGE_NAME, SIGNAL_ROUTING_ACTIVITY)
        val launch = Intent().apply {
            component = signal
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(launch)
            launchedThisBoot = true
            Log.i(TAG, "BootReceiver: launched Signal ($reason); guard set=true")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "BootReceiver: Signal not found; skipping launch ($reason): ${e.message}")
        } catch (t: Throwable) {
            Log.w(TAG, "BootReceiver: failed to start Signal ($reason): ${t.message}")
        }
    }
}
