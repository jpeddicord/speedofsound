package edu.osu.speedofsound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

public class Speed extends Activity {

	LocationManager locationManager;
	LocationUpdater locationUpdater;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
	
	@Override
	public void onPause() {
		super.onPause();
		this.locationManager.removeUpdates(this.locationUpdater);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		this.startListening();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			Intent intent = new Intent(this, Preferences.class);
			startActivity(intent);
			break;
			
		}
		return true;
	}

	private class LocationUpdater implements LocationListener {
		public void onLocationChanged(Location location) {
			TextView speedView = (TextView) findViewById(R.id.speed);
			speedView.setText(String.valueOf(location.getSpeed()));
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	}
}
