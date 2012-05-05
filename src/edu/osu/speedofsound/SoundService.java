package edu.osu.speedofsound;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

public class SoundService extends Service
{

	private static final String TAG = "SoundService";

	@Override
	public void onCreate()
	{
		Log.d(TAG, "Service starting up");
		HandlerThread thread = new HandlerThread("SoundServiceThread",
				Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// TODO: register location handlers
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.d(TAG, "Start command received");

		// force foreground TODO: remove depreciations
		Notification notification = new Notification(R.drawable.ic_launcher, "Speed of Sound is running",
				System.currentTimeMillis());
		Intent notificationIntent = new Intent(this, SpeedActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(this, "Speed of Sound", "Currently running", pendingIntent);
		startForeground(9001, notification); // XXX: 1 is a magic number/id

		return START_STICKY;
	}

	@Override
	public void onDestroy()
	{
		// TODO: unregister service / location handlers
		Log.d(TAG, "Service shutting down");
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

}
