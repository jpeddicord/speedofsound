package edu.osu.speedofsound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AverageSpeed
{

	private int size;
	private List<Float> speeds = new ArrayList<Float>();

	public AverageSpeed(int size)
	{
		this.size = size;
	}

	public void push(float speed)
	{
		this.speeds.add(speed);

		// if the list is too large, remove the top element
		if (this.speeds.size() > this.size)
		{
			this.speeds.remove(0);
		}
	}

	public float getAverage()
	{

		// Copying values instead of just using speeds. We might remove outliers from the average but we want to keep them
		// in case they are accurate readings and we just happened to have a very large jump.
		List<Float> speedsorted = new ArrayList<Float>();
		speedsorted.addAll(this.speeds);

		// Put the values in order for calculation of inner-quartile range (iqr)
		Collections.sort(speedsorted);

		float length = speedsorted.size();

		// IQR doesn't work as well when we have only a few values. Even 6 might be a little low but I'm limiting this to 4.
		// If we have under 4 values then it won't try to filter out any outliers.
		if (length >= 4)
		{

			// Determine the positions of q1 and q3. Could do some optimization here since our length won't change. Won't
			// need to calculate this every time
			int q1 = (int) ((length / 4f));
			int q3 = (int) ((3f * length / 4f));

			float quart3 = speedsorted.get(q3);
			float quart1 = speedsorted.get(q1);

			// Values that are further than 1.5 * the IQR should be considered outliers
			float iqr = (speedsorted.get(q3) - speedsorted.get(q1)) * 1.5f;

			// Instead of running through every value I took advantage of the fact the values are sorted and just look at
			// the end values.
			
			// Start from the left and remove anything under q1 - iqr.
			int i = 0;
			while ((speedsorted.size() > 0) && (speedsorted.get(i) < quart1 - iqr))
			{
				speedsorted.remove(i);
				i++;
			}

			// Start from the right and remove anything over q3 + iqr
			i = speedsorted.size() - 1;
			while ((i >= 0) && (speedsorted.get(i) > quart3 + iqr))
			{
				speedsorted.remove(i);
				i--;
			}
		}

		// calculate the average speed from the ones we have after outliers have been removed.
		// This will only average the 3 most recent speeds which allows the volume to update faster.
		float total = 0;
		int size = speedsorted.size();
		int num = 0;
		for (int i = size - 1; i >= 0 && num < 3; i--)
		{
			total += speedsorted.get(i);
			num++;
		}
		return total / size;
	}
}
