package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import orienteering.Constants
import orienteering.SetGraph
import orienteering.data.Instance
import orienteering.data.Route
import orienteering.getCopy

class Node private constructor(
    val graph: SetGraph,
    private val mustVisitTargets: IntArray,
    private val mustVisitTargetEdges: List<Pair<Int, Int>>
) : Comparable<Node> {
    private val index = getNodeIndex()

    var feasible = true
        private set

    var lpObjective = -Double.MAX_VALUE
        private set

    var lpSolution = listOf<Pair<Route, Double>>()
        private set

    var mipObjective = -Double.MAX_VALUE
        private set

    var mipSolution = listOf<Route>()
        private set

    lateinit var targetReducedCosts: List<Double>
        private set

    override fun toString(): String {
        return "Node($index)"
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

    fun solve(instance: Instance, numReducedCostColumns: Int, cplex: IloCplex) {
        logger.debug("solving node number $index")
        val cgSolver = ColumnGenSolver(
            instance,
            numReducedCostColumns,
            cplex,
            graph,
            mustVisitTargets,
            mustVisitTargetEdges
        )
        cgSolver.solve()
        feasible = !cgSolver.lpInfeasible
        if (feasible) {
            lpObjective = cgSolver.lpObjective
            lpSolution = cgSolver.lpSolution
            mipObjective = cgSolver.mipObjective
            mipSolution = cgSolver.mipSolution
            targetReducedCosts = cgSolver.targetReducedCosts
        }
    }

    fun branchOnTarget(target: Int, targetVertices: List<Int>): List<Node> {
        logger.debug("branching $this on target $target")
        val noVisitNode = getChildWithoutTarget(targetVertices)
        logger.debug("child without $target: $noVisitNode")

        val mustVisitNode = getChildWithTarget(target)
        logger.debug("child with $target: $mustVisitNode")

        return listOf(noVisitNode, mustVisitNode)
    }

    fun branchOnTargetEdge(fromTarget: Int, toTarget: Int, instance: Instance): List<Node> {
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
        return Node(reducedGraph, mustVisitTargets, mustVisitTargetEdges)
    }

    private fun getChildWithTarget(target: Int): Node {
        return Node(graph, mustVisitTargets + target, mustVisitTargetEdges)
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
        return Node(reducedGraph, mustVisitTargets, mustVisitTargetEdges)
    }

    private fun getChildWithTargetEdge(fromTarget: Int, toTarget: Int): Node {
        val newMustVisitTargetEdges = mustVisitTargetEdges.toMutableList()
        newMustVisitTargetEdges.add(Pair(fromTarget, toTarget))
        return Node(graph, mustVisitTargets, newMustVisitTargetEdges)
    }

    override fun compareTo(other: Node): Int {
        return when {
            lpObjective >= other.lpObjective + Constants.EPS -> -1
            lpObjective <= other.lpObjective - Constants.EPS -> 1
            else -> 0
        }
    }

    companion object : KLogging() {
        fun buildRootNode(graph: SetGraph): Node = Node(graph, intArrayOf(), listOf())

        var nodeCount = 0
            private set

        fun getNodeIndex(): Int {
            nodeCount++
            return nodeCount - 1
        }
    }
}
