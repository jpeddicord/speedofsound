package net.codechunk.speedofsound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.codechunk.speedofsound.util.ColorCreator;
import net.codechunk.speedofsound.util.SongInfo;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * Draws a path based on the points in the database. Paths are colored based on
 * the song that was being listened to at that point.
 * 
 * @author Andrew
 */
public class MapperActivity extends SherlockMapActivity
{
	private static final String TAG = "DrawMapActivity";

	private MapView mapView;
	private MapController mc;
	private List<Overlay> mapOverlays;
	private Projection projection;

	private SongTracker songTracker;
	private ColorCreator colorCreator = new ColorCreator();
	private Map<Long, Integer> songColors = new HashMap<Long, Integer>();

	private class SongSet
	{
		SongInfo song;
		ArrayList<GeoPoint> points;
	}

	private ArrayList<SongSet> mapContent = new ArrayList<SongSet>();

	private TableLayout songTable;

	/**
	 * Set up the map and overlay.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.mapper);

		this.songTracker = SongTracker.getInstance(this);

		// activate the up functionality on the action bar
		ActionBar ab = this.getSupportActionBar();
		ab.setHomeButtonEnabled(true);
		ab.setDisplayHomeAsUpEnabled(true);

		// load the map view
		mapView = (MapView) findViewById(R.id.mapView);
		mapView.setBuiltInZoomControls(true);
		songTable = (TableLayout) findViewById(R.id.song_table);

		mapOverlays = mapView.getOverlays();
		projection = mapView.getProjection();
		mapOverlays.add(new SongOverlay());
	}

	/**
	 * Updates the path on resume.
	 */
	@Override
	public void onResume()
	{
		super.onResume();

		// update the path & table
		this.mapContent = this.getPath();
		this.displayTable(this.mapContent);

		// zoom the map in to an appropriate spot if there are points
		if (this.mapContent.size() > 0)
		{
			mc = mapView.getController();

			SongSet loc = this.mapContent.get(mapContent.size() - 1);

			if (loc.points.size() > 0)
			{
				GeoPoint lastpoint = loc.points.get(loc.points.size() - 1);

				mc.animateTo(lastpoint);
				mc.setZoom(17);
			}
		}
	}

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
		ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();

		// iterate the cursor building up a structure
		cursor.moveToFirst();
		while (!cursor.isAfterLast())
		{
			// unpack row data
			long songId = cursor.getLong(1);
			int latitudeE6 = cursor.getInt(2);
			int longitudeE6 = cursor.getInt(3);

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
				points = new ArrayList<GeoPoint>();
				loc.points = points;
				data.add(loc);
			}

			// add the point to the active point list
			points.add(new GeoPoint(latitudeE6, longitudeE6));

			cursor.moveToNext();
		}

		cursor.close();
		return data;
	}

	/**
	 * Displays a list of songs along with the color of the path for that song.
	 * 
	 * @param paths
	 *            List of current paths along with their color and song name.
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

	@Override
	protected boolean isRouteDisplayed()
	{
		return false;
	}

	/**
	 * A map overlay to draw songs listened to as colored lines on the map.
	 */
	class SongOverlay extends Overlay
	{
		/**
		 * Maximum distance to draw a line between points.
		 */
		private static final int MAX_DIST = 6000;

		public SongOverlay()
		{
		}

		/**
		 * Draws the path created by the geopoints on an overlay.
		 */
		public void draw(Canvas canvas, MapView mapv, boolean shadow)
		{
			super.draw(canvas, mapv, shadow);

			GeoPoint previous = null;
			GeoPoint next = null;

			// For each path
			for (SongSet loc : MapperActivity.this.mapContent)
			{
				Path path = new Path();

				Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
				paint.setStyle(Paint.Style.FILL_AND_STROKE);
				paint.setStrokeJoin(Paint.Join.ROUND);
				paint.setStrokeCap(Paint.Cap.ROUND);
				paint.setStrokeWidth(6);

				// Get the color for this path
				paint.setColor(MapperActivity.this.songColors.get(loc.song.id));

				// for each geopoint on this path
				for (GeoPoint point : loc.points)
				{
					// ensure a previous exists to look for distance
					if (previous != null)
					{
						int oldlong = previous.getLongitudeE6();
						int oldlat = previous.getLatitudeE6();

						int newlong = point.getLongitudeE6();
						int newlat = point.getLatitudeE6();

						// if the distance is too large then the path isn't
						// drawn. Prevents large lines being drawn
						// at times when the gps cuts out and can't record
						// points.
						double dist = Math.sqrt(Math.pow((oldlong - newlong), 2) + Math.pow((oldlat - newlat), 2));

						if (dist >= SongOverlay.MAX_DIST)
						{
							previous = null;
							next = null;
						}
					}

					// Checking for first point
					if (next == null)
					{
						next = point;
					}
					else
					// draw between the two points
					{
						previous = next;
						next = point;

						Point p1 = new Point();
						Point p2 = new Point();

						// convert to pixels. This takes the most processing
						// time in this class.
						// Not much can really be done about it.
						projection.toPixels(previous, p1);
						projection.toPixels(next, p2);

						// Create the path
						path.moveTo(p1.x, p1.y);
						path.lineTo(p2.x, p2.y);
					}
				}

				// draw it
				canvas.drawPath(path, paint);
			}
		}
	}

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
