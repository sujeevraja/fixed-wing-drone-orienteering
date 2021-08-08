package orienteering.solver

import branchandbound.api.INode
import mu.KotlinLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import orienteering.Constants
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.SetGraph
import orienteering.main.format
import orienteering.main.getCopy
import kotlin.math.absoluteValue
import kotlin.math.round

private val log = KotlinLogging.logger {}

/**
 * Class to store solution and constraint data at nodes of a branch-and-bound tree.
 *
 * @param graph graph with usable vertices and edges to build routes.
 * @param mustVisitTargets targets that must be visited by at least 1 route in any feasible solution.
 * @param mustVisitTargetEdges target connections necessary to be present in at least 1 route in any feasible solution.
 */
data class Node(
    override val id: Long,
    val graph: SetGraph,
    val mustVisitTargets: IntArray = intArrayOf(),
    val mustVisitTargetEdges: List<Pair<Int, Int>> = listOf(),
    override val parentLpObjective: Double = Double.MAX_VALUE,
    val lpSolved: Boolean = false,
    override val lpFeasible: Boolean = false,
    /**
     * True if LP optimality is proved by lack of negative reduced cost columns.
     */
    val lpOptimal: Boolean = false,
    /**
     * True if all decision variables have binary values in LP solution, false otherwise.
     */
    override val lpIntegral: Boolean = false,
    override val lpObjective: Double = -Double.MAX_VALUE,
    /**
     * Routes in LP solution with non-zero values and corresponding solution values.
     */
    val lpSolution: List<Pair<Route, Double>> = listOf(),
    override val mipObjective: Double? = null,
    /**
     * Routes selected in MIP solution (i.e. solution value of 1.0).
     */
    val mipSolution: List<Route> = listOf(),
    /**
     * Holds reduced costs for each target after LP is solved.
     */
    val targetReducedCosts: List<Double> = listOf()
) : INode, Comparable<Node> {
    /**
     * String representation.
     */
    override fun toString(): String {
        return (if (!lpSolved)
            listOf(
                "$id",
                "ub=${parentLpObjective.format(2)}",
                "unsolved"
            )
        else listOf(
            "$id",
            "ub=${parentLpObjective.format(2)}",
            "lp=${lpObjective.format(2)}",
            "mip=${mipObjective?.format(2)}",
            if (lpFeasible) "f" else "inf"
        )).joinToString(",", "Node(", ")")
    }

    /**
     * Creates child nodes based on fractional values of LP solution.
     *
     * @param instance problem data
     * @return list of nodes that prohibit the current node's LP solution
     */
    fun branch(instance: Instance, idGenerator: Iterator<Long>): List<Node> {
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
            ) continue

            if (bestTarget == null || targetReducedCosts[i] <= leastReducedCost!! - Constants.EPS) {
                bestTarget = i
                leastReducedCost = targetReducedCosts[i]
            }
        }

        // If a target is found, branch on it.
        if (bestTarget != null)
            return branchOnTarget(bestTarget, instance.getVertices(bestTarget), idGenerator)

        // Otherwise, find a target edge to branch. Among fractional flow edges, we select the one
        // with a starting vertex that has least reduced cost.
        var bestEdge: Pair<Int, Int>? = null
        for ((fromTarget, flowMap) in targetEdgeFlows) {
            for ((toTarget, flow) in flowMap) {
                if (isInteger(flow))
                    continue

                if (bestEdge == null || targetReducedCosts[fromTarget] <= leastReducedCost!! - Constants.EPS) {
                    bestEdge = Pair(fromTarget, toTarget)
                    leastReducedCost = targetReducedCosts[fromTarget]
                }
            }
        }

        val bestFromTarget = bestEdge!!.first
        val bestToTarget = bestEdge.second
        return branchOnTargetEdge(bestFromTarget, bestToTarget, instance, idGenerator)
    }

    /**
     * Checks if [num] is close to an integer value.
     */
    private fun isInteger(num: Double): Boolean = (num - round(num)).absoluteValue <= Constants.EPS

    /**
     * Creates child nodes by branching on visits to [target].
     *
     * @return list of child nodes
     */
    private fun branchOnTarget(
        target: Int,
        targetVertices: List<Int>,
        idGenerator: Iterator<Long>
    ): List<Node> {
        log.debug { "branching $this on target $target" }
        val noVisitNode = getChildWithoutTarget(idGenerator.next(), targetVertices)
        log.debug { "child without $target: $noVisitNode" }

        val mustVisitNode = getChildWithTarget(idGenerator.next(), target)
        log.debug { "child with $target: $mustVisitNode" }

        return listOf(noVisitNode, mustVisitNode)
    }

    /**
     * Creates child nodes by branching on the direct connection between [fromTarget] and [toTarget].
     *
     * @return list of child nodes.
     */
    private fun branchOnTargetEdge(
        fromTarget: Int,
        toTarget: Int,
        instance: Instance,
        idGenerator: Iterator<Long>
    ): List<Node> {
        val childNodes = mutableListOf<Node>()
        if (fromTarget in mustVisitTargets || toTarget in mustVisitTargets) {
            log.debug { "branching $this with target visit already enforced" }

            childNodes.add(
                getChildWithoutTargetEdge(
                    idGenerator.next(),
                    fromTarget,
                    toTarget,
                    instance
                )
            )
            log.debug { "child without $fromTarget -> $toTarget: ${childNodes.last()}" }

            childNodes.add(getChildWithTargetEdge(idGenerator.next(), fromTarget, toTarget))
            log.debug { "child with $fromTarget -> $toTarget: ${childNodes.last()}" }
        } else {
            log.debug { "branching $this without target visit already enforced" }
            childNodes.add(
                getChildWithoutTarget(
                    idGenerator.next(),
                    instance.getVertices(fromTarget)
                )
            )
            log.debug("child without $fromTarget: ${childNodes.last()}")

            childNodes.add(
                getChildWithTarget(-1L, fromTarget).getChildWithoutTargetEdge(
                    idGenerator.next(),
                    fromTarget,
                    toTarget,
                    instance
                ).copy(parentLpObjective = lpObjective)
            )
            log.debug { "child with $fromTarget, without $fromTarget -> $toTarget: ${childNodes.last()}" }

            childNodes.add(
                getChildWithTarget(-1L, fromTarget).getChildWithTargetEdge(
                    idGenerator.next(),
                    fromTarget,
                    toTarget
                ).copy(parentLpObjective = lpObjective)
            )
            log.debug { "child with $fromTarget, with $fromTarget -> $toTarget: ${childNodes.last()}" }
        }

        return childNodes
    }

    /**
     * Creates a node by removing all vertices in [targetVertices] from the current node's graph.
     *
     * @return node with modified graph
     */
    private fun getChildWithoutTarget(nodeId: Long, targetVertices: List<Int>): Node {
        val reducedGraph = graph.getCopy()
        for (vertex in targetVertices)
            reducedGraph.removeVertex(vertex)
        return Node(
            id = nodeId,
            graph = reducedGraph,
            mustVisitTargets = mustVisitTargets,
            mustVisitTargetEdges = mustVisitTargetEdges,
            parentLpObjective = lpObjective
        )
    }

    /**
     * Creates a node by adding [target] to the list of must-visit targets.
     *
     * @return node with updated must-visit target list.
     */
    private fun getChildWithTarget(nodeId: Long, target: Int): Node = Node(
        id = nodeId,
        graph = graph,
        mustVisitTargets = mustVisitTargets + target,
        mustVisitTargetEdges = mustVisitTargetEdges,
        parentLpObjective = lpObjective
    )

    /**
     * Creates a node by removing all edges between vertices of [fromTarget] and [toTarget].
     * Vertices are found using the given [instance] data.
     *
     * @return node with removed edges.
     */
    private fun getChildWithoutTargetEdge(
        nodeId: Long,
        fromTarget: Int,
        toTarget: Int,
        instance: Instance
    ): Node {
        val reducedGraph = graph.getCopy()
        for (vertex in instance.getVertices(fromTarget)) {
            if (!graph.containsVertex(vertex))
                continue

            val edgesToRemove = mutableListOf<DefaultWeightedEdge>()
            for (nextVertex in Graphs.successorListOf(graph, vertex))
                if (instance.whichTarget(nextVertex) == toTarget)
                    edgesToRemove.add(graph.getEdge(vertex, nextVertex))

            edgesToRemove.forEach { graph.removeEdge(it) }
        }
        return Node(
            id = nodeId,
            graph = reducedGraph,
            mustVisitTargets = mustVisitTargets,
            mustVisitTargetEdges = mustVisitTargetEdges,
            parentLpObjective = lpObjective
        )
    }

    /**
     * Creates a node by adding the pair ([fromTarget], [toTarget]) to the list of must-visit
     * target edges of the current node.
     *
     * @return node with enforced target edges.
     */
    private fun getChildWithTargetEdge(nodeId: Long, fromTarget: Int, toTarget: Int): Node = Node(
        id = nodeId,
        graph = graph,
        mustVisitTargets = mustVisitTargets,
        mustVisitTargetEdges = mustVisitTargetEdges + Pair(fromTarget, toTarget),
        parentLpObjective = lpObjective
    )

    /**
     * Comparator to store node with the highest LP objective at the top of a priority queue.
     */
    override fun compareTo(other: Node): Int {
        return when {
            lpObjective >= other.lpObjective + Constants.EPS -> -1
            lpObjective <= other.lpObjective - Constants.EPS -> 1
            id < other.id -> -1
            id > other.id -> 1
            else -> 0
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return id == (other as Node).id
    }

    override fun hashCode(): Int = id.hashCode()
}
