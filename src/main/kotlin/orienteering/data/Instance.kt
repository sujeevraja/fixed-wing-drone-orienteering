package orienteering.data

import mu.KLogging

/**
 * Class that holds orienteering instance data.
 *
 * @param budget budget of any path through targets.
 * @param source source target index in case of set orienteering.
 * @param destination source target index in case of set orienteering.
 * @param numVehicles number of vehicles
 * @param numVertices vertices that can be visited.
 * @param numTargets number of targets (clusters), if null, then the instance does not have cluster specifications
 * @param score scores indexed by vertex ids.
 * @param edges directed edges on the vertices stored as a adjacency list
 * @param targetOfVertex value at index i is target of vertex i
 * @param verticesInTarget list of vertex ids in each target, indexed by target ids
 */

class Instance(val budget: Double,
               val source: Int,
               val destination: Int,
               val numVehicles: Int,
               val numVertices: Int,
               val numTargets: Int?,
               private val score: List<Double>,
               private val edges: Map<Int, Map<Int, Double>>,
               private val targetOfVertex: List<Int>,
               private val verticesInTarget: List<List<Int>>) {

    /**
     * Logger object.
     */
    companion object: KLogging()

    /**
     * Function to query the score of a given target
     * @param i vertex index
     * @return score of vertex if index exists, else returns null
     */
    fun getScore(i: Int): Double? { return score.getOrNull(i) }

    /**
     * Return score of the given target.
     *
     * We assume that if a target has any vertices, all vertex scores are
     * equal. So, the target's score will simply be the score of its first
     * vertex.
     *
     * @param i target index
     * @return score of target if it exists, null otherwise.
     */
    fun getTargetScore(i: Int): Double {
        val firstVertex: Int? = getVertices(i)?.getOrNull(0)
        return if (firstVertex != null) score.getOrNull(firstVertex) ?: 0.0 else 0.0
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
    fun getAdjacentVertices(i: Int): Map<Int, Double>? { return edges[i] }

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
    fun getEdgeLength(i: Int, j: Int): Double? { return edges[i]?.get(j) }

    /**
     * Function to query target that a vertex belongs to
     * @param i vertex index
     * @return target index
     */
    fun whichTarget(i: Int): Int? { return targetOfVertex.getOrNull(i) }

    /**
     * Function to get the list of vertices in a target
     * @param i target index
     * @return List of vertex indexes
     */
    fun getVertices(i: Int): List<Int>? { return verticesInTarget.getOrNull(i) }

    /**
     * Return only vertex in pseudo source target
     *
     * Consider replacing pseudo targets with vertices as we only deal with vertex edges and not
     * target edges.
     */
    fun getSourceVertex(): Int { return verticesInTarget[source][0] }

    /**
     * Return only vertex in pseudo sink target
     *
     * Consider replacing pseudo targets with vertices as we only deal with vertex edges and not
     * target edges.
     */
    fun getDestinationVertex(): Int { return verticesInTarget[destination][0] }
}
