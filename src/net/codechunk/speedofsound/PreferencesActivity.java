package net.codechunk.speedofsound;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import net.codechunk.speedofsound.util.AppPreferences;


/**
 * Speed and volume preferences screen.
 */
public class PreferencesActivity extends PreferenceActivity {
	private static final String TAG = "PreferencesActivity";

	private SharedPreferences prefs;
	private AppPreferences listener = new AppPreferences();

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// sadly, the newer fragment preference API is
		// not yet in the support library.
		addPreferencesFromResource(R.xml.preferences);

		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

		registerAbout();
	}

	@Override
	public void onResume() {
		super.onResume();
		this.prefs.registerOnSharedPreferenceChangeListener(this.listener);
	}

	@Override
	public void onPause() {
		super.onPause();
		this.prefs.unregisterOnSharedPreferenceChangeListener(this.listener);
	}

	@SuppressWarnings("deprecation")
	private void registerAbout() {
		// get version number
		PackageInfo pi;
		try {
			pi = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
		} catch (PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Couldn't get package information?!");
			return;
		}

		Preference version = findPreference("about_version");
		version.setSummary(pi.versionName);
		version.setOnPreferenceClickListener(
			new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					try {
						Uri uri = Uri.parse("market://details?id=net.codechunk.speedofsound");
						PreferencesActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, uri));
					} catch (ActivityNotFoundException e) {
						Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=net.codechunk.speedofsound");
						PreferencesActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, uri));
					}
					return true;
				}
			}
		);

		Preference contact = findPreference("about_contact");
		contact.setOnPreferenceClickListener(
			new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Intent email = new Intent(Intent.ACTION_SEND);
					email.setType("plain/text");
					email.putExtra(Intent.EXTRA_EMAIL, new String[]{"mobile@octet.cc"});
					email.putExtra(Intent.EXTRA_SUBJECT, "Speed of Sound");
					PreferencesActivity.this.startActivity(email);
					return true;
				}
			}
		);

		Preference translate = findPreference("about_translate");
		translate.setOnPreferenceClickListener(
			new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					PreferencesActivity.this.startActivity(new Intent(
						Intent.ACTION_VIEW,
						Uri.parse("https://www.transifex.com/projects/p/speedofsound/")
					));
					return true;
				}
			}
		);

		Preference source = findPreference("about_source");
		source.setOnPreferenceClickListener(
			new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					PreferencesActivity.this.startActivity(new Intent(
						Intent.ACTION_VIEW,
						Uri.parse("https://github.com/jpeddicord/speedofsound")
					));
					return true;
				}
			}
		);
	}
}
