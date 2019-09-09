package orienteering.solver

import orienteering.data.Parameters

/**
 * Stores forward/backward partial path information for the DSSR labeling procedure.
 */
class State private constructor(
    /**
     * True if forward state, false if backward.
     */
    val isForward: Boolean,
    /**
     * Predecessor state in case of forward paths, successor in case of backward paths.
     */
    val parent: State?,
    /**
     * Incident vertex of state.
     */
    val vertex: Int,
    /**
     * Sum of edge lengths on partial path.
     */
    val pathLength: Double,
    /**
     * Total score of targets visited on partial path.
     */
    val score: Double,
    /**
     * Reduced cost of partial path.
     */
    val reducedCost: Double,
    /**
     * Number of targets visited on partial path.
     */
    val numTargetsVisited: Int,
    /**
     * Numbers whose bits are used to track target visits using bitwise operations.
     */
    private val visitedBits: LongArray
) : Comparable<State> {
    /**
     * Reduced cost per unit length of partial path up to incident vertex of label.
     */
    private val bangForBuck = if (pathLength >= Parameters.eps) reducedCost / pathLength else 0.0
    /**
     * true if all extensions have been generated, false otherwise
     */
    var extended = false
    /**
     * true if dominated by another State, false otherwise
     */
    var dominated = false

    /**
     * Readable string representation of object
     */
    override fun toString(): String {
        val typeStr = if (isForward) "forward" else "backward"
        return "State($typeStr,v=$vertex,l=$pathLength,s=$score,r=$reducedCost)"
    }

    /**
     * Comparator based on reduced cost per unit length used to store states in a priority queue.
     */
    override fun compareTo(other: State): Int {
        return when {
            bangForBuck <= other.bangForBuck - Parameters.eps -> -1
            bangForBuck >= other.bangForBuck + Parameters.eps -> 1
            else -> 0
        }

        /*
        return when {
            reducedCost <= other.reducedCost - Parameters.EPS -> -1
            reducedCost >= other.reducedCost + Parameters.EPS -> 1
            else -> 0
        }
         */
    }

    /**
     * Extends a state further along the partial path and generates a new state.
     *
     * @param newVertex vertex to extend the state to
     * @param newTarget target of [newVertex]
     * @param isCritical if true, visit to [newTarget] will be tracked
     * @param edgeLength length of edge from [vertex] to [newVertex]
     * @param vertexScore score of [newTarget]
     * @param reducedCostChange change in reduced cost due to the extension
     */
    fun extend(
        newVertex: Int,
        newTarget: Int,
        isCritical: Boolean,
        edgeLength: Double,
        vertexScore: Double,
        reducedCostChange: Double
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
            reducedCost + reducedCostChange,
            numTargetsVisited + 1,
            newVisitedBits
        )
    }

    /**
     * Returns true if the current state dominates the [other] state and false otherwise.
     *
     * We assume that this and other have the same incident vertex.
     *
     * @param useVisitCondition if true, a stricter dominance check is enforced.
     */
    fun dominates(other: State, useVisitCondition: Boolean): Boolean {
        var strict = false
        if (reducedCost >= other.reducedCost + Parameters.eps) {
            return false
        }
        if (reducedCost <= other.reducedCost - Parameters.eps) {
            strict = true
        }
        if(pathLength >= other.pathLength + Parameters.eps) {
            return false
        }
        if (!strict && pathLength <= other.pathLength - Parameters.eps) {
            strict = true
        }

        if (useVisitCondition) {
            if (Parameters.useNumTargetsForDominance) {
                if (numTargetsVisited > other.numTargetsVisited) {
                    return false
                }
                if (!strict && numTargetsVisited < other.numTargetsVisited) {
                    strict = true
                }
            }
            for (i in visitedBits.indices) {
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
        val quotient: Int = target / Parameters.numBits
        val remainder: Int = target % Parameters.numBits
        return visitedBits[quotient] and (1L shl remainder) != 0L
    }

    /**
     * Returns true if any critical target is visited both by this and [other], false otherwise.
     */
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
        /**
         * Factory constructor to build forward (backward) states and source (sink) target.
         *
         * @param isForward true if state should be forward and false for backward.
         * @param vertex vertex of the source/sink target.
         * @param numTargets number of available targets (to determine sizes of some containers).
         */
        fun buildTerminalState(
            isForward: Boolean,
            vertex: Int,
            numTargets: Int
        ): State {
            val size: Int = (numTargets / Parameters.numBits) + 1
            return State(isForward, null, vertex, 0.0, 0.0, 0.0,
                0, LongArray(size) { 0L })
        }

        /**
         * Helper function to mark visit to [target] in the given [visitedBits] numbers.
         */
        private fun markVisited(visitedBits: LongArray, target: Int) {
            val quotient: Int = target / Parameters.numBits
            val remainder: Int = target % Parameters.numBits
            visitedBits[quotient] = visitedBits[quotient] or (1L shl remainder)
        }
    }
}