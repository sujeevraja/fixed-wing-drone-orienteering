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
    private val visitedBits: LongArray
) : Comparable<State> {
    private val bangForBuck = if (pathLength >= Constants.EPS) reducedCost / pathLength else 0.0
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
            bangForBuck <= other.bangForBuck - Constants.EPS -> -1
            bangForBuck >= other.bangForBuck + Constants.EPS -> 1
            else -> 0
        }

        /*
        return when {
            reducedCost <= other.reducedCost - Constants.EPS -> -1
            reducedCost >= other.reducedCost + Constants.EPS -> 1
            else -> 0
        }
         */
    }

    fun extend(
        newVertex: Int,
        newTarget: Int,
        isCritical: Boolean,
        edgeLength: Double,
        vertexScore: Double,
        vertexReducedCost: Double
    ): State {
        val newVisitedBits = visitedBits.copyOf()
        if (isCritical) {
            markVisited(newVisitedBits, newTarget)
        }

        return State(
            isForward,
            this,
            newVertex,
            pathLength + edgeLength,
            score + vertexScore,
            reducedCost + vertexReducedCost,
            numTargetsVisited + 1,
            newVisitedBits
        )
    }

    /**
     * Assumes that this and other have the same incident vertex.
     */
    fun dominates(other: State, useVisitCondition: Boolean): Boolean {
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

        if (useVisitCondition) {
            for (i in 0 until visitedBits.size) {
                // Following condition is satisfied when "this" visits a critical target and
                // "other" does not. So, "this" does not dominate the "other".
                if (visitedBits[i] and other.visitedBits[i].inv() != 0L) {
                    return false
                }

                // Following condition is satisfied when "this" does not visit a critical target
                // and "other" does. So, "this" strictly dominates "other".
                if (!strict && (visitedBits[i].inv() and other.visitedBits[i] != 0L)) {
                    strict = true
                }
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
     * Returns true if [target] is on partial path, false otherwise.
     */
    fun visits(target: Int): Boolean {
        val quotient: Int = target / Constants.NUM_BITS
        val remainder: Int = target % Constants.NUM_BITS
        return visitedBits[quotient] and (1L shl remainder) != 0L
    }

    fun hasCommonVisits(other: State): Boolean {
        for (i in 0 until visitedBits.size) {
            if (visitedBits[i] and other.visitedBits[i] != 0L) {
                return true
            }
        }
        return false
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
            val size: Int = (numTargets / Constants.NUM_BITS) + 1
            return State(isForward, null, vertex, 0.0, 0.0, 0.0,
                0, LongArray(size) { 0L })
        }

        private fun markVisited(visitedBits: LongArray, target: Int) {
            val quotient: Int = target / Constants.NUM_BITS
            val remainder: Int = target % Constants.NUM_BITS
            visitedBits[quotient] = visitedBits[quotient] or (1L shl remainder)
        }
    }
}