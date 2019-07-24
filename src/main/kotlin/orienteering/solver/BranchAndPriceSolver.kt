package orienteering.solver

import mu.KLogging
import java.util.PriorityQueue

class BranchAndPriceSolver {
    private val openNodes = PriorityQueue<Node>()

    fun solve() {
        logger.info("starting branch and price...")
        logger.info("completed branch and price.")
    }

    companion object: KLogging()
}