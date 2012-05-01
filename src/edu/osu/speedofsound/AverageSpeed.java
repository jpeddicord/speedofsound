package edu.osu.speedofsound;

import java.util.List;

public class AverageSpeed {

	private int size;
	// XXX: should this be a queue?
	private List<Float> speeds;

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
