package net.codechunk.speedofsound;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;

import net.codechunk.speedofsound.service.SoundService;
import net.codechunk.speedofsound.service.SoundServiceManager;


public class LocaleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.locale);

		RadioButton start_radio = (RadioButton) findViewById(R.id.tasker_radio_start);
		RadioButton stop_radio = (RadioButton) findViewById(R.id.tasker_radio_stop);

		// load the stored action
		Bundle bundle = getIntent().getBundleExtra(SoundServiceManager.LOCALE_BUNDLE);
		boolean startState = true;
		if (bundle != null) {
			startState = bundle.getBoolean(SoundService.SET_TRACKING_STATE, true);
		}

		// set the selected action
		if (startState) {
			start_radio.toggle();
		} else {
			stop_radio.toggle();
		}

		start_radio.setOnClickListener(this.listener);
		stop_radio.setOnClickListener(this.listener);
    }

	protected final View.OnClickListener listener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			RadioButton radio = (RadioButton) view;
			Bundle data = new Bundle();
			data.putBoolean(SoundService.SET_TRACKING_STATE,
					radio.getId() == R.id.tasker_radio_start);
			Intent result = new Intent();
			result.putExtra(SoundServiceManager.LOCALE_BUNDLE, data);
			result.putExtra(SoundServiceManager.LOCALE_BLURB, radio.getText().toString());
			setResult(RESULT_OK, result);

			finish();
		}
	};

}
