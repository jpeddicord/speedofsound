package net.codechunk.speedofsound;

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
	/**
	 * Logging tag.
	 */
	private static final String TAG = "SoundService";

	/**
	 * Intent extra to set the tracking state.
	 */
	public static final String SET_TRACKING_STATE = "set-tracking-state";

	/**
	 * Broadcast to notify that tracking has started or stopped.
	 */
	public static final String TRACKING_STATE_BROADCAST = "tracking-state-changed";

	/**
	 * Location, speed, and sound update broadcast.
	 */
	public static final String LOCATION_UPDATE_BROADCAST = "location-update";

	/**
	 * Application shared preferences. Used to load speed/volume settings.
	 */
	private SharedPreferences settings;

	/**
	 * Broadcast manager to send speed/volume updates to the UI.
	 */
	private LocalBroadcastManager localBroadcastManager;

	/**
	 * Sound service manager instance.
	 */
	private SoundServiceManager soundServiceManager = new SoundServiceManager();

	/**
	 * Audio manager to control the system media volume.
	 */
	private AudioManager audioManager;

	/**
	 * System maximum volume. Typically 255, but might be different on some
	 * platforms.
	 */
	private int maxVolume;

	/**
	 * Volume changing thread.
	 */
	private VolumeThread volumeThread = null;

	/**
	 * Location manager to receive and process new GPS speeds/values.
	 */
	private LocationManager locationManager;

	/**
	 * Speed averager/smoother.
	 */
	private AverageSpeed averager = new AverageSpeed(6);

	/**
	 * Service binder for activities to communicate with this service.
	 */
	private LocalBinder binder = new LocalBinder();

	/**
	 * The current tracking state.
	 */
	public boolean tracking = false;

	/**
	 * Our local database for storing song/location info.
	 */
	private DatabaseManager db;

	/**
	 * The current song being played.
	 */
	private String song = "Unknown";

	/**
	 * A random color generator.
	 */
	private ColorCreator cc = new ColorCreator();

	/**
	 * Start up the service and initialize some values. Does not start tracking.
	 */
	@Override
	public void onCreate()
	{
		Log.d(TAG, "Service starting up");

		// set up preferences
		PreferencesActivity.setDefaults(this);
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		PreferencesActivity.updateNativeSpeeds(this.settings);

		// register handlers & audio
		this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
		this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		this.db = DatabaseManager.getDBManager(this);

		// listen to certain broadcasts
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.android.music.metachanged");
		filter.addAction("com.android.music.playstatechanged");
		filter.addAction("com.android.music.playbackcomplete");
		filter.addAction("com.android.music.queuechanged");
		this.registerReceiver(this.broadcastReceiver, filter);

		// activation broadcasts
		IntentFilter activationFilter = this.soundServiceManager.activationIntents();
		this.registerReceiver(this.soundServiceManager, activationFilter);
	}

	/**
	 * Handle a start command.
	 * 
	 * Return sticky mode to tell Android to keep the service active.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.d(TAG, "Start command received");

		// check if we've been commanded to start or stop tracking
		Bundle extras = intent.getExtras();
		if (extras != null && extras.containsKey(SoundService.SET_TRACKING_STATE))
		{
			Log.v(TAG, "Commanded to change state");
			if (extras.getBoolean(SoundService.SET_TRACKING_STATE))
			{
				this.startTracking();
			}
			else
			{
				this.stopTracking();
			}
		}

		return START_STICKY;
	}

	/**
	 * Service shut-down log.
	 */
	@Override
	public void onDestroy()
	{
		Log.d(TAG, "Service shutting down");

		// unregister receivers
		this.unregisterReceiver(this.broadcastReceiver);

		// Close the database
		this.db.close();
	}

	/**
	 * Start tracking. Find the best location provider (likely GPS), create an
	 * ongoing notification, and request location updates.
	 */
	public void startTracking()
	{
		// ignore requests when we're already tracking
		if (this.tracking)
		{
			return;
		}

		// request updates
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		String provider = this.locationManager.getBestProvider(criteria, true);
		this.locationManager.requestLocationUpdates(provider, 0, 0, this.locationUpdater);

		// start up the volume thread
		if (this.volumeThread == null)
		{
			this.volumeThread = new VolumeThread(this);
			this.volumeThread.start();
		}

		// force foreground with an ongoing notification
		Intent notificationIntent = new Intent(this, SpeedActivity.class);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setContentTitle(getString(R.string.app_name));
		builder.setContentText(getString(R.string.notification_text));
		builder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0));
		builder.setTicker(getString(R.string.ticker_text));
		builder.setSmallIcon(R.drawable.ic_notification);
		builder.setWhen(System.currentTimeMillis());
		startForeground(R.string.notification_text, builder.getNotification());

		// let everyone know
		Intent intent = new Intent(SoundService.TRACKING_STATE_BROADCAST);
		intent.putExtra("tracking", true);
		SoundService.this.localBroadcastManager.sendBroadcast(intent);

		this.tracking = true;
		Log.d(TAG, "Tracking started with location provider " + provider);

		// Clear the database for new tracking data
		this.db.resetDB();
	}

	/**
	 * Stop tracking. Remove the location updates and notification.
	 */
	public void stopTracking()
	{
		// don't do anything if we're not tracking
		if (!this.tracking)
		{
			return;
		}

		// shut off the volume thread
		if (this.volumeThread != null)
		{
			this.volumeThread.interrupt();
			this.volumeThread = null;
		}

		// disable location updates
		this.locationManager.removeUpdates(this.locationUpdater);

		// remove notification and go to background
		stopForeground(true);

		// let everyone know
		Intent intent = new Intent(SoundService.TRACKING_STATE_BROADCAST);
		intent.putExtra("tracking", false);
		SoundService.this.localBroadcastManager.sendBroadcast(intent);

		this.tracking = false;
		Log.d(TAG, "Tracking stopped");
	}

	/**
	 * Update the system volume based on the given speed. Reads preferences
	 * on-the-fly for low and high speeds and volumes. If below the minimum
	 * speed, uses the minimum volume. If above the maximum speed, uses the max
	 * volume. If somewhere in between, use a log scaling function to smoothly
	 * scale the volume.
	 * 
	 * @param speed
	 *            Reference speed to base volume on
	 * @return Set volume (0-100).
	 */
	private int updateVolume(float speed)
	{
		float volume = 0.0f;

		float lowSpeed = this.settings.getFloat("low_speed", 0);
		int lowVolume = this.settings.getInt("low_volume", 0);
		float highSpeed = this.settings.getFloat("high_speed", 100);
		int highVolume = this.settings.getInt("high_volume", 100);

		if (speed < lowSpeed)
		{
			// minimum volume
			Log.d(TAG, "Low speed triggered at " + speed);
			volume = lowVolume / 100.0f;
		}
		else if (speed > highSpeed)
		{
			// high volume
			Log.d(TAG, "High speed triggered at " + speed);
			volume = highVolume / 100.0f;
		}
		else
		{
			// log scaling
			float volumeRange = (highVolume - lowVolume) / 100.0f;
			float speedRangeFrac = (speed - lowSpeed) / (highSpeed - lowSpeed);
			float volumeRangeFrac = (float) (Math.log1p(speedRangeFrac) / Math.log1p(1));
			volume = lowVolume / 100.0f + volumeRange * volumeRangeFrac;
			Log.d(TAG, "Log scale triggered with " + speed + ", using volume " + volume);
		}

		// apply the volume
		this.volumeThread.setTargetVolume((int) (this.maxVolume * volume));
		return (int) (volume * 100);
	}

	/**
	 * Add a location to the song database.
	 * 
	 * @param location
	 *            Location/point to store.
	 */
	private void addPoint(Location location)
	{
		long songid = this.db.getSongId(this.song);
		double longitude = location.getLongitude();
		double latitude = location.getLatitude();

		if (songid < 0)
		{
			Log.d(TAG, "Song did not exist in db. Adding. Song id: " + songid);

			int pathcolor = cc.getColor();

			this.db.addSong(this.song, pathcolor);

			songid = this.db.getSongId(this.song);

			Log.d(TAG, "Song added. Song id: " + songid);
		}

		int longitudeE6 = (int) (longitude * 1000000);
		int latitudeE6 = (int) (latitude * 1000000);

		this.db.addPoint(songid, latitudeE6, longitudeE6);
	}

	/**
	 * Custom location listener. Triggers volume changes based on the current
	 * average speed.
	 */
	private LocationListener locationUpdater = new LocationListener()
	{
		private Location previousLocation = null;
		private long previousTime = 0;

		/**
		 * How often the location is stored in the database in milliseconds.
		 * Saving more often will result in a smoother path but a larger
		 * database.
		 */
		private static final int POINT_FREQ = 1000;

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

			// get the time
			long time;

			time = location.getTime();

			if (time - this.previousTime >= POINT_FREQ)
			{
				// update previous time since we are saving a point now
				this.previousTime = time;

				Log.v(TAG, "Adding new point to database");
				SoundService.this.addPoint(location);
			}

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

			// push average to filter out spikes
			Log.v(TAG, "Pushing speed " + speed);
			SoundService.this.averager.push(speed);

			// update the speed
			float avg = SoundService.this.averager.getAverage();
			Log.v(TAG, "Average currently " + avg);
			int volume = SoundService.this.updateVolume(avg);

			// send out a local broadcast with the details
			Intent intent = new Intent(SoundService.LOCATION_UPDATE_BROADCAST);
			intent.putExtra("location", location);
			intent.putExtra("speed", speed);
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
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.d(TAG, "Received intent " + action);

			// music actions
			if (action.equals("com.android.music.metachanged") ||
					action.equals("com.android.music.playstatechanged") ||
					action.equals("com.android.music.playbackcomplete") ||
					action.equals("com.android.music.queuechanged"))
			{
				String artist = intent.getStringExtra("artist");
				String track = intent.getStringExtra("track");
				Log.d(TAG, "Track changed: " + track + " by " + artist);

				SoundService.this.song = track + " - " + artist;
			}
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
