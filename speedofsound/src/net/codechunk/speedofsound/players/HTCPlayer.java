package net.codechunk.speedofsound.players;

import android.content.Context;
import android.content.Intent;

public class HTCPlayer extends AndroidMusicPlayer
{
	@Override
	public PlaybackAction getPlaybackAction(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (action.equals("com.htc.music.playbackcomplete"))
			return PlaybackAction.STOPPED;

		if (action.equals("com.htc.music.metachanged"))
			return PlaybackAction.CHANGED;

		return null;
	}
}
