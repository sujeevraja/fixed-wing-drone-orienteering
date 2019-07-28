package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import orienteering.Constants
import orienteering.data.Instance
import orienteering.data.Route
import orienteering.getCopy

class Node private constructor(
    private val graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>,
    private val mustVisitTargets: List<Boolean>,
    private val mustVisitEdges: List<Pair<Int, Int>>
) : Comparable<Node> {
    val index = getNodeIndex()

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
        val enforcedTargets =
            (0 until mustVisitTargets.size).filter { mustVisitTargets[it] }.toList()
        logger.debug("must visit targets: $enforcedTargets")
        logger.debug("must visit edges: $mustVisitEdges")
    }

    fun solve(instance: Instance, numReducedCostColumns: Int, cplex: IloCplex) {
        logger.debug("solving node number $index")
        val cgSolver = ColumnGenSolver(
            instance,
            numReducedCostColumns,
            cplex,
            graph,
            mustVisitTargets,
            mustVisitEdges
        )
        cgSolver.solve()

        lpObjective = cgSolver.lpObjective
        lpSolution = cgSolver.lpSolution
        mipObjective = cgSolver.mipObjective
        mipSolution = cgSolver.mipSolution
        targetReducedCosts = cgSolver.targetReducedCosts
    }

    fun branchOnTarget(target: Int, targetVertices: List<Int>): List<Node> {
        // create a node by deleting all vertices in the target.
        val reducedGraph = graph.getCopy()
        for (vertex in targetVertices) {
            reducedGraph.removeVertex(vertex)
        }
        val noVisitNode = Node(reducedGraph, mustVisitTargets, mustVisitEdges)
        logger.debug("$this target-child without target $target: $noVisitNode")

        // create a node by enforcing a visit to the target.
        val newMustVisitTargets = mustVisitTargets.toMutableList()
        newMustVisitTargets[target] = true
        val mustVisitNode = Node(graph, newMustVisitTargets, mustVisitEdges)
        logger.debug("$this target-child with target $target: $mustVisitNode")

        return listOf(noVisitNode, mustVisitNode)
    }

    fun branchOnEdge(fromVertex: Int, toVertex: Int, instance: Instance): List<Node> {
        val fromTarget = instance.whichTarget(fromVertex)
        val toTarget = instance.whichTarget(toVertex)

        if (mustVisitTargets[fromTarget] || mustVisitTargets[toTarget]) {
            // Create a node by deleting the edge.
            val reducedGraph = graph.getCopy()
            reducedGraph.removeEdge(reducedGraph.getEdge(fromVertex, toVertex))
            val noVisitNode = Node(reducedGraph, mustVisitTargets, mustVisitEdges)
            logger.debug("$this edge-child without edge ($fromVertex -> $toVertex): $noVisitNode")

            // Create a node by enforcing a visit to "toVertex".
            val newMustVisitEdges = mustVisitEdges.toMutableList()
            newMustVisitEdges.add(Pair(fromVertex, toVertex))
            val mustVisitNode = Node(graph, mustVisitTargets, newMustVisitEdges)
            logger.debug("$this edge-child with edge ($fromVertex -> $toVertex): $noVisitNode")

            return listOf(noVisitNode, mustVisitNode)
        } else {
            val childNodes = mutableListOf<Node>()
            // Create a node prohibiting visits to target of "fromVertex".
            val reducedGraph = graph.getCopy()
            Graphs.addGraph(reducedGraph, graph)
            for (vertex in instance.getVertices(fromTarget)) {
                reducedGraph.removeVertex(vertex)
            }
            childNodes.add(Node(reducedGraph, mustVisitTargets, mustVisitEdges))
            logger.debug("$this edge-child without target $fromVertex: ${childNodes.last()}")

            // Create a node by enforcing a visit to fromTarget and prohibiting visit to the edge.
            val newMustVisitTargets = mustVisitTargets.toMutableList()
            newMustVisitTargets[fromTarget] = true
            val edgeReducedGraph = graph.getCopy()
            edgeReducedGraph.removeEdge(edgeReducedGraph.getEdge(fromVertex, toVertex))
            childNodes.add(Node(edgeReducedGraph, newMustVisitTargets, mustVisitEdges))
            logger.debug("$this edge-child with target $fromVertex, without edge ($fromVertex -> $toVertex): ${childNodes.last()}")

            // Create a node by enforcing a visit to fromTarget and the edge.
            val newMustVisitEdges = mustVisitEdges.toMutableList()
            newMustVisitEdges.add(Pair(fromVertex, toVertex))
            childNodes.add(Node(graph, newMustVisitTargets, newMustVisitEdges))
            logger.debug("$this edge-child with target $fromVertex, with edge ($fromVertex -> $toVertex): ${childNodes.last()}")

            return childNodes
        }
    }

    override fun compareTo(other: Node): Int {
        return when {
            lpObjective >= other.lpObjective + Constants.EPS -> -1
            lpObjective <= other.lpObjective - Constants.EPS -> 1
            else -> 0
        }
    }

    companion object : KLogging() {
        fun buildRootNode(
            graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>,
            numTargets: Int
        ): Node {
            return Node(graph, List(numTargets) { false }, listOf())
        }

        private var nodeCount = 0

        fun getNodeIndex(): Int {
            nodeCount++
            return nodeCount -1
        }
    }
}
