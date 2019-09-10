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

/**
 * Class to store solution and constraint data at nodes of a branch-and-bound tree.
 *
 * @param graph graph with usable vertices and edges to build routes.
 * @param mustVisitTargets targets that must be visited by at least 1 route in any feasible solution.
 * @param mustVisitTargetEdges target connections necessary to be present in at least 1 route in any feasible solution.
 * @param upperBound upper bound of parent node that will be used to initialize [lpSolution].
 */
class Node private constructor(
    val graph: SetGraph,
    private val mustVisitTargets: IntArray,
    private val mustVisitTargetEdges: List<Pair<Int, Int>>,
    upperBound: Double
) : Comparable<Node> {
    /**
     * Unique index of node.
     */
    val index = getNodeIndex()
    /**
     * True if LP solution is feasible, i.e. visits all targets in [mustVisitTargets] and uses
     * all direct target connections specified in [mustVisitTargetEdges].
     */
    var lpFeasible = true
        private set
    /**
     * True if LP optimality is proved by lack of negative reduced cost columns.
     */
    var lpOptimal = false
        private set
    /**
     * True if all decision variables have binary values in LP solution, false otherwise.
     */
    var lpIntegral = false
        private set
    /**
     * LP objective value.
     */
    var lpObjective = upperBound
        private set
    /**
     * Routes in LP solution with non-zero values and corresponding solution values.
     */
    private var lpSolution = listOf<Pair<Route, Double>>()
    /**
     * MIP objective value.
     */
    var mipObjective = -Double.MAX_VALUE
        private set
    /**
     * Routes selected in MIP solution (i.e. solution value of 1.0).
     */
    var mipSolution = listOf<Route>()
        private set
    /**
     * Holds reduced costs for each target after LP is solved.
     */
    private lateinit var targetReducedCosts: List<Double>

    /**
     * String representation.
     */
    override fun toString(): String {
        return "Node($index, bound=$lpObjective, feasible=$lpFeasible)"
    }

    /**
     * Solves LP to optimality and MIP using the LP columns to get a feasible solution.
     *
     * @param instance provides problem information
     * @param cplex IloCplex object used to solve the models.
     */
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
        lpFeasible = !cgSolver.lpInfeasible
        if (!lpFeasible) {
            return
        }
        lpOptimal = cgSolver.lpOptimal
        if (lpObjective <= cgSolver.lpObjective - Parameters.eps) {
            logger.error("best LP objective: $lpObjective")
            logger.error("node LP objective: ${cgSolver.lpObjective}")
            throw OrienteeringException("parent node LP objective smaller than child's")
        }
        if (cgSolver.lpOptimal) {
            lpObjective = cgSolver.lpObjective
            lpSolution = cgSolver.lpSolution
            lpIntegral = lpSolution.all {
                it.second >= 1.0 - Parameters.eps
            }
        }
        mipObjective = cgSolver.mipObjective
        mipSolution = cgSolver.mipSolution
        targetReducedCosts = cgSolver.targetReducedCosts
    }

    /**
     * Creates child nodes based on fractional values of LP solution.
     *
     * @param instance problem data
     * @return list of nodes that prohibit the current node's LP solution
     */
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

    /**
     * Checks if [num] is close to an integer value.
     */
    private fun isInteger(num: Double): Boolean {
        return (num - round(num)).absoluteValue <= Parameters.eps
    }

    /**
     * Creates child nodes by branching on visits to [target].
     *
     * @return list of child nodes
     */
    private fun branchOnTarget(target: Int, targetVertices: List<Int>): List<Node> {
        logger.debug("branching $this on target $target")
        val noVisitNode = getChildWithoutTarget(targetVertices)
        logger.debug("child without $target: $noVisitNode")

        val mustVisitNode = getChildWithTarget(target)
        logger.debug("child with $target: $mustVisitNode")

        return listOf(noVisitNode, mustVisitNode)
    }

    /**
     * Creates child nodes by branching on the direct connection between [fromTarget] and [toTarget].
     *
     * @return list of child nodes.
     */
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

    /**
     * Creates a node by removing all vertices in [targetVertices] from the current node's graph.
     *
     * @return node with modified graph
     */
    private fun getChildWithoutTarget(targetVertices: List<Int>): Node {
        val reducedGraph = graph.getCopy()
        for (vertex in targetVertices) {
            reducedGraph.removeVertex(vertex)
        }
        return Node(reducedGraph, mustVisitTargets, mustVisitTargetEdges, lpObjective)
    }

    /**
     * Creates a node by adding [target] to the list of must-visit targets.
     *
     * @return node with updated must-visit target list.
     */
    private fun getChildWithTarget(target: Int): Node {
        return Node(graph, mustVisitTargets + target, mustVisitTargetEdges, lpObjective)
    }

    /**
     * Creates a node by removing all edges between vertices of [fromTarget] and [toTarget].
     * Vertices are found using the given [instance] data.
     *
     * @return node with removed edges.
     */
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

    /**
     * Creates a node by adding the pair ([fromTarget], [toTarget]) to the list of must-visit
     * target edges of the current node.
     *
     * @return node with enforced target edges.
     */
    private fun getChildWithTargetEdge(fromTarget: Int, toTarget: Int): Node {
        val newMustVisitTargetEdges = mustVisitTargetEdges.toMutableList()
        newMustVisitTargetEdges.add(Pair(fromTarget, toTarget))
        return Node(graph, mustVisitTargets, newMustVisitTargetEdges, lpObjective)
    }

    /**
     * Comparator to to store node with the highest LP objective at the top of a priority queue.
     */
    override fun compareTo(other: Node): Int {
        return when {
            lpObjective >= other.lpObjective + Parameters.eps -> -1
            lpObjective <= other.lpObjective - Parameters.eps -> 1
            index < other.index -> -1
            index > other.index -> 1
            else -> 0
        }
    }

    /**
     * Companion object for logging, a factory constructor and node index management.
     */
    companion object : KLogging() {
        /**
         * Factory constructor to build root node without any restrictions.
         */
        fun buildRootNode(graph: SetGraph): Node =
            Node(graph, intArrayOf(), listOf(), Double.MAX_VALUE)

        /**
         * Thread-safe variable to provide unique index to each newly created node.
         */
        private var nodeCount = AtomicInteger(0)

        /**
         * Thread-safe function to get the existing [nodeCount] value and increment it afterward.
         */
        fun getNodeIndex(): Int {
            return nodeCount.getAndIncrement()
        }
    }
}
