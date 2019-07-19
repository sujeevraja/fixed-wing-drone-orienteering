package orienteering.solver

import orienteering.Constants

class State private constructor(
    val isForward: Boolean,
    val parent: State?,
    val vertex: Int,
    val pathLength: Double,
    val score: Double,
    val reducedCost: Double,
    val numTargetsVisited: Int,
    val numTargetVisits: MutableList<Int>
) : Comparable<State> {
    /**
     * true if all extensions have been generated, false otherwise
     */
    var extended = false
    /**
     * true if dominated by another State, false otherwise
     */
    var dominated = false

    override fun toString(): String {
        val typeStr = if (isForward) "forward" else "backward"
        return "State($typeStr,v=$vertex,l=$pathLength,s=$score,r=$reducedCost)"
    }

    override fun compareTo(other: State): Int {
        return when {
            reducedCost <= other.reducedCost - Constants.EPS -> -1
            reducedCost >= other.reducedCost + Constants.EPS -> 1
            else -> 0
        }
    }

    fun extend(
        newVertex: Int,
        newTarget: Int,
        isCritical: Boolean,
        edgeLength: Double,
        vertexScore: Double,
        vertexReducedCost: Double
    ): State {
        val newVisited = numTargetVisits.toMutableList()
        if (isCritical) {
            newVisited[newTarget] += 1
        }

        return State(
            isForward,
            this,
            newVertex,
            pathLength + edgeLength,
            score + vertexScore,
            reducedCost + vertexReducedCost,
            numTargetsVisited + 1,
            newVisited
        )
    }

    /**
     * Assumes that this and other have the same incident vertex.
     */
    fun dominates(other: State): Boolean {
        var strict = false
        if (reducedCost >= other.reducedCost + Constants.EPS) {
            return false
        }
        if (reducedCost <= other.reducedCost - Constants.EPS) {
            strict = true
        }
        if(pathLength >= other.pathLength + Constants.EPS) {
            return false
        }
        if (!strict && pathLength <= other.pathLength - Constants.EPS) {
            strict = true
        }
        for ((thisVisit, otherVisit) in numTargetVisits zip other.numTargetVisits) {
            if (thisVisit > otherVisit) {
                return false
            }
            if (!strict && thisVisit < otherVisit) {
                strict = true
            }
        }
        return strict
    }

    /**
     * Collect and return vertices on partial path of state.
     *
     * The vertices are collected into a list by traversing along the parent chain starting from
     * the current state and adding the vertex of each parent to the vertex list. The collection
     * stops when no parent is found.
     *
     * @return list of partial path vertices
     */
    fun getPartialPathVertices(): List<Int> {
        val path = mutableListOf<Int>()
        var curr: State? = this
        while (curr != null) {
            path.add(curr.vertex)
            curr = curr.parent
        }

        return path
    }

    /**
     * Companion object to provide a factory constructor for terminal states.
     */
    companion object {
        fun buildTerminalState(
            isForward: Boolean,
            vertex: Int,
            numTargets: Int
        ): State {
            return State(isForward, null, vertex, 0.0, 0.0, 0.0,
                0, MutableList(numTargets) { 0 })
        }
    }
}