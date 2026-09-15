package com.driverping.app.data

import android.content.Context
import com.driverping.app.util.Constants

/**
 * Repositorio de preferencias locales de la app mediante SharedPreferences.
 * Guarda si el onboarding ya se completó, el estado del Modo Trabajo, etc.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("driverping_settings", Context.MODE_PRIVATE)

    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun isWorkModeActive(): Boolean = prefs.getBoolean(KEY_WORK_MODE, false)

    fun setWorkModeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_WORK_MODE, active).apply()
    }

    fun getWhitelistPackages(): Set<String> {
        val saved = prefs.getStringSet(KEY_WHITELIST, null)
        return saved ?: Constants.WHITELIST.toSet()
    }

    fun addWhitelistPackage(pkg: String) {
        val current = getWhitelistPackages().toMutableSet()
        current.add(pkg)
        prefs.edit().putStringSet(KEY_WHITELIST, current).apply()
    }

    fun removeWhitelistPackage(pkg: String) {
        val current = getWhitelistPackages().toMutableSet()
        current.remove(pkg)
        prefs.edit().putStringSet(KEY_WHITELIST, current).apply()
    }

    fun resetWhitelistToDefault() {
        prefs.edit().remove(KEY_WHITELIST).apply()
    }

    fun getActiveSessionSeconds(): Long = prefs.getLong(KEY_ACTIVE_SECONDS, 0L)

    fun addActiveSessionSeconds(seconds: Long) {
        val total = getActiveSessionSeconds() + seconds
        prefs.edit().putLong(KEY_ACTIVE_SECONDS, total).apply()
    }

    fun getEstimatedSavedDataMb(): Int = prefs.getInt(KEY_SAVED_DATA_MB, 0)

    fun addEstimatedSavedDataMb(mb: Int) {
        val total = getEstimatedSavedDataMb() + mb
        prefs.edit().putInt(KEY_SAVED_DATA_MB, total).apply()
    }

    companion object {
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_WORK_MODE = "work_mode_active"
        private const val KEY_WHITELIST = "whitelist_packages"
        private const val KEY_ACTIVE_SECONDS = "active_session_seconds"
        private const val KEY_SAVED_DATA_MB = "estimated_saved_data_mb"
    }
}
