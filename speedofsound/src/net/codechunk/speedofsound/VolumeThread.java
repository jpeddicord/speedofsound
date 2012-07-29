package net.codechunk.speedofsound;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * Smooth volume thread. Does its best to approach a set volume rather than
 * jumping to it directly. Has a few tunable constants to tweak for better
 * performance.
 */
public class VolumeThread extends Thread
{
	/**
	 * Logging tag.
	 */
	private static final String TAG = "VolumeThread";

	/**
	 * Rate in milliseconds at which to update the volume.
	 */
	private static final int UPDATE_DELAY = 200;

	/**
	 * Threshold where we're "close enough" to the target volume to jump
	 * straight to it.
	 */
	private static final int VOLUME_THRESHOLD = 3;

	/**
	 * Approach rate. Heavily related to the update delay.
	 */
	private static final float APPROACH_RATE = 0.35f;

	/**
	 * Maximum speed to approach the target volume. This shouldn't be too high
	 * or a jump in volume may be noticed.
	 */
	private static final int MAX_APPROACH = 8;

	/**
	 * Thread mutex.
	 */
	private final Object lock = new Object();

	/**
	 * Audio manager to change the media volume.
	 */
	private AudioManager audioManager;

	/**
	 * Current target volume.
	 */
	private int targetVolume;

	/**
	 * Start up the thread and set the thread name.
	 * 
	 * @param context
	 *            Application context
	 */
	public VolumeThread(Context context)
	{
		this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		this.setName(TAG);
	}

	/**
	 * Set a new target volume.
	 * 
	 * @param volume
	 *            New target
	 */
	public void setTargetVolume(int volume)
	{
		Log.v(TAG, "Setting target volume to " + volume);
		synchronized (this.lock)
		{
			this.targetVolume = volume;
		}
	}

	/**
	 * Thread runner. Smoothly adjusts the volume until interrupted.
	 */
	public void run()
	{
		Log.d(TAG, "Thread starting");

		// get the current system volume
		int currentVolume = this.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		this.setTargetVolume(currentVolume);
		Log.d(TAG, "Current volume is " + currentVolume);

		while (!this.isInterrupted())
		{
			// sleep for a while
			try
			{
				Thread.sleep(UPDATE_DELAY);
			}
			catch (InterruptedException e)
			{
				break;
			}

			// safely grab the target volume
			int targetVolume;
			synchronized (this.lock)
			{
				targetVolume = this.targetVolume;
			}

			// don't do anything if the target is already matched
			if (currentVolume == targetVolume)
			{
				continue;
			}

			// if the target is close enough, just use it
			int newVolume = 0;
			if (Math.abs(currentVolume - targetVolume) < VOLUME_THRESHOLD)
			{
				newVolume = targetVolume;
			}

			// otherwise, gently approach the target
			else
			{
				// approach the target, but not more quickly than the max
				int approach = Math.min((int) ((targetVolume - currentVolume) * APPROACH_RATE), MAX_APPROACH);
				newVolume = currentVolume + approach;
			}

			Log.v(TAG, "New volume is " + newVolume);

			// set the volume
			// TODO: can't assume all devices have a 255 max; should scale this.
			this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
			currentVolume = newVolume;
		}

		Log.d(TAG, "Thread exiting");
	}

}
