package orienteering.solver

import mu.KLogging
import orienteering.data.Parameters

/**
 * Singleton that serves as the sole point of reference to check whether a specified time limit
 * has been reached.
 */
object TimeChecker: KLogging() {
    /**
     * Stores time at which [startTracking] is called.
     */
    private var startTime = 0L

    /**
     * true if time limit reached, false otherwise.
     */
    @Volatile
    private var limitHit = false

    /**
     * initializes values of [startTime] so that time elapsed can be measured against it.
     */
    fun startTracking() {
        startTime = System.currentTimeMillis()
    }

    /**
     * Returns true if time limit is reached and false otherwise.
     *
     * Once the time limit has been reached, [limitHit] is set to true permanently. This is to
     * avoid measuring times repeatedly.
     */
    fun timeLimitReached(): Boolean {
        if (!limitHit) {
            val elapsedTimeInSeconds = ((System.currentTimeMillis() - startTime) / 1000.0).toInt()
            limitHit = elapsedTimeInSeconds >= Parameters.timeLimitInSeconds
            if (limitHit)
                logger.warn("time limit reached")
        }
        return limitHit
    }
}
