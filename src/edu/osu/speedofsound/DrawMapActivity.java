package edu.osu.speedofsound;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

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
	
	private Path path = new Path();
	
	private ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.drawmap);
		
        mapView = (MapView) findViewById(R.id.mapView);
        LinearLayout zoomLayout = (LinearLayout)findViewById(R.id.zoom);  
		View zoomView = mapView.getZoomControls(); 
 
        zoomLayout.addView(zoomView, 
            new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, 
                LayoutParams.WRAP_CONTENT)); 
        mapView.displayZoomControls(true);
        
        mapOverlays = mapView.getOverlays();        
        projection = mapView.getProjection();
        mapOverlays.add(new MyOverlay());
        
        db = new DatabaseManager(this);
        
        this.getPath();
        
        if (this.points.size() > 0){
        	mc = mapView.getController();
        	mc.animateTo(points.get(points.size() - 1));
        	mc.setZoom(17);
        }
        
        //mapView.invalidate();
	}

    private void getPath()
	{
    	ArrayList<Long> range = db.getRange();
        
        long lower = range.get(0);
        long upper = range.get(1);
        
        for (long i = lower; (i <= upper) && (i != -1); i++)
        {
        	ArrayList<Object> point = db.getPointArray(i);
        	
        	int latitude = (Integer) point.get(2);
    		int longitude = (Integer) point.get(3);
        	
    		this.points.add(new GeoPoint(latitude, longitude));
    		
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

            Paint   mPaint = new Paint();
            mPaint.setDither(true);
            mPaint.setColor(Color.RED);
            mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeWidth(4);
           
            GeoPoint previous = null;
            GeoPoint next = null;
            
            Path path = new Path();
            
            for (GeoPoint point : DrawMapActivity.this.points)
            {
            	if (next == null)
            	{
            		next = point;
            		
            		Log.d(TAG, "Initial point: " + next.getLatitudeE6() + ", " + next.getLongitudeE6());
            	}
            	else
            	{
            		previous = next;
            		next = point;
            		
            		Log.d(TAG, "Line from: (" + previous.getLatitudeE6() + "," + previous.getLongitudeE6() + ") to (" + next.getLatitudeE6() + "," + next.getLongitudeE6() + ")");
            		
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