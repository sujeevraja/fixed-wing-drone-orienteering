package orienteering

import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph

fun SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>.numVertices() = this.vertexSet().size

fun SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>.getEdgeWeight(from: Int, to: Int): Double {
    return this.getEdgeWeight(this.getEdge(from, to))
}
