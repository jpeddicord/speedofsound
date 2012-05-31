package net.codechunk.speedofsound;

import java.util.Random;

import android.graphics.Color;

/**
 * Chooses an arbitrary color. Attempts to choose a color that is not very
 * similar to the previously chosen color.
 * 
 * @author Andrew
 *
 */
public class ColorCreator
{
	
	/**
	 * Holds the previous color that was chosen by this color creator.
	 */
	private int previous = Color.WHITE;
	
	/**
	 * Uses a random generator to determine the rgb components and returns
	 * the values as a color-int.
	 * @return
	 */
	public int getColor(){
		
		Random generator = new Random();
		
		// rgb values range from 0 to 255.
		int r = generator.nextInt(255);
		int g = generator.nextInt(255);
		int b = generator.nextInt(255);
		
		// Create color-int
		int color = Color.rgb(r, g, b);
		
		// Check to see if rgb values are similar to previous color values.
		// This is used to help avoid very similar colors being generated in sequence.
		if ((Math.abs(r - Color.red(this.previous)) <= 50) &&
			(Math.abs(b - Color.blue(this.previous)) <= 50) &&
			(Math.abs(g - Color.green(this.previous)) <= 50)){
			
			// generate a different color
			color = this.getColor();
			
		}
		
		this.previous = color;
		return color;
	}
}
