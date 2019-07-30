package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.Constants
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.Route
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

            // Find flows to each target and vertex.
            val targetFlows = MutableList(instance.numTargets) { 0.0 }
            val edgeFlows: MutableMap<Int, MutableMap<Int, Double>> = mutableMapOf()

            for ((route, slnVal) in node.lpSolution) {
                val path = route.path
                for (i in 0 until path.size - 1) {
                    val nextVertex = path[i + 1]
                    targetFlows[instance.whichTarget(nextVertex)] += slnVal

                    val currVertex = path[i]
                    edgeFlows.putIfAbsent(currVertex, hashMapOf())

                    val outFlowMap = edgeFlows[currVertex]!!
                    outFlowMap[nextVertex] = slnVal + outFlowMap.getOrDefault(nextVertex, 0.0)
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
                val childNodes = node.branchOnTarget(bestTarget, instance.getVertices(bestTarget))
                for (childNode in childNodes) {
                    logger.debug("solving LP for child $childNode")
                    childNode.logInfo()
                    if (!childNode.isFeasible(instance)) {
                        logger.debug("$childNode pruned by infeasibility")
                    } else {
                        childNode.solve(instance, numReducedCostColumns, cplex)
                        if (childNode.lpObjective <= lowerBound + Constants.EPS) {
                            logger.debug("$childNode pruned by bound")
                        } else {
                            if (childNode.mipObjective >= lowerBound + Constants.EPS) {
                                lowerBound = childNode.mipObjective
                                bestFeasibleSolution = childNode.mipSolution
                                logger.debug("updated lower bound: $lowerBound")
                            }
                            openNodes.add(childNode)
                        }
                    }
                }
                if (openNodes.isNotEmpty()) {
                    upperBound = openNodes.peek().lpObjective
                }
                logger.debug("upper bound: $upperBound")
                logger.debug("lower bound: $lowerBound")
                continue
            }

            // Otherwise, find an edge to branch. Among fractional flow edges, we select the one with
            // a starting vertex that has least reduced cost.
            var bestEdge: Pair<Int, Int>? = null
            for ((fromVertex, flowMap) in edgeFlows) {
                val fromTarget = instance.whichTarget(fromVertex)
                for ((toVertex, flow) in flowMap) {
                    if (isInteger(flow)) {
                        continue
                    }
                    if (bestEdge == null || node.targetReducedCosts[fromTarget] <= leastReducedCost!! - Constants.EPS) {
                        bestEdge = Pair(fromVertex, toVertex)
                        leastReducedCost = node.targetReducedCosts[fromTarget]
                    }
                }
            }

            val bestFromVertex = bestEdge!!.first
            val bestToVertex = bestEdge.second
            val childNodes = node.branchOnEdge(bestFromVertex, bestToVertex, instance)
            for (childNode in childNodes) {
                logger.debug("solving LP for node $childNode")
                childNode.logInfo()
                if (!childNode.isFeasible(instance)) {
                    logger.debug("$childNode pruned by infeasibility")
                } else {
                    childNode.solve(instance, numReducedCostColumns, cplex)
                    if (childNode.lpObjective <= lowerBound + Constants.EPS) {
                        logger.debug("$childNode pruned by bound")
                    } else {
                        if (childNode.mipObjective >= lowerBound + Constants.EPS) {
                            lowerBound = childNode.mipObjective
                            bestFeasibleSolution = childNode.mipSolution
                            logger.debug("updated lower bound: $lowerBound")
                        }
                        openNodes.add(childNode)
                    }
                }
            }
            upperBound = openNodes.peek().lpObjective
            logger.debug("upper bound: $upperBound")
            logger.debug("lower bound: $lowerBound")
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

    private fun isInteger(num: Double): Boolean {
        return (num - round(num)).absoluteValue <= Constants.EPS
    }

    companion object : KLogging()
}
