package net.codechunk.speedofsound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MapView.LayoutParams;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class DrawMapActivity extends MapActivity
{
	private static final String TAG = "DrawMapActivity";
	
	MapView mapView;
	
	MapController mc;
	
	private List<Overlay> mapOverlays;

	private Projection projection;
	
	private DatabaseManager db;
	
	private ArrayList<ArrayList<Object>> mapContent = new ArrayList<ArrayList<Object>>();
	
	// the table that displays the data
	private TableLayout songTable;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.drawmap);
		
        mapView = (MapView) findViewById(R.id.mapView);
        LinearLayout zoomLayout = (LinearLayout)findViewById(R.id.zoom);
        songTable = (TableLayout)findViewById(R.id.song_table);
		View zoomView = mapView.getZoomControls(); 
 
        zoomLayout.addView(zoomView, 
            new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, 
                LayoutParams.WRAP_CONTENT)); 
        mapView.displayZoomControls(true);
        
        mapOverlays = mapView.getOverlays();        
        projection = mapView.getProjection();
        mapOverlays.add(new MyOverlay());
        
        db = DatabaseManager.getDBManager(this);
        
        this.getPath();
        
        if (this.mapContent.size() > 0){
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
        
        this.displayTable(this.mapContent);
        
        //mapView.invalidate();
	}


	@Override
	public void onResume()
	{
		super.onResume();
		this.getPath();
	}
	
    private void getPath()
	{
    	ArrayList<Long> range = db.getRange();
        
        long lower = range.get(0);
        long upper = range.get(1);
        
        long oldsongid = -1;
        int color = Color.RED;
        String songname = "Unknown";
    	ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();
        
        for (long i = lower; (i <= upper) && (i != -1); i++)
        {
        	
        	ArrayList<Object> point = db.getPointArray(i);
        	
        	long songid = (Long) point.get(1);
        	
        	if (i == lower)
        	{
        		color = db.getColor(songid);
        		songname = db.getSongName(songid); 
        	}
        	
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
        	
        	int latitude = (Integer) point.get(2);
    		int longitude = (Integer) point.get(3);
    		
    		points.add(new GeoPoint(latitude, longitude));
    		
        }
        
        ArrayList<Object> loc = new ArrayList<Object>();
        
		loc.add(color);
		loc.add(songname);
		loc.add(points);
		
		this.mapContent.add(loc);
		
		Log.d(TAG, "Adding points with color: " + color + ", song: " + songname);
        
	}
    
	private void displayTable(ArrayList<ArrayList<Object>> paths)
	{
		HashSet<String> songs = new HashSet<String>();
		
		for (int i = 0; i < paths.size(); i++)
    	{
			
    		TableRow tableRow= new TableRow(this);
 
    		ArrayList<Object> path = paths.get(i);
    		
    		int color = (Integer) path.get(0);
    		String song = (String) path.get(1);
 
    		if (! songs.contains(song)) {
    			
	    		TextView colorTV = new TextView(this);
	    		colorTV.setText("\u25A0");
	    		colorTV.setTextColor(color);
	    		colorTV.setGravity(Gravity.CENTER);
	    		colorTV.setTextSize(20f);
	    		tableRow.addView(colorTV);

	 
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
    protected boolean isRouteDisplayed() {
        return false;
    }
    
    class MyOverlay extends Overlay{

        public MyOverlay(){

        }   

        public void draw(Canvas canvas, MapView mapv, boolean shadow){
            super.draw(canvas, mapv, shadow);
            
            GeoPoint previous = null;
            GeoPoint next = null;
            
            for (ArrayList<Object> loc : DrawMapActivity.this.mapContent)
	        {
            	Path path = new Path();
            	
                Paint   mPaint = new Paint();
                mPaint.setDither(true);
                mPaint.setColor(Color.RED);
                mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                mPaint.setStrokeJoin(Paint.Join.ROUND);
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mPaint.setStrokeWidth(4);
            
            	
            	int color = (Integer) loc.get(0);
            	mPaint.setColor(color);
            	
            	@SuppressWarnings("unchecked")
				ArrayList<GeoPoint> pointsforloc = (ArrayList<GeoPoint>) loc.get(2);
            	
	            for (GeoPoint point : pointsforloc)
	            {
	            	
	            	if (previous != null)
	            	{
	            		int oldlong = previous.getLongitudeE6();
	            		int oldlat = previous.getLatitudeE6();
	            		
	            		int newlong = point.getLongitudeE6();
	            		int newlat = point.getLatitudeE6();
	            		
	            		double dist = Math.sqrt(Math.pow((oldlong - newlong),2) + Math.pow((oldlat - newlat),2));
	            		
	            		if (dist >= 4000)
	            		{
	            			previous = null;
	            			next = null;
	            		}
	            	}
	            	
	            	if (next == null)
	            	{
	            		next = point;
	            		//Log.d(TAG, "Initial point: " + next.getLatitudeE6() + ", " + next.getLongitudeE6());
	            	}
	            	else
	            	{
	            		previous = next;
	            		next = point;
	            		
	            		//Log.d(TAG, "Line from: (" + previous.getLatitudeE6() + "," + previous.getLongitudeE6() + ") to (" + next.getLatitudeE6() + "," + next.getLongitudeE6() + ")");
	            		
	                    Point p1 = new Point();
	                    Point p2 = new Point();
	                    
	                    projection.toPixels(previous, p1);
	                    projection.toPixels(next, p2);
	                    
	                    path.moveTo(p1.x, p1.y);
	                    path.lineTo(p2.x,p2.y);
	            	}
	            }
	        
	            canvas.drawPath(path, mPaint);
            }
        }
    }
}
