package net.codechunk.speedofsound.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager.NameNotFoundException
import android.preference.PreferenceManager
import android.util.Log

class AppPreferences : SharedPreferences.OnSharedPreferenceChangeListener {

    // TODO: some constants for preference keys

    /**
     * Convert stored preferences when the speed units change.
     */
    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        Log.v(TAG, "Preferences $key")

        if (key == "low_speed_localized" || key == "high_speed_localized") {
            // update the internal native speeds
            updateNativeSpeeds(prefs)
        } else if (key == "speed_units") {
            // convert localized speeds from their internal values on unit change
            updateLocalizedSpeeds(prefs)
        }
    }

    companion object {
        private const val TAG = "AppPreferences"

        /**
         * Run an upgrade from one app version to another.
         *
         * Stores the current version as a shared preference so we can detect future
         * upgrades and act on them.
         *
         * @param context Application context
         */
        fun runUpgrade(context: Context) {
            Log.d(TAG, "Running upgrade check")

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            // get the current version
            val versionCode: Int
            try {
                val packageInfo = context.packageManager.getPackageInfo("net.codechunk.speedofsound", 0)
                versionCode = packageInfo.versionCode
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Upgrade failed; name not found")
                return
            }

            // first-time install has no upgrade
            if (!prefs.contains("app_version_code")) {
                Log.v(TAG, "First-time install; no upgrade required")
            } else {
                val prevVersion = prefs.getInt("app_version_code", 0)

                processUpgrade(context, prevVersion, versionCode)
            }

            // mark the currently-installed version
            val editor = prefs.edit()
            editor.putInt("app_version_code", versionCode)
            editor.apply()
        }

        /**
         * Process the upgrade, one version at a time.
         *
         * @param context Application context
         * @param from    Version upgrading from
         * @param to      Version upgrading to
         */
        private fun processUpgrade(context: Context, from: Int, to: Int) {
            // no action to take if versions are equal
            if (from == to)
                return

            val processing = from + 1

            // // upgrading TO version 7
            // if (processing == 7)
            // {
            // do something with preferences
            // }

            // process the next upgrade
            processUpgrade(context, processing, to)
        }

        /**
         * Update internal speeds in native units. Calculated from user-facing
         * localized speeds.
         *
         * @param prefs Shared Preferences to update.
         */
        fun updateNativeSpeeds(prefs: SharedPreferences) {
            Log.v(TAG, "Converting preferences to m/s internally")

            // get the stored units
            val units = prefs.getString("speed_units", "")!!

            // convert speed units into m/s for low-level stuff
            val editor = prefs.edit()
            val low_speed_localized = prefs.getInt("low_speed_localized", 0)
            val high_speed_localized = prefs.getInt("high_speed_localized", 0)
            editor.putFloat("low_speed", SpeedConversions.nativeSpeed(units, low_speed_localized.toFloat()))
            editor.putFloat("high_speed", SpeedConversions.nativeSpeed(units, high_speed_localized.toFloat()))
            editor.apply()
        }

        /**
         * Update user-facing localized speeds from internal values. Useful if
         * changing between units.
         *
         * @param prefs Shared Preferences to update.
         */
        fun updateLocalizedSpeeds(prefs: SharedPreferences) {
            Log.v(TAG, "Converting native speeds to localized values")

            // get the stored units
            val units = prefs.getString("speed_units", "")!!

            // convert speed units into m/s for low-level stuff
            val editor = prefs.edit()
            val low_speed = prefs.getFloat("low_speed", 0f)
            val high_speed = prefs.getFloat("high_speed", 0f)
            editor.putInt("low_speed_localized", SpeedConversions.localizedSpeed(units, low_speed).toInt())
            editor.putInt("high_speed_localized", SpeedConversions.localizedSpeed(units, high_speed).toInt())
            editor.apply()
        }
    }

}
