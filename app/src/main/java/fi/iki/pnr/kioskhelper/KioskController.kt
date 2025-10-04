package fi.iki.pnr.kioskhelper

import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

object KioskController {
    private const val TAG = "KioskController"
    private const val PREFS = "kiosk_prefs"
    private const val KEY_PREV_HOME = "previous_home"
    private const val KEY_APPLIED = "kiosk_applied"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun applyKiosk(
        context: Context,
        admin: ComponentName,
        allowlist: List<String>,
        features: Int,
        suppressStatusBar: Boolean,
        signalPackage: String
    ): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Lock task allowlist
        val packages = (allowlist + listOf("fi.iki.pnr.kioskhelper", signalPackage)).distinct().toTypedArray()
        dpm.setLockTaskPackages(admin, packages)
        dpm.setLockTaskFeatures(admin, features)

        // Status bar
        try {
            dpm.setStatusBarDisabled(admin, suppressStatusBar)
        } catch (t: Throwable) {
            Log.w(TAG, "setStatusBarDisabled not supported on this device: ${t.message}")
        }

        // Persist previous HOME (best-effort). For MVP we skip reading current launcher.
        prefs(context).edit().putBoolean(KEY_APPLIED, true).apply()

        // Become HOME to ensure safe surface
        val homeActivity = ComponentName(context, HomeActivity::class.java)
        KioskHomeUtil.setPersistentHome(context, dpm, admin, homeActivity)

        // Launch Signal into lock task
        val launch = context.packageManager.getLaunchIntentForPackage(signalPackage)
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().apply { setLockTaskEnabled(true) }
        context.startActivity(launch, options.toBundle())
        return true
    }

    fun clearKiosk(context: Context, admin: ComponentName): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Restore status bar
        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (_: Throwable) {}

        // Restore previous HOME to system default chooser once for MVP
        KioskHomeUtil.clearToChooser(context, dpm, admin)

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
}
