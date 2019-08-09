package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
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

    lateinit var finalSolution: BranchAndPriceSolution
        private set

    @ObsoleteCoroutinesApi
    suspend fun solve() {
        val rootNode = solveRootNode()
        if (rootUpperBound - rootLowerBound <= Parameters.eps) {
            logger.info("gap closed in root node")
            return
        }

        val numSolverActors = Parameters.numSolverActors
        withContext(Dispatchers.Default + CoroutineName("BranchAndPrice_")) {
            val nodeActor = nodeActor(coroutineContext, instance, numSolverActors)
            val solverActors = (0 until numSolverActors).map {
                solverActor(it, nodeActor, instance, coroutineContext)
            }

            nodeActor.send(ProcessSolvedNode(rootNode))
            while (true) {
                val nodesAvailableMessage = CanRelease()
                nodeActor.send(nodesAvailableMessage)
                val nodesAvailable =nodesAvailableMessage.response.await()
                // logger.info("nodes available: $nodesAvailable")
                if (nodesAvailable) {
                    nodeActor.send(ReleaseOpenNode(solverActors))
                    continue
                }

                val terminateMessage = Terminate()
                nodeActor.send(terminateMessage)
                val solution = terminateMessage.response.await()
                if (solution != null) {
                    finalSolution = solution
                    break
                }

                delay(100L)
            }

            for (solverActor in solverActors) {
                solverActor.close()
            }
            nodeActor.close()
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

    companion object : KLogging()
}
