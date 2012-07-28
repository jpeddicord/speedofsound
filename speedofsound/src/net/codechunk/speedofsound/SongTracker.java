package net.codechunk.speedofsound;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class SongTracker
{
	private static final String TAG = "SongTracker";

	/**
	 * SQLite opener.
	 */
	private SQLiteOpener sqlite;

	/**
	 * DB write access.
	 */
	private SQLiteDatabase db;

	/**
	 * Maximum update rate in ms.
	 */
	private static final int UPDATE_RATE = 1500;

	/**
	 * The last known location.
	 */
	private Location previousLocation;

	/**
	 * The current route.
	 */
	private long routeId;

	private String songTrack;
	private String songArtist;
	private String songAlbum;

	SongTracker(Context context)
	{
		this.sqlite = new SQLiteOpener(context);
		this.db = this.sqlite.getWritableDatabase();

		// subscribe to song and location broadcasts
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
		IntentFilter filter = new IntentFilter();
		filter.addAction(SoundService.LOCATION_UPDATE_BROADCAST);
		lbm.registerReceiver(this.messageReceiver, filter);
	}

	/**
	 * Start a new route.
	 * 
	 * TODO: it would be nice if we could detect the age of the previous route
	 * and use that instead, if it wasn't too long ago, say, 30s.
	 * 
	 * @return the route ID
	 */
	public long startRoute()
	{
		// XXX: we're not supporting multiple routes in 0.8 initially.
		// but when we do, get rid of this!
		this.db.delete("points", null, null);
		this.db.delete("songs", null, null);
		this.db.delete("routes", null, null);
		
		// grab the current time
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();

		// store the route
		ContentValues values = new ContentValues();
		values.put("name", df.format(date));
		values.put("start", df.format(date));
		this.routeId = this.db.insert("routes", null, values);

		Log.d(TAG, "Starting new route with id " + this.routeId);
		return this.routeId;
	}

	/**
	 * Delete a route and all of its associated data.
	 * 
	 * @param routeId
	 *            the ID of the route to delete
	 */
	public void deleteRoute(long routeId)
	{
		String[] deleteArgs = new String[] {Long.toString(routeId)};
		this.db.delete("points", "route_id = ?", deleteArgs);
		this.db.delete("songs", "route_id = ?", deleteArgs);
		this.db.delete("routes", "id = ?", deleteArgs);
	}

	/**
	 * Find a song's id, creating an entry if it doesn't have one.
	 * 
	 * @param routeId
	 *            Route ID of the song.
	 * @param track
	 *            Track name
	 * @param artist
	 *            Song artist
	 * @param album
	 *            Song album
	 * @return
	 */
	private long findSong(long routeId, String track, String artist, String album)
	{
		Cursor cursor = this.db.query("songs", new String[] { "id" },
				"track = ? AND artist = ? AND album = ?",
				new String[] { track, artist, album },
				null, null, null);
		cursor.moveToFirst();

		// create the song if it wasn't found
		if (cursor.isAfterLast())
		{
			cursor.close();
			ContentValues values = new ContentValues();
			values.put("route_id", routeId);
			values.put("track", track);
			values.put("artist", artist);
			values.put("album", album);
			return this.db.insert("songs", null, values);
		}
		else
		{
			long id = cursor.getLong(0);
			cursor.close();
			return id;
		}
	}

	/**
	 * Local broadcast receiver for location and song updates.
	 */
	private BroadcastReceiver messageReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();

			// new location reported
			if (action.equals(SoundService.LOCATION_UPDATE_BROADCAST))
			{
				Location location = intent.getParcelableExtra("location");
				SongTracker.this.locationUpdate(location);
			}
			// new song reported
			if (action.equals("XXXXX"))
			{
				// TODO
			}
		}
	};

	/**
	 * Process a location update broadcast.
	 * 
	 * @param location
	 *            New location.
	 */
	private void locationUpdate(Location location)
	{
		// ignore if we have no route yet
		if (this.routeId == 0)
			return;

		// we must have song information
		if (this.songTrack == null && this.songArtist == null && this.songAlbum == null)
			return;

		// rate limiting
		if (location.getTime() - this.previousLocation.getTime() < SongTracker.UPDATE_RATE)
			return;

		// get the data we need to store
		int latitudeE6 = (int) (location.getLatitude() * 1000000);
		int longitudeE6 = (int) (location.getLongitude() * 1000000);
		long songId = this.findSong(this.routeId, this.songTrack, this.songArtist, this.songAlbum);

		// store the point
		ContentValues values = new ContentValues();
		values.put("route_id", this.routeId);
		values.put("song_id", songId);
		values.put("latitude", latitudeE6);
		values.put("longitude", longitudeE6);
		this.db.insert("points", null, values);

		// store the location
		this.previousLocation = location;
	}

	/**
	 * Android hook to open the SQLite database.
	 */
	private class SQLiteOpener extends SQLiteOpenHelper
	{
		public static final String DB_NAME = "songtracker";
		public static final int DB_VERSION = 2;

		public SQLiteOpener(Context context)
		{
			super(context, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db)
		{
			db.execSQL("CREATE TABLE routes (" +
					"id INTEGER PRIMERY KEY AUTOINCREMENT NOT NULL," +
					"name TEXT," +
					"start DATETIME," +
					"end DATETIME);");
			db.execSQL("CREATE TABLE songs (" +
					"id INTEGER PRIMERY KEY AUTOINCREMENT NOT NULL," +
					"route_id INTEGER," +
					"track TEXT," +
					"artist TEXT," +
					"album TEXT);");
			db.execSQL("CREATE TABLE points (" +
					"id INTEGER PRIMERY KEY AUTOINCREMENT NOT NULL," +
					"route_id INTEGER," +
					"song_id INTEGER," +
					"latitude INTEGER," +
					"longitude INTEGER);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
		}

	};
}
