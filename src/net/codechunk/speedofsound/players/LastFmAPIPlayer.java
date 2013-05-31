package net.codechunk.speedofsound.players;

import android.content.Context;
import android.content.Intent;

public class LastFmAPIPlayer extends AndroidMusicPlayer
{
	@Override
	public PlaybackAction getPlaybackAction(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (action.equals("fm.last.android.playbackcomplete"))
			return PlaybackAction.STOPPED;

		if (action.equals("fm.last.android.metachanged"))
			return PlaybackAction.CHANGED;

		return null;
	}
}
