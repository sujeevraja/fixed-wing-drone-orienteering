package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.main.OrienteeringException
import orienteering.main.preProcess

class BranchAndPriceSolver(private val instance: Instance) {
    var rootLowerBound: Double = -Double.MAX_VALUE
        private set

    var rootUpperBound: Double = Double.MAX_VALUE
        private set

    lateinit var finalSolution: BranchAndPriceSolution
        private set

    fun solve() {
        runBlocking(Dispatchers.Default + CoroutineName("BranchAndPriceSolver_")) {
            val numSolverActors = Parameters.numSolverActors
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

            val solvedRootNode = solveRootNode(unsolvedNodes, solvedNodes)
            launch {
                logger.info("sending solvedRootNode $solvedRootNode back into solvedNodes")
                solvedNodes.send(solvedRootNode)
            }
            launch {
                val nodeProcessor =
                    NodeProcessor(solvedRootNode, instance, numSolverActors, solution)
                for (node in solvedNodes) {
                    logger.info("received $node in solvedNodes channel for nodeProcessor")
                    nodeProcessor.processSolvedNode(node, unsolvedNodes)
                }
            }

            finalSolution = solution.await()
            coroutineContext.cancelChildren()
        }
    }

    private suspend fun solveRootNode(
        unsolvedNodes: SendChannel<Node>,
        solvedNodes: ReceiveChannel<Node>
    ): Node {
        unsolvedNodes.send(Node.buildRootNode(instance.graph))
        val rootNode = solvedNodes.receive()
        logger.info("received $rootNode in solveRootNode")
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
