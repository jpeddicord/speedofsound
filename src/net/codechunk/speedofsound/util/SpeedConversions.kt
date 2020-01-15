package net.codechunk.speedofsound.util

object SpeedConversions {
    /**
     * Convert a speed into meters per second.
     *
     * @param local_units    Units to convert from
     * @param localizedSpeed Speed to convert
     * @return Converted speed in m/s
     */
    fun nativeSpeed(local_units: String, localizedSpeed: Float): Float {
        return when (local_units) {
            "m/s" -> localizedSpeed
            "km/h" -> localizedSpeed * 0.27778f
            "mph" -> localizedSpeed * 0.44704f
            else -> throw IllegalArgumentException("Not a valid unit: $local_units")
        }
    }

    /**
     * Convert a speed into a localized unit from m/s.
     *
     * @param local_units Unit to convert to.
     * @param nativeSpeed Speed in m/s converting from.
     * @return Localized speed.
     */
    fun localizedSpeed(local_units: String, nativeSpeed: Float): Float {
        return if (local_units == "m/s") {
            nativeSpeed
        } else if (local_units == "km/h") {
            nativeSpeed * 3.6f
        } else if (local_units == "mph") {
            nativeSpeed * 2.23693f
        } else {
            throw IllegalArgumentException("Not a valid unit: $local_units")
        }
    }

}
