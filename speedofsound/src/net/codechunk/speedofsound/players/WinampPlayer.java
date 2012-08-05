package net.codechunk.speedofsound.players;

import android.content.Context;
import android.content.Intent;

public class WinampPlayer extends AndroidMusicPlayer
{
	@Override
	public PlaybackAction getPlaybackAction(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (action.equals("com.nullsoft.winamp.playbackcomplete"))
			return PlaybackAction.STOPPED;

		if (action.equals("com.nullsoft.winamp.metachanged"))
			return PlaybackAction.CHANGED;

		return null;
	}
}
