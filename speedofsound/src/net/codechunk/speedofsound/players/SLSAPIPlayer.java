package net.codechunk.speedofsound.players;

import android.content.Context;
import android.content.Intent;

public class SLSAPIPlayer extends AndroidMusicPlayer
{
	@Override
	public PlaybackAction getPlaybackAction(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (action.equals("com.adam.aslfms.notify.playstatechanged"))
			return PlaybackAction.CHANGED;

		return null;
	}
}
