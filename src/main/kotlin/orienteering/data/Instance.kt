package orienteering.data

import orienteering.util.SetGraph
import orienteering.util.numVertices

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
    val graph: SetGraph,
    val budget: Double,
    val sourceTarget: Int,
    val destinationTarget: Int,
    val numVehicles: Int,
    val numTargets: Int,
    private val vertexScores: List<Double>,
    private val targetOfVertex: List<Int>,
    private val verticesInTarget: List<List<Int>>
) {
    val numVertices = graph.numVertices()
    val targetScores = (0 until numTargets).map {
        val vertices = verticesInTarget[it]
        if (vertices.isEmpty()) 0.0 else vertexScores[vertices[0]]
    }

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

    /**
     * Function to get vertex score
     * @param i vertex index
     * @return vertex score
     */
    fun getScore(i: Int): Double = vertexScores[i]
}
