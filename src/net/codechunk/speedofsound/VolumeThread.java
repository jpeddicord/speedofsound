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
	private static final String TAG = "VolumeThread";

	/**
	 * Rate in milliseconds at which to update the volume.
	 */
	private static final int UPDATE_DELAY = 150;

	/**
	 * Threshold where we're "close enough" to the target volume to jump
	 * straight to it.
	 */
	private static final float VOLUME_THRESHOLD = 0.03f;

	/**
	 * Approach rate. Heavily related to the update delay.
	 */
	private static final float APPROACH_RATE = 0.3f;

	/**
	 * Maximum speed to approach the target volume. This shouldn't be too high
	 * or a jump in volume may be noticed.
	 */
	private static final float MAX_APPROACH = 0.06f;

	private final Object lock = new Object();
	private final Object signal = new Object();
	private final AudioManager audioManager;
	private final int maxVolume;

	/**
	 * The volume percentage we want to approach. Between 0 and 1.
	 */
	private float targetVolumePercent;

	/**
	 * Start up the thread and set the thread name.
	 * 
	 * @param context
	 *            Application context
	 */
	public VolumeThread(Context context)
	{
		this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.setName(TAG);

		Log.d(TAG, "System max volume is " + this.maxVolume);
	}

	/**
	 * Set a new target volume.
	 * 
	 * @param volume
	 *            New target volume percentage from 0 to 1
	 */
	public void setTargetVolume(float volumePercent) {
		// only set & wake if the target has actually changed
		if (volumePercent != this.targetVolumePercent) {
			Log.v(TAG, "Setting target volume to " + volumePercent);
			synchronized (this.lock) {
				this.targetVolumePercent = volumePercent;
			}

			// wake the thread up
			synchronized (this.signal) {
				this.signal.notifyAll();
			}
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
		float currentVolumePercent = currentVolume / (float) this.maxVolume;
		this.setTargetVolume(currentVolumePercent);
		Log.d(TAG, "Current volume is " + currentVolumePercent);

		while (!this.isInterrupted())
		{
			// sleep for a while
			try
			{
				Thread.sleep(VolumeThread.UPDATE_DELAY);
			}
			catch (InterruptedException e)
			{
				break;
			}

			// safely grab the target volume
			float targetVolumePercent;
			synchronized (this.lock)
			{
				targetVolumePercent = this.targetVolumePercent;
			}

			// if the volume is matched, just sleep
			if (currentVolumePercent == targetVolumePercent)
			{
				Log.v(TAG, "Thread sleeping");
				synchronized (this.signal)
				{
					try
					{
						this.signal.wait();
					}
					catch (InterruptedException e)
					{
						break;
					}
				}
				Log.v(TAG, "Thread awoken");

				// get the updated volume
				synchronized (this.lock)
				{
					targetVolumePercent = this.targetVolumePercent;
				}
			}

			// if the target is close enough, just use it
			float newVolumePercent = 0f;
			if (Math.abs(currentVolumePercent - targetVolumePercent) < VolumeThread.VOLUME_THRESHOLD)
			{
				newVolumePercent = targetVolumePercent;
			}

			// otherwise, gently approach the target
			else
			{
				float approach = (targetVolumePercent - currentVolumePercent) * VolumeThread.APPROACH_RATE;

				// don't approach more quickly than the max
				if (Math.abs(approach) > VolumeThread.MAX_APPROACH)
				{
					// approach with the max rate, but with the same sign as the
					// original
					if (approach < 0)
					{
						approach = -VolumeThread.MAX_APPROACH;
					}
					else
					{
						approach = VolumeThread.MAX_APPROACH;
					}
				}

				newVolumePercent = currentVolumePercent + approach;
			}

			// set the volume
			int newVolume = (int) (this.maxVolume * newVolumePercent);
			this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
			currentVolumePercent = newVolumePercent;

			Log.v(TAG, "New volume is " + newVolumePercent + " translated to " + newVolume);
		}

		Log.d(TAG, "Thread exiting");
	}

}
