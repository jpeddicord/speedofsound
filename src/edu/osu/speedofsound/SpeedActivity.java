package edu.osu.speedofsound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

public class SpeedActivity extends Activity {

	LocationManager locationManager;
	LocationUpdater locationUpdater;
	SharedPreferences settings;
	static final String TAG = "SpeedActivity";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.settings = getPreferences(0);
		
		setContentView(R.layout.main);

		TextView speedView = (TextView) findViewById(R.id.speed);
		speedView.setText("waiting for GPS lock...");
		
		this.locationUpdater = new LocationUpdater();
		this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		this.startListening();
	}
	
	private void startListening() {
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setSpeedRequired(true);
		String provider = this.locationManager.getBestProvider(criteria, true);
		this.locationManager.requestLocationUpdates(provider, 0, 0, this.locationUpdater);
	}
	
	private float convertMPH(float metersPerSecond) {
		return (float) (2.237 * metersPerSecond);
	}
	
	private void updateVolume(float mphSpeed) {
		/* Instead of changing the volume directly,
		 * we may want to update a "target volume" and have something else
		 * process that to slowly approach it.
		 */
		
		// minimum volume
		if (mphSpeed < this.settings.getInt("low_speed", 0)) {
			// TODO: low volume
			Log.d(TAG, "Low speed triggered");
		} else if (mphSpeed > this.settings.getInt("high_speed", 0)) {
			// TODO: high volume
			Log.d(TAG, "High speed triggered");
		} else {
			// TODO: linear scaling
			
		}
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
			// speed fall-back (mostly for the emulator)
			} else {
				if (this.previousLocation != null) {
					previousLocation.distanceTo(location);
				}
				this.previousLocation = location;
				// TODO: speed fall-back
				speed = 0;
			}
			float mph = convertMPH(speed);
			
			// show the speed on the screen
			TextView speedView = (TextView) findViewById(R.id.speed);
			speedView.setText(String.valueOf(speed));
			
			// update the speed
			updateVolume(mph);
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	}
}
