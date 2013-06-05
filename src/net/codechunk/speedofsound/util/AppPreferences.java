package net.codechunk.speedofsound.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;

import net.codechunk.speedofsound.R;

import java.io.File;

public class AppPreferences {
	private static final String TAG = "AppPreferences";

	/**
	 * Set defaults for preferences if not already stored. Needed to supplement
	 * missing functionality in setDefaultValues: it doesn't properly handle
	 * custom preferences.
	 *
	 * @param context Application context
	 */
	public static void setDefaults(Context context) {
		PreferenceManager.setDefaultValues(context, R.xml.preferences, false);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();

		// if you change these, be sure to update the defaults in
		// preferences.xml
		if (!prefs.contains("low_speed_localized"))
			editor.putInt("low_speed_localized", 10);
		if (!prefs.contains("low_volume"))
			editor.putInt("low_volume", 60);
		if (!prefs.contains("high_speed_localized"))
			editor.putInt("high_speed_localized", 25);
		if (!prefs.contains("high_volume"))
			editor.putInt("high_volume", 90);

		editor.commit();
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
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt("app_version_code", versionCode);
			editor.commit();
		} else {
			int prevVersion = prefs.getInt("app_version_code", 0);

			processUpgrade(context, prevVersion, versionCode);
		}

		// safe upgrade hack: delete old locations db
		// this was in use before we stored version info
		// XXX: this is safe to remove after a few versions.
		// check the google play listing for % on v7 and up.
		File oldDatabase = context.getDatabasePath("locations");
		if (oldDatabase.exists()) {
			context.deleteDatabase("locations");
		}
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
		editor.commit();
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
		editor.commit();
	}

}
