package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.OrienteeringException
import java.util.*

class BranchAndPriceSolver(
    private val instance: Instance,
    private val cplex: IloCplex
) {
    var rootLowerBound: Double = -Double.MAX_VALUE
        private set

    var rootUpperBound: Double = Double.MAX_VALUE
        private set

    var lowerBound: Double = -Double.MAX_VALUE
        private set

    var bestFeasibleSolution = listOf<Route>()
        private set

    var upperBound: Double = Double.MAX_VALUE
        private set

    private val openNodes = PriorityQueue<Node>()

    var numNodes = 0
        private set

    var optimalityReached = false
        private set

    @ObsoleteCoroutinesApi
    suspend fun solve() {
        val rootNode = solveRootNode()
        if (rootUpperBound - rootLowerBound <= Parameters.eps) {
            logger.info("gap closed in root node")
            return
        }

        val numSolverActors = 3
        val numBranchingActors = 2
        withContext(Dispatchers.Default) {
            val monitorActor = monitorActor(coroutineContext, numBranchingActors)
            val openNodeActor = openNodesActor(coroutineContext, instance, monitorActor)

            val solverActors = (0 until numSolverActors).map {
                solverActor(coroutineContext)
            }

            val branchingActors = (0 until numBranchingActors).map {
                branchingActor(it, monitorActor, coroutineContext, solverActors)
            }

            openNodeActor.send(StoreSolvedNodes(listOf(rootNode)))
            while (true) {
                if (TimeChecker.timeLimitReached()) {
                    solverActors.forEach { it.send(ClearCPLEX) }
                    coroutineContext.cancelChildren()
                    break
                }

                delay(1000L)
                val algorithmStatus = AlgorithmStatus()
                monitorActor.send(algorithmStatus)

                println("--------------------------------------------------------------------")
                println("optimality reached: ${algorithmStatus.optimalityReached.await()}")
                println("branching ongoing: ${algorithmStatus.branchingOngoing.await()}")
                println("open nodes available: ${algorithmStatus.openNodesAvailable.await()}")
                println("--------------------------------------------------------------------")
                if (algorithmStatus.optimalityReached.await()) {
                    break
                }
                if (!(algorithmStatus.branchingOngoing.await()) && !(algorithmStatus.openNodesAvailable.await())) {
                    break
                }
                if (algorithmStatus.openNodesAvailable.await()) {
                    openNodeActor.send(ReleaseNode(branchingActors))
                }
            }

            println("reached end of while loop")
            numNodes = Node.nodeCount - 1
            if (!TimeChecker.timeLimitReached()) {
                optimalityReached = true
            }

            solverActors.forEach { it.send(ClearCPLEX) }
            coroutineContext.cancelChildren()
        }
    }

    private fun solveRootNode(): Node {
        val rootNode = Node.buildRootNode(instance.graph)
        rootNode.solve(instance, cplex)
        rootUpperBound = upperBound
        rootLowerBound = lowerBound
        bestFeasibleSolution = rootNode.mipSolution
        if (lowerBound >= upperBound + Parameters.eps) {
            throw OrienteeringException("lower bound overshoots upper bound")
        }
        return rootNode
    }

    /**
     * Prunes [node] by optimality if possible and returns true, returns false otherwise.
     */
    private fun pruneByOptimality(node: Node): Boolean {
        if (!isIntegral(node.lpSolution)) {
            return false
        }
        if (node.lpObjective >= lowerBound + Parameters.eps) {
            lowerBound = node.lpObjective
            bestFeasibleSolution = node.lpSolution.map { it.first }
            logger.debug("updating lower bound based on MIP solution of $node")
        }
        logger.debug("$node pruned by optimality (integral LP solution)")
        return true
    }

    private fun isIntegral(columnsAndValues: List<Pair<Route, Double>>): Boolean {
        return columnsAndValues.all {
            it.second >= 1.0 - Parameters.eps
        }
    }

    companion object : KLogging()
}
