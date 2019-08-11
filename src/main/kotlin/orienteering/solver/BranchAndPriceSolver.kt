package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
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
            logger.info("launching solver coroutines")
            repeat(numSolverActors) {
                solver(it, instance, unsolvedNodes, solvedNodes)
            }
            logger.info("launched solver coroutines")

            logger.info("creating solver actor")
            val nodeActor = nodeActor(instance, numSolverActors)
            logger.info("created solver actor")

            logger.info("launching root node send")
            launch {
                solvedNodes.send(rootNode)
                logger.info("sent root node")
            }
            logger.info("launched root node send")

            var solvedNode: Node? = null
            while (!unsolvedNodes.isClosedForSend) {
                logger.info("while loop resumes...")
                select<Unit> {
                    if (solvedNode != null) {
                        nodeActor.onSend(ProcessSolvedNode(solvedNode!!, unsolvedNodes)) {
                            solvedNode = null
                        }
                    } else {
                        solvedNodes.onReceive {
                            solvedNode = it
                        }
                    }
                }
            }

            val terminateMessage = Terminate()
            nodeActor.send(terminateMessage)
            val solution = terminateMessage.response.await()
            if (solution != null) {
                finalSolution = solution
            }
            coroutineContext.cancelChildren()
        }
        /*
        withContext(Dispatchers.Default + CoroutineName("BranchAndPrice_")) {
            val solverActors = (0 until numSolverActors).map {
                solverActor(it, nodeActor, instance)
            }

            nodeActor.send(ProcessSolvedNode(rootNode))
            while (true) {
                delay(100L)

                val nodesAvailableMessage = CanRelease()
                nodeActor.send(nodesAvailableMessage)
                val nodesAvailable = nodesAvailableMessage.response.await()
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
            }
            coroutineContext.cancelChildren()
        }
         */
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
