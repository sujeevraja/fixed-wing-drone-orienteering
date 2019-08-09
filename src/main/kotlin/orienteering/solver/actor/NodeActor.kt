package orienteering.solver.actor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.selects.select
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.OrienteeringException
import orienteering.solver.Node
import orienteering.solver.TimeChecker
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

sealed class NodeActorMessage
data class ReleaseOpenNode(val solverActors: List<SolverActor>) : NodeActorMessage()
data class ProcessSolvedNode(val node: Node) : NodeActorMessage()
class CanRelease : NodeActorMessage() {
    val response = CompletableDeferred<Boolean>()
}

class CanStop : NodeActorMessage() {
    val response = CompletableDeferred<Boolean>()
}

class NodeActorState(private val instance: Instance, private val numSolvers: Int) :
    ActorState<NodeActorMessage>() {
    private var lowerBound = -Double.MAX_VALUE
    private var upperBound = Double.MAX_VALUE
    private var bestFeasibleSolution = listOf<Route>()
    private val openNodes = PriorityQueue<Node>()
    private var solvingNodes = mutableMapOf<Int, Double>()
    private val numSolving: Int
        get() = solvingNodes.size

    override suspend fun handle(message: NodeActorMessage) {
        when (message) {
            is ReleaseOpenNode -> {
                val node = openNodes.remove()
                solvingNodes[node.index] = node.lpObjective

                logger.info("received ReleaseOpenNode")
                logger.info("numNodes ${openNodes.size}")
                logger.info("numSolving: $numSolving")

                select<Unit> {
                    message.solverActors.forEachIndexed { index, actor ->
                        actor.onSend(SolveNode(node)) {
                            logger.info("sent $node to solver actor $index")
                        }
                    }
                }
            }
            is ProcessSolvedNode -> {
                val node = message.node
                solvingNodes.remove(node.index)

                logger.debug("received solved node $node")
                logger.info("numNodes ${openNodes.size}")
                logger.info("numSolving: $numSolving")

                if (!prune(message.node)) {
                    branch(message.node)
                }
                updateUpperBound()
            }
            is CanRelease -> {
                // logger.info("received CanRelease")
                // logger.info("numNodes ${openNodes.size}")
                // logger.info("numSolving: $numSolving")
                // logger.info("numIdle: $numSolving")
                message.response.complete(openNodes.isNotEmpty() && numSolving < numSolvers)
            }
            is CanStop -> {
                // logger.info("received CanStop")
                // logger.info("numNodes ${openNodes.size}")
                // logger.info("numNodesSolving: $numSolving")
                var stop = false
                if (canStop()) {
                    printFinalSolution()
                    stop = true
                }
                message.response.complete(stop)
            }
        }
    }

    private fun prune(node: Node): Boolean {
        if (!node.feasible) {
            logger.info("$node pruned by infeasibility after solving")
            return true
        }
        if (node.lpObjective <= lowerBound + Parameters.eps) {
            logger.info("$node pruned by bound")
            return true
        }
        if (node.lpIntegral) {
            updateLowerBound(node)
            logger.debug("$node pruned by optimality (LP integral)")
            return true
        }
        return false
    }

    private fun updateLowerBound(node: Node) {
        if (lowerBound <= node.mipObjective - Parameters.eps) {
            lowerBound = node.mipObjective
            bestFeasibleSolution = node.mipSolution
        }
    }

    private fun updateUpperBound() {
        if (openNodes.isEmpty() && solvingNodes.isEmpty()) {
            return
        }

        val ubFromUnsolvedNodes =
            if (openNodes.isNotEmpty()) openNodes.peek().lpObjective else -Double.MAX_VALUE
        val ubFromSolvingNodes = solvingNodes.values.max() ?: -Double.MAX_VALUE
        val newUpperBound = max(ubFromUnsolvedNodes, ubFromSolvingNodes)

        if (solvingNodes.isNotEmpty()) {
            logger.info("solving node bounds")
            for ((index, bound) in solvingNodes) {
                logger.info("$index -> $bound")
            }
        }
        if (openNodes.isNotEmpty()) {
            logger.info("unsolved node bounds")
            for (node in openNodes) {
                logger.info("${node.index} -> ${node.lpObjective}")
            }
        }
        logger.info("existing upper bound: $upperBound")
        logger.info("upper bound from solving/unsolved nodes: $newUpperBound")
        if (upperBound <= newUpperBound - Parameters.eps) {
            throw OrienteeringException("upper bound not monotonic")
        }
        upperBound = newUpperBound
        logger.info("upper bound: $upperBound")
        logger.info("lower bound: $lowerBound")
        logger.info("number of open nodes: ${openNodes.size}")
    }

    private fun branch(node: Node) {
        logger.info("branching on node $node")
        updateLowerBound(node)
        node.branch(instance).map {
            logger.info("child $it")
            openNodes.add(it)
        }
    }

    private fun canStop(): Boolean {
        if (TimeChecker.timeLimitReached()) {
            logger.info("terminating by time limit")
            return true
        }
        if (upperBound - lowerBound <= Parameters.eps) {
            logger.info("terminating by bound tolerance")
            return true
        }
        if (openNodes.isEmpty() && numSolving == 0) {
            logger.info("terminating by completion of exploration")
            return true
        }
        return false
    }

    private fun printFinalSolution() {
        logger.info("final upper bound: $upperBound")
        logger.info("final lower bound: $lowerBound")
        logger.info("final solution:")
        for (route in bestFeasibleSolution) {
            logger.info(route.toString())
        }
    }
}

@ObsoleteCoroutinesApi
fun CoroutineScope.nodeActor(context: CoroutineContext, instance: Instance, numSolvers: Int) =
    actor<NodeActorMessage>(context = context + CoroutineName("NodeActor_")) {
        val state = NodeActorState(instance, numSolvers)
        for (message in channel) {
            state.handle(message)
        }
    }

typealias NodeActor = SendChannel<NodeActorMessage>
