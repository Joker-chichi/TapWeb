package com.tapweb.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Detects the device manufacturer and provides manufacturer-specific intents
 * for shortcut permission settings on Chinese Android ROMs.
 */
object DeviceHelper {

    enum class Manufacturer {
        XIAOMI, HUAWEI, OPPO, VIVO, OTHER
    }

    fun getManufacturer(): Manufacturer {
        val brand = Build.MANUFACTURER.lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") -> Manufacturer.XIAOMI
            brand.contains("huawei") || brand.contains("honor") -> Manufacturer.HUAWEI
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> Manufacturer.OPPO
            brand.contains("vivo") -> Manufacturer.VIVO
            else -> Manufacturer.OTHER
        }
    }

    fun isChineseRom(): Boolean {
        return getManufacturer() != Manufacturer.OTHER
    }

    /**
     * Returns an intent to open the shortcut/create-shortcut permission settings
     * for the current device. Returns null if not applicable.
     */
    fun getShortcutPermissionIntent(context: Context): Intent? {
        return when (getManufacturer()) {
            Manufacturer.XIAOMI -> xiaomiIntent(context)
            Manufacturer.HUAWEI -> huaweiIntent(context)
            Manufacturer.OPPO -> oppoIntent(context)
            Manufacturer.VIVO -> vivoIntent(context)
            Manufacturer.OTHER -> null
        }
    }

    /**
     * Returns the launcher package name for targeting broadcasts.
     */
    fun getLauncherPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName
    }

    // --- Manufacturer-specific intents ---

    private fun xiaomiIntent(context: Context): Intent? {
        // MIUI Security Center → App Permissions
        return tryIntent(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }, context
        ) ?: tryIntent(
            // Older MIUI
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                putExtra("extra_package_name", context.packageName)
            }, context
        ) ?: fallbackAppDetails(context)
    }

    private fun huaweiIntent(context: Context): Intent? {
        return tryIntent(
            // EMUI / HarmonyOS Manager
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
                )
            }, context
        ) ?: tryIntent(
            // HarmonyOS newer
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }, context
        ) ?: fallbackAppDetails(context)
    }

    private fun oppoIntent(context: Context): Intent? {
        return tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.color.safecenter",
                    "com.color.safecenter.permission.PermissionManagerActivity"
                )
            }, context
        ) ?: tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.PermissionManagerActivity"
                )
            }, context
        ) ?: fallbackAppDetails(context)
    }

    private fun vivoIntent(context: Context): Intent? {
        return tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessiveSettingActivity"
                )
                putExtra("packagename", context.packageName)
            }, context
        ) ?: tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            }, context
        ) ?: fallbackAppDetails(context)
    }

    /**
     * Universal fallback: open Android's app details settings page.
     */
    private fun fallbackAppDetails(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    private fun tryIntent(intent: Intent, context: Context): Intent? {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.resolveActivity(context.packageManager) ?: return null
            intent
        } catch (_: Exception) {
            null
        }
    }
}
