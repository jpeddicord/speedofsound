package edu.osu.speedofsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Sound service activation manager. Responsible for handling external
 * broadcasts, such as system startup or car mode.
 */
public class SoundServiceManager extends BroadcastReceiver
{
	private static final String TAG = "SoundServiceManager";

	// TODO: move all service management (start/stop tracking) to this class

	/**
	 * Receive a broadcast and start the service.
	 */
	@Override
	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();
		Log.d(TAG, "Received intent " + action);

		// start the service on boot
		if (action == "android.intent.action.BOOT_COMPLETED")
		{
			Intent startIntent = new Intent(context, SoundService.class);
			context.startService(startIntent);
		}
		// resume tracking if we're also in a satisfactory mode
		else if (action == "android.intent.action.ACTION_POWER_CONNECTED")
		{
			Log.v(TAG, "Power connected");
		}
		// stop tracking if desired for only when charging
		else if (action == "android.intent.action.ACTION_POWER_DISCONNECTED")
		{
			Log.v(TAG, "Power disconnected");
		}
		// start tracking if desired for car mode
		else if (action == "android.intent.action.ENTER_CAR_MODE")
		{
			Log.v(TAG, "Entered car mode");
		}
		// start or stop tracking for headset events
		else if (action == "android.intent.action.HEADSET_PLUG")
		{
			Log.v(TAG, "Headset event");
		}
	}
}
