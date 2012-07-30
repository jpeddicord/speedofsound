package net.codechunk.speedofsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
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

	/**
	 * Get the filter of extra intents we care about.
	 */
	public IntentFilter activationIntents()
	{
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.intent.action.ACTION_POWER_CONNECTED");
		filter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED");
		filter.addAction("android.intent.action.HEADSET_PLUG");

		// NOTE: these are undocumented!
		filter.addAction("android.bluetooth.a2dp.action.SINK_STATE_CHANGED");
		filter.addAction("android.bluetooth.a2dp.intent.action.SINK_STATE_CHANGED");
		return filter;
	}

	/**
	 * Receive a broadcast and start the service or update the tracking state.
	 */
	@Override
	@SuppressWarnings("deprecation")
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

		// get power status
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		// getApplicationContext() workaround for android issue 5111
		Intent powerStatus = context.getApplicationContext().registerReceiver(null, filter);
		int plugState = powerStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		boolean powerConnected = (plugState == BatteryManager.BATTERY_PLUGGED_AC ||
				plugState == BatteryManager.BATTERY_PLUGGED_USB);

		// get headset status
		AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		boolean headphoneConnected = audioManager.isWiredHeadsetOn();

		// load preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean powerPreference = prefs.getBoolean("enable_only_charging", false);
		boolean headphonePreference = prefs.getBoolean("enable_headphones", false);

		// resume tracking if we're also in a satisfactory mode
		if (action.equals("android.intent.action.ACTION_POWER_CONNECTED"))
		{
			Log.d(TAG, "Power connected");

			// ignore if preference is inactive
			if (powerPreference)
			{
				// activate if we want and have a headset
				if (headphonePreference && headphoneConnected)
				{
					SoundServiceManager.setTracking(context, true);
				}
			}
		}

		// stop tracking if desired for only when charging
		else if (action.equals("android.intent.action.ACTION_POWER_DISCONNECTED"))
		{
			Log.d(TAG, "Power disconnected");

			// ignore if preference is inactive
			if (powerPreference)
			{
				Log.v(TAG, "Preference active, stopping tracking");
				SoundServiceManager.setTracking(context, false);
			}
		}

		// start or stop tracking for headset events
		else if (action.equals("android.intent.action.HEADSET_PLUG"))
		{
			Log.d(TAG, "Headset event");

			// ignore if preference not active
			if (headphonePreference)
			{
				// check the headphone plug state
				if (intent.getIntExtra("state", 0) == 1)
				{
					// only if we don't care or are powered anyway
					if (!powerPreference || powerConnected)
					{
						Log.v(TAG, "Plugged in, starting tracking");
						SoundServiceManager.setTracking(context, true);
					}
				}
				else
				{
					Log.v(TAG, "Unplugged, stopping tracking");
					SoundServiceManager.setTracking(context, false);
				}
			}
		}

		// these broadcasts are undocumented, but seem to work on a bunch of
		// android versions. used with caution.
		else if (action.equals("android.bluetooth.a2dp.action.SINK_STATE_CHANGED") ||
				action.equals("android.bluetooth.a2dp.intent.action.SINK_STATE_CHANGED"))
		{
			Log.d(TAG, "A2DP undocumented event");

			// ignore if BT preference is inactive TODO
			if (false)
			{
				if (audioManager.isBluetoothA2dpOn())
				{
					Log.v(TAG, "A2DP active, starting tracking");
					SoundServiceManager.setTracking(context, true);
				}
				else
				{
					Log.v(TAG, "A2DP inactive, stopping tracking");
					SoundServiceManager.setTracking(context, false);
				}
			}
		}
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
		Intent serviceIntent = new Intent(context, SoundService.class);
		serviceIntent.putExtra(SoundService.SET_TRACKING_STATE, state);
		context.startService(serviceIntent);
	}

}
