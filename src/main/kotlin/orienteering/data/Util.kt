package orienteering.data

import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import kotlin.math.min

fun preProcess(
    graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>,
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