package branchandbound.api

/**
 * Data class to hold the solution of a branch-and-bound algorithm
 *
 * @property objective objective value
 * @property incumbent branch and bound node corresponding to the incumbent
 * @property numCreatedNodes number of nodes in the branch-and-bound algorithm
 * @property numFeasibleNodes number of nodes that had a feasible LP solution
 * @property maxParallelSolves maximum number of parallel solves performed by algorithm
 */
data class Solution(
    val optimalityReached: Boolean,
    val solvedRootNode: INode,
    val upperBound: Double,
    val objective: Double,
    val incumbent: INode?,
    val numCreatedNodes: Int,
    val numFeasibleNodes: Int,
    val maxParallelSolves: Int
) {
    val lowerBound: Double
        get() = incumbent?.mipObjective ?: -Double.MAX_VALUE
}