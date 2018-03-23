package net.codechunk.speedofsound.service

import android.content.SharedPreferences
import android.util.Log

import net.codechunk.speedofsound.util.AverageSpeed

class VolumeConversion : SharedPreferences.OnSharedPreferenceChangeListener {

    private val averager = AverageSpeed(6)
    private var lowSpeed: Float = 0.toFloat()
    private var highSpeed: Float = 0.toFloat()
    private var lowVolume: Int = 0
    private var highVolume: Int = 0

    /**
     * Convert a speed instant to a desired volume. Stateful;
     * based on previous speeds.
     */
    fun speedToVolume(speed: Float): Float {
        val volume: Float

        Log.v(TAG, "Pushing speed $speed")
        this.averager.push(speed)
        val averageSpeed = this.averager.average
        Log.v(TAG, "Average currently $averageSpeed")

        when {
            averageSpeed < this.lowSpeed -> {
                // minimum volume
                Log.d(TAG, "Low averageSpeed triggered at $averageSpeed")
                volume = this.lowVolume / 100f
            }
            averageSpeed > this.highSpeed -> {
                // high volume
                Log.d(TAG, "High averageSpeed triggered at $averageSpeed")
                volume = this.highVolume / 100f
            }
            else -> {
                // linear scaling
                val volumeRange = (this.highVolume - this.lowVolume) / 100f
                val relativeSpeed = (averageSpeed - this.lowSpeed) / (this.highSpeed - this.lowSpeed)
                volume = this.lowVolume / 100f + volumeRange * relativeSpeed
                Log.d(TAG, "Linear scale triggered with $averageSpeed, using volume $volume")
            }
        }

        return volume
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, s: String) {
        this.lowSpeed = prefs.getFloat("low_speed", 0f)
        this.lowVolume = prefs.getInt("low_volume", 0)
        this.highSpeed = prefs.getFloat("high_speed", 100f)
        this.highVolume = prefs.getInt("high_volume", 100)
    }

    companion object {
        private const val TAG = "VolumeConversion"
    }

}
