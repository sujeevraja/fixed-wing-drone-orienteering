package orienteering.solver

import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import orienteering.Constants

class Node private constructor(
    private val graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>,
    private val mustVisitVertices: List<Int>,
    private val mustVisitEdges: List<Pair<Int, Int>>
) : Comparable<Node> {
    private val upperBound = 0.0

    fun branchOnVertex(vertex: Int): List<Node> {
        // create a node by deleting the vertex.
        val reducedGraph = SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        Graphs.addGraph(reducedGraph, graph)
        reducedGraph.removeVertex(vertex)
        val noVisitNode = Node(reducedGraph, mustVisitVertices, mustVisitEdges)

        // create a node by enforcing a visit to the vertex.
        val newMustVisitVertices = mustVisitVertices.toMutableList()
        newMustVisitVertices.add(vertex)
        val mustVisitNode = Node(graph, newMustVisitVertices, mustVisitEdges)

        return listOf(noVisitNode, mustVisitNode)
    }

    fun branchOnEdge(from: Int, to: Int): List<Node> {
        // create a node by deleting the edge.
        val reducedGraph = SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
        Graphs.addGraph(reducedGraph, graph)
        reducedGraph.removeEdge(reducedGraph.getEdge(from, to))
        val noVisitNode = Node(reducedGraph, mustVisitVertices, mustVisitEdges)

        // create a node by enforcing a visit to the vertex.
        val newMustVisitEdges = mustVisitEdges.toMutableList()
        newMustVisitEdges.add(Pair(from, to))
        val mustVisitNode = Node(graph, mustVisitVertices, newMustVisitEdges)

        return listOf(noVisitNode, mustVisitNode)
    }

    override fun compareTo(other: Node): Int {
        return when {
            upperBound >= other.upperBound + Constants.EPS -> -1
            upperBound <= other.upperBound - Constants.EPS -> 1
            else -> 0
        }
    }

    companion object: KLogging() {
        fun buildRootNode(graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>): Node {
            return Node(graph, listOf(), listOf())
        }
    }
}