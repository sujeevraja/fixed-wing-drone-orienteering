package orienteering.solver

import orienteering.data.Route

data class BranchAndPriceSolution(
    val optimalityReached: Boolean = false,
    val rootLowerBound: Double = -Double.MAX_VALUE,
    val rootUpperBound: Double = Double.MAX_VALUE,
    val rootLpOptimal: Boolean = false,
    val lowerBound: Double = -Double.MAX_VALUE,
    val upperBound: Double = Double.MAX_VALUE,
    val bestFeasibleSolution: List<Route> = listOf(),
    val numNodesCreated: Int = 0,
    val numFeasibleNodes: Int = 0,
    val maxParallelSolves: Int = 0
)