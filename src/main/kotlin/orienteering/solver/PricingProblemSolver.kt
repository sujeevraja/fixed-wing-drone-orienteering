package orienteering.solver

import mu.KLogging
import orienteering.Constants
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
 * Kaarthik Sundar, Sujeevraja Sanjeevi and Christopher Montez.
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
    private val parameters: Parameters,
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
     * Flag for indicating if at least one target has been indicated as a critical target
     */
    private var hasCriticalTargets = false

    /**
     * If true, state dominance check uses the following additional condition:
     *
     * State s1 dominates s2 iff critical targets visited by s1 is a subset of those visited by s2.
     *
     * For interleaved search, this condition is controlled by the "relaxDominanceRules" parameter.
     * For simple search, this parameter is always true.
     */
    private var useVisitCondition = (!parameters.relaxDominanceRules ||
            !parameters.useInterleavedSearch)

    /**
     * Generates negative reduced cost elementaryRoutes.
     */
    //Good
    fun generateColumns() {
        // Store source states.
        for (srcVertex in srcVertices) {
            forwardStates[srcVertex].add(
                State.buildTerminalState(
                    true,
                    srcVertex,
                    instance.whichTarget(srcVertex),
                    numTargets
                )
            )
        }

        // Store destination state.
        for (dstVertex in dstVertices) {
            backwardStates[dstVertex].add(
                State.buildTerminalState(
                    false,
                    dstVertex,
                    instance.whichTarget(dstVertex),
                    numTargets
                )
            )
        }

        var searchIteration = 0
        var stop = false
        do {
            logger.debug("----- START search iteration $searchIteration")
            initializeIteration()

            val stopSearch = if (parameters.useInterleavedSearch) interleavedSearch()
            else simpleSearch()

            if (stopSearch) {
                logger.debug("----- STOP column search as search() stopped")
                break
            }

            if (parameters.useInterleavedSearch &&
                elementaryRoutes.size >= parameters.maxPathsAfterSearch
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

    // Need to update how cycles detected
    private fun initializeIteration() {
        // Update critical vertices.
        for (i in isCritical.indices)
            if (!isCritical[i])
                isCritical[i] = isVisitedMultipleTimes[i]

        // Clear all states except source and destination.
        for (i in forwardStates.indices) {
            if (i !in srcVertices)
                forwardStates[i].clear()
            else for (state in forwardStates[i]) {
                state.extended = false
                state.dominated = false
            }
            if (i !in dstVertices)
                backwardStates[i].clear()
            else for (state in backwardStates[i]) {
                state.extended = false
                state.dominated = false
            }
        }

        // Update optimal route with best cached elementary route if necessary.
        if (optimalRoute != null && hasCycle(optimalRoute!!.vertexPath)) {
            optimalRoute = elementaryRoutes.firstOrNull()
            for (route in elementaryRoutes.drop(1)) {
                if (route.reducedCost <= optimalRoute!!.reducedCost - Constants.EPS)
                    optimalRoute = route
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
                processState(state) { unprocessedForwardStates.add(it) }
            }
        }

        // Extend destination state.
        for (dstVertex in dstVertices) {
            for (state in backwardStates[dstVertex]) {
                processState(state) { unprocessedBackwardStates.add(it) }
            }
        }

        while (unprocessedForwardStates.isNotEmpty() || unprocessedBackwardStates.isNotEmpty()) {
            if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                return true

            var state: State? = null
            if (processForwardState) {
                if (unprocessedForwardStates.isNotEmpty())
                    state = unprocessedForwardStates.remove()
            } else if (unprocessedBackwardStates.isNotEmpty())
                state = unprocessedBackwardStates.remove()
            processForwardState = !processForwardState

            if (state == null || state.dominated)
                continue

            if (state.extended)
                throw OrienteeringException("extended state in unprocessed state container")

            val vertex = state.vertex

            // Current state is a forward state, so join with all non-dominated backward states
            if (state.isForward) {
                // Join with all backward states.
                for (e in graph.outgoingEdgesOf(vertex))
                    for (bs in backwardStates[graph.getEdgeTarget(e)])
                        if (save(state, bs))
                            return true
                if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                    return true

                processState(state) { unprocessedForwardStates.add(it) }

                if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                    return true
            } else { // Current state is a backward state
                // Join with all forward states.
                for (e in graph.incomingEdgesOf(vertex))
                    for (fs in forwardStates[graph.getEdgeSource(e)])
                        if (save(fs, state))
                            return true

                if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                    return true

                processState(state) { unprocessedBackwardStates.add(it) }

                if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                    return true
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
            if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                return true

            val vertex = candidateVertices.first()

            // Complete all forward extensions.
            for (state in forwardStates[vertex])
                if (!state.dominated)
                    processState(state) { candidateVertices.add(it.vertex) }

            if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                return true

            // Complete all backward extensions.
            for (state in backwardStates[vertex])
                if (!state.dominated)
                    processState(state) { candidateVertices.add(it.vertex) }

            if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                return true

            candidateVertices.remove(vertex)
        }

        // Join forward and backward paths.
        val edgeSet = graph.edgeSet()
        for (edge in edgeSet) {
            val head = graph.getEdgeSource(edge)
            val tail = graph.getEdgeTarget(edge)
            for (fs in forwardStates[head])
                for (bs in backwardStates[tail])
                    if (save(fs, bs))
                        return true

            if (TimeChecker.timeLimitReached(parameters.timeLimitInSeconds))
                return true
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
                hasCriticalTargets = true
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
    // Need to make obsolete
    private fun hasCycle(path: List<Int>): Boolean {
        val visited = hashSetOf<Int>()
        for (vertex in path) {
            val target = instance.whichTarget(vertex)
            if (visited.contains(target))
                return true
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
        val currentVertex = state.vertex
        for (e in graph.outgoingEdgesOf(currentVertex)) {
            val nextVertex = graph.getEdgeTarget(e)

            // Don't extend to critical targets more than once
            if (state.usedCriticalTarget(instance.whichTarget(nextVertex)))
                continue

            // No 2-cycles
            if (state.parent != null && sameTarget(state.parent.vertex, nextVertex))
                continue

            // Checking if an extension is feasible
            val edgeLength = graph.getEdgeWeight(currentVertex, nextVertex)
            val extension = extendIfFeasible(state, nextVertex, edgeLength) ?: continue

            // Extension is feasible. Update non-dominated states accordingly
            updateNonDominatedStates(forwardStates[nextVertex], extension, onExtend)
        }
    }

    private fun extendBackward(state: State, onExtend: (State) -> Unit) {
        val currentVertex = state.vertex
        for (e in graph.incomingEdgesOf(currentVertex)) {
            val prevVertex = graph.getEdgeSource(e)
            if (state.usedCriticalTarget(instance.whichTarget(prevVertex)))
                continue

            if (state.parent != null && sameTarget(state.parent.vertex, prevVertex))
                continue

            val edgeLength = graph.getEdgeWeight(prevVertex, currentVertex)
            val extension = extendIfFeasible(state, prevVertex, edgeLength) ?: continue

            updateNonDominatedStates(backwardStates[prevVertex], extension, onExtend)
        }

    }

    private fun sameTarget(v1: Int, v2: Int): Boolean =
        instance.whichTarget(v1) == instance.whichTarget(v2)

    private fun canExtend(state: State): Boolean {
        if (state.dominated)
            return false

        // Prevent extension of states that have consumed more than half the path length
        // budget. This reduces the number of extensions to be considered, while ensuring that
        // optimality is unaffected. Refer to section 4.3 in the paper for further details.
        if (state.pathLength >= (maxPathLength / 2.0) - Constants.EPS)
            return false

        if (parameters.useNumTargetsForDominance) {
            // Paths joined will always be on an edge (i,j) with a forward label at i and a
            // backward label at j. So, any label has visited more targets than (numTargets - 1)
            // can be discarded.
            if (state.numTargetsVisited > numTargets - 1)
                return false
        }

        return true
    }

    private fun extendIfFeasible(state: State, neighbor: Int, edgeLength: Double): State? {
        // Prevent budget infeasibility.
        if (state.pathLength + edgeLength > maxPathLength)
            return null

        // Here, extension is feasible. So, generate and return it.
        val neighborTarget = instance.whichTarget(neighbor)

        val rcUpdate = targetReducedCosts[neighborTarget] +
                if (state.isForward) targetEdgeDuals[state.target][neighborTarget]
                else targetEdgeDuals[neighborTarget][state.target]

        return state.extend(
            newVertex = neighbor,
            newTarget = neighborTarget,
            isCritical = isCritical[neighborTarget],
            edgeLength = edgeLength,
            vertexScore = instance.targetScores[neighborTarget],
            reducedCostChange = rcUpdate,
            useBangForBuck = parameters.useBangForBuck
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
        if (!feasible(forwardState, backwardState) || !halfway(forwardState, backwardState))
            return false

        val forwardTarget = forwardState.target
        val backwardTarget = backwardState.target
        val reducedCost = (routeDual + forwardState.reducedCost + backwardState.reducedCost +
                targetEdgeDuals[forwardTarget][backwardTarget])

        if (reducedCost >= -Constants.EPS)
            return false

        val joinedVertexPath = forwardState.getPartialPathVertices()
            .asReversed() + backwardState.getPartialPathVertices()

        val route = Route(
            joinedVertexPath,
            joinedVertexPath.map { instance.whichTarget(it) },
            forwardState.score + backwardState.score,
            getJoinedPathLength(forwardState, backwardState),
            reducedCost
        )

        if (optimalRoute == null || reducedCost <= optimalRoute!!.reducedCost - Constants.EPS)
            optimalRoute = route

        if (!hasCycle(joinedVertexPath)) {
            elementaryRoutes.add(route)
            if (elementaryRoutes.size >= parameters.maxPathsInsideSearch)
                return true
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
    private fun feasible(fs: State, bs: State): Boolean = !fs.hasCommonCriticalVisits(bs) &&
            getJoinedPathLength(fs, bs) <= maxPathLength &&
            (fs.parent == null || !sameTarget(fs.parent.vertex, bs.vertex)) &&
            (bs.parent == null || !sameTarget(fs.vertex, bs.parent.vertex))


    /**
     * Compute the path length of path obtained by joining the given states.
     *
     * @param fs forward state with partial path from source
     * @param bs backward state with partial path to destination
     * @return computed path cost (i.e. total edge length)
     */
    private fun getJoinedPathLength(fs: State, bs: State): Double =
        fs.pathLength + bs.pathLength + graph.getEdgeWeight(fs.vertex, bs.vertex)

    /**
     * Check if the given forward and backward state satisfy "half-way-point" conditions.
     *
     * @param fs forward state for partial path from source
     * @param bs backward state for partial path to destination
     * @return true if (fs,bs) satisfy the half-way-point condition, false otherwise
     */
    private fun halfway(fs: State, bs: State): Boolean {
        val currDiff = (fs.pathLength - bs.pathLength).absoluteValue
        if (currDiff <= Constants.EPS)
            return true

        val joinEdgeLength = graph.getEdgeWeight(fs.vertex, bs.vertex)
        var otherDiff = 0.0
        if (fs.pathLength <= bs.pathLength - Constants.EPS) {
            if (bs.parent != null)
                otherDiff = (fs.pathLength + joinEdgeLength - bs.parent.pathLength).absoluteValue
        } else if (fs.parent != null)
            otherDiff = (fs.parent.pathLength - (joinEdgeLength + bs.pathLength)).absoluteValue

        if (currDiff <= otherDiff - Constants.EPS)
            return true
        if (currDiff >= otherDiff + Constants.EPS)
            return false
        return fs.pathLength >= bs.pathLength + Constants.EPS
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
        // Before checking for domination, updating unreachable targets for stronger dominance
        if (hasCriticalTargets)
            updateUnreachableCriticalTargets(newState)

        // Checking for domination both ways
        for (i in existingStates.indices.reversed()) {
            val existingState = existingStates[i]
            if (existingState.dominates(
                    newState,
                    useVisitCondition,
                    parameters.useNumTargetsForDominance
                )
            ) {
                if (canRemoveDominated(existingState, newState)) {
                    newState.dominated = true
                    return
                }
            } else if (newState.dominates(
                    existingState,
                    useVisitCondition,
                    parameters.useNumTargetsForDominance
                ) && canRemoveDominated(newState, existingState)
            ) {
                existingState.dominated = true
                existingStates.removeAt(i)
            }
        }

        // As the new state is found to be non-dominated, store it for further processing.
        existingStates.add(newState)
        onExtend(newState)
    }

    /**
     * Function that identifies all targets that are unreachable for a given state in the sense that taking a single
     * edge to a new critical target will exceed the length budget. Since only a single move is considered, the edge
     * lengths need not satisfy the triangle inequality for this to behave properly.
     */
    private fun updateUnreachableCriticalTargets(state: State) {
        val currentVertex = state.vertex
        if (state.isForward) {
            // Finding all targets reachable from the current target using a single edge
            for (e in graph.outgoingEdgesOf(currentVertex)) {
                val nextTarget = instance.whichTarget(graph.getEdgeTarget(e))
                val edgeLength = graph.getEdgeWeight(e)

                // If the next target is a critical target, check if it is unreachable and mark if so
                if (isCritical[nextTarget] && state.pathLength + edgeLength > instance.budget)
                    state.markCriticalTargetUnreachable(nextTarget)
            }
        } else {
            // Finding all targets reachable from the current target using a single edge
            for (e in graph.incomingEdgesOf(currentVertex)) {
                val prevTarget = instance.whichTarget(graph.getEdgeSource(e))
                val edgeLength = graph.getEdgeWeight(e)

                // If the target is a critical target, check if it is unreachable and mark if so
                if (isCritical[prevTarget] && state.pathLength + edgeLength > instance.budget)
                    state.markCriticalTargetUnreachable(prevTarget)
            }
        }
    }

    /**
     * Logger object
     */
    companion object : KLogging()
}

/**
 * Check if the [dominated] state can be removed from consideration even though it is dominated by the [dominating]
 * state. If yes, return true. Otherwise, make sure to update the dominating predecessor value of [dominated]
 *
 * This function makes sure that 2-cycle elimination is handled correctly. For specifics on how this elimination works,
 * refer to the subsection titled "SPPRC-2-cyc" in Page 51 of the following book chapter.
 *
 * Irnich, Stefan, and Guy Desaulniers. "Shortest path problems with resource constraints." Column generation.
 * Springer, Boston, MA, 2005. 33-65.
 */
private fun canRemoveDominated(dominating: State, dominated: State): Boolean =
    if (dominating.predecessorTargetUnreachable || dominating.predecessorTarget == dominated.predecessorTarget)
        true
    else if (dominated.dominatingPredecessor == null) {
        dominated.dominatingPredecessor = dominating.predecessorTarget
        false
    } else dominated.dominatingPredecessor != dominating.predecessorTarget
