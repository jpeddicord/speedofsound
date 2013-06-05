package net.codechunk.speedofsound;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import net.codechunk.speedofsound.util.AppPreferences;


/**
 * Speed and volume preferences screen.
 */
public class PreferencesActivity extends PreferenceActivity {
	/**
	 * Logging tag.
	 */
	private static final String TAG = "PreferencesActivity";

	/**
	 * Load preferences and prepare conversions.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// activate the up functionality on the action bar
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			ActionBar ab = this.getActionBar();
			if (ab != null) {
				ab.setHomeButtonEnabled(true);
				ab.setDisplayHomeAsUpEnabled(true);
			}
		}

		// sadly, the newer fragment preference API is
		// not yet in the support library.
		addPreferencesFromResource(R.xml.preferences);

		// register change listener
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(new AppPreferences());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.prefs_menu, menu);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.about), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	/**
	 * Handle the home button press on the action bar.
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				Intent intent = new Intent(this, SpeedActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				break;
			case R.id.about:
				startActivity(new Intent(this, AboutActivity.class));
				break;
		}
		return true;
	}
}
