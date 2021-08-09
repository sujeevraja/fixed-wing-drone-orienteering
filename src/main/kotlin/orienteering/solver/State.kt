package orienteering.solver

import orienteering.Constants

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
     * Target of incident vertex.
     */
    val target: Int,
    /**
     * Target of predecessor vertex, will be -1 for source vertices.
     */
    val predecessorTarget: Int,
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

    private val visitedGeneralBits : LongArray,
    /**
     * Used in the comparator to order states, can be equal to [reducedCost] or bang for buck
     * (i.e. reduced cost per unit path length).
     */
    private val selectionMetric: Double,

    val hasCycle : Boolean
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
     * A value that tracks the target of the predecessor vertex of some state that dominates the current state. Say
     * the current state is s. Its only use is in 2-cycle elimination. This value can have the following meanings:
     *
     * - If null, then s is not dominated by any other state.
     * - If some non-null x, there exists a state s' incident at [vertex] that dominates s. Also, the predecessor
     *   vertex of s' (i.e. the vertex before [vertex] on the partial path represented by s') has target x.
     *
     * If the current state is s and is being compared with some other state s' that dominates s, this value can be
     * used as follows:
     * - If null, set this value as the predecessor target of s'.
     * - If not null and the predecessor target of s' is same as this value, s cannot be discarded even though it is
     *   dominated as it may contain useful paths.
     * - If not null and the predecessor target of s' is different from this value, all feasible extensions of s are
     *   guaranteed to be generated by extending s' and some other state (i.e. the state whose predecessor target was
     *   used to set this value). So, the current state can be discarded.
     *
     * Specifics of the 2-cycle elimination implementation are available in the body and documentation of the function
     * [canRemoveDominated] in the [PricingProblemSolver] file.
     */
    var dominatingPredecessor: Int? = null

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
        selectionMetric <= other.selectionMetric - Constants.EPS -> -1
        selectionMetric >= other.selectionMetric + Constants.EPS -> 1
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

        val newVisitedGeneralBits = visitedGeneralBits.copyOf()

        if (isCritical)
            markVisited(newVisitedCriticalBits, newTarget)

        // Reduced cost associated with going to the new target
        val newReducedCost = reducedCost + reducedCostChange

        // Updating metric used for selection of unprocessed state
        val metric =
            if (useBangForBuck)
                if (pathLength >= Constants.EPS) reducedCost / pathLength else 0.0
            else newReducedCost

        if (!hasCycle) {

            // Current state does not already have a cycle present in its partial path

            // Checking if the extension to newTarget results in a cycle
            if (usedGeneralTarget(newTarget)) {

                // Extension to newTarget results in a cycle. Don't need to update visited vertices, so directly
                // return the new state with hasCycle set to true

                return State(
                    isForward = isForward,
                    parent = this,
                    vertex = newVertex,
                    target = newTarget,
                    predecessorTarget = target,
                    pathLength = pathLength + edgeLength,
                    score = score + vertexScore,
                    reducedCost = newReducedCost,
                    numTargetsVisited = numTargetsVisited + 1,
                    visitedCriticalBits = newVisitedCriticalBits,
                    visitedGeneralBits = newVisitedGeneralBits,
                    selectionMetric = metric,
                    hasCycle = true
                )

            }
            else {

                // Extension to newTarget does not result in a cycle, i.e., newTarget has not yet been visited

                // Marking newTarget as visited and return a new state with hasCycle set to false
                markVisited(newTarget, newVisitedGeneralBits)

                return State(
                    isForward = isForward,
                    parent = this,
                    vertex = newVertex,
                    target = newTarget,
                    predecessorTarget = target,
                    pathLength = pathLength + edgeLength,
                    score = score + vertexScore,
                    reducedCost = newReducedCost,
                    numTargetsVisited = numTargetsVisited + 1,
                    visitedCriticalBits = newVisitedCriticalBits,
                    visitedGeneralBits = newVisitedGeneralBits,
                    selectionMetric = metric,
                    hasCycle = false
                )
            }
        }
        else {

            // State already has a cycle previously detected, so do not need to check for new cycles.
            // Marking the newTarget as visited (even if it was already marked before, because there's no need
            // to check before marking)
            markVisited(newTarget, newVisitedGeneralBits)

            return State(
                isForward = isForward,
                parent = this,
                vertex = newVertex,
                target = newTarget,
                predecessorTarget = target,
                pathLength = pathLength + edgeLength,
                score = score + vertexScore,
                reducedCost = newReducedCost,
                numTargetsVisited = numTargetsVisited + 1,
                visitedCriticalBits = newVisitedCriticalBits,
                visitedGeneralBits = newVisitedGeneralBits,
                selectionMetric = metric,
                hasCycle = true
            )
        }


        /*
        return State(
            isForward = isForward,
            parent = this,
            vertex = newVertex,
            target = newTarget,
            predecessorTarget = target,
            pathLength = pathLength + edgeLength,
            score = score + vertexScore,
            reducedCost = newReducedCost,
            numTargetsVisited = numTargetsVisited + 1,
            visitedCriticalBits = newVisitedCriticalBits,
            visitedGeneralBits = newVisitedGeneralBits,
            selectionMetric = metric
        )

         */
    }

    /**
     * Returns true if the current state dominates the [other] state and false otherwise.
     *
     * We assume that this and other have the same incident vertex.
     *
     * @param useVisitCondition if true, a stricter dominance check is enforced.
     */
    fun dominates(
        other: State,
        useVisitCondition: Boolean,
        useNumTargetsForDominance: Boolean
    ): Boolean {
        if (reducedCost >= other.reducedCost + Constants.EPS)
            return false

        var strict = false
        if (reducedCost <= other.reducedCost - Constants.EPS)
            strict = true

        if (pathLength >= other.pathLength + Constants.EPS)
            return false

        if (!strict && pathLength <= other.pathLength - Constants.EPS)
            strict = true

        // Checking visited critical vertices
        if (useVisitCondition) {
            if (useNumTargetsForDominance) {
                if (numTargetsVisited > other.numTargetsVisited)
                    return false
                if (!strict && numTargetsVisited < other.numTargetsVisited)
                    strict = true
            }
            for (i in visitedCriticalBits.indices) {
                // Following condition is satisfied when "this" visits a critical target and
                // "other" does not. So, "this" does not dominate the "other".
                if (visitedCriticalBits[i] and other.visitedCriticalBits[i].inv() != 0L)
                    return false

                // Following condition is satisfied when "this" does not visit a critical target
                // and "other" does. So, "this" strictly dominates "other".
                if (!strict && (visitedCriticalBits[i].inv() and other.visitedCriticalBits[i] != 0L))
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
     * Returns true if critical [target] has been used, false otherwise. A critical vertex may have either been used
     * by existing in the partial path associated with the state or because it is no longer reachable from the current
     * state.
     */
    fun usedCriticalTarget(target: Int): Boolean {
        val quotient: Int = target / Constants.NUM_BITS
        val remainder: Int = target % Constants.NUM_BITS

        return visitedCriticalBits[quotient] and (1L shl remainder) != 0L
    }

    private fun usedGeneralTarget(target : Int) : Boolean {
        val quotient : Int = target / Constants.NUM_BITS
        val remainder : Int = target % Constants.NUM_BITS

        return visitedGeneralBits[quotient] and (1L shl remainder) != 0L
    }

    /**
     * Returns true if any critical target is visited both by this and [other], false otherwise.
     */
    fun hasCommonCriticalVisits(other: State): Boolean {
        for (i in visitedCriticalBits.indices)
            if (visitedCriticalBits[i] and other.visitedCriticalBits[i] != 0L)
                return true
        return false
    }

    fun hasCommonGeneralVisits(otherState : State) : Boolean {
        for (i in visitedGeneralBits.indices) {
            if (visitedGeneralBits[i] and otherState.visitedGeneralBits[i] != 0L)
                return true
        }
        return false
    }

    private fun markVisited(target : Int, visitedVertices : LongArray) {
        val quotient : Int = target / Constants.NUM_BITS
        val remainder : Int = target % Constants.NUM_BITS

        visitedVertices[quotient] or (1L shl remainder)
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
            target: Int,
            numTargets: Int
        ): State {
            val numberOfLongs: Int = (numTargets / Constants.NUM_BITS) + 1
            return State(
                isForward = isForward,
                parent = null,
                vertex = vertex,
                target = target,
                predecessorTarget = -1,
                pathLength = 0.0,
                score = 0.0,
                reducedCost = 0.0,
                numTargetsVisited = 1,
                visitedCriticalBits = LongArray(numberOfLongs) { 0L },
                visitedGeneralBits = LongArray(numberOfLongs) {0L},
                selectionMetric = 0.0,
                hasCycle = false
            )
        }

        /**
         * Helper function to mark visit to [target] in the given [visitedBits] numbers.
         */
        private fun markVisited(visitedBits: LongArray, target: Int) {
            val quotient: Int = target / Constants.NUM_BITS
            val remainder: Int = target % Constants.NUM_BITS
            visitedBits[quotient] = visitedBits[quotient] or (1L shl remainder)
        }
    }
}
