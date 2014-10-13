package net.codechunk.speedofsound.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.codechunk.speedofsound.util.AverageSpeed;

public class VolumeConversion {
	private static final String TAG = "VolumeConversion";

	private final AverageSpeed averager = new AverageSpeed(6);
	private final SharedPreferences settings;

	public VolumeConversion(Context context) {
		this.settings = PreferenceManager.getDefaultSharedPreferences(context);
	}

	/**
	 * Convert a speed instant to a desired volume. Stateful;
	 * based on previous speeds.
	 */
	public float speedToVolume(float speed) {
		float volume;

		Log.v(TAG, "Pushing speed " + speed);
		this.averager.push(speed);
		float averageSpeed = this.averager.getAverage();
		Log.v(TAG, "Average currently " + averageSpeed);

		// TODO: only read these on preference change notification
		float lowSpeed = this.settings.getFloat("low_speed", 0);
		int lowVolume = this.settings.getInt("low_volume", 0);
		float highSpeed = this.settings.getFloat("high_speed", 100);
		int highVolume = this.settings.getInt("high_volume", 100);

		if (averageSpeed < lowSpeed) {
			// minimum volume
			Log.d(TAG, "Low averageSpeed triggered at " + averageSpeed);
			volume = lowVolume / 100f;
		} else if (averageSpeed > highSpeed) {
			// high volume
			Log.d(TAG, "High averageSpeed triggered at " + averageSpeed);
			volume = highVolume / 100f;
		} else {
			// log scaling
			float volumeRange = (highVolume - lowVolume) / 100f;
			float speedRangeFrac = (averageSpeed - lowSpeed) / (highSpeed - lowSpeed);
			float volumeRangeFrac = (float) (Math.log1p(speedRangeFrac) / Math.log1p(1));
			volume = lowVolume / 100f + volumeRange * volumeRangeFrac;
			Log.d(TAG, "Log scale triggered with " + averageSpeed + ", using volume " + volume);
		}

		return volume;
	}
}
