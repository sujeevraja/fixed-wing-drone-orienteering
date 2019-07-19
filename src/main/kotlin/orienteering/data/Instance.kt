package orienteering.data

import mu.KLogging

/**
 * Class that holds orienteering instance data.
 *
 * @param budget budget of any path through targets.
 * @param sourceTarget source target index in case of set orienteering.
 * @param destinationTarget source target index in case of set orienteering.
 * @param numVehicles number of vehicles
 * @param numVertices vertices that can be visited.
 * @param numTargets number of targets (clusters), if null, then the instance does not have cluster specifications
 * @param vertexScores scores indexed by vertex ids.
 * @param edges directed edges on the vertices stored as a adjacency list
 * @param targetOfVertex value at index i is target of vertex i
 * @param verticesInTarget list of vertex ids in each target, indexed by target ids
 */
class Instance(
    val budget: Double,
    val sourceTarget: Int,
    val destinationTarget: Int,
    val numVehicles: Int,
    val numVertices: Int,
    val numTargets: Int,
    private val vertexScores: List<Double>,
    private val edges: Map<Int, Map<Int, Double>>,
    private val targetOfVertex: List<Int>,
    private val verticesInTarget: List<List<Int>>
) {
    /**
     * Targets that can be visited multiple times.
     */
    val multiVisitTargets: List<Int> = getTargetsToSkipCovering()

    /**
     * Scores indexed by target id.
     */
    val targetScores = (0 until numTargets).map {
        val vertices = verticesInTarget[it]
        if (vertices.isEmpty()) 0.0 else vertexScores[vertices[0]]
    }

    /**
     * Function to query if an edge exists
     * @param i from vertex id
     * @param j to vertex id
     */
    fun hasEdge(i: Int, j: Int): Boolean {
        return edges.containsKey(i) && edges.getValue(i).containsKey(j)
    }

    /**
     * Function to get adjacent vertices
     * @param i vertex id
     * @return adjacent vertices as a Map<Int, Double>, where Double is the cost of the edge
     */
    fun getAdjacentVertices(i: Int): Map<Int, Double>? {
        return edges[i]
    }

    /**
     * Function to get incoming edges
     * @param i vertex id
     * @return list of edges
     */
    fun getIncomingEdgeList(i: Int): List<Pair<Int, Int>> {
        val edgeList = mutableListOf<Pair<Int, Int>>()
        for (j in 0 until numVertices)
            if (hasEdge(j, i)) edgeList.add(Pair(j, i))
        return edgeList
    }

    /**
     * Function to get outgoing edges
     * @param i vertex id
     * @return list of edges
     */
    fun getOutgoingEdgeList(i: Int): List<Pair<Int, Int>> {
        val edgeList = mutableListOf<Pair<Int, Int>>()
        for (j in edges.getValue(i).keys)
            edgeList.add(Pair(i, j))
        return edgeList
    }

    /**
     * Function to get internal edges given a set of vertices
     * @param vertexSet set of vertices
     * @return list of edges
     */
    fun getInternalEdgeList(vertexSet: Set<Int>): MutableList<Pair<Int, Int>> {
        val edgeList = mutableListOf<Pair<Int, Int>>()
        for (i in vertexSet) {
            for (j in vertexSet) {
                if (i == j)
                    continue
                if (hasEdge(i, j))
                    edgeList.add(Pair(i, j))
            }
        }
        return edgeList
    }


    /**
     * Function to get the edge cost
     * @param i from vertex id
     * @param j to vertex id
     * @return cost or null
     */
    fun getEdgeLength(i: Int, j: Int): Double? {
        return edges[i]?.get(j)
    }

    /**
     * Function to query target that a vertex belongs to
     * @param i vertex index
     * @return target index
     */
    fun whichTarget(i: Int): Int {
        return targetOfVertex[i]
    }

    /**
     * Function to get the list of vertices in a target
     * @param i target index
     * @return List of vertex indexes
     */
    fun getVertices(i: Int): List<Int>? {
        return verticesInTarget.getOrNull(i)
    }

    /**
     * Return only vertex in pseudo source target
     *
     * Consider replacing pseudo targets with vertices as we only deal with vertex edges and not
     * target edges.
     */
    fun getSourceVertex(): Int {
        return verticesInTarget[sourceTarget][0]
    }

    /**
     * Return only vertex in pseudo sink target
     *
     * Consider replacing pseudo targets with vertices as we only deal with vertex edges and not
     * target edges.
     */
    fun getDestinationVertex(): Int {
        return verticesInTarget[destinationTarget][0]
    }
    /**
     * Function to get the targets on which the covering constraint can be skipped
     *
     * These targets correspond to outgoing targets from source and incoming from destination
     */
    private fun getTargetsToSkipCovering(): List<Int> {
        val sourceAdjacentVertices = getOutgoingEdgeList(getSourceVertex())
        val sourceToSkip = whichTarget(sourceAdjacentVertices.first().second)

        val destinationAdjacentVertices = getIncomingEdgeList(getDestinationVertex())
        val destinationToSkip = whichTarget(destinationAdjacentVertices.first().first)

        return listOf(sourceTarget, destinationTarget, sourceToSkip, destinationToSkip)
    }

    /**
     * Logger object.
     */
    companion object : KLogging()
}
