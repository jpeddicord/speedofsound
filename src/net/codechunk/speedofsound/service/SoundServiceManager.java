package net.codechunk.speedofsound.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Sound service activation manager. Used to start the service at boot;
 * referenced in the manifest.
 */
public class SoundServiceManager extends BroadcastReceiver {
	private static final String TAG = "SoundServiceManager";

	public static final String LOCALE_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
	public static final String LOCALE_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB";
	public static final String LOCALE_FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";

	/**
	 * Keep track of the bluetooth state here, as the undocumented broadcasts
	 * might not be sticky. Not sure if the official ones are either.
	 */
	private boolean bluetoothConnected = false;

	/**
	 * Get the filter of extra intents we care about.
	 */
	public IntentFilter activationIntents() {
		IntentFilter filter = new IntentFilter();

		// headset plug/unplug events (*mush* be registered dynamically)
		filter.addAction(Intent.ACTION_HEADSET_PLUG);

		// documented API11+ bluetooth API
		filter.addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);

		return filter;
	}

	/**
	 * Receive a broadcast and start the service or update the tracking state.
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		Log.d(TAG, "Received intent " + action);

		// start the service on boot
		if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
			Intent startIntent = new Intent(context, SoundService.class);
			context.startService(startIntent);
			return;
		}

		// resume tracking if we're also in a satisfactory mode
		if (action.equals(Intent.ACTION_POWER_CONNECTED) ||
				action.equals(Intent.ACTION_POWER_DISCONNECTED) ||
				action.equals(Intent.ACTION_HEADSET_PLUG)) {
			SoundServiceManager.setTracking(context, this.shouldTrack(context));
		}

		// official API 11+ bluetooth A2DP broadcasts
		else if (action.equals(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
			Log.d(TAG, "A2DP API11+ event");

			// set the bluetooth state
			int state = intent.getIntExtra(android.bluetooth.BluetoothA2dp.EXTRA_STATE, -1);
			if (state == android.bluetooth.BluetoothA2dp.STATE_CONNECTED) {
				Log.v(TAG, "A2DP active");
				this.bluetoothConnected = true;
			} else if (state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED) {
				Log.v(TAG, "A2DP inactive");
				this.bluetoothConnected = false;
			}

			// start or stop tracking
			SoundServiceManager.setTracking(context, this.shouldTrack(context));
		}

		// locale/tasker intents
		else {
			if (action.equals(SoundServiceManager.LOCALE_FIRE)) {
				Bundle bundle = intent.getBundleExtra(SoundServiceManager.LOCALE_BUNDLE);
				if (bundle != null) {
					boolean state = bundle.getBoolean(SoundService.SET_TRACKING_STATE, true);
					SoundServiceManager.setTracking(context, state);
				}
			}
		}
	}

	/**
	 * Determine whether we should be tracking.
	 *
	 * @param context Application context
	 * @return suggested state
	 */
	private boolean shouldTrack(Context context) {
		// load preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean powerPreference = prefs.getBoolean("enable_only_charging", false);
		boolean headphonePreference = prefs.getBoolean("enable_headphones", false);
		boolean bluetoothPreference = prefs.getBoolean("enable_bluetooth", false);

		// get power status
		IntentFilter plugFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent powerStatus = context.getApplicationContext().registerReceiver(null, plugFilter);
		boolean powerConnected = false;
		if (powerStatus != null) {
			int plugState = powerStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			powerConnected = (plugState == BatteryManager.BATTERY_PLUGGED_AC ||
					plugState == BatteryManager.BATTERY_PLUGGED_USB);
		} else {
			Log.e(TAG, "Power status was null");
		}

		// don't track if power is disconnected and we care
		if (powerPreference && !powerConnected) {
			Log.v(TAG, "Power preference active & disconnected");
			return false;
		}

		// get headphone status
		IntentFilter headsetFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		Intent headphoneStatus = context.getApplicationContext().registerReceiver(null, headsetFilter);
		boolean headphoneConnected = false;
		if (headphoneStatus != null) {
			headphoneConnected = headphoneStatus.getIntExtra("state", 0) == 1;
		} else {
			Log.e(TAG, "Headphone status was null");
		}

		// activate if headphones are plugged in
		if (headphonePreference && headphoneConnected) {
			Log.v(TAG, "Headphone connected");
			return true;
		}

		// also activate if bluetooth is connected
		if (bluetoothPreference && this.bluetoothConnected) {
			Log.v(TAG, "Bluetooth connected");
			return true;
		}

		// anything else is a no-go
		return false;
	}

	/**
	 * Set the tracking state by sending a service start command.
	 *
	 * @param context Application context.
	 * @param state   Turn tracking on or off.
	 */
	private static void setTracking(Context context, boolean state) {
		Log.d(TAG, "Setting tracking state: " + state);
		Intent serviceIntent = new Intent(context, SoundService.class);
		serviceIntent.putExtra(SoundService.SET_TRACKING_STATE, state);
		context.startService(serviceIntent);
	}

}
