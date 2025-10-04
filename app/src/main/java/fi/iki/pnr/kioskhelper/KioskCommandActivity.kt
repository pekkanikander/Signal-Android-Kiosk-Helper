package fi.iki.pnr.kioskhelper

import android.app.Activity
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log

class KioskCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val resultReceiver: ResultReceiver? = intent?.getParcelableExtra("extra_result_receiver", ResultReceiver::class.java)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.getComponentName(this)

        // Basic prechecks
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDndGranted = nm.isNotificationPolicyAccessGranted
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)

        if (!isDeviceOwner) {
            sendResult(resultReceiver, "ERR_UNPROVISIONED_API28PLUS_REQUIRED")
            finish(); return
        }

        when (action) {
            ACTION_ENABLE -> {
                if (!isDndGranted) {
                    sendResult(resultReceiver, "ERR_DND_PERMISSION_MISSING")
                    finish(); return
                }

                val allowlist = intent?.getStringArrayListExtra("extra_allowlist") ?: arrayListOf()
                val features = intent?.getIntExtra("extra_features", DEFAULT_FEATURES) ?: DEFAULT_FEATURES
                val suppressStatusBar = intent?.getBooleanExtra("extra_suppress_status_bar", true) ?: true
                val signalPkg = DEFAULT_SIGNAL_PKG

                val ok = KioskController.applyKiosk(
                    this, admin, allowlist, features, suppressStatusBar, signalPkg
                )
                if (ok) {
                    sendResult(resultReceiver, "OK")
                    sendBroadcast(Intent(BROADCAST_APPLIED).putExtra("extra_dnd_active", true))
                } else {
                    sendResult(resultReceiver, "ERR_INTERNAL")
                }
            }

            ACTION_DISABLE -> {
                val ok = KioskController.clearKiosk(this, admin)
                if (ok) {
                    sendResult(resultReceiver, "OK")
                    sendBroadcast(Intent(BROADCAST_CLEARED))
                } else {
                    sendResult(resultReceiver, "ERR_INTERNAL")
                }
            }

            else -> sendResult(resultReceiver, "ERR_INVALID_PARAMS")
        }

        finish()
    }

    private fun sendResult(receiver: ResultReceiver?, code: String) {
        try { receiver?.send(0, Bundle().apply { putString("extra_result", code) }) } catch (_: Throwable) {}
    }

    companion object {
        private const val ACTION_ENABLE = "fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK"
        private const val ACTION_DISABLE = "fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK"
        private const val BROADCAST_APPLIED = "fi.iki.pnr.kioskhelper.KIOSK_APPLIED"
        private const val BROADCAST_CLEARED = "fi.iki.pnr.kioskhelper.KIOSK_CLEARED"
        private const val DEFAULT_FEATURES = 0 // omit notifications by default; set via caller in future
        private const val DEFAULT_SIGNAL_PKG = "org.thoughtcrime.securesms"
    }
}
