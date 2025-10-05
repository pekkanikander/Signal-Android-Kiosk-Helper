package fi.iki.pnr.kioskhelper

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.app.NotificationManager

object KioskController {
    private const val TAG = "KioskHelper"
    private const val PREFS = "kiosk_prefs"
    private const val KEY_PREV_HOME = "previous_home"
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

    fun clearKiosk(context: Context, admin: ComponentName): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Restore status bar
        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (_: Throwable) {}

        Log.i(TAG, "clearKiosk called")
        val stored = prefs(context).getString(KEY_PREV_HOME, null)
        if (stored != null) {
            val component = ComponentName.unflattenFromString(stored)
            if (component != null) {
                Log.i(TAG, "Restoring previous HOME=$stored")
                KioskHomeUtil.setPersistentHome(context, dpm, admin, component)
            } else {
                Log.w(TAG, "Stored previous HOME string invalid; clearing helper preferences for HOME")
                dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            }
        } else {
            Log.i(TAG, "No previous HOME stored; leaving HOME as-is")
        }

        prefs(context).edit().putBoolean(KEY_APPLIED, false).apply()
        return true
    }
}

object KioskHomeUtil {
    fun setPersistentHome(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
        home: ComponentName
    ) {
        // Minimal persistent preferred for HOME/MAIN
        // Using newer API simplified by clearing and letting DPM prefer our HOME.
        dpm.addPersistentPreferredActivity(
            admin,
            android.content.IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            home
        )
    }

    fun clearToChooser(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        // Clear helper persistent preferences so HOME chooser appears
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
    }

    fun resolveDefaultHome(context: Context): ComponentName? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolveInfo = pm.resolveActivity(intent, 0) ?: return null
        val activityInfo = resolveInfo.activityInfo ?: return null
        return ComponentName(activityInfo.packageName, activityInfo.name)
    }
}
