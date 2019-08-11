package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.main.OrienteeringException
import orienteering.main.preProcess

class BranchAndPriceSolver(
    private val instance: Instance,
    private val cplex: IloCplex
) {
    var rootLowerBound: Double = -Double.MAX_VALUE
        private set

    var rootUpperBound: Double = Double.MAX_VALUE
        private set

    lateinit var finalSolution: BranchAndPriceSolution
        private set

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    suspend fun solve() {
        val rootNode = solveRootNode()
        if (finalSolution.optimalityReached) {
            logger.info("gap closed in root node")
            return
        }

        val numSolverActors = Parameters.numSolverActors
        withContext(Dispatchers.Default + CoroutineName("BranchAndPrice_")) {
            val unsolvedNodes = Channel<Node>()
            val solvedNodes = Channel<Node>()
            val solution = CompletableDeferred<BranchAndPriceSolution>()

            repeat(numSolverActors) {
                launch {
                    val cplex = IloCplex()
                    for (node in unsolvedNodes) {
                        solveNode(cplex, node, solvedNodes)
                    }
                }
            }
            launch {
                val nodeProcessor = NodeProcessor(instance, numSolverActors, solution)
                for (node in solvedNodes) {
                    nodeProcessor.processSolvedNode(node, unsolvedNodes)
                }
            }
            launch {
                solvedNodes.send(rootNode)
                logger.info("sent root node")
            }

            finalSolution = solution.await()
            coroutineContext.cancelChildren()
        }
    }

    private fun solveRootNode(): Node {
        val rootNode = Node.buildRootNode(instance.graph)
        rootNode.solve(instance, cplex)
        rootUpperBound = rootNode.lpObjective
        rootLowerBound = rootNode.mipObjective
        if (rootLowerBound >= rootUpperBound + Parameters.eps) {
            throw OrienteeringException("lower bound overshoots upper bound")
        }

        val optimalityReached = rootUpperBound - rootLowerBound <= Parameters.eps

        finalSolution = BranchAndPriceSolution(
            optimalityReached = optimalityReached,
            lowerBound = rootLowerBound,
            upperBound = rootUpperBound,
            bestFeasibleSolution = rootNode.mipSolution
        )

        return rootNode
    }

    private suspend fun solveNode(
        cplex: IloCplex,
        node: Node,
        solvedNodes: SendChannel<Node>
    ) {
        logger.info("$node solve starting")
        preProcess(
            node.graph,
            instance.budget,
            instance.getVertices(instance.sourceTarget),
            instance.getVertices(instance.destinationTarget)
        )
        if (!node.isFeasible(instance)) {
            logger.info("$node infeasible before solving")
            return
        }
        node.solve(instance, cplex)
        logger.info("$node sending to solvedNodes channel after solving")
        solvedNodes.send(node)
        logger.info("$node sent to solvedNodes channel after solving")
    }

    companion object : KLogging()
}
