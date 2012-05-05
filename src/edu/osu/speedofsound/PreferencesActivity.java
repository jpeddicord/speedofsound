package edu.osu.speedofsound;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity
{

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// sadly, the newer fragment preference API is
		// not yet in the support library.
		addPreferencesFromResource(R.xml.preferences);
	}

	/*
	 * TODO: assert volumes of proper ranges ensure high vol/speed is not less
	 * than low
	 */

}
