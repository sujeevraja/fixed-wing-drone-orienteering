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
     * Numbers whose bits are used to track critical target visits using bitwise operations.
     */
    private val visitedCriticalBits: LongArray,
    /**
     * Numbers whose bits are used to track unreachable critical targets using bitwise operations
     */
    private val unreachableCriticalBits: LongArray,
    /**
     * Used in the comparator to order states, can be equal to [reducedCost] or bang for buck
     * (i.e. reduced cost per unit path length).
     */
    private val selectionMetric: Double
) : Comparable<State> {
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
    override fun compareTo(other: State): Int = when {
        selectionMetric <= other.selectionMetric - Parameters.eps -> -1
        selectionMetric >= other.selectionMetric + Parameters.eps -> 1
        else -> 0
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
        reducedCostChange: Double,
        useBangForBuck: Boolean
    ): State {

        // If new target is a critical target, mark it as being visited
        val newVisitedCriticalBits = visitedCriticalBits.copyOf()

        if (isCritical)
            markVisited(newVisitedCriticalBits, newTarget)

        // Reduced cost associated with going to the new target
        val newReducedCost = reducedCost + reducedCostChange

        // Updating metric used for selection of unprocessed state
        val metric =
            if (useBangForBuck)
                if (pathLength >= Parameters.eps) reducedCost / pathLength else 0.0
            else newReducedCost

        return State(
            isForward = isForward,
            parent = this,
            vertex = newVertex,
            pathLength = pathLength + edgeLength,
            score = score + vertexScore,
            reducedCost = newReducedCost,
            numTargetsVisited = numTargetsVisited + 1,
            visitedCriticalBits = newVisitedCriticalBits,
            unreachableCriticalBits = unreachableCriticalBits.copyOf(),
            selectionMetric = metric
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

        if (reducedCost >= other.reducedCost + Parameters.eps)
            return false

        if (reducedCost <= other.reducedCost - Parameters.eps)
            strict = true

        if (pathLength >= other.pathLength + Parameters.eps)
            return false

        if (!strict && pathLength <= other.pathLength - Parameters.eps)
            strict = true

        // Checking visited critical vertices

        if (useVisitCondition) {
            if (Parameters.useNumTargetsForDominance) {
                if (numTargetsVisited > other.numTargetsVisited) {
                    return false
                }
                if (!strict && numTargetsVisited < other.numTargetsVisited) {
                    strict = true
                }
            }
            for (i in visitedCriticalBits.indices) {
                // Following condition is satisfied when "this" visits a critical target and
                // "other" does not. So, "this" does not dominate the "other".

                val thisCombined = visitedCriticalBits[i] or unreachableCriticalBits[i]
                val otherCombined = other.visitedCriticalBits[i] or other.unreachableCriticalBits[i]

                if (thisCombined and otherCombined.inv() != 0L) {
                    return false
                }

                // Following condition is satisfied when "this" does not visit a critical target
                // and "other" does. So, "this" strictly dominates "other".
                if (!strict && (thisCombined.inv() and otherCombined != 0L)) {
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
     * Returns true if critical [target] has been used, false otherwise. A critical vertex may have either been used
     * by existing in the partial path associated with the state or because it is no longer reachable from the current
     * state.
     */
    fun usedCriticalTarget(target: Int): Boolean {
        val quotient: Int = target / Parameters.numBits
        val remainder: Int = target % Parameters.numBits

        val combined = visitedCriticalBits[quotient] or unreachableCriticalBits[quotient]

        return combined and (1L shl remainder) != 0L
    }

    /**
     * Returns true if any critical target is visited both by this and [other], false otherwise.
     */
    fun hasCommonCriticalVisits(other: State): Boolean {
        for (i in visitedCriticalBits.indices) {
            if (visitedCriticalBits[i] and other.visitedCriticalBits[i] != 0L) {
                return true
            }
        }
        return false
    }

    /**
     * Function that marks a critical [target] as unreachable in the sense that using a single edge to reach this
     * target will always exceed the budget
     */
    fun markCriticalTargetUnreachable(target: Int) {
        val quotient : Int = target / Parameters.numBits
        val remainder : Int = target % Parameters.numBits

        // Updating unreachable critical targets
        unreachableCriticalBits[quotient] = unreachableCriticalBits[quotient] or (1L shl remainder)
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

            val numberOfLongs : Int = (numTargets / Parameters.numBits) + 1

            val arrayOfLongs = LongArray(numberOfLongs){0L}

            return State(
                isForward = isForward,
                parent = null,
                vertex = vertex,
                pathLength = 0.0,
                score = 0.0,
                reducedCost = 0.0,
                numTargetsVisited = 1,
                visitedCriticalBits = arrayOfLongs,
                unreachableCriticalBits = arrayOfLongs,
                selectionMetric = 0.0)
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