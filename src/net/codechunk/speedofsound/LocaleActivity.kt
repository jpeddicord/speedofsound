package net.codechunk.speedofsound

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton

import net.codechunk.speedofsound.service.SoundService
import net.codechunk.speedofsound.service.SoundServiceManager


class LocaleActivity : Activity() {

    private val listener: View.OnClickListener = View.OnClickListener { view ->
        val radio = view as RadioButton
        val data = Bundle()
        data.putBoolean(SoundService.SET_TRACKING_STATE,
                radio.id == R.id.tasker_radio_start)
        val result = Intent()
        result.putExtra(SoundServiceManager.LOCALE_BUNDLE, data)
        result.putExtra(SoundServiceManager.LOCALE_BLURB, radio.text.toString())
        setResult(Activity.RESULT_OK, result)

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.locale)

        val startRadio = findViewById<View>(R.id.tasker_radio_start) as RadioButton
        val stopRadio = findViewById<View>(R.id.tasker_radio_stop) as RadioButton

        // load the stored action
        val bundle = intent.getBundleExtra(SoundServiceManager.LOCALE_BUNDLE)
        var startState = true
        if (bundle != null) {
            startState = bundle.getBoolean(SoundService.SET_TRACKING_STATE, true)
        }

        // set the selected action
        if (startState) {
            startRadio.toggle()
        } else {
            stopRadio.toggle()
        }

        startRadio.setOnClickListener(this.listener)
        stopRadio.setOnClickListener(this.listener)
    }

}
