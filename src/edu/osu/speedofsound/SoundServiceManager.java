package edu.osu.speedofsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Sound service activation manager.
 */
public class SoundServiceManager extends BroadcastReceiver
{
	private static final String TAG = "SoundServiceManager";

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
	}
}
