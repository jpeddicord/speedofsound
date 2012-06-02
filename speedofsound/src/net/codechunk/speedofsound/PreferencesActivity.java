package net.codechunk.speedofsound;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

/**
 * Speed and volume preferences screen.
 */
public class PreferencesActivity extends SherlockPreferenceActivity implements OnSharedPreferenceChangeListener
{
	/**
	 * Logging tag.
	 */
	private static final String TAG = "PreferencesActivity";

	/**
	 * Load preferences and prepare conversions.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// activate the up functionality on the action bar
		ActionBar ab = this.getSupportActionBar();
		ab.setHomeButtonEnabled(true);
		ab.setDisplayHomeAsUpEnabled(true);

		// sadly, the newer fragment preference API is
		// not yet in the support library.
		addPreferencesFromResource(R.xml.preferences);

		// register change listener
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	/**
	 * Convert stored preferences when the speed units change.
	 */
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
	{
		Log.v(TAG, "Preferences " + key);

		if (key.equals("low_speed_localized") || key.equals("high_speed_localized"))
		{
			// update the internal native speeds
			PreferencesActivity.updateNativeSpeeds(prefs);
		}
		else if (key.equals("speed_units"))
		{
			// convert localized speeds from their internal values on unit
			// change
			PreferencesActivity.updateLocalizedSpeeds(prefs);
		}

	}

	/**
	 * Set defaults for preferences if not already stored. Needed to supplement
	 * missing functionality in setDefaultValues: it doesn't properly handle
	 * custom preferences.
	 * 
	 * @param context
	 *            Application context
	 */
	public static void setDefaults(Context context)
	{
		PreferenceManager.setDefaultValues(context, R.xml.preferences, false);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();

		// if you change these, be sure to update the defaults in
		// preferences.xml
		if (!prefs.contains("low_speed_localized"))
			editor.putInt("low_speed_localized", 15);
		if (!prefs.contains("low_volume"))
			editor.putInt("low_volume", 60);
		if (!prefs.contains("high_speed_localized"))
			editor.putInt("high_speed_localized", 40);
		if (!prefs.contains("high_volume"))
			editor.putInt("high_volume", 95);

		editor.commit();
	}

	/**
	 * Convert a speed into meters per second.
	 * 
	 * @param local_units
	 *            Units to convert from
	 * @param localizedSpeed
	 *            Speed to convert
	 * @return Converted speed in m/s
	 */
	public static float nativeSpeed(String local_units, float localizedSpeed)
	{
		if (local_units.equals("m/s"))
		{
			return localizedSpeed;
		}
		else if (local_units.equals("km/h"))
		{
			return localizedSpeed * 0.27778f;
		}
		else if (local_units.equals("mph"))
		{
			return localizedSpeed * 0.44704f;
		}
		else
		{
			Log.w(TAG, "Not an appropriate unit: " + local_units);
			return -1;
		}
	}

	/**
	 * Convert a speed into a localized unit from m/s.
	 * 
	 * @param local_units
	 *            Unit to convert to.
	 * @param nativeSpeed
	 *            Speed in m/s converting from.
	 * @return Localized speed.
	 */
	public static float localizedSpeed(String local_units, float nativeSpeed)
	{
		if (local_units.equals("m/s"))
		{
			return nativeSpeed;
		}
		else if (local_units.equals("km/h"))
		{
			return nativeSpeed * 3.6f;
		}
		else if (local_units.equals("mph"))
		{
			return nativeSpeed * 2.23693f;
		}
		else
		{
			Log.w(TAG, "Not an appropriate unit: " + local_units);
			return -1;
		}
	}

	/**
	 * Update internal speeds in native units. Calculated from user-facing
	 * localized speeds.
	 * 
	 * @param prefs
	 *            Shared Preferences to update.
	 */
	public static void updateNativeSpeeds(SharedPreferences prefs)
	{
		Log.v(TAG, "Converting preferences to m/s internally");

		// get the stored units
		String units = prefs.getString("speed_units", "");

		// convert speed units into m/s for low-level stuff
		SharedPreferences.Editor editor = prefs.edit();
		int low_speed_localized = prefs.getInt("low_speed_localized", 0);
		int high_speed_localized = prefs.getInt("high_speed_localized", 0);
		editor.putFloat("low_speed", PreferencesActivity.nativeSpeed(units, low_speed_localized));
		editor.putFloat("high_speed", PreferencesActivity.nativeSpeed(units, high_speed_localized));
		editor.commit();
	}

	/**
	 * Update user-facing localized speeds from internal values. Useful if
	 * changing between units.
	 * 
	 * @param prefs
	 *            Shared Preferences to update.
	 */
	private static void updateLocalizedSpeeds(SharedPreferences prefs)
	{
		Log.v(TAG, "Converting native speeds to localized values");

		// get the stored units
		String units = prefs.getString("speed_units", "");

		// convert speed units into m/s for low-level stuff
		SharedPreferences.Editor editor = prefs.edit();
		float low_speed = prefs.getFloat("low_speed", 0);
		float high_speed = prefs.getFloat("high_speed", 0);
		editor.putInt("low_speed_localized", (int) PreferencesActivity.localizedSpeed(units, low_speed));
		editor.putInt("high_speed_localized", (int) PreferencesActivity.localizedSpeed(units, high_speed));
		editor.commit();
	}

	/**
	 * Handle the home button press on the action bar.
	 */
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				Intent intent = new Intent(this, SpeedActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				break;
		}
		return true;
	}
}
