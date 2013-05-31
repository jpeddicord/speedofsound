package net.codechunk.speedofsound.players;

import android.content.Context;
import android.content.Intent;

public class SamsungPlayer extends AndroidMusicPlayer
{
	@Override
	public PlaybackAction getPlaybackAction(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (action.equals("com.samsung.sec.android.MusicPlayer.playbackcomplete"))
			return PlaybackAction.STOPPED;

		if (action.equals("com.samsung.sec.android.MusicPlayer.metachanged"))
			return PlaybackAction.CHANGED;

		return null;
	}
}
