package fi.iki.pnr.kioskhelper

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.app.NotificationManager

object KioskController {
    private const val TAG = "KioskHelper"
    private const val PREFS = "kiosk_prefs"
    private const val KEY_APPLIED = "kiosk_applied"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun prepareKiosk(
        context: Context,
        admin: ComponentName,
        allowlist: List<String>,
        features: Int,
        suppressStatusBar: Boolean,
        dndMode: String
    ): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        Log.i(TAG, "prepareKiosk: allowlist=$allowlist, features=$features, suppressStatusBar=$suppressStatusBar, dndMode=$dndMode")

        // Build lock-task allowlist (ensure helper is always included)
        val packages = (allowlist + listOf(context.packageName)).distinct().toTypedArray()
        dpm.setLockTaskPackages(admin, packages)
        dpm.setLockTaskFeatures(admin, features)

        // Status bar policy (best-effort)
        try {
            dpm.setStatusBarDisabled(admin, suppressStatusBar)
        } catch (t: Throwable) {
            Log.w(TAG, "setStatusBarDisabled not supported on this device: ${t.message}")
        }

        // DND (best-effort; KioskCommandActivity already checked permission if mode != none)
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            when (dndMode) {
                "none" -> nm?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                "alarms" -> nm?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                else -> nm?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) // total
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "DND change denied; proceeding without DND: ${se.message}")
            return false
        } catch (t: Throwable) {
            Log.w(TAG, "DND change not applied: ${t.message}")
        }

        // Mark applied (without taking over HOME in v1)
        prefs(context).edit().putBoolean(KEY_APPLIED, true).apply()
        return true
    }

    fun isPrepared(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APPLIED, false)

    fun clearKiosk(context: Context, admin: ComponentName): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Restore status bar
        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (_: Throwable) {}

        Log.i(TAG, "clearKiosk called (no HOME restore in v1)")

        prefs(context).edit().putBoolean(KEY_APPLIED, false).apply()
        return true
    }
}
