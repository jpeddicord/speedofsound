package edu.osu.speedofsound;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Speed and volume preferences screen.
 */
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "PreferencesActivity";

	/**
	 * Load preferences.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// sadly, the newer fragment preference API is
		// not yet in the support library.
		addPreferencesFromResource(R.xml.preferences);

		// register change listener
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

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
			return -1;
		}
	}

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
			return -1;
		}
	}

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

}