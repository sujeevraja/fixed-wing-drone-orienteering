package orienteering.solver

import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import orienteering.Constants
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.Route
import orienteering.getEdgeWeight
import java.util.*
import kotlin.math.absoluteValue

/**
 * Pricing problem solver to find negative reduced cost paths for a branch and price algorithm
 * using the Decremental State Space Relaxation (DSSR) algorithm.
 *
 * Algorithm reference:
 *
 * Righini, Giovanni, and Matteo Salani.
 * "New dynamic programming algorithms for the resource constrained elementary shortest path problem."
 * Networks: An International Journal 51.3 (2008): 155-170.
 *
 * This class implements Algorithm 3 in the paper.
 *
 * @param instance problem data
 * @param targetReducedCosts reduced costs indexed by vertex id
 * @param numReducedCostColumns maxim
 */
class PricingProblemSolver(
    private val instance: Instance,
    private val routeDual: Double,
    private val targetReducedCosts: List<Double>,
    private val numReducedCostColumns: Int,
    private val graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>,
    private val mustVisitTargets: List<Boolean>,
    private val mustVisitEdges: List<Pair<Int, Int>>
) {
    /**
     * Number of targets (i.e. vertex clusters) in given instance.
     */
    private val numTargets = instance.numTargets
    /**
     * Number of vertices in given instance.
     */
    private val numVertices = graph.vertexSet().max()!! + 1
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
     */
    private var useVisitCondition = false

    /**
     * Generates negative reduced cost elementaryRoutes.
     *
     * @return list of elementaryRoutes with negative reduced cost.
     */
    fun generateColumns() {
        /*
        logger.debug("vehicle cover dual: $routeDual")
        for (i in 0 until numTargets) {
            if (targetReducedCosts[i].absoluteValue >= Constants.EPS) {
                logger.debug("reduced cost of target: $i: ${targetReducedCosts[i]}")
            }
        }
        logger.debug("starting column generation...")
         */

        // Store source states.
        for (srcVertex in srcVertices) {
            forwardStates[srcVertex].add(State.buildTerminalState(true, srcVertex, numTargets))
        }

        // Store destination state.
        for (dstVertex in dstVertices) {
            backwardStates[dstVertex].add(State.buildTerminalState(false, dstVertex, numTargets))
        }

        var searchIteration = 0
        var stop = false
        do {
            logger.debug("----- START search iteration $searchIteration")
            initializeIteration()

            // Extend source states.
            for (srcVertex in srcVertices) {
                for (state in forwardStates[srcVertex]) {
                    state.extended = false
                    extendForward(state)
                }
            }

            // Extend destination state.
            for (dstVertex in dstVertices) {
                for (state in backwardStates[dstVertex]) {
                    state.extended = false
                    extendBackward(state)
                }
            }

            val stopSearch = search()
            if (stopSearch) {
                logger.debug("----- STOP column search due to #columns limit")
                break
            }

            if (elementaryRoutes.size >= 10) {
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

        // logger.debug("completed column generation.")
    }

    private fun initializeIteration() {
        // Update critical vertices.
        for (i in 0 until isCritical.size) {
            if (!isCritical[i]) {
                isCritical[i] = isVisitedMultipleTimes[i]
            }
        }

        // Clear all states except source and destination.
        for (i in 0 until forwardStates.size) {
            if (i !in srcVertices) {
                forwardStates[i].clear()
            }
            if (i !in dstVertices) {
                backwardStates[i].clear()
            }
        }

        // Update optimal route with best cached elementary route if necessary.
        if (optimalRoute != null && hasCycle(optimalRoute!!.vertexPath)) {
            optimalRoute = elementaryRoutes.firstOrNull()
            for (route in elementaryRoutes.drop(1)) {
                if (route.reducedCost <= optimalRoute!!.reducedCost - Constants.EPS) {
                    optimalRoute = route
                }
            }
        }
    }

    private fun search(): Boolean {
        val criticalTargets = (0 until numTargets).filter { isCritical[it] }
        logger.debug("critical targets: $criticalTargets")

        while (unprocessedForwardStates.isNotEmpty() || unprocessedBackwardStates.isNotEmpty()) {
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

                if (Graphs.vertexHasSuccessors(graph, vertex)) {
                    extendForward(state)
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

                if (Graphs.vertexHasPredecessors(graph, vertex)) {
                    extendBackward(state)
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

    private fun extendForward(state: State) {
        if (state.extended) {
            return
        }
        state.extended = true

        if (!canExtend(state)) {
            return
        }

        val vertex = state.vertex
        for (nextVertex in Graphs.successorListOf(graph, vertex)) {
            if (state.parent != null && sameTarget(state.parent.vertex, nextVertex)) {
                continue
            }
            val edgeLength = graph.getEdgeWeight(vertex, nextVertex)
            val extension = extendIfFeasible(state, nextVertex, edgeLength) ?: continue
            updateNonDominatedStates(forwardStates[nextVertex], extension)
        }
    }

    private fun extendBackward(state: State) {
        if (state.extended) {
            return
        }
        state.extended = true

        if (!canExtend(state)) {
            return
        }

        val vertex = state.vertex
        for (prevVertex in Graphs.predecessorListOf(graph, vertex)) {
            if (state.parent != null && sameTarget(state.parent.vertex, prevVertex)) {
                continue
            }
            val edgeLength = graph.getEdgeWeight(prevVertex, vertex)
            val extension = extendIfFeasible(state, prevVertex, edgeLength) ?: continue
            updateNonDominatedStates(backwardStates[prevVertex], extension)
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
        if (state.pathLength >= (maxPathLength / 2.0) - Constants.EPS) {
            return false
        }

        // To be joined, each state needs to have at least 2 vertices on its partial path, the
        // pseudo source/destination vertex and an actual source/destination vertex. Also, we only
        // join a forward and backward state if they don't have the same incident vertex. So, the
        // maximum number of vertices on any state's partial path can be numTargets - 2.
        if (state.numTargetsVisited > numTargets - 2) {
            return false
        }

        return true
    }

    private fun extendIfFeasible(state: State, neighbor: Int, edgeLength: Double): State? {
        // Prevent multiple visits to critical targets.
        val neighborTarget = instance.whichTarget(neighbor)
        if (isCritical[neighborTarget] && state.visits(neighborTarget)) {
            return null
        }

        // Prevent budget infeasibility.
        if (state.pathLength + edgeLength >= maxPathLength) {
            return null
        }

        // Here, extension is feasible. So, generate and return it.
        return state.extend(
            neighbor,
            neighborTarget,
            isCritical[neighborTarget],
            edgeLength,
            instance.targetScores[neighborTarget],
            targetReducedCosts[neighborTarget]
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

        val reducedCost = routeDual + forwardState.reducedCost + backwardState.reducedCost
        if (reducedCost >= -Constants.EPS) {
            return false
        }

        val joinedPath = mutableListOf<Int>()
        joinedPath.addAll(forwardState.getPartialPathVertices().asReversed())
        joinedPath.addAll(backwardState.getPartialPathVertices())

        val visitedTargets = mutableSetOf<Int>()
        val visitedEdges = mutableSetOf<Pair<Int, Int>>()
        for (i in 0 until joinedPath.size) {
            visitedTargets.add(instance.whichTarget(joinedPath[i]))
            if (i != joinedPath.size - 1) {
                visitedEdges.add(Pair(joinedPath[i], joinedPath[i + 1]))
            }
        }

        for (i in 0 until instance.numTargets) {
            if (mustVisitTargets[i] && i !in visitedTargets) {
                return false
            }
        }

        for (edge in mustVisitEdges) {
            if (edge !in visitedEdges) {
                return false
            }
        }

        val targetPath = joinedPath.map { instance.whichTarget(it) }
        val routeLength = getJoinedPathLength(forwardState, backwardState)
        val route = Route(
            joinedPath,
            targetPath,
            forwardState.score + backwardState.score,
            routeLength,
            reducedCost
        )
        // var printed = false
        if (optimalRoute == null || reducedCost <= optimalRoute!!.reducedCost - Constants.EPS) {
            optimalRoute = route
            /*
            logger.debug("join at ${forwardState.vertex} -> ${backwardState.vertex}")
            printed = true
            logger.debug("opt update: $route")
             */
        }

        if (!hasCycle(joinedPath) && (route !in elementaryRoutes)) {
            elementaryRoutes.add(route)
            /*
            if (!printed) {
                logger.debug("join at ${forwardState.vertex} -> ${backwardState.vertex}")
            }
            logger.debug("ele update: $route")
             */
            if (elementaryRoutes.size >= numReducedCostColumns) {
                return true
            }
        }

        return false
    }

    /**
     * Check if the path generated by joining the given states is budget-feasible.
     *
     * @return true if path is budget-feasible, false otherwise.
     */
    private fun feasible(fs: State, bs: State): Boolean {
        return (!fs.hasCommonVisits(bs) && getJoinedPathLength(fs, bs) <= maxPathLength &&
                !has2Or3Cycle(fs, bs))
    }

    private fun has2Or3Cycle(fs: State, bs: State): Boolean {
        if (fs.parent != null && sameTarget(fs.parent.vertex, bs.vertex)) {
            return true
        }

        if (bs.parent != null && sameTarget(fs.vertex, bs.parent.vertex)) {
            return true
        }

        if (fs.parent != null &&
            bs.parent != null &&
            sameTarget(fs.parent.vertex, bs.parent.vertex)
        ) {
            return true
        }

        return false
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
     * This condition is used to prune duplicate paths. At a high level, say the given forward
     * state is at vertex i and the backward state is at vertex j. Also assume that the parent
     * (i.e. predecessor) of the forward state has vertex i1 and the parent (i.e. successor) of
     * the backward state has vertex j1. The half-way-point condition is satisfied by the given
     * forward-backward state pair if the length of the edge (i,j) is minimum among (i1,i),
     * (i,j) and (j,j1). If we only save paths that satisfy the halfway condition, we can prune out
     * most duplicate paths (the only remaining duplicates occur when there are ties for the
     * minimum edge length, which can also be pruned by this function with a bit more care).
     *
     * In this example, assume that (i,j) satisfies the half-way-point condition. Then, this path
     * would be stored. If the same path got generated by joining the forward state at i1 and the
     * backward state at i, that path, being a duplicate of the (i,j) path would be rejected
     * because it fails the half-way-point condition (as (i,j) edge length is smaller than the
     * (i1,i) edge length). The duplicate path generated by joining the (j,j1) path would also
     * be rejected for the same reason. For further details, refer section 5.1 in the paper.
     *
     * @param fs forward state for partial path from source
     * @param bs backward state for partial path to destination
     * @return true if (fs,bs) satisfy the half-way-point condition, false otherwise
     */
    private fun halfway(fs: State, bs: State): Boolean {
        val currDiff = (fs.pathLength - bs.pathLength).absoluteValue

        if (fs.pathLength <= bs.pathLength - Constants.EPS) {
            var nextDiff = 0.0
            if (bs.parent != null) {
                nextDiff = graph.getEdgeWeight(graph.getEdge(bs.vertex, bs.parent.vertex))
            }
            return currDiff <= nextDiff - Constants.EPS
        }

        var prevDiff = 0.0
        if (fs.parent != null) {
            prevDiff = graph.getEdgeWeight(graph.getEdge(fs.parent.vertex, fs.vertex))
        }
        return currDiff <= prevDiff + Constants.EPS
    }

    /**
     * Adds [newState] to [existingStates] if it is not dominated by any state in [existingStates].
     * Also removes states in [existingStates] dominated by [newState].
     *
     * @return true if newState is a non-dominated state and false otherwise.
     */
    private fun updateNonDominatedStates(
        existingStates: MutableList<State>,
        newState: State
    ) {
        var i = 0
        while (i < existingStates.size) {
            val state = existingStates[i]
            if (state.dominates(newState, useVisitCondition)) {
                newState.dominated = true
                return
            }
            if (newState.dominates(state, useVisitCondition)) {
                state.dominated = true
                existingStates.removeAt(i)
            } else {
                i++
            }
        }
        existingStates.add(newState)
        if (newState.isForward) {
            unprocessedForwardStates.add(newState)
        } else {
            unprocessedBackwardStates.add(newState)
        }
    }

    /**
     * Logger object
     */
    companion object : KLogging()
}