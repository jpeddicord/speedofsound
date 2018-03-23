package net.codechunk.speedofsound.util

import android.content.Context
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

/**
 * A preference that is displayed as a seek bar.
 *
 * Customized for speed ranges; localized to the currently-selected units
 * when the user opens it. minValue/maxValue should be in m/s.
 */
class SpeedSliderPreference(context: Context, attrs: AttributeSet) : SliderPreference(context, attrs) {

    private var localMin: Int = 0
    private var localMax: Int = 0

    /**
     * Set up the preference display.
     */
    override fun onCreateDialogView(): View {
        // grabbing the saved value before calling superclass;
        // when the seekbar is set up there the value will be clamped to
        // unlocalized min/max which would give us the wrong value
        val `val` = getPersistedInt(-1)

        super.onCreateDialogView()

        // grab the units currently in use
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val units = prefs.getString("speed_units", "")
        this.units = " " + units!!

        // adjust the maximum value for the correct units; maxValue is in m/s
        this.localMax = SpeedConversions.localizedSpeed(units, this.maxValue.toFloat()).toInt()
        this.localMin = SpeedConversions.localizedSpeed(units, this.minValue.toFloat()).toInt()
        this.seekBar!!.max = this.localMax
        this.seekBar!!.progress = `val` - this.localMin

        // restore the value and redraw
        this.value = `val`
        this.updateDisplay()

        return this.view!!
    }

    override fun onProgressChanged(seekBar: SeekBar, value: Int, fromTouch: Boolean) {
        this.value = value + this.localMin
        this.updateDisplay()
    }


}
