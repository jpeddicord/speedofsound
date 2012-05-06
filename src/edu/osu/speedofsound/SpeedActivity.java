package edu.osu.speedofsound;

import edu.osu.speedofsound.SoundService.LocalBinder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class SpeedActivity extends Activity implements OnCheckedChangeListener
{
	private static final String TAG = "SpeedActivity";

	private CheckBox enabledCheckBox;
	private boolean bound = false;
	private SoundService service;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		this.enabledCheckBox = (CheckBox) findViewById(R.id.checkbox_enabled);
		this.enabledCheckBox.setOnCheckedChangeListener(this);
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Log.d(TAG, "View starting");

		// bind to our service after explicitly starting it
		Intent intent = new Intent(this, SoundService.class);
		startService(intent);
		bindService(intent, this.serviceConnection, 0);
	}

	@Override
	public void onStop()
	{
		super.onStop();
		Log.d(TAG, "View stopping");

		// unbind from our service
		if (this.bound)
		{
			// if tracking is disabled, just shut off the service
			if (!this.service.tracking)
			{
				Intent intent = new Intent(this, SoundService.class);
				stopService(intent);
			}

			unbindService(this.serviceConnection);
			this.bound = false;
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();
		Log.d(TAG, "Paused, unsubscribing from updates");
		
		LocalBroadcastManager.getInstance(this).unregisterReceiver(this.messageReceiver);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		Log.d(TAG, "Resumed, subscribing to service updates");
		
		LocalBroadcastManager.getInstance(this).registerReceiver(this.messageReceiver,
				new IntentFilter("speed-sound-changed"));
	}

	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
	{
		Log.d(TAG, "Checkbox changed to " + isChecked);

		if (!this.bound)
		{
			Log.e(TAG, "Service is unavailable");
			return;
		}

		if (isChecked)
		{
			this.service.startTracking();
		} else
		{
			this.service.stopTracking();
		}
	}

	private BroadcastReceiver messageReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.v(TAG, "Received broadcast");

			TextView speedView = (TextView) findViewById(R.id.speed);
			speedView.setText(String.format("%.1f mph", intent.getFloatExtra("speed", -1.0f)));

			TextView volumeView = (TextView) findViewById(R.id.volume);
			volumeView.setText(String.format("%d%%", intent.getIntExtra("volume", -1)));
		}
	};

	private ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			Log.v(TAG, "ServiceConnection connected");

			LocalBinder binder = (LocalBinder) service;
			SpeedActivity.this.service = binder.getService();
			SpeedActivity.this.bound = true;

			// update the enabled check box
			SpeedActivity.this.enabledCheckBox.setChecked(SpeedActivity.this.service.tracking);
		}

		public void onServiceDisconnected(ComponentName arg)
		{
			Log.v(TAG, "ServiceConnection disconnected");
			SpeedActivity.this.bound = false;
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.speed_menu, menu);
		MenuItemCompat.setShowAsAction(menu.findItem(R.id.preferences), 1);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.preferences:
				Intent intent = new Intent(this, PreferencesActivity.class);
				startActivity(intent);
				break;
		}
		return true;
	}

}
