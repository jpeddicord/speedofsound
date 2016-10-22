package net.codechunk.speedofsound.service;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.codechunk.speedofsound.R;
import net.codechunk.speedofsound.SongTracker;
import net.codechunk.speedofsound.SpeedActivity;
import net.codechunk.speedofsound.util.AppPreferences;

/**
 * The main sound control service.
 *
 * Responsible for adjusting the volume based on the current speed. Can be
 * started and stopped externally, but is largely independent.
 */
public class SoundService extends Service {
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
	 * The current tracking state.
	 */
	private boolean tracking = false;

	private GoogleApiClient googleApiClient;
	private LocalBroadcastManager localBroadcastManager;
	private SoundServiceManager soundServiceManager = new SoundServiceManager();

	private SharedPreferences settings;
	private VolumeThread volumeThread = null;
	private LocalBinder binder = new LocalBinder();
	private VolumeConversion volumeConversion;
	private SongTracker songTracker;

	/**
	 * Start up the service and initialize some values. Does not start tracking.
	 */
	@Override
	public void onCreate() {
		Log.d(TAG, "Service starting up");

		// connect to google api stuff, because for some reason you need to do that for location
		this.googleApiClient = new GoogleApiClient.Builder(this)
				.addApi(LocationServices.API)
				.build();

		// set up preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		AppPreferences.runUpgrade(this);
		AppPreferences.updateNativeSpeeds(settings);

		// register handlers & audio
		this.localBroadcastManager = LocalBroadcastManager.getInstance(this);
		this.volumeConversion = new VolumeConversion();
		this.volumeConversion.onSharedPreferenceChanged(this.settings, null); // set initial
		this.songTracker = SongTracker.getInstance(this);

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
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "Start command received");

		this.googleApiClient.connect();

		// register pref watching
		this.settings.registerOnSharedPreferenceChangeListener(this.volumeConversion);

		// check if we've been commanded to start or stop tracking
		if (intent != null) {
			Bundle extras = intent.getExtras();
			if (extras != null && extras.containsKey(SoundService.SET_TRACKING_STATE)) {
				Log.v(TAG, "Commanded to change state");
				if (extras.getBoolean(SoundService.SET_TRACKING_STATE)) {
					this.startTracking();
				} else {
					this.stopTracking();
				}
			}
		}

		return START_STICKY;
	}

	/**
	 * Service shut-down.
	 */
	@Override
	public void onDestroy() {
		Log.d(TAG, "Service shutting down");

		this.settings.unregisterOnSharedPreferenceChangeListener(this.volumeConversion);
		this.googleApiClient.disconnect();
	}

	public boolean isTracking() {
		return this.tracking;
	}

	/**
	 * Start tracking. Find the best location provider (likely GPS), create an
	 * ongoing notification, and request location updates.
	 */
	public void startTracking() {
		// ignore requests when we're already tracking
		if (this.tracking) {
			return;
		}

		// check runtime permission
		int hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (hasPermission != PackageManager.PERMISSION_GRANTED) {
			SoundService.showNeedLocationToast(this);
			return;
		}

		// request location updates
		LocationRequest req = new LocationRequest();
		req.setInterval(1000);
		req.setFastestInterval(500);
		req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		LocationServices.FusedLocationApi.requestLocationUpdates(
				this.googleApiClient, req, this.locationUpdater);

		// start a new route
		this.songTracker.startRoute();

		// start up the volume thread
		if (this.volumeThread == null) {
			this.volumeThread = new VolumeThread(this);
			this.volumeThread.start();
		}

		// show the notification
		startForeground(R.string.notification_text, getNotification());

		// let everyone know
		Intent intent = new Intent(SoundService.TRACKING_STATE_BROADCAST);
		intent.putExtra("tracking", true);
		SoundService.this.localBroadcastManager.sendBroadcast(intent);

		this.tracking = true;
		Log.d(TAG, "Tracking started");
	}

	public static void showNeedLocationToast(Context ctx) {
		Toast toast = Toast.makeText(ctx, ctx.getString(R.string.no_location_providers), Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * Build a fancy-pants notification.
	 */
	private Notification getNotification() {
		// force foreground with an ongoing notification
		Intent notificationIntent = new Intent(this, SpeedActivity.class);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setContentTitle(getString(R.string.app_name));
		builder.setContentText(getString(R.string.notification_text));
		builder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0));
		builder.setTicker(getString(R.string.ticker_text));
		builder.setSmallIcon(R.drawable.ic_notification);
		builder.setWhen(System.currentTimeMillis());

		// 4.1+ actions
		Intent stopIntent = new Intent(this, SoundService.class);
		stopIntent.putExtra(SoundService.SET_TRACKING_STATE, false);
		builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
				PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT));
		return builder.build();
	}

	/**
	 * Stop tracking. Remove the location updates and notification.
	 */
	public void stopTracking() {
		// don't do anything if we're not tracking
		if (!this.tracking) {
			return;
		}

		// shut off the volume thread
		if (this.volumeThread != null) {
			this.volumeThread.interrupt();
			this.volumeThread = null;
		}

		// end the current route
		this.songTracker.endRoute();

		// disable location updates
		LocationServices.FusedLocationApi.removeLocationUpdates(
				this.googleApiClient, this.locationUpdater);

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
	 * Custom location listener. Triggers volume changes based on the current
	 * average speed.
	 */
	private LocationListener locationUpdater = new LocationListener() {
		private Location previousLocation = null;

		/**
		 * Change the volume based on the current average speed. If speed is not
		 * available from the current location provider, calculate it from the
		 * previous location. After updating the average and updating the
		 * volume, send out a broadcast notifying of the changes.
		 */
		public void onLocationChanged(Location location) {
			// some stupid phones occasionally send a null location.
			// who does that, seriously.
			if (location == null)
				return;

			// use the GPS-provided speed if available
			float speed;
			if (location.hasSpeed()) {
				speed = location.getSpeed();
			} else {
				// speed fall-back (mostly for the emulator)
				if (this.previousLocation != null) {
					// get the distance between this and the previous update
					float meters = previousLocation.distanceTo(location);
					float timeDelta = location.getTime() - previousLocation.getTime();

					Log.v(TAG, "Location distance: " + meters);

					// convert to meters/second
					speed = 1000 * meters / timeDelta;
				} else {
					speed = 0;
				}

				this.previousLocation = location;
			}

			float volume = SoundService.this.volumeConversion.speedToVolume(speed);
			SoundService.this.volumeThread.setTargetVolume(volume);

			// send out a local broadcast with the details
			Intent intent = new Intent(SoundService.LOCATION_UPDATE_BROADCAST);
			intent.putExtra("location", location);
			intent.putExtra("speed", speed);
			intent.putExtra("volumePercent", (int) (volume * 100));
			SoundService.this.localBroadcastManager.sendBroadcast(intent);
		}
	};

	/**
	 * Service-level access for external classes and activities.
	 */
	public class LocalBinder extends Binder {
		/**
		 * Return the service associated with this binder.
		 */
		public SoundService getService() {
			return SoundService.this;
		}
	}

	/**
	 * Return the binder associated with this service.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return this.binder;
	}

}
