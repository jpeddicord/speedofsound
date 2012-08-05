package net.codechunk.speedofsound;

import android.app.Activity;
import android.os.Bundle;

public class AboutActivity extends Activity
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.about);
		
		//ListView list = (ListView) this.findViewById(R.layout.about_listview);
	}

}
