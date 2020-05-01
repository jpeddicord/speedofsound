package net.codechunk.speedofsound.util

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView

import net.codechunk.speedofsound.R

/**
 * A preference that is displayed as a seek bar.
 */
open class SliderPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs), OnSeekBarChangeListener {

    /**
     * Seek bar widget to control preference value.
     */
    protected var seekBar: SeekBar? = null

    /**
     * Text view displaying the value of the seek bar.
     */
    private var valueDisplay: TextView? = null

    /**
     * Dialog view.
     */
    protected var view: View? = null

    /**
     * Minimum value.
     */
    protected val minValue: Int

    /**
     * Maximum value.
     */
    protected val maxValue: Int

    /**
     * Units of this preference.
     */
    protected var units: String? = null

    /**
     * Current value.
     */
    protected var value: Int = 0

    init {
        this.minValue = attrs.getAttributeIntValue(LOCAL_NS, "minValue", 0)
        this.maxValue = attrs.getAttributeIntValue(LOCAL_NS, "maxValue", 0)
        this.units = attrs.getAttributeValue(LOCAL_NS, "units")
    }

    /**
     * Set the initial value.
     * Needed to properly load the default value.
     */
    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            // Restore existing state
            this.value = this.getPersistedInt(-1)
        } else {
            // Set default state from the XML attribute
            this.value = defaultValue as Int
            persistInt(this.value)
        }
    }

    /**
     * Support loading a default value.
     */
    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, -1)
    }

    /**
     * Set up the preference display.
     */
    override fun onCreateDialogView(): View {
        // reload the persisted value since onSetInitialValue is only performed once
        // for the activity, not each time the preference is opened
        this.value = getPersistedInt(-1)

        // load the layout
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        this.view = inflater.inflate(R.layout.slider_preference_dialog, null)

        // setup the slider
        this.seekBar = this.view!!.findViewById<View>(R.id.slider_preference_seekbar) as SeekBar
        this.seekBar!!.max = this.maxValue - this.minValue
        this.seekBar!!.progress = this.value - this.minValue
        this.seekBar!!.setOnSeekBarChangeListener(this)

        this.valueDisplay = this.view!!.findViewById<View>(R.id.slider_preference_value) as TextView
        this.updateDisplay()

        return this.view!!
    }

    /**
     * Save on dialog close.
     */
    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        if (!positiveResult) {
            return
        }

        if (shouldPersist()) {
            this.persistInt(this.value)
        }

        this.notifyChanged()
    }

    protected fun updateDisplay() {
        var text = this.value.toString()
        if (this.units != null) {
            text += this.units
        }
        this.valueDisplay!!.text = text
    }

    /**
     * Updated the displayed value on change.
     */
    override fun onProgressChanged(seekBar: SeekBar, value: Int, fromTouch: Boolean) {
        this.value = value + this.minValue
        this.updateDisplay()
    }

    override fun onStartTrackingTouch(sb: SeekBar) {}

    override fun onStopTrackingTouch(sb: SeekBar) {}

    companion object {
        /**
         * Our custom namespace for this preference.
         */
        protected const val LOCAL_NS = "http://schemas.android.com/apk/res-auto"
    }

}
