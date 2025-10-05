package fi.iki.pnr.kioskhelper

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log

class KioskCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.extras?.classLoader = ResultReceiver::class.java.classLoader

        val action = intent?.action
        val resultReceiver: ResultReceiver? = try {
            intent?.getParcelableExtra(Extras.RESULT_RECEIVER, ResultReceiver::class.java)
        } catch (t: Throwable) {
            Log.w(TAG, "Ignoring ResultReceiver extra (unmarshal failed): ${t.message}")
            null
        }
        val resultPi: PendingIntent? = try {
            intent?.getParcelableExtra(Extras.RESULT_PENDING_INTENT, PendingIntent::class.java)
        } catch (t: Throwable) {
            Log.w(TAG, "Ignoring PendingIntent extra (unmarshal failed): ${t.message}")
            null
        }
        Log.i(TAG, "KioskCommandActivity received action=$action")

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.getComponentName(this)

        // Basic prechecks
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDndGranted = nm.isNotificationPolicyAccessGranted
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)

        if (!isDeviceOwner) {
            Log.w(TAG, "Caller is not device owner; aborting")
            sendStatus(resultPi, resultReceiver, "ERR_NOT_DEVICE_OWNER")
            finish(); return
        }

        when (action) {
            ACTION_ENABLE -> {
                // Determine desired DND behavior; default to "total" for v1
                val dndMode = intent?.getStringExtra(Extras.DND_MODE) ?: "total" // none | alarms | total

                // If caller wants DND but we don't have access, fail fast (minimal helper)
                if (dndMode != "none" && !isDndGranted) {
                    Log.w(TAG, "DND permission missing; aborting enable (mode=$dndMode)")
                    sendStatus(resultPi, resultReceiver, "ERR_DND_PERMISSION_MISSING")
                    finish(); return
                }

                val allowlist = intent?.getStringArrayListExtra(Extras.ALLOWLIST) ?: arrayListOf()
                val features = intent?.getIntExtra(Extras.FEATURES, DEFAULT_FEATURES) ?: DEFAULT_FEATURES
                val suppressStatusBar = intent?.getBooleanExtra(Extras.SUPPRESS_STATUS_BAR, true) ?: true

                Log.i(TAG, "Preparing kiosk (no launch): allowlist=$allowlist, features=$features, suppressStatusBar=$suppressStatusBar, dndMode=$dndMode")
                val ok = KioskController.prepareKiosk(
                    this, admin, allowlist, features, suppressStatusBar, dndMode
                )
                if (ok) {
                    Log.i(TAG, "Kiosk prepared (Signal should call startLockTask)")
                    sendStatus(resultPi, resultReceiver, "OK")
                    sendBroadcast(Intent(BROADCAST_APPLIED).putExtra("extra_dnd_active", dndMode != "none"))
                } else {
                    Log.e(TAG, "Failed to prepare kiosk")
                    sendStatus(resultPi, resultReceiver, "ERR_INTERNAL")
                }
            }

            ACTION_DISABLE -> {
                Log.i(TAG, "Clearing kiosk (no relaunch)")
                val ok = KioskController.clearKiosk(this, admin)
                if (ok) {
                    Log.i(TAG, "Kiosk cleared")
                    sendStatus(resultPi, resultReceiver, "OK")
                    sendBroadcast(Intent(BROADCAST_CLEARED))
                } else {
                    Log.e(TAG, "Failed to clear kiosk")
                    sendStatus(resultPi, resultReceiver, "ERR_INTERNAL")
                }
            }

            else -> sendStatus(resultPi, resultReceiver, "ERR_INVALID_PARAMS")
        }

        finish()
    }

    private fun sendStatus(resultPi: PendingIntent?, receiver: ResultReceiver?, status: String) {
        try {
            resultPi?.send(this, 0, Intent().putExtra("status", status))
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Result PendingIntent canceled: ${e.message}")
        } catch (t: Throwable) {
            Log.w(TAG, "Result PendingIntent send failed: ${t.message}")
        }
        try {
            receiver?.send(0, Bundle().apply { putString("extra_result", status) })
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
            const val RESULT_PENDING_INTENT = "fi.iki.pnr.kioskhelper.extra.RESULT_PENDING_INTENT"
        }
        private const val ACTION_ENABLE = "fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK"
        private const val ACTION_DISABLE = "fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK"
        private const val BROADCAST_APPLIED = "fi.iki.pnr.kioskhelper.KIOSK_APPLIED"
        private const val BROADCAST_CLEARED = "fi.iki.pnr.kioskhelper.KIOSK_CLEARED"
        private const val DEFAULT_FEATURES = 0 // helper prepares policy; Signal pins/unpins
    }
}
