package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import orienteering.Constants
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.Route
import kotlin.math.absoluteValue

/**
 * Solves the multi-vehicle orienteering problem with column generation.
 */
class ColumnGenSolver(
    private val instance: Instance,
    private val numReducedCostColumns: Int,
    private val cplex: IloCplex,
    private val graph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge>,
    private val mustVisitTargets: List<Boolean>,
    private val mustVisitEdges: List<Pair<Int, Int>>
) {
    /**
     * Column pool (updated every time a pricing problem is solved).
     */
    private var columns: MutableList<Route> = mutableListOf()
    /**
     * Dual of vehicle limit constraint.
     */
    private var vehicleCoverDual = 0.0
    /**
     * Reduced costs indexed by target id.
     */
    var targetReducedCosts = (0 until instance.numTargets).map {
        -instance.targetScores[it]
    }.toMutableList()
        private set
    /**
     * Final LP objective value
     */
    var lpObjective = 0.0
        private set
    /**
     * Final LP lpSolution
     */
    var lpSolution = mutableListOf<Pair<Route, Double>>()
        private set
    /**
     * Final MIP objective value
     */
    var mipObjective = 0.0
        private set
    /**
     * Final MIP solution
     */
    var mipSolution = listOf<Route>()
        private set

    /**
     * Runs the column generation algorithm.
     *
     * This algorithm works by iterating between solving a set cover LP of a Restricted Master
     * Problem (RMP) with a limited number of columns and solving a pricing problem to find a
     * pre-specified number of reduced cost columns. Optimality is reached with the pricing problem
     * solver fails to find any negative reduced cost columns.
     */
    fun solve() {
        // Both the route cover and target cover constraints are of the <= form. So, for a primal
        // lpSolution of zero, all primal slacks are non-zero. So, the dual lpSolution is also zero.
        // For this dual lpSolution, the reduced cost of each target is simply the negative of the
        // target score. We use these reduced costs to run the pricing problem and generate some
        // negative reduced cost columns.
        columns.addAll(generateColumns())

        var columnGenIteration = 0
        while (true) {
            logger.info("----- START column generation iteration $columnGenIteration")
            solveRestrictedMasterProblem()
            val newColumns = generateColumns()
            if (newColumns.isEmpty()) {
                logger.info("----- TERMINATE due to optimality")
                break
            }
            columns.addAll(newColumns)
            logger.info("----- END column generation iteration $columnGenIteration")
            columnGenIteration++
        }

        solveRestrictedMasterProblem(asMip = true)
    }

    /**
     * Generates negative reduced cost columns by solving the pricing problem. Optimality can be
     * declared if no columns can be found by this function.
     *
     * @return generated list of negative reduced cost elementary paths.
     */
    private fun generateColumns(): List<Route> {
        val pricingProblemSolver =
            PricingProblemSolver(instance, vehicleCoverDual, targetReducedCosts, numReducedCostColumns, graph, mustVisitTargets, mustVisitEdges)
        pricingProblemSolver.generateColumns()
        val routes = pricingProblemSolver.elementaryRoutes
        if (routes.isEmpty()) {
            logger.info("no reduced cost columns found")
        } else {
            val bestRoute = routes.minBy { it.reducedCost }
            logger.info("least reduced cost route: $bestRoute")
        }
        return pricingProblemSolver.elementaryRoutes
    }

    /**
     * Solves restricted mater problem with latest available columns.
     *
     * @param asMip true if model needs to be solved as integer, false otherwise.
     */
    private fun solveRestrictedMasterProblem(asMip: Boolean = false) {
        logger.info("starting to solve restricted master problem...")
        logger.debug("number of columns: ${columns.size}")
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(instance, columns, asMip)
        setCoverModel.solve()
        if (asMip) {
            // collect MIP solution
            mipObjective = setCoverModel.objective
            val setCoverSolution = setCoverModel.getSolution()
            val selectedRoutes = mutableListOf<Route>()
            for (i in 0 until setCoverSolution.size) {
                if (setCoverSolution[i] >= Constants.EPS) {
                    selectedRoutes.add(columns[i])
                }
            }
            mipSolution = selectedRoutes
            logger.info("solved restricted master MIP")
        } else {
            // collect duals
            vehicleCoverDual = setCoverModel.getRouteDual()
            val targetDuals = setCoverModel.getTargetDuals()
            for (i in 0 until targetDuals.size) {
                targetReducedCosts[i] = targetDuals[i] - instance.targetScores[i]
            }

            // collect LP solution
            lpObjective = setCoverModel.objective
            lpSolution.clear()
            val setCoverSolution = setCoverModel.getSolution()
            for (i in 0 until setCoverSolution.size) {
                if (setCoverSolution[i] >= Constants.EPS) {
                    lpSolution.add(Pair(columns[i], setCoverSolution[i]))
                }
            }
            logger.info("solved restricted master LP")
        }
        cplex.clearModel()
        logger.info("cleared CPLEX")
    }

    /**
     * Logger object.
     */
    companion object : KLogging()
}