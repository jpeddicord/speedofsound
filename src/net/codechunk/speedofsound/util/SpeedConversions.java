package net.codechunk.speedofsound.util;

public class SpeedConversions
{
	/**
	 * Convert a speed into meters per second.
	 * 
	 * @param local_units
	 *            Units to convert from
	 * @param localizedSpeed
	 *            Speed to convert
	 * @return Converted speed in m/s
	 */
	public static float nativeSpeed(String local_units, float localizedSpeed)
	{
		if (local_units.equals("m/s"))
		{
			return localizedSpeed;
		}
		else if (local_units.equals("km/h"))
		{
			return localizedSpeed * 0.27778f;
		}
		else if (local_units.equals("mph"))
		{
			return localizedSpeed * 0.44704f;
		}
		else
		{
			throw new IllegalArgumentException("Not a valid unit: " + local_units);
		}
	}

	/**
	 * Convert a speed into a localized unit from m/s.
	 * 
	 * @param local_units
	 *            Unit to convert to.
	 * @param nativeSpeed
	 *            Speed in m/s converting from.
	 * @return Localized speed.
	 */
	public static float localizedSpeed(String local_units, float nativeSpeed)
	{
		if (local_units.equals("m/s"))
		{
			return nativeSpeed;
		}
		else if (local_units.equals("km/h"))
		{
			return nativeSpeed * 3.6f;
		}
		else if (local_units.equals("mph"))
		{
			return nativeSpeed * 2.23693f;
		}
		else
		{
			throw new IllegalArgumentException("Not a valid unit: " + local_units);
		}
	}

}
