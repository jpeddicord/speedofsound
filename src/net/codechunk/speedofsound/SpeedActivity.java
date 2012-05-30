package net.codechunk.speedofsound;

import android.app.Activity;
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
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

/**
 * Main status activity. Displays the current speed and set volume. Does not
 * actually track the volume itself; that is handled in SoundService.
 */
public class SpeedActivity extends Activity implements OnCheckedChangeListener, OnClickListener
{
	private static final String TAG = "SpeedActivity";

	private static final int DIALOG_DISCLAIMER = 1;
	private static final int DIALOG_GPS = 3;

	private SharedPreferences settings;
	private CheckBox enabledCheckBox;
	private boolean bound = false;
	private SoundService service;

	/**
	 * Load the view and attach a checkbox listener.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.enabledCheckBox = (CheckBox) findViewById(R.id.checkbox_enabled);
		this.enabledCheckBox.setOnCheckedChangeListener(this);

		View btnMap = findViewById(R.id.buttonMap);
		btnMap.setOnClickListener(this);

		this.startupMessages();
	}

	/**
	 * Start the service with the view, if it wasn't already running.
	 */
	@Override
	public void onStart()
	{
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
	public void onStop()
	{
		super.onStop();
		Log.d(TAG, "View stopping");

		// unbind from our service
		if (this.bound)
		{
			unbindService(this.serviceConnection);
			this.bound = false;
		}
	}

	/**
	 * Pause the view and stop listening to broadcasts.
	 */
	@Override
	public void onPause()
	{
		super.onPause();
		Log.d(TAG, "Paused, unsubscribing from updates");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(this.messageReceiver);
	}

	/**
	 * Resume the view and subscribe to broadcasts.
	 */
	@Override
	public void onResume()
	{
		super.onResume();
		Log.d(TAG, "Resumed, subscribing to service updates");

		LocalBroadcastManager.getInstance(this).registerReceiver(this.messageReceiver,
				new IntentFilter("speed-sound-changed"));
	}

	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (id)
		{
			case DIALOG_DISCLAIMER:
				builder.setMessage(getString(R.string.launch_disclaimer))
						.setTitle(getString(R.string.warning))
						.setCancelable(false)
						.setPositiveButton(getString(R.string.launch_disclaimer_accept),
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int id)
									{
										SpeedActivity.this.checkGPS();
									}
								});
				dialog = builder.create();
				break;

			case DIALOG_GPS:
				builder.setMessage(getString(R.string.gps_warning))
						.setCancelable(false)
						.setPositiveButton(getString(R.string.location_settings),
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog, int which)
									{
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

	@SuppressWarnings("deprecation")
	private void startupMessages()
	{
		// only show disclaimers and the like once
		boolean runonce = this.settings.getBoolean("runonce", false);

		// firstrun things
		if (!runonce)
		{
			SharedPreferences.Editor editor = this.settings.edit();
			editor.putBoolean("runonce", true);
			editor.commit();

			// driving disclaimer (followed by GPS)
			this.showDialog(DIALOG_DISCLAIMER);
		}
		else
		{
			// GPS notice
			this.checkGPS();
		}
	}

	@SuppressWarnings("deprecation")
	private void checkGPS()
	{
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			this.showDialog(DIALOG_GPS);
		}
	}

	/**
	 * Start or stop tracking in the service on checked/unchecked.
	 */
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
	{
		Log.d(TAG, "Checkbox changed to " + isChecked);

		if (!this.bound)
		{
			Log.e(TAG, "Service is unavailable");
			return;
		}

		if (isChecked)
		{
			this.service.startTracking();

			// update the UI
			this.updateStatusState(true);

			// reset speed/volume to waiting state.
			// we don't do this in updateStatusState as that would happen too
			// frequently.
			TextView speed = (TextView) findViewById(R.id.speed_value);
			TextView volume = (TextView) findViewById(R.id.volume_value);
			speed.setText(getString(R.string.waiting));
			volume.setText(getString(R.string.waiting));
		}
		else
		{
			this.service.stopTracking();

			// update the UI
			this.updateStatusState(false);
		}
	}

	private void updateStatusState(boolean tracking)
	{
		View statusDetails = findViewById(R.id.status_details);
		statusDetails.setVisibility(tracking ? View.VISIBLE : View.GONE);
	}

	/**
	 * Handle incoming broadcasts from the service.
	 */
	private BroadcastReceiver messageReceiver = new BroadcastReceiver()
	{
		/**
		 * Receive a speed/sound status update.
		 */
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.v(TAG, "Received broadcast");

			// unpack the speed/volume
			float speed = intent.getFloatExtra("speed", -1.0f);
			int volume = intent.getIntExtra("volume", -1);

			// convert the speed to the appropriate units
			String units = SpeedActivity.this.settings.getString("speed_units", "");
			float localizedSpeed = PreferencesActivity.localizedSpeed(units, speed);

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

			if (volume <= lowVolume)
			{
				volumeDesc.setText(getString(R.string.volume_header_low));
			}
			else if (volume >= highVolume)
			{
				volumeDesc.setText(getText(R.string.volume_header_high));
			}
			else
			{
				volumeDesc.setText(getText(R.string.volume_header_scaled));
			}
		}
	};

	/**
	 * Attach to the sound service.
	 */
	private ServiceConnection serviceConnection = new ServiceConnection()
	{
		/**
		 * Update the UI "Enabled" checkbox based on the tracking state.
		 */
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			Log.v(TAG, "ServiceConnection connected");

			SoundService.LocalBinder binder = (SoundService.LocalBinder) service;
			SpeedActivity.this.service = binder.getService();
			SpeedActivity.this.bound = true;

			// update the enabled check box
			SpeedActivity.this.enabledCheckBox.setChecked(SpeedActivity.this.service.tracking);
			SpeedActivity.this.updateStatusState(SpeedActivity.this.service.tracking);
		}

		/**
		 * Mark the service as unbound on disconnect.
		 */
		public void onServiceDisconnected(ComponentName arg)
		{
			Log.v(TAG, "ServiceConnection disconnected");
			SpeedActivity.this.bound = false;
		}
	};

	/**
	 * Show a menu on menu button press. Where supported, show an action item
	 * instead.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.preferences), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.view_map), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	/**
	 * Handle actions from the menu/action bar.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				break;
			case R.id.view_map:
				startActivity(new Intent(this, DrawMapActivity.class));
				break;
		}
		return true;
	}

	/**
	 * Handle button click actions.
	 */
	public void onClick(View v)
	{
		switch (v.getId())
		{
			case R.id.buttonMap:
				startActivity(new Intent(this, DrawMapActivity.class));
				break;
		}
	}

}
