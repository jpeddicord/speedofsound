package net.codechunk.speedofsound;
 
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.util.Log;


/**
 * Allows for easy use of the database. This is a singleton class.
 * 
 * This class is based on the work of Randall Mitchell in his Android
 * Database Tutorial located at the following:
 * 
 * http://www.anotherandroidblog.com/2010/08/04/android-database-tutorial/
 * 
 * @author Andrew
 *
 */
public class DatabaseManager
{
	/**
	 * A reference to the database manager.
	 */
	private static DatabaseManager ref;
	
	/**
	 * The Activity or Application that is creating an object from this class.
	 */
	Context context;
 
	/**
	 * A reference to the database used by this application/object
	 */
	private SQLiteDatabase db;
 
	/**
	 * Name of the database.
	 */
	private final String DB_NAME = "locations";
	
	/**
	 * Version number.
	 */
	private final int DB_VERSION = 1;
 
	/**
	 * Fields for the table that will hold songs.
	 */
	private final String SONG_TABLE = "songs";
	private final String SONG_TABLE_ID = "id";
	private final String SONG_TABLE_TRACK = "name";
	private final String SONG_TABLE_COLOR = "color";
	
	/**
	 * Fields for the table that will hold location points.
	 */
	private final String POINT_TABLE = "points";
	private final String POINT_TABLE_ID = "pointid";
	private final String POINT_TABLE_SONG_ID = "songid";
	private final String POINT_TABLE_LAT = "latitude";
	private final String POINT_TABLE_LONG = "longitude";
	
	/**
	 * The total number of points being stored.
	 */
	private long numPoints = 0;

	/**
	 * The limit on number of points before earlier points are removed.
	 */
	private final int LIMIT = 7200;
	
	/**
	 * The number of points to be removed.
	 */
	private final int REMOVE_SIZE = 500;
	
 
	/**
	 * Creates or opens the database if it has not been created or opened.
	 * 
	 * @param context The Activity or Application that is creating an object from this class.
	 */
	private DatabaseManager(Context context)
	{
		this.context = context;
 
		// create or open the database
		CustomSQLiteOpenHelper helper = new CustomSQLiteOpenHelper(context);
		this.db = helper.getWritableDatabase();
	}
 
	/**
	 * Calls the constructor if a DBManager does not already exist. Returns the newly opened
	 * or previously opened DBManager.
	 * 
	 * @param context The Activity or Application that is creating an object from this class.
	 * 
	 * @return A reference to this DatabaseManager
	 */
	public static DatabaseManager getDBManager(Context context)
	{
		// One does not currently exist
		if (ref == null)
		{
			ref = new DatabaseManager(context);
		}
		
		return ref;
	}
	
	/**
	 * Clears all values from the songs and points tables.
	 */
	public void resetDB()
	{

		try
		{
			
			// Drop the song table
			String dropQuery = "drop table " + SONG_TABLE;
			db.execSQL(dropQuery);
			
			// Drop the point table
			dropQuery = "drop table " + POINT_TABLE;
			db.execSQL(dropQuery);
			
			// Recreate them
			String newTableQueryString = "create table " +
					SONG_TABLE +
					" (" +
					SONG_TABLE_ID + " integer primary key autoincrement not null," +
					SONG_TABLE_TRACK + " text," +
					SONG_TABLE_COLOR + " integer" +
					");";
			db.execSQL(newTableQueryString);

			
			newTableQueryString =   "create table " +
				POINT_TABLE +
				" (" +
				POINT_TABLE_ID + " integer primary key autoincrement not null," +
				POINT_TABLE_SONG_ID + " integer," +
				POINT_TABLE_LAT + " integer," +
				POINT_TABLE_LONG + " integer" +
				");";

			db.execSQL(newTableQueryString);
			
			// Make sure our count is reset
			this.numPoints = 0;
		}
		catch (SQLException e)
		{
			Log.e("DB ERROR", "Error when attempting to reset the DB");
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Close the database if it is currently open.
	 */
	public void close()
	{
		if (db.isOpen())
		{
			db.close();
		}
	}
 
	
	/**
	 * Add a song to the database.
	 * 
	 * the key is automatically assigned by the database
	 * @param name The name of the song
	 * @param color The color of the path associated with this song.
	 */
	public void addSong(String name, int color)
	{
		// this is a key value pair holder used by android's SQLite functions
		ContentValues values = new ContentValues();
		values.put(SONG_TABLE_TRACK, name);
		values.put(SONG_TABLE_COLOR, color);
 
		// ask the database object to insert the new data 
		try{db.insert(SONG_TABLE, null, values);}
		catch(Exception e)
		{
			Log.e("DB ERROR", "Error when attempting to add a song");
			e.printStackTrace();
		}
	}
	
	/**
	 * Add a point to the database.
	 * 
	 * the key is automatically assigned by the database
	 * @param songid The id of the song being played at this point
	 * @param latitude The latitude in microdegrees (degrees * 1E6)
	 * @param longitude The longitude in microdegrees (degrees * 1E6)
	 * 
	 */
	public void addPoint(long songid, int latitude, int longitude)
	{
		// Store value in a ContentValues
		ContentValues values = new ContentValues();
		values.put(POINT_TABLE_SONG_ID, songid);
		values.put(POINT_TABLE_LAT, latitude);
		values.put(POINT_TABLE_LONG, longitude);

		// ask the database object to insert the new data 
		try
		{
			db.insert(POINT_TABLE, null, values);
			
			// Update our counter for number of points
			this.numPoints++;
			
			// If the limit has be exceeded, prune the database
			if (this.numPoints > this.LIMIT)
			{
				Log.v("Database", "Limit exceeded. Pruning database.");
				this.prunedb();
			}
		}
		catch(Exception e)
		{
			Log.e("DB ERROR", "Error when attempting to add a point.");
			e.printStackTrace();
		}		
	}
 
	/**
	 * Removes older entries from the database. Used to keep the database
	 * from becoming too large.
	 */
	private void prunedb()
	{
		
		// remove the REMOVE_SIZE oldest points from the database
		try
		{
			String deleteQuery = "DELETE FROM " + POINT_TABLE +
					" WHERE " + POINT_TABLE_ID + " NOT IN (" +
					" SELECT " + POINT_TABLE_ID + 
					" FROM ( SELECT " + POINT_TABLE_ID +
					" FROM " + POINT_TABLE +
					" ORDER BY " + POINT_TABLE_ID + " DESC " +
					" LIMIT " + (this.LIMIT - this.REMOVE_SIZE) + "));";
			db.execSQL(deleteQuery);	
			
			this.numPoints -= this.REMOVE_SIZE;
		}
		catch(Exception e)
		{
			Log.e("DB ERROR", "Error when attempting prune db.");
			e.printStackTrace();
		}
		
	}

	/**
	 * Find the index of the song provided. Songs should not be added more than once
	 * but if they are this will return the first record. Returns -1 if the song
	 * was not found.
	 * 
	 * @param name Name of the song
	 * @return The id of the song in the song table
	 */
	public long getSongId(String name)
	{

		long rowNum = -1;
		Cursor cursor;
 
		try
		{
			// Query the database for the song
			cursor = db.query
			(
					true,
					SONG_TABLE,
					new String[] { SONG_TABLE_ID },
					SONG_TABLE_TRACK + "=?",
					new String[] { name },
					null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer,
			// get the value as a long.
			if (!cursor.isAfterLast())
			{
				rowNum = cursor.getLong(0);
			}
			else
			{
				Log.e("DB ERROR", "The song could not be found.");
			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", "Error when attempting to find a song");
			e.printStackTrace();
		}
 
		// return the index of the song
		return rowNum;
	}
	
	/**
	 * Find the name of the song with the given index. If the index does not exist
	 * this will return a song name of "Unknown".
	 * 
	 * @param id The id of the song.
	 * @return The name of the song.
	 */
	public String getSongName(long id)
	{
		String name = "Unknown";
		Cursor cursor;
 
		try
		{
			// query the database for the songid
			cursor = db.query
			(
					true,
					SONG_TABLE,
					new String[] { SONG_TABLE_TRACK },
					SONG_TABLE_ID + "=" + id,
					null, null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer,
			// get the value as a string
			if (!cursor.isAfterLast())
			{
				name = cursor.getString(0);
			}
			else
			{
				Log.e("DB ERROR", "Song id was not found.");
			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", "Error when attempting to find songid");
			e.printStackTrace();
		}
 
		// return the name of the song
		return name;
	}
	
	/**
	 * Get the color-int of the path associated with the song id. If no song
	 * can be found then this will return the color-int for Color.WHITE.
	 * 
	 * @param id id for the song
	 * @return color-int associated with that song
	 */
	public int getColor(long id)
	{	
		int color = Color.RED;
		
		Cursor cursor;
 
		try
		{

			// query the database for the color
			cursor = db.query
			(
					SONG_TABLE,
					new String[] { SONG_TABLE_COLOR },
					SONG_TABLE_ID + "=" + id,
					null, null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer, 
			// get the value as an int (color-int)
			if (!cursor.isAfterLast())
			{
				color = cursor.getInt(0);

			}
			else
			{
				Log.e("DB ERROR", "Song id was not found.");
			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", "Error when attempting to find color");
			e.printStackTrace();
		}
		
		// return the color
		return color;
	}
 
	/**
	 * Determine the range of the points table (lowest and highest indexes).
	 * 
	 * @return An array where the first item is the lowest index and second is highest
	 */
	public ArrayList<Long> getRange()
	{
		long lowerIndex = -1;
		long upperIndex = -1;
		Cursor cursor;
		
		try
		{
			cursor = db.query(
					POINT_TABLE,
					new String[] { POINT_TABLE_ID },
					null, null, null, null,
					POINT_TABLE_ID + " ASC"
			);
			
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
			
			// if there is data available after the cursor's pointer,
			// get the value as a long.
			if (!cursor.isAfterLast())
			{
				lowerIndex = cursor.getLong(0);
				
				// now move the cursor to the very end and get the largest index
				cursor.moveToLast();
				upperIndex = cursor.getLong(0);
			}
			
			// Finished with the cursor
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", "Error when attempting to find range of indexes");
			e.printStackTrace();
		}
		
		ArrayList<Long> range = new ArrayList<Long>();
		
		range.add(lowerIndex);
		range.add(upperIndex);
		
		return range;
	}
 
	/**
	 * Retrieve the values for a single point (one record) from the database.
	 * 
	 * @param rowID the id of the point to retrieve
	 * @return an array containing the data from the row
	 */
	public ArrayList<Object> getPointArray(long rowID)
	{
		// create an array list to store data from the database row.
		ArrayList<Object> rowArray = new ArrayList<Object>();
		Cursor cursor;
 
		try
		{
			// query for the point record
			cursor = db.query
			(
					POINT_TABLE,
					new String[] { POINT_TABLE_ID, POINT_TABLE_SONG_ID, POINT_TABLE_LAT, POINT_TABLE_LONG },
					POINT_TABLE_ID + "=" + rowID,
					null, null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer, add
			// it to the ArrayList that will be returned by the method.
			if (!cursor.isAfterLast())
			{
				do
				{
					rowArray.add(cursor.getLong(0));
					rowArray.add(cursor.getLong(1));
					rowArray.add(cursor.getInt(2));
					rowArray.add(cursor.getInt(3));
				}
				while (cursor.moveToNext());
			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", "Error when attempting to retrieve a point array");
			e.printStackTrace();
		}
 
		// return the ArrayList containing the given row from the database.
		return rowArray;
	}
 
 
 
 
	/**
	 * Retrieve all points from the database.
	 * 
	 * return an array of arrays containing values for each point.
	 */
 
	public ArrayList<ArrayList<Object>> getAllPoints()
	{
		// create an ArrayList that will hold all of the data collected from
		// the database.
		ArrayList<ArrayList<Object>> dataArrays = new ArrayList<ArrayList<Object>>();
		Cursor cursor;
 
		try
		{
			// ask the database object to create the cursor.
			cursor = db.query(
					POINT_TABLE,
					new String[]{POINT_TABLE_ID, POINT_TABLE_SONG_ID, POINT_TABLE_LAT, POINT_TABLE_LONG },
					null, null, null, null, null
			);
 
			// move the cursor's pointer to position zero.
			cursor.moveToFirst();
 
			// if there is data after the current cursor position, add it
			// to the ArrayList.
			if (!cursor.isAfterLast())
			{
				do
				{
					ArrayList<Object> dataList = new ArrayList<Object>();
 
					dataList.add(cursor.getLong(0));
					dataList.add(cursor.getLong(1));
					dataList.add(cursor.getInt(2));
					dataList.add(cursor.getInt(3));
 
					dataArrays.add(dataList);
				}
				// move the cursor's pointer up one position.
				while (cursor.moveToNext());
			}
			
			cursor.close();
		}
		catch (SQLException e)
		{
			Log.e("DB Error", "Error when attempting to retrieve all points");
			e.printStackTrace();
		}
 
		// return the ArrayList that holds the data collected from
		// the database.
		return dataArrays;
	}
 
	/**
	 * This class is designed to check if there is a database that currently
	 * exists for the given program.  If the database does not exist, it creates
	 * one.  After the class ensures that the database exists, this class
	 * will open the database for use.  Most of this functionality will be
	 * handled by the SQLiteOpenHelper parent class.  The purpose of extending
	 * this class is to tell the class how to create (or update) the database.
	 * 
	 * @author Randall Mitchell
	 *
	 */
	private class CustomSQLiteOpenHelper extends SQLiteOpenHelper
	{
		public CustomSQLiteOpenHelper(Context context)
		{
			super(context, DB_NAME, null, DB_VERSION);
		}
 
		@Override
		public void onCreate(SQLiteDatabase db)
		{
			// String to create the song table
			String newTableQueryString = "create table " +
										SONG_TABLE +
										" (" +
										SONG_TABLE_ID + " integer primary key autoincrement not null," +
										SONG_TABLE_TRACK + " text," +
										SONG_TABLE_COLOR + " integer" +
										");";
			// execute the query string to the database.
			db.execSQL(newTableQueryString);
			
			// String to create the point table
			newTableQueryString =   "create table " +
									POINT_TABLE +
									" (" +
									POINT_TABLE_ID + " integer primary key autoincrement not null," +
									POINT_TABLE_SONG_ID + " integer," +
									POINT_TABLE_LAT + " integer," +
									POINT_TABLE_LONG + " integer" +
									");";
			
			// execute the query string to the database.
			db.execSQL(newTableQueryString);
		}
 
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			// NOTHING TO DO HERE. THIS IS THE ORIGINAL DATABASE VERSION.
		}
	}
}
