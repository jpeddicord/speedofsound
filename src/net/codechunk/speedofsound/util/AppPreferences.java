package net.codechunk.speedofsound.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;

public class AppPreferences implements SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String TAG = "AppPreferences";

	// TODO: some constants for preference keys

	/**
	 * Convert stored preferences when the speed units change.
	 */
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		Log.v(TAG, "Preferences " + key);

		if (key.equals("low_speed_localized") || key.equals("high_speed_localized")) {
			// update the internal native speeds
			AppPreferences.updateNativeSpeeds(prefs);
		} else if (key.equals("speed_units")) {
			// convert localized speeds from their internal values on unit change
			AppPreferences.updateLocalizedSpeeds(prefs);
		}
	}

	/**
	 * Run an upgrade from one app version to another.
	 *
	 * Stores the current version as a shared preference so we can detect future
	 * upgrades and act on them.
	 *
	 * @param context Application context
	 */
	public static void runUpgrade(Context context) {
		Log.d(TAG, "Running upgrade check");

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// get the current version
		int versionCode;
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo("net.codechunk.speedofsound", 0);
			versionCode = packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Upgrade failed; name not found");
			return;
		}

		// first-time install has no upgrade
		if (!prefs.contains("app_version_code")) {
			Log.v(TAG, "First-time install; no upgrade required");
		} else {
			int prevVersion = prefs.getInt("app_version_code", 0);

			processUpgrade(context, prevVersion, versionCode);
		}

		// mark the currently-installed version
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("app_version_code", versionCode);
		editor.apply();
	}

	/**
	 * Process the upgrade, one version at a time.
	 *
	 * @param context Application context
	 * @param from    Version upgrading from
	 * @param to      Version upgrading to
	 */
	private static void processUpgrade(Context context, int from, int to) {
		// no action to take if versions are equal
		if (from == to)
			return;

		int processing = from + 1;

		// // upgrading TO version 7
		// if (processing == 7)
		// {
		// do something with preferences
		// }

		// process the next upgrade
		AppPreferences.processUpgrade(context, processing, to);
	}

	/**
	 * Update internal speeds in native units. Calculated from user-facing
	 * localized speeds.
	 *
	 * @param prefs Shared Preferences to update.
	 */
	public static void updateNativeSpeeds(SharedPreferences prefs) {
		Log.v(TAG, "Converting preferences to m/s internally");

		// get the stored units
		String units = prefs.getString("speed_units", "");

		// convert speed units into m/s for low-level stuff
		SharedPreferences.Editor editor = prefs.edit();
		int low_speed_localized = prefs.getInt("low_speed_localized", 0);
		int high_speed_localized = prefs.getInt("high_speed_localized", 0);
		editor.putFloat("low_speed", SpeedConversions.nativeSpeed(units, low_speed_localized));
		editor.putFloat("high_speed", SpeedConversions.nativeSpeed(units, high_speed_localized));
		editor.apply();
	}

	/**
	 * Update user-facing localized speeds from internal values. Useful if
	 * changing between units.
	 *
	 * @param prefs Shared Preferences to update.
	 */
	public static void updateLocalizedSpeeds(SharedPreferences prefs) {
		Log.v(TAG, "Converting native speeds to localized values");

		// get the stored units
		String units = prefs.getString("speed_units", "");

		// convert speed units into m/s for low-level stuff
		SharedPreferences.Editor editor = prefs.edit();
		float low_speed = prefs.getFloat("low_speed", 0);
		float high_speed = prefs.getFloat("high_speed", 0);
		editor.putInt("low_speed_localized", (int) SpeedConversions.localizedSpeed(units, low_speed));
		editor.putInt("high_speed_localized", (int) SpeedConversions.localizedSpeed(units, high_speed));
		editor.apply();
	}

}
