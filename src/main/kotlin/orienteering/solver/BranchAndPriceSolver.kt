package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.OrienteeringException
import orienteering.main.numVertices
import orienteering.main.preProcess
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.round

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
        solveRootNode()
        val numActors = 3

        withContext(Dispatchers.Default) {
            val actors = (0 until numActors).map {
                solverActor(coroutineContext)
            }
            while (openNodes.isNotEmpty()) {
                if (TimeChecker.timeLimitReached()) {
                    break
                }

                val node = openNodes.remove()
                logger.debug("processing $node")
                logger.debug("upper bound: $upperBound")
                logger.debug("lower bound: $lowerBound")
                node.logInfo()

                if (pruneByOptimality(node)) {
                    continue
                }

                val childNodes = branch(node)
                val responses = List(childNodes.size) { CompletableDeferred<Boolean>() }

                childNodes.forEachIndexed { index, childNode ->
                    logger.debug("solving LP for child $childNode")
                    childNode.logInfo()

                    logger.debug("number of vertices before pre-processing: ${childNode.graph.numVertices()}")
                    preProcess(
                        childNode.graph,
                        instance.budget,
                        instance.getVertices(instance.sourceTarget),
                        instance.getVertices(instance.destinationTarget)
                    )
                    logger.debug("number of vertices after pre-processing: ${childNode.graph.numVertices()}")

                    if (!childNode.isFeasible(instance)) {
                        logger.debug("$childNode pruned by infeasibility before solving")
                        return@forEachIndexed
                    }

                    /*
                    println("sending message $index to actor $index")
                    actors[index].send(
                        Envelope(
                            index,
                            Payload(childNode, instance),
                            Solve(responses[index])
                        )
                    )
                     */

                    select {
                        actors.forEach {
                            it.onSend(
                                Solve(index, childNode, instance, responses[index])
                            ) {
                                logger.debug("sent message $index")
                            }
                        }
                    }
                }

                responses.forEach {
                    logger.debug("actor solve status ${it.await()}")
                }

                for (childNode in childNodes) {
                    if (!childNode.feasible) {
                        logger.debug("$childNode pruned by infeasibility after solving")
                    } else if (childNode.lpObjective <= lowerBound + Parameters.eps) {
                        logger.debug("$childNode pruned by bound")
                    } else {
                        if (childNode.mipObjective >= lowerBound + Parameters.eps) {
                            lowerBound = childNode.mipObjective
                            bestFeasibleSolution = childNode.mipSolution
                            logger.debug("updated lower bound using open child node: $lowerBound")
                        }
                        openNodes.add(childNode)
                        logger.debug("added $childNode to open nodes")
                    }
                    if (TimeChecker.timeLimitReached()) {
                        break
                    }
                }

                if (openNodes.isNotEmpty()) {
                    val newUpperBound = openNodes.peek().lpObjective
                    if (upperBound <= newUpperBound - Parameters.eps) {
                        logger.error("existing upper bound: $upperBound")
                        logger.error("open nodes upper bound: $newUpperBound")
                        throw OrienteeringException("upper bound smaller than it should be")
                    }
                    upperBound = newUpperBound
                }
            }

            numNodes = Node.nodeCount - 1
            if (!TimeChecker.timeLimitReached()) {
                optimalityReached = true
            }

            actors.forEach {
                it.send(ClearCPLEX)
                // it.close()
            }
            coroutineContext.cancelChildren()
        }
    }

    private fun solveRootNode() {
        val rootNode = Node.buildRootNode(instance.graph)
        rootNode.solve(instance, cplex)

        upperBound = rootNode.lpObjective
        rootUpperBound = upperBound

        lowerBound = rootNode.mipObjective
        rootLowerBound = lowerBound

        bestFeasibleSolution = rootNode.mipSolution
        if (lowerBound >= upperBound + Parameters.eps) {
            throw OrienteeringException("lower bound overshoots upper bound")
        }
        if (upperBound - lowerBound <= Parameters.eps) {
            logger.info("gap closed in root node")
        } else {
            openNodes.add(rootNode)
        }
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

    private fun branch(node: Node): List<Node> {
        // Find flows to each target and on edges between targets.
        val targetFlows = MutableList(instance.numTargets) { 0.0 }
        val targetEdgeFlows: MutableMap<Int, MutableMap<Int, Double>> = mutableMapOf()

        for ((route, slnVal) in node.lpSolution) {
            val path = route.vertexPath
            for (i in 0 until path.size - 1) {
                val currTarget = route.targetPath[i]
                val nextTarget = route.targetPath[i + 1]
                targetFlows[nextTarget] += slnVal

                targetEdgeFlows.putIfAbsent(currTarget, hashMapOf())

                val outFlowMap = targetEdgeFlows[currTarget]!!
                val edgeFlow = slnVal + outFlowMap.getOrDefault(nextTarget, 0.0)
                outFlowMap[nextTarget] = edgeFlow
            }
        }

        // Try to find a target for branching.
        var bestTarget: Int? = null
        var leastReducedCost: Double? = null
        for (i in 0 until targetFlows.size) {
            if (i == instance.sourceTarget ||
                i == instance.destinationTarget ||
                isInteger(targetFlows[i])
            ) {
                continue
            }

            if (bestTarget == null ||
                node.targetReducedCosts[i] <= leastReducedCost!! - Parameters.eps

            ) {
                bestTarget = i
                leastReducedCost = node.targetReducedCosts[i]
            }
        }

        // If a target is found, branch on it.
        if (bestTarget != null) {
            return node.branchOnTarget(bestTarget, instance.getVertices(bestTarget))
        }

        // Otherwise, find a target edge to branch. Among fractional flow edges, we select the one
        // with a starting vertex that has least reduced cost.
        var bestEdge: Pair<Int, Int>? = null
        for ((fromTarget, flowMap) in targetEdgeFlows) {
            for ((toTarget, flow) in flowMap) {
                if (isInteger(flow)) {
                    continue
                }
                if (bestEdge == null ||
                    node.targetReducedCosts[fromTarget] <= leastReducedCost!! - Parameters.eps
                ) {
                    bestEdge = Pair(fromTarget, toTarget)
                    leastReducedCost = node.targetReducedCosts[fromTarget]
                }
            }
        }

        val bestFromTarget = bestEdge!!.first
        val bestToTarget = bestEdge.second
        return node.branchOnTargetEdge(bestFromTarget, bestToTarget, instance)
    }

    private fun isInteger(num: Double): Boolean {
        return (num - round(num)).absoluteValue <= Parameters.eps
    }

    companion object : KLogging()
}
