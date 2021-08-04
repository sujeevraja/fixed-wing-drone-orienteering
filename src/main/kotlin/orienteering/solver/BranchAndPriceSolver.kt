package orienteering.solver

import branchandbound.api.BranchAndBoundApi
import branchandbound.api.SelectionStrategy
import orienteering.data.Instance
import orienteering.data.Parameters

/**
 * BranchAndPriceSolver implements the Coroutine scope using the CoroutineScope() factory so that
 *
 * 1. channels can be made class members,
 *
 * 2. coroutines can be directly launched from functions instead of creating new contexts using
 *    "withContext(Dispatchers.Default)" every time a coroutine needs to be launched.
 */
class BranchAndPriceSolver(private val instance: Instance) {
    var rootLowerBound: Double = -Double.MAX_VALUE
        private set

    var rootUpperBound: Double = Double.MAX_VALUE
        private set

    var rootLpOptimal: Boolean = false
        private set

    fun solve(): BranchAndPriceSolution {
        val idGenerator = generateSequence(0L) { it + 1 }.iterator()
        val rootNode = Node(id = idGenerator.next(), instance.graph)
        val solution = BranchAndBoundApi.runBranchAndBound(
            (0 until Parameters.numSolverCoroutines).map { NodeSolver(instance) },
            SelectionStrategy.BEST_BOUND,
            rootNode,
            { TimeChecker.timeLimitReached() }
        ) {
            (it as Node).branch(instance, idGenerator)
        } ?: return BranchAndPriceSolution()

        val bestSolution = solution.incumbent?.let { (it as Node).mipSolution } ?: listOf()

        val solvedRootNode = solution.solvedRootNode as Node

        return BranchAndPriceSolution(
            optimalityReached = true,
            rootLowerBound = solvedRootNode.mipObjective ?: -Double.MAX_VALUE,
            rootUpperBound = solvedRootNode.lpObjective,
            rootLpOptimal = solvedRootNode.lpOptimal,
            lowerBound = solution.lowerBound,
            upperBound = solution.upperBound,
            bestFeasibleSolution = bestSolution,
            numNodesCreated = solution.numCreatedNodes,
            numFeasibleNodes = solution.numFeasibleNodes,
            maxParallelSolves = solution.maxParallelSolves
        )
    }
}
