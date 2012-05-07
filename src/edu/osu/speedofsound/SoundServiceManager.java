package edu.osu.speedofsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Sound service activation manager.
 * Responsible for handling external broadcasts, such as system startup
 * or car mode.
 */
public class SoundServiceManager extends BroadcastReceiver
{
	/**
	 * Receive a broadcast and start the service.
	 */
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Intent startIntent = new Intent(context, SoundService.class);
		context.startService(startIntent);
	}
}
