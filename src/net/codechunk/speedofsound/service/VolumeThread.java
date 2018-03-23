package net.codechunk.speedofsound.service;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

/**
 * Smooth volume thread. Does its best to approach a set volume rather than
 * jumping to it directly. Has a few tunable constants to tweak for better
 * performance.
 */
public class VolumeThread extends Thread {
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
	 * The volume we want to approach. Between 0 and 1.
	 */
	private float targetVolume;

	/**
	 * Start up the thread and set the thread name.
	 *
	 * @param context Application context
	 */
	public VolumeThread(Context context) {
		this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.setName(TAG);

		Log.d(TAG, "System max volume is " + this.maxVolume);
	}

	/**
	 * Set a new target volume.
	 *
	 * @param volume New target volume from 0 to 1
	 */
	public void setTargetVolume(float volume) {
		// only set & wake if the target has actually changed
		if (volume != this.targetVolume) {
			Log.v(TAG, "Setting target volume to " + volume);
			synchronized (this.lock) {
				this.targetVolume = volume;
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
	public void run() {
		Log.d(TAG, "Thread starting");

		// get the current system volume
		int systemVolume = this.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		float currentVolume = systemVolume / (float) this.maxVolume;
		this.setTargetVolume(currentVolume);
		Log.d(TAG, "Current volume is " + currentVolume);

		while (!this.isInterrupted()) {
			// sleep for a while
			try {
				Thread.sleep(VolumeThread.UPDATE_DELAY);
			} catch (InterruptedException e) {
				break;
			}

			// safely grab the target volume
			float targetVolume;
			synchronized (this.lock) {
				targetVolume = this.targetVolume;
			}

			// if the volume is matched, just sleep
			if (currentVolume == targetVolume) {
				Log.v(TAG, "Thread sleeping");
				synchronized (this.signal) {
					try {
						this.signal.wait();
					} catch (InterruptedException e) {
						break;
					}
				}
				Log.v(TAG, "Thread awoken");

				// get the updated volume
				synchronized (this.lock) {
					targetVolume = this.targetVolume;
				}
			}

			// if the target is close enough, just use it
			float newVolume;
			if (Math.abs(currentVolume - targetVolume) < VolumeThread.VOLUME_THRESHOLD) {
				newVolume = targetVolume;
			}

			// otherwise, gently approach the target
			else {
				float approach = (targetVolume - currentVolume) * VolumeThread.APPROACH_RATE;

				// don't approach more quickly than the max
				if (Math.abs(approach) > VolumeThread.MAX_APPROACH) {
					// approach with the max rate, but with the same sign as the
					// original
					if (approach < 0) {
						approach = -VolumeThread.MAX_APPROACH;
					} else {
						approach = VolumeThread.MAX_APPROACH;
					}
				}

				newVolume = currentVolume + approach;
			}

			// set the volume
			try {
				int newSystemVolume = (int) (this.maxVolume * newVolume);
				this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newSystemVolume, 0);
				currentVolume = newVolume;

				Log.v(TAG, "New volume is " + newVolume + " translated to " + newSystemVolume);
			} catch (SecurityException e) {
				// fucking Samsung devices throw this when that stupid "you might
				// hurt your ears" pop-up comes up when changing the volume.
				Log.e(TAG, "SecurityException when trying to change the volume", e);
			}
		}

		Log.d(TAG, "Thread exiting");
	}

}
