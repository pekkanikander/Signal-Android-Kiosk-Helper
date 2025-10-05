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
        val resultReceiver: ResultReceiver? = intent?.getParcelableExtra(Extras.RESULT_RECEIVER, ResultReceiver::class.java)
        Log.i(TAG, "KioskCommandActivity received action=$action")

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.getComponentName(this)

        // Basic prechecks
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDndGranted = nm.isNotificationPolicyAccessGranted
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)

        if (!isDeviceOwner) {
            Log.w(TAG, "Caller is not device owner; aborting")
            sendResult(resultReceiver, "ERR_NOT_DEVICE_OWNER")
            finish(); return
        }

        when (action) {
            ACTION_ENABLE -> {
                if (!isDndGranted) {
                    Log.w(TAG, "DND permission missing; aborting enable")
                    sendResult(resultReceiver, "ERR_DND_PERMISSION_MISSING")
                    finish(); return
                }

                val allowlist = intent?.getStringArrayListExtra(Extras.ALLOWLIST) ?: arrayListOf()
                val features = intent?.getIntExtra(Extras.FEATURES, DEFAULT_FEATURES) ?: DEFAULT_FEATURES
                val suppressStatusBar = intent?.getBooleanExtra(Extras.SUPPRESS_STATUS_BAR, true) ?: true
                val signalPkg = DEFAULT_SIGNAL_PKG

                Log.i(TAG, "Enabling kiosk: allowlist=${allowlist}, features=${features}, suppressStatusBar=${suppressStatusBar}")
                val ok = KioskController.applyKiosk(
                    this, admin, allowlist, features, suppressStatusBar, signalPkg
                )
                if (ok) {
                    Log.i(TAG, "Kiosk applied")
                    sendResult(resultReceiver, "OK")
                    sendBroadcast(Intent(BROADCAST_APPLIED).putExtra("extra_dnd_active", true))
                } else {
                    Log.e(TAG, "Failed to apply kiosk")
                    sendResult(resultReceiver, "ERR_INTERNAL")
                }
            }

            ACTION_DISABLE -> {
                Log.i(TAG, "Disabling kiosk")
                val ok = KioskController.clearKiosk(this, admin)
                if (ok) {
                    Log.i(TAG, "Kiosk cleared")
                    sendResult(resultReceiver, "OK")
                    sendBroadcast(Intent(BROADCAST_CLEARED))
                } else {
                    Log.e(TAG, "Failed to clear kiosk")
                    sendResult(resultReceiver, "ERR_INTERNAL")
                }
            }

            else -> sendResult(resultReceiver, "ERR_INVALID_PARAMS")
        }

        finish()
    }

    private fun sendResult(receiver: ResultReceiver?, code: String) {
        try {
            Log.d(TAG, "sendResult: $code")
            receiver?.send(0, Bundle().apply { putString("extra_result", code) })
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "KioskHelper"
        object Extras {
            const val ALLOWLIST = "fi.iki.pnr.kioskhelper.extra.ALLOWLIST"
            const val FEATURES = "fi.iki.pnr.kioskhelper.extra.FEATURES"
            const val SUPPRESS_STATUS_BAR = "fi.iki.pnr.kioskhelper.extra.SUPPRESS_STATUS_BAR"
            const val DND_MODE = "fi.iki.pnr.kioskhelper.extra.DND_MODE"
            const val RESULT_RECEIVER = "fi.iki.pnr.kioskhelper.extra.RESULT_RECEIVER"
        }
        private const val ACTION_ENABLE = "fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK"
        private const val ACTION_DISABLE = "fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK"
        private const val BROADCAST_APPLIED = "fi.iki.pnr.kioskhelper.KIOSK_APPLIED"
        private const val BROADCAST_CLEARED = "fi.iki.pnr.kioskhelper.KIOSK_CLEARED"
        private const val DEFAULT_FEATURES = 0 // omit notifications by default; set via caller in future
        private const val DEFAULT_SIGNAL_PKG = "org.thoughtcrime.securesms"
    }
}
