package net.codechunk.speedofsound;


import java.util.Random;

import android.graphics.Color;


//TODO Document
public class ColorCreator
{
	
	private int previous = Color.WHITE;
	
	public int getColor(){
		
		Random generator = new Random();
		
		int r = generator.nextInt(255);
		int g = generator.nextInt(255);
		int b = generator.nextInt(255);
		
		int color = Color.rgb(r, g, b);
		
		if ((Math.abs(r - Color.red(this.previous)) <= 50) &&
			(Math.abs(b - Color.blue(this.previous)) <= 50) &&
			(Math.abs(g - Color.green(this.previous)) <= 50)){
			
			color = getColor();
			
		}
		
		this.previous = color;
		return color;
	}
}
