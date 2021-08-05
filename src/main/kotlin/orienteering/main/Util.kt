package orienteering.main

import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import kotlin.math.min

/**
 * Custom exception to throw problem-specific exception.
 */
class OrienteeringException(message: String) : Exception(message)

typealias SetGraph = SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>

/**
 * Returns number of vertices in graph.
 */
fun SetGraph.numVertices() = this.vertexSet().size

/**
 * Returns weight of edge between [from] vertex and [to] vertex.
 */
fun SetGraph.getEdgeWeight(from: Int, to: Int): Double {
    return this.getEdgeWeight(this.getEdge(from, to))
}

/**
 * Creates a shallow copy of the graph.
 *
 * The returned SetGraph object is new, but its edges are references.
 */
fun SetGraph.getCopy(): SetGraph {
    val graphCopy = SetGraph(DefaultWeightedEdge::class.java)
    Graphs.addGraph(graphCopy, this)
    return graphCopy
}

/**
 * Removes any vertex v from [graph] for which the length of the shortest path starting from a
 * vertex in [sourceVertices] and reaching any vertex in [destinationVertices] via v exceeds
 * [budget].
 */
fun preProcess(
    graph: SetGraph,
    budget: Double,
    sourceVertices: List<Int>,
    destinationVertices: List<Int>
) {
    val sourceToVertexLength: MutableMap<Int, Double> = mutableMapOf()
    val vertexToDestinationLength: MutableMap<Int, Double> = mutableMapOf()

    graph.vertexSet().iterator().forEach {
        when (it) {
            in sourceVertices -> {
                sourceToVertexLength[it] = 0.0
                vertexToDestinationLength[it] = 0.0
            }
            in destinationVertices -> {
                sourceToVertexLength[it] = 0.0
                vertexToDestinationLength[it] = 0.0
            }
            else -> {
                sourceToVertexLength[it] = Double.MAX_VALUE
                vertexToDestinationLength[it] = Double.MAX_VALUE
            }
        }
        for (source in sourceVertices) {
            if (graph.containsEdge(source, it)) {
                val length = graph.getEdgeWeight(graph.getEdge(source, it))
                val givenLength = sourceToVertexLength[it]!!
                sourceToVertexLength[it] = min(givenLength, length)
            }
        }
        for (destination in destinationVertices) {
            if (graph.containsEdge(it, destination)) {
                val length = graph.getEdgeWeight(graph.getEdge(it, destination))
                val givenLength = vertexToDestinationLength[it]!!
                vertexToDestinationLength[it] = min(givenLength, length)
            }
        }
    }

    sourceToVertexLength.keys.forEach {
        if (sourceToVertexLength[it]!!.plus(vertexToDestinationLength[it]!!) > budget)
            graph.removeVertex(it)
    }
}

fun Double.format(digits: Int): String = when {
    this <= -1e20 -> "-inf"
    this >= 1e20 -> "inf"
    else -> "%.${digits}f".format(this)
}
