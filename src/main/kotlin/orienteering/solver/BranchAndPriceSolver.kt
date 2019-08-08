package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.OrienteeringException
import orienteering.solver.actor.*

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

        val numSolverActors = 8
        withContext(Dispatchers.Default + CoroutineName("BranchAndPrice_")) {
            val newNodeActor = newNodeActor(coroutineContext, instance, numSolverActors)
            val solverActors = (0 until numSolverActors).map {
                newSolverActor(it, newNodeActor, instance, coroutineContext)
            }

            newNodeActor.send(ProcessSolvedNode(rootNode))
            while (true) {
                val nodesAvailableMessage = CanRelease()
                newNodeActor.send(nodesAvailableMessage)
                val nodesAvailable =nodesAvailableMessage.response.await()
                logger.info("nodes available: $nodesAvailable")
                if (nodesAvailable) {
                    newNodeActor.send(ReleaseOpenNode(solverActors))
                    continue
                }

                val canStopMessage = CanStop()
                newNodeActor.send(canStopMessage)
                val canStop = canStopMessage.response.await()
                logger.info("can stop: $canStop")
                if (canStop) {
                    break
                }

                delay(1000L)
            }

            newNodeActor.close()
            for (solverActor in solverActors) {
                solverActor.close()
            }
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
