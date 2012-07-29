package net.codechunk.speedofsound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MapView.LayoutParams;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * Draws a path based on the points in the database. Paths are colored based on
 * the song that was being listened to at that point.
 * 
 * @author Andrew
 */
public class DrawMapActivity extends SherlockMapActivity
{
	/**
	 * Used for the logger.
	 */
	private static final String TAG = "DrawMapActivity";

	/**
	 * Displays the map
	 */
	private MapView mapView;

	/**
	 * Allows for manipulation of the map
	 */
	private MapController mc;

	/**
	 * Holds overlay on which the path is drawn
	 */
	private List<Overlay> mapOverlays;
	private Projection projection;

	/**
	 * A reference to the database manager.
	 */
	private DatabaseManager db;

	/**
	 * An array that holds records containing the latitude and longitude of the
	 * points
	 */
	private ArrayList<ArrayList<Object>> mapContent = new ArrayList<ArrayList<Object>>();

	/**
	 * The table that displays the songs and path colors
	 */
	private TableLayout songTable;

	/**
	 * Set up the map and overlay.
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.drawmap);

		// activate the up functionality on the action bar
		ActionBar ab = this.getSupportActionBar();
		ab.setHomeButtonEnabled(true);
		ab.setDisplayHomeAsUpEnabled(true);

		// load the map view
		mapView = (MapView) findViewById(R.id.mapView);
		LinearLayout zoomLayout = (LinearLayout) findViewById(R.id.zoom);
		songTable = (TableLayout) findViewById(R.id.song_table);
		View zoomView = mapView.getZoomControls();

		// add zoom controls
		zoomLayout.addView(zoomView,
				new LinearLayout.LayoutParams(
						LayoutParams.WRAP_CONTENT,
						LayoutParams.WRAP_CONTENT));
		mapView.displayZoomControls(true);

		mapOverlays = mapView.getOverlays();
		projection = mapView.getProjection();
		mapOverlays.add(new SongOverlay());

		// Get access to the database
		db = DatabaseManager.getDBManager(this);

		// Retrieve the points from the database
		this.getPath();

		// Zoom the map in to an appropriate spot if there are points
		if (this.mapContent.size() > 0)
		{
			mc = mapView.getController();

			ArrayList<Object> loc = this.mapContent.get(mapContent.size() - 1);
			@SuppressWarnings("unchecked")
			ArrayList<GeoPoint> locpoints = (ArrayList<GeoPoint>) loc.get(2);

			if (locpoints.size() > 0)
			{
				GeoPoint lastpoint = locpoints.get(locpoints.size() - 1);

				mc.animateTo(lastpoint);
				mc.setZoom(17);
			}
		}

		// Display the table of songs and colors
		this.displayTable(this.mapContent);
	}

	/**
	 * Updates the path on resume.
	 */
	@Override
	public void onResume()
	{
		super.onResume();
		this.getPath();
	}

	/**
	 * Retrieves the point records from the database and stores it in an array
	 * along with the song name and the path color.
	 */
	private void getPath()
	{
		// Find the lower and upper indexes
		ArrayList<Long> range = db.getRange();

		long lower = range.get(0);
		long upper = range.get(1);

		long oldsongid = -1;
		int color = Color.RED;
		String songname = "Unknown";
		ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();

		// Retrieve each record one at a time
		// This might be more efficient if all records are retrieved at once
		for (long i = lower; (i <= upper) && (i != -1); i++)
		{

			ArrayList<Object> point = db.getPointArray(i);

			long songid = (Long) point.get(1);

			// Set the initial color and song name for the very first point
			if (i == lower)
			{
				color = db.getColor(songid);
				songname = db.getSongName(songid);
				oldsongid = songid;
			}

			// If the songid changes we want to start a new path and will create
			// a new array in the arraylist
			if (songid != oldsongid)
			{
				ArrayList<Object> loc = new ArrayList<Object>();
				ArrayList<GeoPoint> pointsforloc = new ArrayList<GeoPoint>();

				pointsforloc.addAll(points);

				loc.add(color);
				loc.add(songname);
				loc.add(points);

				this.mapContent.add(loc);

				Log.d(TAG, "Adding points with color: " + color + ", song: " + songname);

				// Start creating new list
				oldsongid = songid;

				color = db.getColor(songid);
				songname = db.getSongName(songid);

				points = new ArrayList<GeoPoint>();

			}

			// Add the point from this record to the arraylist as a geopoint
			int latitude = (Integer) point.get(2);
			int longitude = (Integer) point.get(3);

			points.add(new GeoPoint(latitude, longitude));

		}

		// Add the last path
		ArrayList<Object> loc = new ArrayList<Object>();

		loc.add(color);
		loc.add(songname);
		loc.add(points);

		this.mapContent.add(loc);

		Log.d(TAG, "Adding points with color: " + color + ", song: " + songname);

	}

	/**
	 * Displays a list of songs along with the color of the path for that song.
	 * 
	 * @param paths
	 *            List of current paths along with their color and song name.
	 */
	private void displayTable(ArrayList<ArrayList<Object>> paths)
	{
		// Disallows duplicate songs to be added to the table even if two paths
		// have the same song and color
		HashSet<String> songs = new HashSet<String>();

		// for each path
		for (int i = 0; i < paths.size(); i++)
		{

			TableRow tableRow = new TableRow(this);

			ArrayList<Object> path = paths.get(i);

			int color = (Integer) path.get(0);
			String song = (String) path.get(1);

			// ensure the song isn't listed yet
			if (!songs.contains(song))
			{

				// create a text view for a black that is colored to the path
				// color
				TextView colorTV = new TextView(this);
				colorTV.setText("\u25A0");
				colorTV.setTextColor(color);
				colorTV.setGravity(Gravity.CENTER);
				colorTV.setTextSize(20f);
				tableRow.addView(colorTV);

				// create a text view for the song name
				TextView songTV = new TextView(this);
				songTV.setText(song);
				songTV.setTextSize(18f);
				tableRow.addView(songTV);

				songTable.addView(tableRow);

				songs.add(song);
			}
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
			for (SongSet loc : DrawMapActivity.this.mapContent)
			{
				Path path = new Path();

				Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
				paint.setColor(Color.RED);
				paint.setStyle(Paint.Style.FILL_AND_STROKE);
				paint.setStrokeJoin(Paint.Join.ROUND);
				paint.setStrokeCap(Paint.Cap.ROUND);
				paint.setStrokeWidth(6);

				// Get the color for this path
				int color = (Integer) loc.get(0);

				@SuppressWarnings("unchecked")
				ArrayList<GeoPoint> pointsforloc = (ArrayList<GeoPoint>) loc.get(2);
				paint.setColor(color);

				// for each geopoint on this path
				for (GeoPoint point : pointsforloc)
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
