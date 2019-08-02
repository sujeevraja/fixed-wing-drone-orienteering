package orienteering.solver

import mu.KLogging
import orienteering.data.Parameters

object TimeChecker: KLogging() {
    private var startTime = 0L

    private var limitHit = false

    fun startTracking() {
        startTime = System.currentTimeMillis()
    }

    fun timeLimitReached(): Boolean {
        if (!limitHit) {
            val elapsedTimeInSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            limitHit = elapsedTimeInSeconds >= Parameters.timeLimitInSeconds
            if (limitHit) {
                logger.warn("time limit reached")
            }
        }
        return limitHit
    }
}