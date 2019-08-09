package orienteering.solver

import orienteering.data.Route

data class BranchAndPriceSolution(
    val optimalityReached: Boolean,
    val lowerBound: Double,
    val upperBound: Double,
    val bestFeasibleSolution: List<Route>,
    val numNodesSolved: Int = 1,
    val maxConcurrentSolves: Int = 1,
    val averageConcurrentSolves: Double = 1.0
)