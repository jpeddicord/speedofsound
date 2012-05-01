package edu.osu.speedofsound;

import java.util.ArrayList;
import java.util.List;

public class AverageSpeed {

	private int size;
	private List<Float> speeds = new ArrayList<Float>();

	public AverageSpeed(int size) {
		this.size = size;
	}

	public void push(float speed) {
		this.speeds.add(speed);

		// if the list is too large, remove the top element
		if (this.speeds.size() > this.size) {
			this.speeds.remove(0);
		}
	}

	public float getAverage() {
		// calculate the average speed from the ones we have
		float total = 0;
		int size = this.speeds.size();
		for (float speed : this.speeds) {
			total += speed;
		}
		return total / size;
	}
}
