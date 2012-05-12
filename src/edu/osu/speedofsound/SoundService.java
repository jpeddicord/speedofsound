package edu.osu.speedofsound;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * The main sound control service.
 * 
 * Responsible for adjusting the volume based on the current speed. Can be
 * started and stopped externally, but is largely independent.
 */
public class SoundService extends Service
{
	private static final String TAG = "SoundService";

	private SharedPreferences settings;
	private LocalBroadcastManager localBroadcastManager;
	private AudioManager audioManager;
	private int maxVolume;
	private LocationManager locationManager;
	private AverageSpeed averager = new AverageSpeed(6);
	private LocalBinder binder = new LocalBinder();
	public boolean tracking = false;

	/**
	 * Start up the service and initialize some values. Does not start tracking.
	 */
	@Override
	public void onCreate()
	{
		Log.d(TAG, "Service starting up");

		// register handlers & audio
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
		this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		// listen to certain broadcasts
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.intent.action.ACTION_POWER_CONNECTED");
		filter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED");
		filter.addAction("android.intent.action.ENTER_CAR_MODE");
		filter.addAction("android.intent.action.EXIT_CAR_MODE");
		filter.addAction("android.intent.action.HEADSET_PLUG");
		this.registerReceiver(this.broadcastReceiver, filter);
	}

	/**
	 * Return sticky mode to tell Android to keep the service active.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.d(TAG, "Start command received");

		return START_STICKY;
	}

	/**
	 * Service shut-down log.
	 */
	@Override
	public void onDestroy()
	{
		Log.d(TAG, "Service shutting down");
	}

	/**
	 * Start tracking. Find the best location provider (likely GPS), create an
	 * ongoing notification, and request location updates.
	 */
	public void startTracking()
	{
		// request updates
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		String provider = this.locationManager.getBestProvider(criteria, true);
		this.locationManager.requestLocationUpdates(provider, 0, 0, this.locationUpdater);

		// force foreground with an ongoing notification
		Intent notificationIntent = new Intent(this, SpeedActivity.class);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setContentTitle(getString(R.string.app_name));
		builder.setContentText(getString(R.string.notification_text));
		builder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0));
		builder.setTicker(getString(R.string.ticker_text));
		builder.setSmallIcon(R.drawable.ic_launcher);
		builder.setWhen(System.currentTimeMillis());
		startForeground(R.string.notification_text, builder.getNotification());

		this.tracking = true;
		Log.d(TAG, "Tracking started with location provider " + provider);
	}

	/**
	 * Stop tracking. Remove the location updates and notification.
	 */
	public void stopTracking()
	{
		this.tracking = false;
		Log.d(TAG, "Tracking stopped");

		// disable location updates
		this.locationManager.removeUpdates(this.locationUpdater);

		// remove notification and go to background
		stopForeground(true);
	}

	/**
	 * Convert m/s to MPH.
	 * 
	 * @param metersPerSecond
	 *            Meters per second to convert
	 * @return Speed in MPH.
	 */
	private float convertMPH(float metersPerSecond)
	{
		return (float) (2.237 * metersPerSecond);
	}

	/**
	 * Update the system volume based on the given speed. Reads preferences
	 * on-the-fly for low and high speeds and volumes. If below the minimum
	 * speed, uses the minimum volume. If above the maximum speed, uses the max
	 * volume. If somewhere in between, use a log scaling function to smoothly
	 * scale the volume.
	 * 
	 * @param mphSpeed
	 *            Reference speed to base volume on
	 * @return Set volume (0-100).
	 */
	private int updateVolume(float mphSpeed)
	{
		/*
		 * Instead of changing the volume directly, we may want to update a
		 * "target volume" and have something else process that to slowly
		 * approach it.
		 */
		float volume = 0.0f;

		int lowSpeed = this.settings.getInt("low_speed", 15);
		int lowVolume = this.settings.getInt("low_volume", 60);
		int highSpeed = this.settings.getInt("high_speed", 50);
		int highVolume = this.settings.getInt("high_volume", 100);

		if (mphSpeed < lowSpeed)
		{
			// minimum volume
			Log.d(TAG, "Low speed triggered");
			volume = lowVolume / 100.0f;
		}
		else if (mphSpeed > highSpeed)
		{
			// high volume
			Log.d(TAG, "High speed triggered");
			volume = highVolume / 100.0f;
		}
		else
		{
			// log scaling
			float volumeRange = (highVolume - lowVolume) / 100.0f;
			float speedRangeFrac = (mphSpeed - lowSpeed) / (highSpeed - lowSpeed);
			float volumeRangeFrac = (float) (Math.log1p(speedRangeFrac) / Math.log1p(1));
			volume = lowVolume / 100.0f + volumeRange * volumeRangeFrac;
			Log.d(TAG, "Log scale triggered, using volume " + volume);
		}

		// apply the volume
		this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (this.maxVolume * volume), 0);
		return (int) (volume * 100);
	}

	/**
	 * Custom location listener. Triggers volume changes based on the current
	 * average speed.
	 */
	private LocationListener locationUpdater = new LocationListener()
	{
		private Location previousLocation = null;

		/**
		 * Change the volume based on the current average speed. If speed is not
		 * available from the current location provider, calculate it from the
		 * previous location. After updating the average and updating the
		 * volume, send out a broadcast notifying of the changes.
		 */
		public void onLocationChanged(Location location)
		{
			// grab the speed
			float speed;

			// use the GPS-provided speed if available
			if (location.hasSpeed())
			{
				speed = location.getSpeed();
			}
			else
			{
				Log.v(TAG, "Location fallback mode");

				// speed fall-back (mostly for the emulator)
				if (this.previousLocation != null)
				{
					// get the distance between this and the previous update
					float meters = previousLocation.distanceTo(location);
					float timeDelta = location.getTime() - previousLocation.getTime();

					Log.v(TAG, "Location distance: " + meters);

					// convert to meters/second
					speed = 1000 * meters / timeDelta;
				}
				else
				{
					speed = 0;
				}

				this.previousLocation = location;
			}

			// convert
			float mph = SoundService.this.convertMPH(Math.abs(speed));

			// push average to filter out spikes
			Log.v(TAG, "Pushing speed " + mph);
			SoundService.this.averager.push(mph);

			// update the speed
			float avg = SoundService.this.averager.getAverage();
			Log.v(TAG, "Average currently " + avg);
			int volume = SoundService.this.updateVolume(avg);

			// send out a local broadcast with the details
			Intent intent = new Intent("speed-sound-changed");
			intent.putExtra("speed", mph);
			intent.putExtra("volume", volume);
			SoundService.this.localBroadcastManager.sendBroadcast(intent);
		}

		public void onProviderDisabled(String provider)
		{
		}

		public void onProviderEnabled(String provider)
		{
		}

		public void onStatusChanged(String provider, int status, Bundle extras)
		{
		}
	};

	/**
	 * Start or stop tracking on certain broadcasts.
	 */
	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		private boolean carMode = false;
		private boolean headphonesPlugged = false;

		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.d(TAG, "Received intent " + action);

			// get power status
			IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent powerStatus = context.registerReceiver(null, filter);
			int plugState = powerStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			boolean powerConnected = (plugState == BatteryManager.BATTERY_PLUGGED_AC ||
					plugState == BatteryManager.BATTERY_PLUGGED_USB);

			// get power preference
			boolean powerPreference = SoundService.this.settings.getBoolean("enable_only_charging", false);
			boolean carmodePreference = SoundService.this.settings.getBoolean("enable_carmode", false);
			boolean headphonePreference = SoundService.this.settings.getBoolean("enable_headphones", false);

			// resume tracking if we're also in a satisfactory mode
			if (action.equals("android.intent.action.ACTION_POWER_CONNECTED"))
			{
				Log.v(TAG, "Power connected");

				// ignore if preference is inactive
				if (!powerPreference)
				{
					return;
				}

				// enable if car mode / headphones requested and active
				if ((carmodePreference && this.carMode) ||
				    (headphonePreference && this.headphonesPlugged))
				{
					SoundService.this.startTracking();
				}
			}

			// stop tracking if desired for only when charging
			else if (action.equals("android.intent.action.ACTION_POWER_DISCONNECTED"))
			{
				Log.v(TAG, "Power disconnected");

				// ignore if preference is inactive
				if (powerPreference)
				{
					Log.v(TAG, "Preference active, stopping tracking");
					SoundService.this.stopTracking();
				}
			}

			// start tracking if desired for car mode
			else if (action.equals("android.intent.action.ENTER_CAR_MODE"))
			{
				Log.v(TAG, "Entered car mode");
				this.carMode = true;

				// check the charge state
				if (powerPreference && !powerConnected)
				{
					return;
				}

				// only do anything if the preference is active
				if (carmodePreference)
				{
					Log.v(TAG, "Preference activated, starting");
					SoundService.this.startTracking();
				}
			}

			// stop when exiting car mode
			else if (action.equals("android.intent.action.EXIT_CAR_MODE"))
			{
				Log.v(TAG, "Exited car mode");
				this.carMode = false;

				if (carmodePreference)
				{
					Log.v(TAG, "Preference activated, stopping");
					SoundService.this.stopTracking();
				}
			}

			// start or stop tracking for headset events
			else if (action.equals("android.intent.action.HEADSET_PLUG"))
			{
				Log.v(TAG, "Headset event");
				this.headphonesPlugged = intent.getIntExtra("state", 0) == 1;

				// ignore if preference not active
				if (!headphonePreference)
				{
					return;
				}

				if (this.headphonesPlugged)
				{
					// ignore if charge preference is set and unplugged
					if (powerPreference && !powerConnected)
					{
						return;
					}

					Log.v(TAG, "Plugged in, starting tracking");
					SoundService.this.startTracking();
				}
				else
				{
					Log.v(TAG, "Unplugged, stopping tracking");
					SoundService.this.stopTracking();
				}
			}

			// TODO: case for both headphones and car mode
		}
	};

	/**
	 * Service-level access for external classes and activities.
	 */
	public class LocalBinder extends Binder
	{
		/**
		 * Return the service associated with this binder.
		 */
		public SoundService getService()
		{
			return SoundService.this;
		}
	}

	/**
	 * Return the binder associated with this service.
	 */
	@Override
	public IBinder onBind(Intent intent)
	{
		return this.binder;
	}

}
