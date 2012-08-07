package net.codechunk.speedofsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Sound service activation manager. Used to start the service at boot;
 * referenced in the manifest.
 */
public class SoundServiceManager extends BroadcastReceiver
{
	private static final String TAG = "SoundServiceManager";

	private static final int UNDOCUMENTED_STATE_DISCONNECTED = 0;
	private static final int UNDOCUMENTED_STATE_CONNECTED = 2;
	private static final String UNDOCUMENTED_A2DP_ACTION = "android.bluetooth.a2dp.action.SINK_STATE_CHANGED";
	private static final String UNDOCUMENTED_A2DP_ACTION_ALTERNATE = "android.bluetooth.a2dp.intent.action.SINK_STATE_CHANGED";
	private static final String UNDOCUMENTED_A2DP_EXTRA_STATE = "android.bluetooth.a2dp.extra.SINK_STATE";

	/**
	 * Keep track of the bluetooth state here, as the undocumented broadcasts
	 * might not be sticky. Not sure if the official ones are either.
	 */
	private boolean bluetoothConnected = false;

	/**
	 * Get the filter of extra intents we care about.
	 */
	public IntentFilter activationIntents()
	{
		IntentFilter filter = new IntentFilter();

		// power events
		filter.addAction(Intent.ACTION_POWER_CONNECTED);
		filter.addAction(Intent.ACTION_POWER_DISCONNECTED);

		// headset plug/unplug events
		filter.addAction(Intent.ACTION_HEADSET_PLUG);

		// undocumented bluetooth API
		filter.addAction(SoundServiceManager.UNDOCUMENTED_A2DP_ACTION);
		filter.addAction(SoundServiceManager.UNDOCUMENTED_A2DP_ACTION_ALTERNATE);

		// documented API11+ bluetooth API
		filter.addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);

		return filter;
	}

	/**
	 * Receive a broadcast and start the service or update the tracking state.
	 */
	@Override
	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();
		Log.d(TAG, "Received intent " + action);

		// start the service on boot
		if (action.equals("android.intent.action.BOOT_COMPLETED"))
		{
			Intent startIntent = new Intent(context, SoundService.class);
			context.startService(startIntent);
			return;
		}

		// resume tracking if we're also in a satisfactory mode
		if (action.equals(Intent.ACTION_POWER_CONNECTED) ||
				action.equals(Intent.ACTION_POWER_DISCONNECTED) ||
				action.equals(Intent.ACTION_HEADSET_PLUG))
		{
			SoundServiceManager.setTracking(context, this.shouldTrack(context));
		}

		// these broadcasts are undocumented, but seem to work on a bunch of
		// android versions. used with caution.
		else if (action.equals(SoundServiceManager.UNDOCUMENTED_A2DP_ACTION) ||
				action.equals(SoundServiceManager.UNDOCUMENTED_A2DP_ACTION_ALTERNATE))
		{
			Log.d(TAG, "A2DP undocumented event");

			// set the bluetooth state
			int state = intent.getIntExtra(SoundServiceManager.UNDOCUMENTED_A2DP_EXTRA_STATE, -1);
			if (state == SoundServiceManager.UNDOCUMENTED_STATE_CONNECTED)
			{
				Log.v(TAG, "A2DP active");
				this.bluetoothConnected = true;
			}
			else if (state == SoundServiceManager.UNDOCUMENTED_STATE_DISCONNECTED)
			{
				Log.v(TAG, "A2DP inactive");
				this.bluetoothConnected = false;
			}

			// start or stop tracking
			SoundServiceManager.setTracking(context, this.shouldTrack(context));
		}

		// official API 11+ bluetooth A2DP broadcasts
		else if (action.equals(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
		{
			Log.d(TAG, "A2DP API11+ event");

			// set the bluetooth state
			int state = intent.getIntExtra(android.bluetooth.BluetoothA2dp.EXTRA_STATE, -1);
			if (state == android.bluetooth.BluetoothA2dp.STATE_CONNECTED)
			{
				Log.v(TAG, "A2DP active");
				this.bluetoothConnected = true;
			}
			else if (state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED)
			{
				Log.v(TAG, "A2DP inactive");
				this.bluetoothConnected = false;
			}

			// start or stop tracking
			SoundServiceManager.setTracking(context, this.shouldTrack(context));
		}
	}

	/**
	 * Determine whether we should be tracking.
	 * 
	 * @param context
	 *            Application context
	 * @return suggested state
	 */
	private boolean shouldTrack(Context context)
	{
		// load preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean powerPreference = prefs.getBoolean("enable_only_charging", false);
		boolean headphonePreference = prefs.getBoolean("enable_headphones", false);
		boolean bluetoothPreference = prefs.getBoolean("enable_bluetooth", false);

		// get power status
		IntentFilter plugFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent powerStatus = context.getApplicationContext().registerReceiver(null, plugFilter);
		int plugState = powerStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		boolean powerConnected = (plugState == BatteryManager.BATTERY_PLUGGED_AC ||
				plugState == BatteryManager.BATTERY_PLUGGED_USB);

		// don't track if power is disconnected and we care
		if (powerPreference && !powerConnected)
		{
			Log.v(TAG, "Power preference active & disconnected");
			return false;
		}

		// get headphone status
		IntentFilter headsetFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		Intent headphoneStatus = context.getApplicationContext().registerReceiver(null, headsetFilter);
		boolean headphoneConnected = headphoneStatus.getIntExtra("state", 0) == 1;

		// activate if headphones are plugged in
		if (headphonePreference && headphoneConnected)
		{
			Log.v(TAG, "Headphone connected");
			return true;
		}

		// also activate if bluetooth is connected
		if (bluetoothPreference && this.bluetoothConnected)
		{
			Log.v(TAG, "Bluetooth connected");
			return true;
		}

		// anything else is a no-go
		return false;
	}

	/**
	 * Set the tracking state by sending a service start command.
	 * 
	 * @param context
	 *            Application context.
	 * @param state
	 *            Turn tracking on or off.
	 */
	private static void setTracking(Context context, boolean state)
	{
		Log.d(TAG, "Setting tracking state: " + state);
		Intent serviceIntent = new Intent(context, SoundService.class);
		serviceIntent.putExtra(SoundService.SET_TRACKING_STATE, state);
		context.startService(serviceIntent);
	}

}
