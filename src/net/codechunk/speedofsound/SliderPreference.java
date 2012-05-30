package net.codechunk.speedofsound;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

/**
 * A preference that is displayed as a seek bar.
 */
public class SliderPreference extends DialogPreference implements
		OnSeekBarChangeListener
{
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final String LOCAL_NS = "http://schemas.android.com/apk/res/net.codechunk.speedofsound";

	private SeekBar seekBar;
	private TextView valueDisplay;
	private View view;

	private int defaultValue;
	private int minValue;
	private int maxValue;
	private int value;

	/**
	 * Create a new slider preference.
	 * 
	 * @param context
	 *            Context to use
	 * @param attrs
	 *            XML attributes to load
	 */
	public SliderPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		this.defaultValue = attrs.getAttributeIntValue(ANDROID_NS,
				"defaultValue", 0);
		this.minValue = attrs.getAttributeIntValue(LOCAL_NS, "minValue", 0);
		this.maxValue = attrs.getAttributeIntValue(LOCAL_NS, "maxValue", 0);
	}

	/**
	 * Set up the preference display.
	 */
	@Override
	protected View onCreateDialogView()
	{
		this.value = getPersistedInt(this.defaultValue);

		// load the layout
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.view = inflater.inflate(R.layout.slider_preference_dialog, null);

		// setup the slider
		this.seekBar = (SeekBar) view.findViewById(R.id.slider_preference_seekbar);
		this.seekBar.setMax(this.maxValue - this.minValue);
		this.seekBar.setProgress(this.value - this.minValue);
		this.valueDisplay = (TextView) this.view.findViewById(R.id.slider_preference_value);
		this.valueDisplay.setText(Integer.toString(this.value));
		this.seekBar.setOnSeekBarChangeListener(this);

		return this.view;
	}

	/**
	 * Save on dialog close.
	 */
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		super.onDialogClosed(positiveResult);

		if (!positiveResult)
		{
			return;
		}

		if (shouldPersist())
		{
			this.persistInt(this.value);
		}

		this.notifyChanged();
	}

	/**
	 * Updated the displayed value on change.
	 */
	public void onProgressChanged(SeekBar seekBar, int value, boolean fromTouch)
	{
		this.value = value + this.minValue;
		this.valueDisplay.setText(Integer.toString(this.value));
	}

	public void onStartTrackingTouch(SeekBar sb)
	{
	}

	public void onStopTrackingTouch(SeekBar sb)
	{
	}

}
