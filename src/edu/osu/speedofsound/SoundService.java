package edu.osu.speedofsound;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

public class SoundService extends Service
{
	private static final String TAG = "SoundService";

	private SharedPreferences settings;
	private LocalBroadcastManager broadcastManager;
	private AudioManager audioManager;
	private int maxVolume;
	private LocationUpdater locationUpdater;
	private LocationManager locationManager;
	private AverageSpeed averager = new AverageSpeed(6);
	private LocalBinder binder = new LocalBinder();
	public boolean tracking = false;

	@Override
	public void onCreate()
	{
		Log.d(TAG, "Service starting up");

		// register handlers & audio
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		this.broadcastManager = LocalBroadcastManager.getInstance(this);
		this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.locationUpdater = new LocationUpdater();
		this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.d(TAG, "Start command received");

		return START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		Log.d(TAG, "Service shutting down");
	}

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
		builder.setContentTitle("Speed of Sound");
		builder.setContentText("Tracking your speed");
		builder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0));
		builder.setTicker("Speed of Sound is running");
		builder.setSmallIcon(R.drawable.ic_launcher);
		builder.setWhen(System.currentTimeMillis());
		startForeground(9001, builder.getNotification());

		this.tracking = true;
		Log.d(TAG, "Tracking started with location provider " + provider);
	}

	public void stopTracking()
	{
		this.tracking = false;
		Log.d(TAG, "Tracking stopped");

		// disable location updates
		this.locationManager.removeUpdates(this.locationUpdater);

		// remove notification and go to background
		stopForeground(true);
	}

	private float convertMPH(float metersPerSecond)
	{
		return (float) (2.237 * metersPerSecond);
	}

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
			float speedRangeFrac = (mphSpeed - lowSpeed)
					/ (highSpeed - lowSpeed);
			float volumeRangeFrac = (float) (Math.log1p(speedRangeFrac) / Math
					.log1p(1));
			volume = lowVolume / 100.0f + volumeRange * volumeRangeFrac;
			Log.d(TAG, "Log scale triggered, using volume " + volume);
		}

		// apply the volume
		this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
				(int) (this.maxVolume * volume), 0);
		return (int) (volume * 100);
	}

	private class LocationUpdater implements LocationListener
	{
		private Location previousLocation = null;

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
					float timeDelta = location.getTime()
							- previousLocation.getTime();

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
			SoundService.this.broadcastManager.sendBroadcast(intent);
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
	}

	public class LocalBinder extends Binder
	{
		public SoundService getService()
		{
			return SoundService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return this.binder;
	}

}
