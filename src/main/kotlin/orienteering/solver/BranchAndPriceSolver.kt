package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.main.OrienteeringException
import orienteering.main.preProcess

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

    /**
     * Channel that sends nodes from the node processor to solver coroutines for solving.
     */
    private val unsolvedNodes = Channel<Node>()
    /**
     * Channel that sends nodes from solver coroutines to node processor for bounding updating,
     * pruning and/or branching.
     */
    private val solvedNodes = Channel<Node>()

    fun solve(): BranchAndPriceSolution = runBlocking {
        withContext(Dispatchers.Default) {
            solveInParallel(this)
        }
    }

    private suspend fun solveInParallel(scope: CoroutineScope): BranchAndPriceSolution {
        val numSolvers = Parameters.numSolverCoroutines
        val solution = CompletableDeferred<BranchAndPriceSolution>()
        var finalSolution: BranchAndPriceSolution
        scope.launch {
            // Prepare a set of coroutines to consume nodes from the unsolvedNodes channel, solve them and send back
            // the solved nodes to the solvedNodes channel. Each coroutine is created by the "launch" command and
            // remains suspended at the for loop line, i.e. waiting for the iterator of the unsolvedNodes channel to
            // release a node.
            repeat(numSolvers) {
                scope.launch {
                    // This CPLEX object will be created just once and be re-used for all nodes released by the
                    // iterator in the loop below. This is because the scope (together with the CPLEX object and the
                    // solveNode() function call will persist as long as the for loop is suspended. We will go out of
                    // scope only when the unsolvedNodes channel is closed.
                    val cplex = IloCplex()
                    for (node in unsolvedNodes) {
                        solveNode(cplex, node)
                    }
                }
            }

            // Solve the root node by sending it to the unsolvedNodes channel and consuming it immediately from the
            // solvedNodes channel to collect root nodes bounds and an initial solution.
            val solvedRootNode = solveRootNode()
            finalSolution = BranchAndPriceSolution(
                optimalityReached = rootUpperBound - rootLowerBound <= Parameters.eps,
                lowerBound = rootLowerBound,
                upperBound = rootUpperBound,
                bestFeasibleSolution = solvedRootNode.mipSolution
            )

            // Prepare to send the root node back into the solvedNodes channel so that the node processor can branch on
            // it if needed.
            scope.launch {
                logger.info("sending solvedRootNode $solvedRootNode back into solvedNodes")
                solvedNodes.send(solvedRootNode)
            }

            // Prepare a coroutine to consume nodes from the solvedNodes channel and use their LP/MIP objective and
            // solution values to update global bounds, prune them or branch on them. If branched, new unsolved nodes
            // will be stored in the node processor's open node queue and released to the unsolvedNodes channel
            // whenever solvers are available.
            scope.launch {
                val nodeProcessor = NodeProcessor(solvedRootNode, instance, numSolvers, solution)
                for (node in solvedNodes) {
                    logger.info("received $node in solvedNodes channel for nodeProcessor")
                    nodeProcessor.processSolvedNode(node, unsolvedNodes)
                }
            }
        }

        // The data pipeline we have set up is:
        // 1. Consume node from the solvedNodes channel with the node processor.
        // 2. If the algorithm can terminate, complete the deferred "solution" variable with a value.
        // 3. Otherwise, the node processor should still have some open nodes. Send these nodes to the unsolvedNodes
        //    channel whenever there are solvers available.
        // 4. Consume nodes from the unsolved nodes channel with the solver pool.
        // 5. Send back solved nodes to the solvedNodes channel.
        // As explained above, the only termination condition now is that the "solution" completes. We simply wait for
        // it here.
        finalSolution = solution.await()
        scope.coroutineContext.cancelChildren()
        return finalSolution
    }

    private suspend fun solveRootNode(): Node {
        unsolvedNodes.send(Node.buildRootNode(instance.graph))
        val rootNode = solvedNodes.receive()
        logger.info("received $rootNode in solveRootNode")
        rootUpperBound = rootNode.lpObjective
        rootLowerBound = rootNode.mipObjective
        rootLpOptimal = rootNode.lpOptimal
        if (rootLowerBound >= rootUpperBound + Parameters.eps) {
            throw OrienteeringException("lower bound overshoots upper bound")
        }

        return rootNode
    }

    private suspend fun solveNode(cplex: IloCplex, node: Node) {
        logger.info("$node solve starting")
        preProcess(
            node.graph,
            instance.budget,
            instance.getVertices(instance.sourceTarget),
            instance.getVertices(instance.destinationTarget)
        )
        node.solve(instance, cplex)
        logger.info("$node sending to solvedNodes channel after solving")
        solvedNodes.send(node)
        logger.info("$node sent to solvedNodes channel after solving")
    }

    companion object : KLogging()
}
