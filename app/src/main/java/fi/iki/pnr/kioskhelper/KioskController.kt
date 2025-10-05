package fi.iki.pnr.kioskhelper

import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.content.ActivityNotFoundException

object KioskController {
    private const val TAG = "KioskHelper"
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

        Log.i(TAG, "applyKiosk called: allowlist=${allowlist}, features=${features}, suppressStatusBar=${suppressStatusBar}, signal=${signalPackage}")

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

        // Persist current default HOME (best-effort) before we take over HOME.
        val currentHome = KioskHomeUtil.resolveDefaultHome(context)
        if (currentHome != null) {
            Log.i(TAG, "Persisting previous HOME=${currentHome.flattenToString()}")
            prefs(context).edit()
                .putString(KEY_PREV_HOME, currentHome.flattenToString())
                .putBoolean(KEY_APPLIED, true)
                .apply()
        } else {
            Log.i(TAG, "No previous HOME found to persist")
            prefs(context).edit().putBoolean(KEY_APPLIED, true).apply()
        }

        // Become HOME to ensure safe surface
        val homeActivity = ComponentName(context, HomeActivity::class.java)
        KioskHomeUtil.setPersistentHome(context, dpm, admin, homeActivity)

        // Launch Signal into lock task
        Log.i(TAG, "Launching Signal=${signalPackage}")
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(signalPackage)
        if (launch == null) {
            Log.e(TAG, "Failed to get launch intent for ${signalPackage}")
            // Deep diagnostic logging to understand *why* resolution failed
            debugLaunchResolution(context, signalPackage)

            // Fallback 1: attempt explicit launcher component(s) commonly used by Signal
            val candidates = listOf(
                ComponentName(signalPackage, "$signalPackage.RoutingActivity"),
                ComponentName(signalPackage, "$signalPackage.MainActivity")
            )
            var explicit: Intent? = null
            for (cn in candidates) {
                try {
                    explicit = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = cn
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.packageManager.getActivityInfo(cn, 0) // throws if not found/visible
                    Log.w(TAG, "Using explicit component fallback: ${cn.flattenToShortString()}")
                    break
                } catch (_: Throwable) {
                    explicit = null
                }
            }
            if (explicit == null) {
                Log.e(TAG, "No explicit launcher component available; giving up")
                clearKiosk(context, admin)
                return false
            }
            val options = ActivityOptions.makeBasic().apply { setLockTaskEnabled(true) }
            try {
                context.startActivity(explicit, options.toBundle())
                return true
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Explicit start failed: ${e.message}")
                clearKiosk(context, admin)
                return false
            } catch (t: Throwable) {
                Log.e(TAG, "Explicit start error: ${t.javaClass.simpleName}: ${t.message}")
                clearKiosk(context, admin)
                return false
            }
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().apply { setLockTaskEnabled(true) }
        context.startActivity(launch, options.toBundle())
        return true
    }

    private fun debugLaunchResolution(context: Context, targetPkg: String) {
        val pm = context.packageManager
        Log.e(TAG, "---- DEBUG: launch resolution for $targetPkg ----")
        Log.e(TAG, "SDK_INT=${Build.VERSION.SDK_INT} appUid=${Process.myUid()} appPkg=${context.packageName}")
        val curUid = Process.myUid()
        val approxUserId = if (curUid >= 100000) curUid / 100000 else 0
        Log.e(TAG, "approxUserId=$approxUserId (from uid=$curUid)")

        // 1) getLaunchIntentForPackage
        val gi = pm.getLaunchIntentForPackage(targetPkg)
        Log.e(TAG, "getLaunchIntentForPackage -> ${if (gi == null) "null" else gi.toString()}")

        // 2) ApplicationInfo
        try {
            val ai: ApplicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(targetPkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(targetPkg, 0)
            }
            Log.e(TAG, "getApplicationInfo: enabled=${ai.enabled} packageName=${ai.packageName} uid=${ai.uid}")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "getApplicationInfo: NameNotFoundException for $targetPkg")
        } catch (t: Throwable) {
            Log.e(TAG, "getApplicationInfo: ${t.javaClass.simpleName}: ${t.message}")
        }

        // 3) PackageInfo (activities)
        try {
            val pi: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(targetPkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(targetPkg, PackageManager.GET_ACTIVITIES)
            }
            val count = pi.activities?.size ?: 0
            Log.e(TAG, "getPackageInfo(GET_ACTIVITIES): activitiesCount=$count")
            pi.activities?.forEach { a ->
                Log.e(TAG, "  activity: ${a.packageName}/${a.name} exported=${a.exported} enabled=${a.enabled}")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "getPackageInfo: NameNotFoundException for $targetPkg")
        } catch (t: Throwable) {
            Log.e(TAG, "getPackageInfo: ${t.javaClass.simpleName}: ${t.message}")
        }

        // 4) Query MAIN/LAUNCHER activities scoped to this package
        val probe = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = targetPkg
        }
        try {
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, 0)
            }
            Log.e(TAG, "queryIntentActivities(MAIN/LAUNCHER, pkg=$targetPkg): size=${list.size}")
            list.forEach { ri ->
                val ai = ri.activityInfo
                Log.e(TAG, "  resolves: ${ai.packageName}/${ai.name} exported=${ai.exported} enabled=${ai.enabled}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "queryIntentActivities error: ${t.javaClass.simpleName}: ${t.message}")
        }

        // 5) Query unscoped MAIN/LAUNCHER and filter for visibility (can be noisy but useful)
        val anyMain = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        try {
            val listAll = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(anyMain, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(anyMain, 0)
            }
            val visible = listAll.filter { it.activityInfo?.packageName == targetPkg }
            Log.e(TAG, "unscoped MAIN/LAUNCHER total=${listAll.size} visibleForTarget=${visible.size}")
            visible.forEach { ri ->
                val ai = ri.activityInfo
                Log.e(TAG, "  visible: ${ai.packageName}/${ai.name} exported=${ai.exported} enabled=${ai.enabled}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "unscoped query error: ${t.javaClass.simpleName}: ${t.message}")
        }

        Log.e(TAG, "---- DEBUG END ----")
    }

    fun clearKiosk(context: Context, admin: ComponentName): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        // Restore status bar
        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (_: Throwable) {}

        Log.i(TAG, "clearKiosk called")
        // Restore previous HOME if we had one; otherwise fall back to showing chooser once
        val stored = prefs(context).getString(KEY_PREV_HOME, null)
        if (stored != null) {
            val component = ComponentName.unflattenFromString(stored)
            if (component != null) {
                Log.i(TAG, "Restoring previous HOME=${stored}")
                KioskHomeUtil.setPersistentHome(context, dpm, admin, component)
            } else {
                Log.w(TAG, "Stored previous HOME string invalid; showing chooser")
                KioskHomeUtil.clearToChooser(context, dpm, admin)
            }
        } else {
            Log.i(TAG, "No previous HOME stored; showing chooser")
            KioskHomeUtil.clearToChooser(context, dpm, admin)
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
