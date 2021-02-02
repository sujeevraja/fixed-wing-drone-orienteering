package orienteering.solver

import mu.KLogging
import org.jgrapht.Graphs
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import orienteering.main.OrienteeringException
import orienteering.main.SetGraph
import orienteering.main.getEdgeWeight
import java.util.*
import kotlin.math.absoluteValue

/**
 * Pricing problem solver to find negative reduced cost paths for a branch and price algorithm.
 * It can be configured to use either the I-DSSR algorithm (implemented in the
 * [interleavedSearch] function), or the DSSR algorithm (implemented in the [simpleSearch]
 * function).
 *
 * I-DSSR reference:
 * Kaarthik Sundar and Sujeevraja Sanjeevi.
 * "A Branch-and-Price Algorithm for a Team Orienteering Problem for Fixed-Wing Drones"
 * Under review, Arxiv: https://arxiv.org/abs/1912.04353.
 *
 * DSSR reference:
 * Righini Giovanni and Matteo Salani.
 * "New dynamic programming algorithms for the resource constrained elementary shortest path problem"
 * Networks: An International Journal 51.3 (2008): 155-170.
 * This class implements Algorithm 3 in the paper.
 *
 * @param instance problem data
 * @param targetReducedCosts reduced costs indexed by vertex id
 */
class PricingProblemSolver(
    private val instance: Instance,
    private val routeDual: Double,
    private val targetReducedCosts: List<Double>,
    private val targetEdgeDuals: List<List<Double>>,
    private val graph: SetGraph
) {
    /**
     * Number of targets (i.e. vertex clusters) in given instance.
     */
    private val numTargets = instance.numTargets
    /**
     * Number of vertices in given instance.
     */
    private val numVertices = graph.vertexSet().maxOrNull()!! + 1
    /**
     * source vertices
     */
    private val srcVertices =
        instance.getVertices(instance.sourceTarget).filter { it in graph.vertexSet() }
    /**
     * destination vertices
     */
    private val dstVertices =
        instance.getVertices(instance.destinationTarget).filter { it in graph.vertexSet() }
    /**
     * maximum length of a vehicle's path
     */
    private val maxPathLength = instance.budget
    /**
     * True at index i if target i is critical (Theta in paper).
     */
    private var isCritical = BooleanArray(numTargets) { false }
    /**
     * True at index i if target i is visited multiple times by the optimal path of a search
     * iteration (Psi in paper).
     */
    private var isVisitedMultipleTimes = BooleanArray(numTargets) { false }
    /**
     * Forward states indexed by vertex id.
     */
    private var forwardStates = List(numVertices) { mutableListOf<State>() }
    /**
     * Backward states indexed by vertex id.
     */
    private var backwardStates = List(numVertices) { mutableListOf<State>() }
    /**
     * Unprocessed forward states in ascending order of bang-for-buck.
     */
    private var unprocessedForwardStates = PriorityQueue<State>()
    /**
     * Unprocessed backward states in descending order of bang-for-buck.
     */
    private var unprocessedBackwardStates = PriorityQueue<State>()
    /**
     * Switch to decide whether a forward or backward unprocessed state needs to be extended.
     */
    private var processForwardState = true
    /**
     * Route with least reduced cost (used to define Psi at the end of a search iteration).
     */
    private var optimalRoute: Route? = null
    /**
     * Elementary paths with negative reduced cost.
     */
    var elementaryRoutes = mutableListOf<Route>()
        private set

    /**
     * If true, state dominance check uses the following additional condition:
     *
     * State s1 dominates s2 iff critical targets visited by s1 is a subset of those visited by s2.
     *
     * For interleaved search, this condition is controlled by the "relaxDominanceRules" parameter.
     * For simple search, this parameter is always true.
     */
    private var useVisitCondition = (!Parameters.relaxDominanceRules ||
            !Parameters.useInterleavedSearch)

    /**
     * Generates negative reduced cost elementaryRoutes.
     */
    fun generateColumns() {
        // Store source states.
        for (srcVertex in srcVertices) {
            forwardStates[srcVertex].add(
                State.buildTerminalState(true, srcVertex, numTargets)
            )
        }

        // Store destination state.
        for (dstVertex in dstVertices) {
            backwardStates[dstVertex].add(
                State.buildTerminalState(false, dstVertex, numTargets)
            )
        }

        var searchIteration = 0
        var stop = false
        do {
            logger.debug("----- START search iteration $searchIteration")
            initializeIteration()

            val stopSearch = if (Parameters.useInterleavedSearch) interleavedSearch()
            else simpleSearch()

            if (stopSearch) {
                logger.debug("----- STOP column search as search() stopped")
                break
            }

            if (Parameters.useInterleavedSearch &&
                elementaryRoutes.size >= Parameters.maxPathsAfterSearch
            ) {
                logger.debug("----- STOP column search due to elementary route existence")
                break
            }

            multipleVisits()
            if (optimalRoute == null && !useVisitCondition) {
                useVisitCondition = true
                logger.debug("----- REPEAT column search with stricter dominance check")
                searchIteration++
                continue
            }

            stop = isVisitedMultipleTimes.none { it }
            logger.debug("----- END search iteration $searchIteration, stop: $stop")
            searchIteration++
        } while (!stop)
    }

    private fun initializeIteration() {
        // Update critical vertices.
        for (i in isCritical.indices) {
            if (!isCritical[i]) {
                isCritical[i] = isVisitedMultipleTimes[i]
            }
        }

        // Clear all states except source and destination.
        for (i in forwardStates.indices) {
            if (i !in srcVertices) {
                forwardStates[i].clear()
            } else {
                for (state in forwardStates[i]) {
                    state.extended = false
                    state.dominated = false
                }
            }
            if (i !in dstVertices) {
                backwardStates[i].clear()
            } else {
                for (state in backwardStates[i]) {
                    state.extended = false
                    state.dominated = false
                }
            }
        }

        // Update optimal route with best cached elementary route if necessary.
        if (optimalRoute != null && hasCycle(optimalRoute!!.vertexPath)) {
            optimalRoute = elementaryRoutes.firstOrNull()
            for (route in elementaryRoutes.drop(1)) {
                if (route.reducedCost <= optimalRoute!!.reducedCost - Parameters.eps) {
                    optimalRoute = route
                }
            }
        }
    }

    /**
     * Implementation of the I-DSSR algorithm presented in the paper.
     */
    private fun interleavedSearch(): Boolean {
        val criticalTargets = (0 until numTargets).filter { isCritical[it] }
        logger.debug("critical targets: $criticalTargets")

        // Extend source states.
        for (srcVertex in srcVertices) {
            for (state in forwardStates[srcVertex]) {
                processState(state) {
                    unprocessedForwardStates.add(it)
                }
            }
        }

        // Extend destination state.
        for (dstVertex in dstVertices) {
            for (state in backwardStates[dstVertex]) {
                processState(state) {
                    unprocessedBackwardStates.add(it)
                }
            }
        }

        while (unprocessedForwardStates.isNotEmpty() || unprocessedBackwardStates.isNotEmpty()) {
            if (TimeChecker.timeLimitReached()) {
                return true
            }

            var state: State? = null
            if (processForwardState) {
                if (unprocessedForwardStates.isNotEmpty()) {
                    state = unprocessedForwardStates.remove()
                }
            } else if (unprocessedBackwardStates.isNotEmpty()) {
                state = unprocessedBackwardStates.remove()
            }
            processForwardState = !processForwardState
            if (state == null || state.dominated) {
                continue
            }

            if (state.extended) {
                throw OrienteeringException("extended state in unprocessed state container")
            }

            val vertex = state.vertex
            if (state.isForward) {
                // Join with all backward states.
                for (j in 0 until numVertices) {
                    if (j == vertex || !graph.containsEdge(vertex, j)) {
                        continue
                    }
                    for (bs in backwardStates[j]) {
                        val shouldExit = save(state, bs)
                        if (shouldExit) {
                            return true
                        }
                    }
                }
                if (TimeChecker.timeLimitReached()) {
                    return true
                }

                if (Graphs.vertexHasSuccessors(graph, vertex)) {
                    processState(state) {
                        unprocessedForwardStates.add(it)
                    }
                }
                if (TimeChecker.timeLimitReached()) {
                    return true
                }
            } else {
                // Join with all forward states.
                for (j in 0 until numVertices) {
                    if (j == vertex || !graph.containsEdge(j, vertex)) {
                        continue
                    }
                    for (fs in forwardStates[j]) {
                        val shouldExit = save(fs, state)
                        if (shouldExit) {
                            return true
                        }
                    }
                }
                if (TimeChecker.timeLimitReached()) {
                    return true
                }

                if (Graphs.vertexHasPredecessors(graph, vertex)) {
                    processState(state) {
                        unprocessedBackwardStates.add(it)
                    }
                }
                if (TimeChecker.timeLimitReached()) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * This is the DSSR algorithm presented as Algorithm 3 in the [Giovanni,Salani] paper.
     * Reference is available in the class documentation.
     */
    private fun simpleSearch(): Boolean {
        val candidateVertices = mutableSetOf<Int>()
        candidateVertices.addAll(srcVertices)
        candidateVertices.addAll(dstVertices)

        while (candidateVertices.isNotEmpty()) {
            if (TimeChecker.timeLimitReached()) {
                return true
            }
            val vertex = candidateVertices.first()

            // Complete all forward extensions.
            for (state in forwardStates[vertex]) {
                if (!state.dominated) {
                    processState(state) {
                        candidateVertices.add(it.vertex)
                    }
                }
            }
            if (TimeChecker.timeLimitReached()) {
                return true
            }

            // Complete all backward extensions.
            for (state in backwardStates[vertex]) {
                if (!state.dominated) {
                    processState(state) {
                        candidateVertices.add(it.vertex)
                    }
                }
            }
            if (TimeChecker.timeLimitReached()) {
                return true
            }

            candidateVertices.remove(vertex)
        }

        // Join forward and backward paths.
        for (i in 0 until numVertices) {
            for (j in 0 until numVertices) {
                if (j == i || !graph.containsEdge(i, j)) {
                    continue
                }
                for (fs in forwardStates[i]) {
                    for (bs in backwardStates[j]) {
                        val shouldExit = save(fs, bs)
                        if (shouldExit) {
                            return true
                        }
                    }
                }
                if (TimeChecker.timeLimitReached()) {
                    return true
                }
            }
        }

        return false
    }

    private fun multipleVisits() {
        isVisitedMultipleTimes.fill(false)
        val optimalPath = optimalRoute?.vertexPath ?: return

        val numVisits = IntArray(numTargets) { 0 }
        for (vertex in optimalPath) {
            val target = instance.whichTarget(vertex)
            numVisits[target]++
            if (numVisits[target] > 1) {
                isVisitedMultipleTimes[target] = true
                if (isCritical[target]) {
                    logger.error("multiple visits to critical target $target")
                    logger.error("problematic route: $optimalRoute")
                    throw OrienteeringException("cycles with critical target")
                }
            }
        }
        val multipleVisits = (0 until numTargets).filter { isVisitedMultipleTimes[it] }
        logger.debug("current multiple visits: $multipleVisits")
    }

    /**
     * Returns true if the given path visits a target more than once, false otherwise.
     *
     * @param path list of vertices.
     */
    private fun hasCycle(path: List<Int>): Boolean {
        val visited = hashSetOf<Int>()
        for (vertex in path) {
            val target = instance.whichTarget(vertex)
            if (visited.contains(target)) {
                return true
            }
            visited.add(target)
        }
        return false
    }

    /**
     * This is the ProcessLabel function presented in Algorithm 2 in the paper.
     */
    private fun processState(state: State, onExtend: (State) -> Unit) {
        if (state.extended) return
        state.extended = true
        if (!canExtend(state)) return
        if (state.isForward) extendForward(state, onExtend)
        else extendBackward(state, onExtend)
    }

    private fun extendForward(state: State, onExtend: (State) -> Unit) {
        for (nextVertex in Graphs.successorListOf(graph, state.vertex)) {
            if (state.visits(instance.whichTarget(nextVertex))) {
                continue
            }
            if (state.parent != null && sameTarget(state.parent.vertex, nextVertex)) {
                continue
            }
            val edgeLength = graph.getEdgeWeight(state.vertex, nextVertex)
            val extension = extendIfFeasible(state, nextVertex, edgeLength) ?: continue
            updateNonDominatedStates(forwardStates[nextVertex], extension, onExtend)
        }
    }

    private fun extendBackward(state: State, onExtend: (State) -> Unit) {
        for (prevVertex in Graphs.predecessorListOf(graph, state.vertex)) {
            if (state.visits(instance.whichTarget(prevVertex))) {
                continue
            }
            if (state.parent != null && sameTarget(state.parent.vertex, prevVertex)) {
                continue
            }
            val edgeLength = graph.getEdgeWeight(prevVertex, state.vertex)
            val extension = extendIfFeasible(state, prevVertex, edgeLength) ?: continue
            updateNonDominatedStates(backwardStates[prevVertex], extension, onExtend)
        }
    }

    private fun sameTarget(v1: Int, v2: Int): Boolean {
        return instance.whichTarget(v1) == instance.whichTarget(v2)
    }

    private fun canExtend(state: State): Boolean {
        if (state.dominated) {
            return false
        }

        // Prevent extension of states that have consumed more than half the path length
        // budget. This reduces the number of extensions to be considered, while ensuring that
        // optimality is unaffected. Refer to section 4.3 in the paper for further details.
        if (state.pathLength >= (maxPathLength / 2.0) - Parameters.eps) {
            return false
        }

        if (Parameters.useNumTargetsForDominance) {
            // Paths joined will always be on an edge (i,j) with a forward label at i and a
            // backward label at j. So, any label has visited more targets than (numTargets - 1)
            // can be discarded.
            if (state.numTargetsVisited > numTargets - 1) {
                return false
            }
        }

        return true
    }

    private fun extendIfFeasible(state: State, neighbor: Int, edgeLength: Double): State? {
        // Prevent budget infeasibility.
        if (state.pathLength + edgeLength >= maxPathLength) {
            return null
        }

        // Here, extension is feasible. So, generate and return it.
        val neighborTarget = instance.whichTarget(neighbor)
        var rcUpdate = targetReducedCosts[neighborTarget]
        rcUpdate += if (state.isForward) {
            targetEdgeDuals[instance.whichTarget(state.vertex)][neighborTarget]
        } else {
            targetEdgeDuals[neighborTarget][instance.whichTarget(state.vertex)]
        }
        return state.extend(
            neighbor,
            neighborTarget,
            isCritical[neighborTarget],
            edgeLength,
            instance.targetScores[neighborTarget],
            rcUpdate
        )
    }

    /**
     * Update optimal route if path obtained by joining given labels has the least reduced cost.
     *
     * @param forwardState forward label with partial path from source
     * @param backwardState backward label with partial path to destination
     * @return true if enough negative reduced cost columns are available, false otherwise
     */
    private fun save(forwardState: State, backwardState: State): Boolean {
        if (!feasible(forwardState, backwardState) || !halfway(forwardState, backwardState)) {
            return false
        }

        val forwardTarget = instance.whichTarget(forwardState.vertex)
        val backwardTarget = instance.whichTarget(backwardState.vertex)
        val reducedCost = (routeDual + forwardState.reducedCost + backwardState.reducedCost +
                targetEdgeDuals[forwardTarget][backwardTarget])
        if (reducedCost >= -Parameters.eps) {
            return false
        }

        val joinedVertexPath = mutableListOf<Int>()
        joinedVertexPath.addAll(forwardState.getPartialPathVertices().asReversed())
        joinedVertexPath.addAll(backwardState.getPartialPathVertices())

        val route = Route(
            joinedVertexPath,
            joinedVertexPath.map { instance.whichTarget(it) },
            forwardState.score + backwardState.score,
            getJoinedPathLength(forwardState, backwardState),
            reducedCost
        )
        if (optimalRoute == null || reducedCost <= optimalRoute!!.reducedCost - Parameters.eps) {
            optimalRoute = route
        }

        if (!hasCycle(joinedVertexPath)) {
            elementaryRoutes.add(route)
            if (elementaryRoutes.size >= Parameters.maxPathsInsideSearch) {
                return true
            }
        }

        return false
    }

    /**
     * Check if the path generated by joining the given states is feasible. Feasibility means:
     * - States cannot visit the same critical vertex.
     * - Total budget is feasible.
     * - The join does not produce a 2-cycle in terms of targets.
     *
     * @return true if path is feasible, false otherwise.
     */
    private fun feasible(fs: State, bs: State): Boolean {
        return (!fs.hasCommonVisits(bs) &&
                getJoinedPathLength(fs, bs) <= maxPathLength &&
                (fs.parent == null || !sameTarget(fs.parent.vertex, bs.vertex)) &&
                (bs.parent == null || !sameTarget(fs.vertex, bs.parent.vertex)))
    }

    /**
     * Compute the path length of path obtained by joining the given states.
     *
     * @param fs forward state with partial path from source
     * @param bs backward state with partial path to destination
     * @return computed path cost (i.e. total edge length)
     */
    private fun getJoinedPathLength(fs: State, bs: State): Double {
        return fs.pathLength + bs.pathLength + graph.getEdgeWeight(fs.vertex, bs.vertex)
    }

    /**
     * Check if the given forward and backward state satisfy "half-way-point" conditions.
     *
     * @param fs forward state for partial path from source
     * @param bs backward state for partial path to destination
     * @return true if (fs,bs) satisfy the half-way-point condition, false otherwise
     */
    private fun halfway(fs: State, bs: State): Boolean {
        val currDiff = (fs.pathLength - bs.pathLength).absoluteValue
        if (currDiff <= Parameters.eps) {
            return true
        }

        val joinEdgeLength = graph.getEdgeWeight(fs.vertex, bs.vertex)
        var otherDiff = 0.0
        if (fs.pathLength <= bs.pathLength - Parameters.eps) {
            if (bs.parent != null) {
                otherDiff = (fs.pathLength + joinEdgeLength - bs.parent.pathLength).absoluteValue
            }
        } else if (fs.parent != null) {
            otherDiff = (fs.parent.pathLength - (joinEdgeLength + bs.pathLength)).absoluteValue
        }

        if (currDiff <= otherDiff - Parameters.eps) {
            return true
        }
        if (currDiff >= otherDiff + Parameters.eps) {
            return false
        }
        return fs.pathLength >= bs.pathLength + Parameters.eps
    }

    /**
     * Add [newState] to [existingStates] if it is not dominated by any state in [existingStates].
     * If [newState] is dominated, mark it as dominated, just in case it is held elsewhere for use.
     */
    private fun updateNonDominatedStates(
        existingStates: MutableList<State>,
        newState: State,
        onExtend: (State) -> Unit
    ) {
        var dominatingPredecessorTarget: Int? = null
        for (state in existingStates) {
            if (!state.dominates(newState, useVisitCondition)) {
                continue
            }
            val predecessorTarget = instance.whichTarget(state.parent!!.vertex)
            if (dominatingPredecessorTarget == null) {
                dominatingPredecessorTarget = predecessorTarget
            } else if (predecessorTarget != dominatingPredecessorTarget) {
                newState.dominated = true
                return
            }
        }
        existingStates.add(newState)
        onExtend(newState)
    }

    /**
     * Logger object
     */
    companion object : KLogging()
}