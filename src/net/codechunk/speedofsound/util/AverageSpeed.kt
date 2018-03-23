package net.codechunk.speedofsound.util

import java.util.*

/**
 * Speed averager. Consumes a set amount of speeds, throwing away those that are
 * older beyond a set limit.
 */
class AverageSpeed(private val size: Int) {

    /**
     * Cycling list of speeds to average.
     */
    private val speeds = ArrayList<Float>()

    /**
     * Get the current average. Calculated based on the IQR of the most recent
     * values, if there are enough.
     *
     * @return The current average.
     */
    val average: Float
        get() {
            // Copying values instead of just using speeds. We might remove outliers
            // from the average but we want to keep them
            // in case they are accurate readings and we just happened to have a
            // very large jump.
            val speedsorted = ArrayList<Float>()
            speedsorted.addAll(this.speeds)

            // Put the values in order for calculation of inner-quartile range (iqr)
            speedsorted.sort()
            val length = speedsorted.size.toFloat()

            // IQR doesn't work as well when we have only a few values. Even 6 might
            // be a little low but I'm limiting this to 4.
            // If we have under 4 values then it won't try to filter out any
            // outliers.
            if (length >= 4) {
                // Determine the positions of q1 and q3. Could do some optimization
                // here since our length won't change. Won't
                // need to calculate this every time
                val q1 = (length / 4f).toInt()
                val q3 = (3f * length / 4f).toInt()

                val quart3 = speedsorted[q3]
                val quart1 = speedsorted[q1]

                // Values that are further than 1.5 * the IQR should be considered
                // outliers
                val iqr = (speedsorted[q3] - speedsorted[q1]) * 1.5f

                // Instead of running through every value I took advantage of the
                // fact the values are sorted and just look at
                // the end values.

                // Start from the left and remove anything under q1 - iqr.
                var i = 0
                while (speedsorted.size > 0 && speedsorted[i] < quart1 - iqr) {
                    speedsorted.removeAt(i)
                    i++
                }

                // Start from the right and remove anything over q3 + iqr
                i = speedsorted.size - 1
                while (i >= 0 && speedsorted[i] > quart3 + iqr) {
                    speedsorted.removeAt(i)
                    i--
                }
            }

            // calculate the average speed from the ones we have after outliers have
            // been removed. This will only average the 3 most recent speeds which
            // allows the volume to update more quickly.
            var total = 0f
            val size = speedsorted.size
            var num = 0
            var i = size - 1
            while (i >= 0 && num < 3) {
                total += speedsorted[i]
                num++
                i--
            }
            return total / num
        }

    /**
     * Record a new speed.
     *
     * @param speed Speed to record.
     */
    fun push(speed: Float) {
        this.speeds.add(speed)

        // if the list is too large, remove the top element
        if (this.speeds.size > this.size) {
            this.speeds.removeAt(0)
        }
    }
}
