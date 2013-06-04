package net.codechunk.speedofsound;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import net.codechunk.speedofsound.util.ColorCreator;
import net.codechunk.speedofsound.util.SongInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


/**
 * Draws a path based on the points in the database. Paths are colored based on
 * the song that was being listened to at that point.
 */
public class MapperActivity extends FragmentActivity
{
	private static final String TAG = "DrawMapActivity";
	private static final float ZOOM_LEVEL = 16.0f;

	private GoogleMap map;
	private TableLayout songTable;

	private SongTracker songTracker;
	private ColorCreator colorCreator = new ColorCreator();
	private Map<Long, Integer> songColors = new HashMap<Long, Integer>();

	private class SongSet
	{
		SongInfo song;
		ArrayList<LatLng> points;
	}

	private ArrayList<SongSet> mapContent = new ArrayList<SongSet>();


	/**
	 * Set up the map and overlay.
	 */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.mapper);

		this.songTracker = SongTracker.getInstance(this);
		this.songTable = (TableLayout) findViewById(R.id.song_table);

		// activate the up functionality on the action bar
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			ActionBar ab = this.getActionBar();
			if (ab != null) {
				ab.setHomeButtonEnabled(true);
				ab.setDisplayHomeAsUpEnabled(true);
			}
		}

		// load the map
		this.map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment)).getMap();
		if (this.map == null) {
			Log.e(TAG, "Couldn't load map");
			this.finish();
		}
	}

	/**
	 * Updates the path on resume.
	 */
	@Override
	public void onResume()
	{
		super.onResume();

		// update the path, table, and map
		this.mapContent = this.getPath();
		this.displayTable(this.mapContent);
		this.drawPaths(this.mapContent);

		// zoom the map in to an appropriate spot if there are points
		if (this.mapContent.size() > 0)
		{
			SongSet loc = this.mapContent.get(mapContent.size() - 1);

			if (loc.points.size() > 0)
			{
				LatLng lastpoint = loc.points.get(loc.points.size() - 1);

				this.map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastpoint, MapperActivity.ZOOM_LEVEL));
			}
		}
	}

	/**
	 * Fetch a list of paths for stored routes.
	 */
	private ArrayList<SongSet> getPath()
	{
		ArrayList<SongSet> data = new ArrayList<SongSet>();

		// XXX: 0.8 hack to get the only route
		SQLiteDatabase db = this.songTracker.getReadableDatabase();
		Cursor routeCursor = db.query("routes", new String[] { "id" },
				null, null, null, null, "id DESC", "1");
		routeCursor.moveToFirst();
		if (routeCursor.isAfterLast())
		{
			Log.w(TAG, "No routes found");
			routeCursor.close();
			return data;
		}
		long routeId = routeCursor.getLong(0);
		routeCursor.close();

		// get the points from the route
		Log.v(TAG, "Fetching path of route " + routeId);
		Cursor cursor = this.songTracker.getRoutePoints(routeId);

		long prevSongId = -1;
		ArrayList<LatLng> points = new ArrayList<LatLng>();

		// iterate the cursor building up a structure
		cursor.moveToFirst();
		while (!cursor.isAfterLast())
		{
			Log.v(TAG, "Adding point");
			// unpack row data
			long songId = cursor.getLong(1);
			int latitudeE6 = cursor.getInt(2);
			int longitudeE6 = cursor.getInt(3);

			double lat = latitudeE6 / 1000000.0f;
			double lon = longitudeE6 / 1000000.0f;

			// new song => new meta
			if (songId != prevSongId)
			{
				prevSongId = songId;

				// store song data
				SongSet loc = new SongSet();
				loc.song = this.songTracker.getSongInfo(songId);

				// set a color if we don't have one
				if (!this.songColors.containsKey(songId))
				{
					this.songColors.put(songId, this.colorCreator.getColor());
				}

				// reference a new list in the location structure
				points = new ArrayList<LatLng>();
				loc.points = points;
				data.add(loc);
			}

			// add the point to the active point list
			points.add(new LatLng(lat, lon));

			cursor.moveToNext();
		}

		cursor.close();
		return data;
	}

	/**
	 * Displays a list of songs along with the color of the path for that song.
	 */
	private void displayTable(ArrayList<SongSet> paths)
	{
		// Disallows duplicate songs to be added to the table even if two paths
		// have the same song and color
		HashSet<Long> songs = new HashSet<Long>();

		// for each path
		for (SongSet loc : paths)
		{
			// ensure the song isn't listed yet
			if (songs.contains(loc.song.id))
				continue;

			TableRow tableRow = new TableRow(this);

			// make a colored block
			TextView colorTV = new TextView(this);
			colorTV.setText("\u25A0");
			colorTV.setTextColor(this.songColors.get(loc.song.id));
			colorTV.setGravity(Gravity.CENTER);
			colorTV.setTextSize(20f);
			tableRow.addView(colorTV);

			// get the song name
			String song;
			if (loc.song == null)
			{
				song = "Unknown";
			}
			else
			{
				song = loc.song.track;
			}

			// create a text view for the song name
			TextView songTV = new TextView(this);
			songTV.setText(song);
			songTV.setTextSize(18f);
			tableRow.addView(songTV);

			songTable.addView(tableRow);

			songs.add(loc.song.id);
		}
	}

	/**
	 * Draw the song paths on the map.
	 */
	private void drawPaths(ArrayList<SongSet> paths) {
		// draw all the paths
		for (SongSet loc : paths) {
			// generate a new line
			PolylineOptions opts = new PolylineOptions();
			opts.color(this.songColors.get(loc.song.id));
			opts.width(6);
			opts.addAll(loc.points);

			// add it
			this.map.addPolyline(opts);
		}
	}

//	/**
//	 * A map overlay to draw songs listened to as colored lines on the map.
//	 */
//	class SongOverlay extends Overlay
//	{
//		/**
//		 * Maximum distance to draw a line between points.
//		 */
//
//		public SongOverlay()
//		{
//		}
//
//		/**
//		 * Draws the path created by the geopoints on an overlay.
//		 */
//		public void draw(Canvas canvas, MapView mapv, boolean shadow)
//		{
//			super.draw(canvas, mapv, shadow);
//
//			GeoPoint previous = null;
//			GeoPoint next = null;
//
//			// For each path
//			for (SongSet loc : MapperActivity.this.mapContent)
//			{
//				Path path = new Path();
//
//				Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
//				paint.setStyle(Paint.Style.FILL_AND_STROKE);
//				paint.setStrokeJoin(Paint.Join.ROUND);
//				paint.setStrokeCap(Paint.Cap.ROUND);
//				paint.setStrokeWidth(6);
//
//				// Get the color for this path
//				paint.setColor(MapperActivity.this.songColors.get(loc.song.id));
//
//				// for each geopoint on this path
//				for (GeoPoint point : loc.points)
//				{
//					// ensure a previous exists to look for distance
//					if (previous != null)
//					{
//						int oldlong = previous.getLongitudeE6();
//						int oldlat = previous.getLatitudeE6();
//
//						int newlong = point.getLongitudeE6();
//						int newlat = point.getLatitudeE6();
//
//						// if the distance is too large then the path isn't
//						// drawn. Prevents large lines being drawn
//						// at times when the gps cuts out and can't record
//						// points.
//						double dist = Math.sqrt(Math.pow((oldlong - newlong), 2) + Math.pow((oldlat - newlat), 2));
//
//						if (dist >= SongOverlay.MAX_DIST)
//						{
//							previous = null;
//							next = null;
//						}
//					}
//
//					// Checking for first point
//					if (next == null)
//					{
//						next = point;
//					}
//					else
//					// draw between the two points
//					{
//						previous = next;
//						next = point;
//
//						Point p1 = new Point();
//						Point p2 = new Point();
//
//						// convert to pixels. This takes the most processing
//						// time in this class.
//						// Not much can really be done about it.
//						projection.toPixels(previous, p1);
//						projection.toPixels(next, p2);
//
//						// Create the path
//						path.moveTo(p1.x, p1.y);
//						path.lineTo(p2.x, p2.y);
//					}
//				}
//
//				// draw it
//				canvas.drawPath(path, paint);
//			}
//		}
//	}

	/**
	 * Handle the home button press on the action bar.
	 */
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				Intent intent = new Intent(this, SpeedActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				break;
		}
		return true;
	}

}
