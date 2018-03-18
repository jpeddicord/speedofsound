package net.codechunk.speedofsound;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.util.LongSparseArray;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
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
public class MapperActivity extends AppCompatActivity {
	private static final String TAG = "DrawMapActivity";
	private static final float ZOOM_LEVEL = 16.0f;
	private static final int LINE_WIDTH = 8;

	private GoogleMap map;
	private TableLayout songTable;

	private SongTracker songTracker;
	private ColorCreator colorCreator = new ColorCreator();
	private LongSparseArray<Integer> songColors = new LongSparseArray<>();

	private class SongSet {
		SongInfo song;
		ArrayList<LatLng> points;
	}

	/**
	 * Set up the map and overlay.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.mapper);

		this.songTracker = SongTracker.getInstance(this);
		this.songTable = (TableLayout) findViewById(R.id.song_table);

		// activate the up functionality on the action bar
		ActionBar ab = this.getSupportActionBar();
		if (ab != null) {
			ab.setHomeButtonEnabled(true);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		// load the map
		((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment))
				.getMapAsync(googleMap -> {
					this.map = googleMap;

					// update the path, table, and polyline
					ArrayList<SongSet> mapContent = this.getPath();
					this.displayTable(mapContent);
					this.drawPaths(mapContent);

					// zoom the map in to an appropriate spot if there are points
					if (mapContent.size() > 0) {
						SongSet loc = mapContent.get(mapContent.size() - 1);

						if (loc.points.size() > 0) {
							LatLng lastpoint = loc.points.get(loc.points.size() - 1);

							this.map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastpoint, MapperActivity.ZOOM_LEVEL));
						}
					}
				});
	}

	/**
	 * Fetch a list of paths for stored routes.
	 */
	private ArrayList<SongSet> getPath() {
		ArrayList<SongSet> data = new ArrayList<SongSet>();

		// XXX: 0.8 hack to get the only route
		SQLiteDatabase db = this.songTracker.getReadableDatabase();
		Cursor routeCursor = db.query("routes", new String[]{"id"},
				null, null, null, null, "id DESC", "1");
		routeCursor.moveToFirst();
		if (routeCursor.isAfterLast()) {
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
		while (!cursor.isAfterLast()) {
			Log.v(TAG, "Adding point");
			// unpack row data
			long songId = cursor.getLong(1);
			int latitudeE6 = cursor.getInt(2);
			int longitudeE6 = cursor.getInt(3);

			double lat = latitudeE6 / 1000000.0f;
			double lon = longitudeE6 / 1000000.0f;

			// new song => new meta
			if (songId != prevSongId) {
				prevSongId = songId;

				// store song data
				SongSet loc = new SongSet();
				loc.song = this.songTracker.getSongInfo(songId);

				// set a color if we don't have one
				if (this.songColors.get(songId) == null) {
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
	private void displayTable(ArrayList<SongSet> paths) {
		// Disallows duplicate songs to be added to the table even if two paths
		// have the same song and color
		HashSet<Long> songs = new HashSet<Long>();

		// for each path
		for (SongSet loc : paths) {
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
			if (loc.song == null) {
				song = "Unknown";
			} else {
				song = loc.song.track;
			}

			// create a text view for the song name
			TextView songTV = new TextView(this);
			songTV.setText(song);
			songTV.setTextSize(18f);
			tableRow.addView(songTV);

			this.songTable.addView(tableRow);

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
			opts.width(MapperActivity.LINE_WIDTH);
			opts.addAll(loc.points);

			// add it
			this.map.addPolyline(opts);
		}
	}

	/**
	 * Handle the home button press on the action bar.
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				Intent intent = new Intent(this, SpeedActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				break;
		}
		return true;
	}

}
