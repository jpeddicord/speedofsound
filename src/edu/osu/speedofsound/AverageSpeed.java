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

		List<Float> speedsorted = new ArrayList<Float>();
		speedsorted.addAll(this.speeds);

		Collections.sort(speedsorted);

		float length = speedsorted.size();

		if (length >= 4)
		{

			int q1 = (int) ((length / 4f));
			int q3 = (int) ((3f * length / 4f));

			float quart3 = speedsorted.get(q3);
			float quart1 = speedsorted.get(q1);

			float iqr = (speedsorted.get(q3) - speedsorted.get(q1)) * 1.5f;

			int i = 0;
			while ((speedsorted.size() > 0) && (speedsorted.get(i) < quart1 - iqr))
			{
				speedsorted.remove(i);
				i++;
			}

			i = speedsorted.size() - 1;
			while ((i >= 0) && (speedsorted.get(i) > quart3 + iqr))
			{
				speedsorted.remove(i);
				i--;
			}
		}

		// calculate the average speed from the ones we have
		float total = 0;
		int size = speedsorted.size();
		for (float speed : speedsorted)
		{
			total += speed;
		}
		return total / size;
	}
}
