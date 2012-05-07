package edu.osu.speedofsound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SoundServiceManager extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Intent startIntent = new Intent(context, SoundService.class);
		context.startService(startIntent);
	}
}
