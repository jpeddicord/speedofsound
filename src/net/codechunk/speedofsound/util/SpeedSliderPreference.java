package net.codechunk.speedofsound.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

/**
 * A preference that is displayed as a seek bar.
 *
 * Customized for speed ranges; localized to the currently-selected units
 * when the user opens it. minValue/maxValue should be in m/s.
 */
public class SpeedSliderPreference extends SliderPreference {
	public SpeedSliderPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	protected int localMin;
	protected int localMax;

	/**
	 * Set up the preference display.
	 */
	@Override
	protected View onCreateDialogView() {
		// grabbing the saved value before calling superclass;
		// when the seekbar is set up there the value will be clamped to
		// unlocalized min/max which would give us the wrong value
		int val = getPersistedInt(-1);

		super.onCreateDialogView();

		// grab the units currently in use
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String units = prefs.getString("speed_units", "");
		this.units = " " + units;

		// adjust the maximum value for the correct units; maxValue is in m/s
		this.localMax = (int) SpeedConversions.localizedSpeed(units, this.maxValue);
		this.localMin = (int) SpeedConversions.localizedSpeed(units, this.minValue);
		this.seekBar.setMax(this.localMax);
		this.seekBar.setProgress(val - this.localMin);

		// restore the value and redraw
		this.value = val;
		this.updateDisplay();

		return this.view;
	}

	public void onProgressChanged(SeekBar seekBar, int value, boolean fromTouch) {
		this.value = value + this.localMin;
		this.updateDisplay();
	}


}
