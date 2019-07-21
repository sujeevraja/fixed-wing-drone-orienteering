package orienteering.data

import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph

/**
 * Class that holds the Dubins Team orienteering instance data
 *
 * @param budget budget of any path through the targets
 * @param sourceTarget source target index
 * @param destinationTarget destination target index
 * @param numVehicles number of vehicles
 * @param numTargets number of targets
 * @param vertexScores scores indexed by vertex ids
 * @param targetOfVertex value at index i is the target of vertex i
 * @param verticesInTarget list of vertex ids in each target, indexed by target ids
 * @param graph directed weighted graph on the vertex set
 */

class Instance(
    val budget: Double,
    val sourceTarget: Int,
    val destinationTarget: Int,
    val numVehicles: Int,
    val numTargets: Int,
    private val vertexScores: List<Double>,
    private val targetOfVertex: List<Int>,
    private val verticesInTarget: List<List<Int>>,
    private val graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>
) {
    val targetScores = (0 until numTargets).map {
        val vertices = verticesInTarget[it]
        if (vertices.isEmpty()) 0.0 else vertexScores[vertices[0]]
    }

    /**
     * Function to query if an edge exists
     * @param i from vertex id
     * @param j to vertex id
     * @return Boolean
     */
    fun hasEdge(i: Int, j: Int): Boolean = graph.containsEdge(i, j)

    /**
     * Function to get the outgoing edge set
     * @param i vertex id
     * @return list of edges
     */
    fun getOutgoingEdgeList(i: Int): List<DefaultWeightedEdge> = graph.outgoingEdgesOf(i).toList()

    /**
     * Function to get incoming edge set
     * @param i vertex id
     * @return list of edges
     */
    fun getIncomingEdgeList(i: Int): List<DefaultWeightedEdge> = graph.incomingEdgesOf(i).toList()

    /**
     * Function to get edge length
     * @param i from vertex id
     * @param j to vertex id
     * @return edge length
     */
    fun getEdgeLength(i: Int, j: Int): Double {
        val edge = graph.getEdge(i, j)
        return graph.getEdgeWeight(edge)
    }

    /**
     * Function to get the number of vertices
     * @return number of vertices
     */
    fun getNumVertices(): Int = graph.vertexSet().size

    /**
     * Function to query target that a vertex belongs to
     * @param i vertex index
     * @return target index
     */
    fun whichTarget(i: Int): Int = targetOfVertex[i]

    /**
     * Function to get the list of vertices in a target
     * @param i target index
     * @return List of vertex indexes
     */
    fun getVertices(i: Int): List<Int> = verticesInTarget[i]

    fun getPredecessors(vertex: Int): List<Int> {
        return Graphs.predecessorListOf(graph, vertex)
    }

    fun getSuccessors(vertex: Int): List<Int> {
        return Graphs.successorListOf(graph, vertex)
    }

    /**
     * Logger object
     */
    companion object : KLogging()
}
