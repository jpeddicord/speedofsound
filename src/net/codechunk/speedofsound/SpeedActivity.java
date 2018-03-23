package net.codechunk.speedofsound;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.google.android.gms.common.GooglePlayServicesUtil;

import net.codechunk.speedofsound.service.SoundService;
import net.codechunk.speedofsound.util.SpeedConversions;

/**
 * Main status activity. Displays the current speed and set volume. Does not
 * actually track the volume itself; that is handled in SoundService.
 */
public class SpeedActivity extends AppCompatActivity implements View.OnClickListener {
	private static final String TAG = "SpeedActivity";

	private enum UIState {
		INACTIVE, ACTIVE, TRACKING
	}

	private UIState uiState;

	/**
	 * Disclaimer dialog unique ID.
	 */
	private static final int DIALOG_DISCLAIMER = 1;

	/**
	 * GPS nag dialog unique ID.
	 */
	private static final int DIALOG_GPS = 2;

	/**
	 * Location permission identifier.
	 */
	private static final int LOCATION_PERMISSION_REQUEST = 3;

	/**
	 * Application's shared preferences.
	 */
	private SharedPreferences settings;

	/**
	 * The main "Enable Speed of Sound" checkbox.
	 */
	private CheckBox enabledCheckBox;

	/**
	 * Whether we're bound to the background service or not. If everything is
	 * working, this should be true.
	 */
	private boolean bound = false;

	/**
	 * The background service.
	 */
	private SoundService service;

	/**
	 * Load the view and attach a checkbox listener.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.main);

		// hook up the checkbox
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.enabledCheckBox = (CheckBox) findViewById(R.id.checkbox_enabled);
		this.enabledCheckBox.setOnClickListener(this);
		this.uiState = UIState.INACTIVE;

		// show disclaimer and/or GPS nag
		this.startupMessages();
	}

	/**
	 * Start the service with the view, if it wasn't already running.
	 */
	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "View starting");

		// bind to our service after explicitly starting it
		Intent intent = new Intent(this, SoundService.class);
		startService(intent);
		bindService(intent, this.serviceConnection, 0);
	}

	/**
	 * Stop the view and unbind from the service (but don't stop that).
	 */
	@Override
	public void onStop() {
		super.onStop();
		Log.d(TAG, "View stopping");

		// unbind from our service
		if (this.bound) {
			unbindService(this.serviceConnection);
			this.bound = false;
		}
	}

	/**
	 * Pause the view and stop listening to broadcasts.
	 */
	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "Paused, unsubscribing from updates");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(this.messageReceiver);
	}

	/**
	 * Resume the view and subscribe to broadcasts.
	 */
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "Resumed, subscribing to service updates");

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(SoundService.LOCATION_UPDATE_BROADCAST);
		filter.addAction(SoundService.TRACKING_STATE_BROADCAST);
		lbm.registerReceiver(this.messageReceiver, filter);
	}

	/**
	 * Create disclaimer/gps dialogs to show.
	 */
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (id) {
			case DIALOG_DISCLAIMER:
				builder.setMessage(getString(R.string.launch_disclaimer))
						.setTitle(getString(R.string.warning))
						.setCancelable(false)
						.setPositiveButton(getString(R.string.launch_disclaimer_accept),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										SpeedActivity.this.checkGPS();
									}
								});
				dialog = builder.create();
				break;

			case DIALOG_GPS:
				builder.setMessage(getString(R.string.gps_warning))
						.setCancelable(false)
						.setPositiveButton(getString(R.string.location_settings),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
									}
								})
						.setNegativeButton(getString(R.string.gps_warning_dismiss), null);
				dialog = builder.create();
				break;

			default:
				dialog = null;
		}
		return dialog;
	}

	/**
	 * Show some startup messages. Will show the disclaimer dialog if this is
	 * the first run. If GPS is disabled, will show another dialog to ask to
	 * enable it.
	 */
	@SuppressWarnings("deprecation")
	private void startupMessages() {
		// only show disclaimers and the like once
		boolean runonce = this.settings.getBoolean("runonce", false);

		// firstrun things
		if (!runonce) {
			SharedPreferences.Editor editor = this.settings.edit();
			editor.putBoolean("runonce", true);
			editor.apply();

			// driving disclaimer (followed by GPS)
			this.showDialog(DIALOG_DISCLAIMER);
		} else {
			// GPS notice
			this.checkGPS();
		}
	}

	/**
	 * Check if GPS is enabled. If it isn't, bug the user to turn it on.
	 */
	@SuppressWarnings("deprecation")
	private void checkGPS() {
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			this.showDialog(DIALOG_GPS);
		}
	}

	/**
	 * Start or stop tracking in the service on checked/unchecked.
	 */
	@Override
	public void onClick(View view) {
		boolean isChecked = ((CheckBox) view).isChecked();
		Log.d(TAG, "Checkbox changed to " + isChecked);

		// uh-oh
		if (!this.bound) {
			Log.e(TAG, "Service is unavailable");
			return;
		}

		// go get 'em buddy
		int hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (isChecked && hasPermission != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
					LOCATION_PERMISSION_REQUEST);
			return;
		}

		this.toggleTracking();
	}

	@Override
	public void onRequestPermissionsResult(int code, String permissions[], int[] results) {
		CheckBox enabled = (CheckBox) findViewById(R.id.checkbox_enabled);

		switch (code) {
			case LOCATION_PERMISSION_REQUEST:
				if (results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED) {
					SoundService.showNeedLocationToast(this);
					enabled.setChecked(false);
				} else {
					this.toggleTracking();
				}
		}
	}

	/**
	 * Start or stop the service depending on the checkbox state.
	 */
	private void toggleTracking() {
		boolean isChecked = ((CheckBox) findViewById(R.id.checkbox_enabled)).isChecked();

		// start up the service
		if (isChecked) {
			this.service.startTracking();

			// update the UI
			this.updateStatusState(UIState.ACTIVE);

			// reset speed/volume to waiting state.
			// we don't do this in updateStatusState as that would happen too
			// frequently.
			TextView speed = (TextView) findViewById(R.id.speed_value);
			TextView volume = (TextView) findViewById(R.id.volume_value);
			speed.setText(getString(R.string.waiting));
			volume.setText(getString(R.string.waiting));
		}
		// stop the service
		else {
			this.service.stopTracking();

			// update the UI
			this.updateStatusState(UIState.INACTIVE);
		}
	}

	/**
	 * Switch between the intro message and the speed details.
	 */
	private void updateStatusState(UIState state) {
		View trackingDetails = findViewById(R.id.tracking_details);
		trackingDetails.setVisibility(state == UIState.TRACKING ? View.VISIBLE : View.GONE);

		View waitingForGps = findViewById(R.id.waiting_for_gps);
		waitingForGps.setVisibility(state == UIState.ACTIVE ? View.VISIBLE : View.GONE);

		View inactiveIntro = findViewById(R.id.inactive_intro);
		inactiveIntro.setVisibility(state == UIState.INACTIVE ? View.VISIBLE : View.GONE);
	}

	/**
	 * Handle incoming broadcasts from the service.
	 */
	private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		/**
		 * Receive a speed/sound status update.
		 */
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.v(TAG, "Received broadcast " + action);

			if (SoundService.TRACKING_STATE_BROADCAST.equals(action)) {
				boolean tracking = intent.getBooleanExtra("tracking", false);
				SpeedActivity.this.enabledCheckBox.setChecked(tracking);
				SpeedActivity.this.updateStatusState(tracking ? UIState.ACTIVE : UIState.INACTIVE);
			}

			// new location data
			else if (SoundService.LOCATION_UPDATE_BROADCAST.equals(action)) {
				// unpack the speed/volume
				float speed = intent.getFloatExtra("speed", -1.0f);
				int volume = intent.getIntExtra("volumePercent", -1);

				// convert the speed to the appropriate units
				String units = SpeedActivity.this.settings.getString("speed_units", "");
				float localizedSpeed = SpeedConversions.localizedSpeed(units, speed);

				SpeedActivity.this.updateStatusState(UIState.TRACKING);

				// display the speed
				TextView speedView = (TextView) findViewById(R.id.speed_value);
				speedView.setText(String.format("%.1f %s", localizedSpeed, units));

				// display the volume as well
				TextView volumeView = (TextView) findViewById(R.id.volume_value);
				volumeView.setText(String.format("%d%%", volume));

				// ui goodies
				TextView volumeDesc = (TextView) findViewById(R.id.volume_description);
				int lowVolume = SpeedActivity.this.settings.getInt("low_volume", 0);
				int highVolume = SpeedActivity.this.settings.getInt("high_volume", 100);

				// show different text values depending on the limits hit
				if (volume <= lowVolume) {
					volumeDesc.setText(getString(R.string.volume_header_low));
				} else if (volume >= highVolume) {
					volumeDesc.setText(getText(R.string.volume_header_high));
				} else {
					volumeDesc.setText(getText(R.string.volume_header_scaled));
				}
			}
		}
	};

	/**
	 * Attach to the sound service.
	 */
	private ServiceConnection serviceConnection = new ServiceConnection() {
		/**
		 * Trigger service and UI actions once we have a connection.
		 */
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.v(TAG, "ServiceConnection connected");

			SoundService.LocalBinder binder = (SoundService.LocalBinder) service;
			SpeedActivity.this.service = binder.getService();
			SpeedActivity.this.bound = true;

			// update the enabled check box
			SpeedActivity.this.enabledCheckBox.setChecked(SpeedActivity.this.service.isTracking());
			SpeedActivity.this.updateStatusState(SpeedActivity.this.service.isTracking() ? UIState.ACTIVE : UIState.INACTIVE);

			// start tracking if preference set
			if (SpeedActivity.this.settings.getBoolean("enable_on_launch", false)) {
				SpeedActivity.this.service.startTracking();
			}
		}

		/**
		 * Mark the service as unbound on disconnect.
		 */
		public void onServiceDisconnected(ComponentName arg) {
			Log.v(TAG, "ServiceConnection disconnected");
			SpeedActivity.this.bound = false;
		}
	};

	/**
	 * Show a menu on menu button press. Where supported, show an action item
	 * instead.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.preferences), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

		return true;
	}

	/**
	 * Handle actions from the menu/action bar.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				break;

		}
		return true;
	}
}
