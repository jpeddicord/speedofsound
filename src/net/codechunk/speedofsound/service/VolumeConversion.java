package net.codechunk.speedofsound.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.codechunk.speedofsound.util.AverageSpeed;

public class VolumeConversion implements SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String TAG = "VolumeConversion";

	private final AverageSpeed averager = new AverageSpeed(6);
	private float lowSpeed;
	private float highSpeed;
	private int lowVolume;
	private int highVolume;

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

		if (averageSpeed < this.lowSpeed) {
			// minimum volume
			Log.d(TAG, "Low averageSpeed triggered at " + averageSpeed);
			volume = this.lowVolume / 100f;
		} else if (averageSpeed > this.highSpeed) {
			// high volume
			Log.d(TAG, "High averageSpeed triggered at " + averageSpeed);
			volume = this.highVolume / 100f;
		} else {
			// log scaling
			float volumeRange = (this.highVolume - this.lowVolume) / 100f;
			float speedRangeFrac = (averageSpeed - this.lowSpeed) / (this.highSpeed - this.lowSpeed);
			float volumeRangeFrac = (float) (Math.log1p(speedRangeFrac) / Math.log1p(1));
			volume = this.lowVolume / 100f + volumeRange * volumeRangeFrac;
			Log.d(TAG, "Log scale triggered with " + averageSpeed + ", using volume " + volume);
		}

		return volume;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String s) {
		this.lowSpeed = prefs.getFloat("low_speed", 0);
		this.lowVolume = prefs.getInt("low_volume", 0);
		this.highSpeed = prefs.getFloat("high_speed", 100);
		this.highVolume = prefs.getInt("high_volume", 100);
	}

}
