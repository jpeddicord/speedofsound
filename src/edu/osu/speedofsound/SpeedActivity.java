package edu.osu.speedofsound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class SpeedActivity extends Activity {

	private static final String TAG = "SpeedActivity";
	private SharedPreferences settings;
	private AudioManager audioManager;
	private int maxVolume;
	private LocationUpdater locationUpdater;
	private LocationManager locationManager;
	private AverageSpeed averager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		this.maxVolume = this.audioManager
				.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.locationUpdater = new LocationUpdater();
		this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		this.averager = new AverageSpeed(6);

		CheckBox enabledCheckBox = (CheckBox) findViewById(R.id.checkbox_enabled);
		enabledCheckBox.setOnCheckedChangeListener(
			new OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					Intent intent = new Intent(SpeedActivity.this, SoundService.class);
					if (isChecked) {
						startService(intent);
					} else {
						stopService(intent);
					}

				}
		
			}
		);

		this.startListening();
	}

	private void startListening() {
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setSpeedRequired(true);
		String provider = this.locationManager.getBestProvider(criteria, true);
		this.locationManager.requestLocationUpdates(provider, 0, 0,
				this.locationUpdater);
	}

	private float convertMPH(float metersPerSecond) {
		return (float) (2.237 * metersPerSecond);
	}

	private int updateVolume(float mphSpeed) {
		/*
		 * Instead of changing the volume directly, we may want to update a
		 * "target volume" and have something else process that to slowly
		 * approach it.
		 */
		float volume = 0.0f;

		int lowSpeed = this.settings.getInt("low_speed", 15);
		int lowVolume = this.settings.getInt("low_volume", 60);
		int highSpeed = this.settings.getInt("high_speed", 50);
		int highVolume = this.settings.getInt("high_volume", 100);

		if (mphSpeed < lowSpeed) {
			// minimum volume
			Log.d(TAG, "Low speed triggered");
			volume = lowVolume / 100.0f;

		} else if (mphSpeed > highSpeed) {
			// high volume
			Log.d(TAG, "High speed triggered");
			volume = highVolume / 100.0f;

		} else {
			// log scaling
			float volumeRange = (highVolume - lowVolume) / 100.0f;
			float speedRangeFrac = (mphSpeed - lowSpeed)
					/ (highSpeed - lowSpeed);
			float volumeRangeFrac = (float) (Math.log1p(speedRangeFrac) / Math
					.log1p(1));
			volume = lowVolume / 100.0f + volumeRange * volumeRangeFrac;
			Log.d(TAG, "Log scale triggered, using volume " + volume);
		}

		// apply the volume
		this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
				(int) (this.maxVolume * volume), 0);
		return (int) (volume * 100);
	}

	private void updateUI(float mphSpeed, int volume) {
		TextView speedView = (TextView) findViewById(R.id.speed);
		speedView.setText(String.format("%.1f mph", mphSpeed));

		TextView volumeView = (TextView) findViewById(R.id.volume);
		volumeView.setText(String.format("%d%%", volume));
	}

	@Override
	public void onPause() {
		super.onPause();
		this.locationManager.removeUpdates(this.locationUpdater);

		Log.d(TAG, "Paused, removing location updates");
	}

	@Override
	public void onResume() {
		super.onResume();
		this.startListening();

		Log.d(TAG, "Resumed, subscribing to location updates");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.preferences), 1);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, PreferencesActivity.class);
			startActivity(intent);
			break;
		}
		return true;
	}

	private class LocationUpdater implements LocationListener {

		private Location previousLocation = null;

		public void onLocationChanged(Location location) {
			// grab the speed
			float speed;

			// use the GPS-provided speed if available
			if (location.hasSpeed()) {
				speed = location.getSpeed();

			} else {
				Log.v(TAG, "Location fallback mode");

				// speed fall-back (mostly for the emulator)
				if (this.previousLocation != null) {

					// get the distance between this and the previous update
					float meters = previousLocation.distanceTo(location);
					float timeDelta = location.getTime()
							- previousLocation.getTime();

					Log.v(TAG, "Location distance: " + meters);

					// convert to meters/second
					speed = 1000 * meters / timeDelta;

				} else {
					speed = 0;
				}

				this.previousLocation = location;
			}
			float mph = SpeedActivity.this.convertMPH(speed);

			// push average to filter out spikes
			Log.v(TAG, "Pushing speed " + mph);
			SpeedActivity.this.averager.push(mph);

			// update the speed
			Log.v(TAG, "Getting average speed");
			float avg = SpeedActivity.this.averager.getAverage();
			Log.v(TAG, "Average currently " + avg);
			int volume = SpeedActivity.this.updateVolume(avg);
			SpeedActivity.this.updateUI(mph, volume);
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	}
}
