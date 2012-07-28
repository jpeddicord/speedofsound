package net.codechunk.speedofsound;

import net.codechunk.speedofsound.util.SpeedConversions;
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
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Main status activity. Displays the current speed and set volume. Does not
 * actually track the volume itself; that is handled in SoundService.
 */
public class SpeedActivity extends SherlockActivity implements OnCheckedChangeListener
{
	/**
	 * Logging tag.
	 */
	private static final String TAG = "SpeedActivity";

	/**
	 * Disclaimer dialog unique ID.
	 */
	private static final int DIALOG_DISCLAIMER = 1;

	/**
	 * GPS nag dialog unique ID.
	 */
	private static final int DIALOG_GPS = 2;

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
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// hook up the checkbox
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.enabledCheckBox = (CheckBox) findViewById(R.id.checkbox_enabled);
		this.enabledCheckBox.setOnCheckedChangeListener(this);

		// show disclaimer and/or GPS nag
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

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
		lbm.registerReceiver(this.messageReceiver, new IntentFilter(SoundService.LOCATION_UPDATE_BROADCAST));
		lbm.registerReceiver(this.messageReceiver, new IntentFilter(SoundService.TRACKING_STATE_BROADCAST));
	}

	/**
	 * Create disclaimer/gps dialogs to show.
	 */
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

	/**
	 * Show some startup messages. Will show the disclaimer dialog if this is
	 * the first run. If GPS is disabled, will show another dialog to ask to
	 * enable it.
	 */
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

	/**
	 * Check if GPS is enabled. If it isn't, bug the user to turn it on.
	 */
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

		// uh-oh
		if (!this.bound)
		{
			Log.e(TAG, "Service is unavailable");
			return;
		}

		// start up the service
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
		// stop the service
		else
		{
			this.service.stopTracking();

			// update the UI
			this.updateStatusState(false);
		}
	}

	/**
	 * Switch between the intro message and the speed details.
	 * 
	 * @param tracking
	 *            The current tracking state
	 */
	private void updateStatusState(boolean tracking)
	{
		View statusDetails = findViewById(R.id.status_details);
		statusDetails.setVisibility(tracking ? View.VISIBLE : View.GONE);

		View disabledMessage = findViewById(R.id.disabled_message);
		disabledMessage.setVisibility(tracking ? View.GONE : View.VISIBLE);
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
			String action = intent.getAction();
			Log.v(TAG, "Received broadcast " + action);

			if (action.equals(SoundService.TRACKING_STATE_BROADCAST))
			{
				boolean state = intent.getBooleanExtra("tracking", false);
				SpeedActivity.this.enabledCheckBox.setChecked(state);
				SpeedActivity.this.updateStatusState(state);
			}

			// new location data
			else if (action.equals(SoundService.LOCATION_UPDATE_BROADCAST))
			{
				// unpack the speed/volume
				float speed = intent.getFloatExtra("speed", -1.0f);
				int volume = intent.getIntExtra("volume", -1);

				// convert the speed to the appropriate units
				String units = SpeedActivity.this.settings.getString("speed_units", "");
				float localizedSpeed = SpeedConversions.localizedSpeed(units, speed);

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
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		menu.findItem(R.id.preferences).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.findItem(R.id.view_map).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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
}
