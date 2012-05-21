package edu.osu.speedofsound;
 
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.util.Log;
 
// TODO: Clean up the documentation in this file

public class DatabaseManager
{
	private static DatabaseManager ref;
	
	// the Activity or Application that is creating an object from this class.
	Context context;
 
	// a reference to the database used by this application/object
	private SQLiteDatabase db;
 
	// These constants are specific to the database.  They should be 
	// changed to suit your needs.
	private final String DB_NAME = "locations";
	private final int DB_VERSION = 1;
 
	// These constants are specific to the database table.  They should be
	// changed to suit your needs.
	private final String TABLE_NAME = "songs";
	private final String TABLE_ROW_ID = "id";
	private final String TABLE_ROW_ONE = "name";
	private final String TABLE_ROW_TWO = "color";
	
	private final String TABLE2_NAME = "points";
	private final String TABLE2_ROW_ID = "pointid";
	private final String TABLE2_ROW_ONE = "songid";
	private final String TABLE2_ROW_TWO = "latitude";
	private final String TABLE2_ROW_THREE = "longitude";

	
 
	private DatabaseManager(Context context)
	{
		this.context = context;
 
		// create or open the database
		CustomSQLiteOpenHelper helper = new CustomSQLiteOpenHelper(context);
		this.db = helper.getWritableDatabase();
	}
 
	public static DatabaseManager getDBManager(Context context)
	{
		if (ref == null)
		{
			ref = new DatabaseManager(context);
		}
		
		return ref;
	}
	
	public void resetDB()
	{

		try
		{
			String dropQuery = "drop table " + TABLE_NAME;
			db.execSQL(dropQuery);
			
			dropQuery = "drop table " + TABLE2_NAME;
			db.execSQL(dropQuery);
			
			
			String newTableQueryString = "create table " +
					TABLE_NAME +
					" (" +
					TABLE_ROW_ID + " integer primary key autoincrement not null," +
					TABLE_ROW_ONE + " text," +
					TABLE_ROW_TWO + " integer" +
					");";
			// execute the query string to the database.
			db.execSQL(newTableQueryString);

			// This string is used to create the database.  It should
			// be changed to suit your needs.
			newTableQueryString =   "create table " +
				TABLE2_NAME +
				" (" +
				TABLE2_ROW_ID + " integer primary key autoincrement not null," +
				TABLE2_ROW_ONE + " integer," +
				TABLE2_ROW_TWO + " integer," +
				TABLE2_ROW_THREE + " integer" +
				");";

			// execute the query string to the database.
			db.execSQL(newTableQueryString);
		}
		catch (SQLException e)
		{
			Log.e("DB ERROR on reset", e.toString());
			e.printStackTrace();
		}
		
	}
	
	public void close()
	{
		if (db.isOpen())
		{
			db.close();
		}
	}
 
	
	/**********************************************************************
	 * ADDING A ROW TO THE DATABASE TABLE
	 * 
	 * This is an example of how to add a row to a database table
	 * using this class.  You should edit this method to suit your
	 * needs.
	 * 
	 * the key is automatically assigned by the database
	 * @param rowStringOne the value for the row's first column
	 * @param rowStringTwo the value for the row's second column 
	 */
	public void addSong(String name, int color)
	{
		// this is a key value pair holder used by android's SQLite functions
		ContentValues values = new ContentValues();
		values.put(TABLE_ROW_ONE, name);
		values.put(TABLE_ROW_TWO, color);
 
		// ask the database object to insert the new data 
		try{db.insert(TABLE_NAME, null, values);}
		catch(Exception e)
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
	}
	
	public void addPoint(long songid, int latitude, int longitude)
	{
		// this is a key value pair holder used by android's SQLite functions
		ContentValues values = new ContentValues();
		values.put(TABLE2_ROW_ONE, songid);
		values.put(TABLE2_ROW_TWO, latitude);
		values.put(TABLE2_ROW_THREE, longitude);

		// ask the database object to insert the new data 
		try{db.insert(TABLE2_NAME, null, values);}
		catch(Exception e)
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}		
	}
 
	public long getSongId(String name)
	{
		// create an array list to store data from the database row.
		// I would recommend creating a JavaBean compliant object 
		// to store this data instead.  That way you can ensure
		// data types are correct.
		long rowNum = -1;
		Cursor cursor;
 
		try
		{
			// this is a database call that creates a "cursor" object.
			// the cursor object store the information collected from the
			// database and is used to iterate through the data.
			cursor = db.query
			(
					true,
					TABLE_NAME,
					new String[] { TABLE_ROW_ID },
					TABLE_ROW_ONE + "=?",
					new String[] { name },
					null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer, add
			// it to the ArrayList that will be returned by the method.
			if (!cursor.isAfterLast())
			{
				rowNum = cursor.getLong(0);
			}
			else
			{
				Log.e("DB ERROR", "Nothing was found!");
			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
 
		// return the ArrayList containing the given row from the database.
		return rowNum;
	}
	
	public String getSongName(long id)
	{
		// create an array list to store data from the database row.
		// I would recommend creating a JavaBean compliant object 
		// to store this data instead.  That way you can ensure
		// data types are correct.
		String name = "Unknown";
		Cursor cursor;
 
		try
		{
			// this is a database call that creates a "cursor" object.
			// the cursor object store the information collected from the
			// database and is used to iterate through the data.
			cursor = db.query
			(
					true,
					TABLE_NAME,
					new String[] { TABLE_ROW_ONE },
					TABLE_ROW_ID + "=" + id,
					null, null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer, add
			// it to the ArrayList that will be returned by the method.
			if (!cursor.isAfterLast())
			{
				name = cursor.getString(0);
			}
			else
			{
				Log.e("DB ERROR", "Nothing was found!");
			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
 
		// return the ArrayList containing the given row from the database.
		return name;
	}
	
	public int getColor(long id)
	{
		// create an array list to store data from the database row.
		// I would recommend creating a JavaBean compliant object 
		// to store this data instead.  That way you can ensure
		// data types are correct.
		
		int color = Color.WHITE;
		
		Cursor cursor;
 
		try
		{
			// this is a database call that creates a "cursor" object.
			// the cursor object store the information collected from the
			// database and is used to iterate through the data.
			cursor = db.query
			(
					TABLE_NAME,
					new String[] { TABLE_ROW_TWO },
					TABLE_ROW_ID + "=" + id,
					null, null, null, null, null
			);
 
			// move the pointer to position zero in the cursor.
			cursor.moveToFirst();
 
			// if there is data available after the cursor's pointer, add
			// it to the ArrayList that will be returned by the method.
			if (!cursor.isAfterLast())
			{
				color = cursor.getInt(0);

			}
 
			// let java know that you are through with the cursor.
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
		
		// return the ArrayList containing the given row from the database.
		return color;
	}
 
	public ArrayList<Long> getRange()
	{
		long lowerIndex = -1;
		long upperIndex = -1;
		Cursor cursor;
		
		try
		{
			cursor = db.query(
					TABLE2_NAME,
					new String[] { TABLE2_ROW_ID },
					null, null, null, null,
					TABLE2_ROW_ID + " ASC"
			);
			
			cursor.moveToFirst();
			
			if (!cursor.isAfterLast())
			{
				lowerIndex = cursor.getLong(0);
				
				cursor.moveToLast();
				upperIndex = cursor.getLong(0);
			}
			
			cursor.close();
		}
		catch (SQLException e) 
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
		
		ArrayList<Long> range = new ArrayList<Long>();
		
		range.add(lowerIndex);
		range.add(upperIndex);
		
		return range;
	}
 
	/**********************************************************************
	 * DELETING A ROW FROM THE DATABASE TABLE
	 * 
	 * This is an example of how to delete a row from a database table
	 * using this class. In most cases, this method probably does
	 * not need to be rewritten.
	 * 
	 * @param rowID the SQLite database identifier for the row to delete.
	 */
	public void deleteRow(long rowID)
	{
		// ask the database manager to delete the row of given id
		try {db.delete(TABLE_NAME, TABLE_ROW_ID + "=" + rowID, null);}
		catch (Exception e)
		{
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
	}
 
	/**********************************************************************
	 * UPDATING A ROW IN THE DATABASE TABLE
	 * 
	 * This is an example of how to update a row in the database table
	 * using this class.  You should edit this method to suit your needs.
	 * 
	 * @param rowID the SQLite database identifier for the row to update.
	 * @param rowStringOne the new value for the row's first column
	 * @param rowStringTwo the new value for the row's second column 
	 */ 
	public void updateRow(long rowID, String rowStringOne, String rowStringTwo)
	{
		// this is a key value pair holder used by android's SQLite functions
		ContentValues values = new ContentValues();
		values.put(TABLE_ROW_ONE, rowStringOne);
		values.put(TABLE_ROW_TWO, rowStringTwo);
 
		// ask the database object to update the database row of given rowID
		try {db.update(TABLE_NAME, values, TABLE_ROW_ID + "=" + rowID, null);}
		catch (Exception e)
		{
			Log.e("DB Error", e.toString());
			e.printStackTrace();
		}
	}
 
	/**********************************************************************
	 * RETRIEVING A ROW FROM THE DATABASE TABLE
	 * 
	 * This is an example of how to retrieve a row from a database table
	 * using this class.  You should edit this method to suit your needs.
	 * 
	 * @param rowID the id of the row to retrieve
	 * @return an array containing the data from the row
	 */
	public ArrayList<Object> getPointArray(long rowID)
	{
		// create an array list to store data from the database row.
		// I would recommend creating a JavaBean compliant object 
		// to store this data instead.  That way you can ensure
		// data types are correct.
		ArrayList<Object> rowArray = new ArrayList<Object>();
		Cursor cursor;
 
		try
		{
			// this is a database call that creates a "cursor" object.
			// the cursor object store the information collected from the
			// database and is used to iterate through the data.
			cursor = db.query
			(
					TABLE2_NAME,
					new String[] { TABLE2_ROW_ID, TABLE2_ROW_ONE, TABLE2_ROW_TWO, TABLE2_ROW_THREE },
					TABLE2_ROW_ID + "=" + rowID,
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
			Log.e("DB ERROR", e.toString());
			e.printStackTrace();
		}
 
		// return the ArrayList containing the given row from the database.
		return rowArray;
	}
 
 
 
 
	/**********************************************************************
	 * RETRIEVING ALL ROWS FROM THE DATABASE TABLE
	 * 
	 * This is an example of how to retrieve all data from a database
	 * table using this class.  You should edit this method to suit your
	 * needs.
	 * 
	 * the key is automatically assigned by the database
	 */
 
	public ArrayList<ArrayList<Object>> getAllPoints()
	{
		// create an ArrayList that will hold all of the data collected from
		// the database.
		ArrayList<ArrayList<Object>> dataArrays = new ArrayList<ArrayList<Object>>();
 
		// this is a database call that creates a "cursor" object.
		// the cursor object store the information collected from the
		// database and is used to iterate through the data.
		Cursor cursor;
 
		try
		{
			// ask the database object to create the cursor.
			cursor = db.query(
					TABLE2_NAME,
					new String[]{TABLE2_ROW_ID, TABLE2_ROW_ONE, TABLE2_ROW_TWO, TABLE2_ROW_THREE },
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
			Log.e("DB Error", e.toString());
			e.printStackTrace();
		}
 
		// return the ArrayList that holds the data collected from
		// the database.
		return dataArrays;
	}
 
 
 
 
	/**********************************************************************
	 * THIS IS THE BEGINNING OF THE INTERNAL SQLiteOpenHelper SUBCLASS.
	 * 
	 * I MADE THIS CLASS INTERNAL SO I CAN COPY A SINGLE FILE TO NEW APPS 
	 * AND MODIFYING IT - ACHIEVING DATABASE FUNCTIONALITY.  ALSO, THIS WAY 
	 * I DO NOT HAVE TO SHARE CONSTANTS BETWEEN TWO FILES AND CAN
	 * INSTEAD MAKE THEM PRIVATE AND/OR NON-STATIC.  HOWEVER, I THINK THE
	 * INDUSTRY STANDARD IS TO KEEP THIS CLASS IN A SEPARATE FILE.
	 *********************************************************************/
 
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
			// This string is used to create the database.  It should
			// be changed to suit your needs.
			String newTableQueryString = "create table " +
										TABLE_NAME +
										" (" +
										TABLE_ROW_ID + " integer primary key autoincrement not null," +
										TABLE_ROW_ONE + " text," +
										TABLE_ROW_TWO + " integer" +
										");";
			// execute the query string to the database.
			db.execSQL(newTableQueryString);
			
			// This string is used to create the database.  It should
			// be changed to suit your needs.
			newTableQueryString =   "create table " +
									TABLE2_NAME +
									" (" +
									TABLE2_ROW_ID + " integer primary key autoincrement not null," +
									TABLE2_ROW_ONE + " integer," +
									TABLE2_ROW_TWO + " integer," +
									TABLE2_ROW_THREE + " integer" +
									");";
			
			// execute the query string to the database.
			db.execSQL(newTableQueryString);
		}
 
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
		{
			// NOTHING TO DO HERE. THIS IS THE ORIGINAL DATABASE VERSION.
			// OTHERWISE, YOU WOULD SPECIFIY HOW TO UPGRADE THE DATABASE.
		}
	}
}