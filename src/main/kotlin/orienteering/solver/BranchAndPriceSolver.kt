package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.Constants
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.Route
import orienteering.data.preProcess
import orienteering.numVertices
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.round

class BranchAndPriceSolver(
    private val instance: Instance,
    private val numReducedCostColumns: Int,
    private val cplex: IloCplex
) {
    private var lowerBound: Double = -Double.MAX_VALUE
    private var bestFeasibleSolution = listOf<Route>()
    private var upperBound: Double = Double.MAX_VALUE
    private val openNodes = PriorityQueue<Node>()

    fun solve(): List<Route> {
        initialize()
        while (openNodes.isNotEmpty()) {
            val node = openNodes.remove()
            logger.debug("processing $node")
            logger.debug("upper bound: $upperBound")
            logger.debug("lower bound: $lowerBound")
            node.logInfo()

            if (pruneByOptimality(node)) {
                continue
            }

            val childNodes = branch(node)
            for (childNode in childNodes) {
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
                    logger.debug("$childNode pruned by infeasibility")
                    continue
                }

                childNode.solve(instance, numReducedCostColumns, cplex)
                if (childNode.lpObjective <= lowerBound + Constants.EPS) {
                    logger.debug("$childNode pruned by bound")
                    continue
                }

                if (childNode.mipObjective >= lowerBound + Constants.EPS) {
                    lowerBound = childNode.mipObjective
                    bestFeasibleSolution = childNode.mipSolution
                    logger.debug("updated lower bound using open child node: $lowerBound")
                }
                openNodes.add(childNode)
                upperBound = openNodes.peek().lpObjective
                logger.debug("added $childNode to open nodes")
                logger.debug("upper bound: $upperBound")
                logger.debug("lower bound: $lowerBound")
            }
        }
        return bestFeasibleSolution
    }

    private fun initialize() {
        val rootNode = Node.buildRootNode(instance.graph, instance.numTargets)
        rootNode.solve(instance, numReducedCostColumns, cplex)
        upperBound = rootNode.lpObjective
        lowerBound = rootNode.mipObjective
        bestFeasibleSolution = rootNode.mipSolution
        if (lowerBound >= upperBound + Constants.EPS) {
            throw OrienteeringException("lower bound overshoots upper bound")
        }
        if (upperBound - lowerBound <= Constants.EPS) {
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
        if (node.lpObjective >= lowerBound + Constants.EPS) {
            lowerBound = node.lpObjective
            bestFeasibleSolution = node.lpSolution.map { it.first }
            logger.debug("updating lower bound based on MIP solution of $node")
        }
        logger.debug("$node pruned by optimality (integral LP solution)")
        logger.debug("upper bound: $upperBound")
        logger.debug("lower bound: $lowerBound")
        return true
    }

    private fun isIntegral(columnsAndValues: List<Pair<Route, Double>>): Boolean {
        return columnsAndValues.all {
            it.second >= 1.0 - Constants.EPS
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
                node.targetReducedCosts[i] <= leastReducedCost!! - Constants.EPS
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
                    node.targetReducedCosts[fromTarget] <= leastReducedCost!! - Constants.EPS
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
        return (num - round(num)).absoluteValue <= Constants.EPS
    }

    companion object : KLogging()
}
