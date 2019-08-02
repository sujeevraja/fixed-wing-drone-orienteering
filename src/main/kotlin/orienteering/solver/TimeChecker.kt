package orienteering.solver

import orienteering.data.Parameters

object TimeChecker {
    private var startTime = 0L

    fun startTracking() {
        startTime = System.currentTimeMillis()
    }

    fun timeLimitReached(): Boolean {
        return (System.currentTimeMillis() - startTime) / 1000.0 >= Parameters.timeLimitInSeconds
    }
}