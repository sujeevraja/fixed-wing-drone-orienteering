package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.OrienteeringException
import orienteering.main.SetGraph
import orienteering.main.getCopy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.math.round

class Node private constructor(
    val graph: SetGraph,
    private val mustVisitTargets: IntArray,
    private val mustVisitTargetEdges: List<Pair<Int, Int>>,
    upperBound: Double
) : Comparable<Node> {
    val index = getNodeIndex()

    var feasible = true
        private set

    var lpObjective = upperBound
        private set

    var lpSolution = listOf<Pair<Route, Double>>()
        private set

    var mipObjective = -Double.MAX_VALUE
        private set

    var mipSolution = listOf<Route>()
        private set

    var lpIntegral = false
        private set

    private lateinit var targetReducedCosts: List<Double>

    override fun toString(): String {
        return "Node($index, bound=$lpObjective, feasible=$feasible)"
    }

    fun logInfo() {
        logger.debug("index: $index")
        logger.debug("vertices: ${graph.vertexSet()}")
        logger.debug("must visit targets: ${mustVisitTargets.toList()}")
        logger.debug("must visit target edges: $mustVisitTargetEdges")
    }

    fun isFeasible(instance: Instance): Boolean {
        // Check if source target has at least 1 outgoing edge.
        val sourceEdgeExists = instance.getVertices(instance.sourceTarget).any {
            graph.containsVertex(it) && Graphs.vertexHasSuccessors(graph, it)
        }
        if (!sourceEdgeExists) {
            return false
        }

        // Check if destination target has at least 1 incoming edge.
        val destinationEdgeExists = instance.getVertices(instance.destinationTarget).any {
            graph.containsVertex(it) && Graphs.vertexHasPredecessors(graph, it)
        }
        if (!destinationEdgeExists) {
            return false
        }

        // Check if all must-visit targets are connected.
        for (target in mustVisitTargets) {
            if (instance.getVertices(target).none { isVertexConnected(it) }) {
                logger.debug("graph does not contain a must-visit target")
                return false
            }
        }

        // Check if all must-visit edges are present in graph.
        val requiredEdgesExist = mustVisitTargetEdges.all {
            graph.containsEdge(it.first, it.second)
        }

        if (!requiredEdgesExist) {
            logger.debug("graph does not contain a must-visit edge")
        }
        return requiredEdgesExist
    }

    private fun isVertexConnected(vertex: Int): Boolean {
        return (graph.containsVertex(vertex) &&
                Graphs.vertexHasPredecessors(graph, vertex) &&
                Graphs.vertexHasSuccessors(graph, vertex))
    }

    fun solve(instance: Instance, cplex: IloCplex) {
        logger.debug("solving node number $index")
        val cgSolver = ColumnGenSolver(
            instance,
            cplex,
            graph,
            mustVisitTargets,
            mustVisitTargetEdges
        )
        cgSolver.solve()
        feasible = !cgSolver.lpInfeasible
        if (feasible) {
            if (lpObjective <= cgSolver.lpObjective - Parameters.eps) {
                throw OrienteeringException("parent node LP objective smaller than child's")
            }
            lpObjective = cgSolver.lpObjective
            lpSolution = cgSolver.lpSolution
            mipObjective = cgSolver.mipObjective
            mipSolution = cgSolver.mipSolution
            targetReducedCosts = cgSolver.targetReducedCosts
            lpIntegral = lpSolution.all {
                it.second >= 1.0 - Parameters.eps
            }
        }
    }

    fun branch(instance: Instance): List<Node> {
        // Find flows to each target and on edges between targets.
        val targetFlows = MutableList(instance.numTargets) { 0.0 }
        val targetEdgeFlows: MutableMap<Int, MutableMap<Int, Double>> = mutableMapOf()

        for ((route, slnVal) in lpSolution) {
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
                targetReducedCosts[i] <= leastReducedCost!! - Parameters.eps
            ) {
                bestTarget = i
                leastReducedCost = targetReducedCosts[i]
            }
        }

        // If a target is found, branch on it.
        if (bestTarget != null) {
            return branchOnTarget(bestTarget, instance.getVertices(bestTarget))
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
                    targetReducedCosts[fromTarget] <= leastReducedCost!! - Parameters.eps
                ) {
                    bestEdge = Pair(fromTarget, toTarget)
                    leastReducedCost = targetReducedCosts[fromTarget]
                }
            }
        }

        val bestFromTarget = bestEdge!!.first
        val bestToTarget = bestEdge.second
        return branchOnTargetEdge(bestFromTarget, bestToTarget, instance)
    }

    private fun isInteger(num: Double): Boolean {
        return (num - round(num)).absoluteValue <= Parameters.eps
    }

    private fun branchOnTarget(target: Int, targetVertices: List<Int>): List<Node> {
        logger.debug("branching $this on target $target")
        val noVisitNode = getChildWithoutTarget(targetVertices)
        logger.debug("child without $target: $noVisitNode")

        val mustVisitNode = getChildWithTarget(target)
        logger.debug("child with $target: $mustVisitNode")

        return listOf(noVisitNode, mustVisitNode)
    }

    private fun branchOnTargetEdge(fromTarget: Int, toTarget: Int, instance: Instance): List<Node> {
        val childNodes = mutableListOf<Node>()
        if (fromTarget in mustVisitTargets || toTarget in mustVisitTargets) {
            logger.debug("branching $this with target visit already enforced")

            childNodes.add(getChildWithoutTargetEdge(fromTarget, toTarget, instance))
            logger.debug("child without $fromTarget -> $toTarget: ${childNodes.last()}")

            childNodes.add(getChildWithTargetEdge(fromTarget, toTarget))
            logger.debug("child with $fromTarget -> $toTarget: ${childNodes.last()}")
        } else {
            logger.debug("branching $this without target visit already enforced")
            childNodes.add(getChildWithoutTarget(instance.getVertices(fromTarget)))
            logger.debug("child without $fromTarget: ${childNodes.last()}")

            childNodes.add(
                getChildWithTarget(fromTarget).getChildWithoutTargetEdge(
                    fromTarget,
                    toTarget,
                    instance
                )
            )
            logger.debug("child with $fromTarget, without $fromTarget -> $toTarget: ${childNodes.last()}")

            childNodes.add(
                getChildWithTarget(fromTarget).getChildWithTargetEdge(
                    fromTarget,
                    toTarget
                )
            )
            logger.debug("child with $fromTarget, with $fromTarget -> $toTarget: ${childNodes.last()}")
        }

        return childNodes
    }

    private fun getChildWithoutTarget(targetVertices: List<Int>): Node {
        val reducedGraph = graph.getCopy()
        for (vertex in targetVertices) {
            reducedGraph.removeVertex(vertex)
        }
        return Node(reducedGraph, mustVisitTargets, mustVisitTargetEdges, lpObjective)
    }

    private fun getChildWithTarget(target: Int): Node {
        return Node(graph, mustVisitTargets + target, mustVisitTargetEdges, lpObjective)
    }

    private fun getChildWithoutTargetEdge(
        fromTarget: Int,
        toTarget: Int,
        instance: Instance
    ): Node {
        val reducedGraph = graph.getCopy()
        for (vertex in instance.getVertices(fromTarget)) {
            val edgesToRemove = mutableListOf<DefaultWeightedEdge>()
            for (nextVertex in Graphs.successorListOf(graph, vertex)) {
                if (instance.whichTarget(nextVertex) == toTarget) {
                    edgesToRemove.add(graph.getEdge(vertex, nextVertex))
                }
            }

            edgesToRemove.forEach { graph.removeEdge(it) }
        }
        return Node(reducedGraph, mustVisitTargets, mustVisitTargetEdges, lpObjective)
    }

    private fun getChildWithTargetEdge(fromTarget: Int, toTarget: Int): Node {
        val newMustVisitTargetEdges = mustVisitTargetEdges.toMutableList()
        newMustVisitTargetEdges.add(Pair(fromTarget, toTarget))
        return Node(graph, mustVisitTargets, newMustVisitTargetEdges, lpObjective)
    }

    override fun compareTo(other: Node): Int {
        return when {
            lpObjective >= other.lpObjective + Parameters.eps -> -1
            lpObjective <= other.lpObjective - Parameters.eps -> 1
            index < other.index -> -1
            index > other.index -> 1
            else -> 0
        }
    }

    companion object : KLogging() {
        fun buildRootNode(graph: SetGraph): Node =
            Node(graph, intArrayOf(), listOf(), Double.MAX_VALUE)

        var nodeCount = AtomicInteger(0)
            private set

        fun getNodeIndex(): Int {
            return nodeCount.getAndIncrement()
        }
    }
}
