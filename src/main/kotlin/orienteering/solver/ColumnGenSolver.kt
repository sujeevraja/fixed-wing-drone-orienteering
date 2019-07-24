package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
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
    private val cplex: IloCplex
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
     * Duals of target coverage constraints indexed by target id.
     */
    private var targetDuals = List(instance.numTargets) { 0.0 }
    /**
     * Final solution
     */
    private var solution = mutableListOf<Pair<Route, Double>>()

    /**
     * Runs the column generation algorithm.
     *
     * This algorithm works by iterating between solving a set cover LP of a Restricted Master
     * Problem (RMP) with a limited number of columns and solving a pricing problem to find a
     * pre-specified number of reduced cost columns. Optimality is reached with the pricing problem
     * solver fails to find any negative reduced cost columns.
     */
    fun solve(): List<Pair<Route, Double>> {
        // Both the route cover and target cover constraints are of the <= form. So, for a primal
        // solution of zero, all primal slacks are non-zero. So, the dual solution is also zero.
        // For this dual solution, the reduced cost of each target is simply the negative of the
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

        // solveRestrictedMasterProblem(asMip = true)
        return solution
    }

    /**
     * Generates negative reduced cost columns by solving the pricing problem. Optimality can be
     * declared if no columns can be found by this function.
     *
     * @return generated list of negative reduced cost elementary paths.
     */
    private fun generateColumns(): List<Route> {
        val reducedCosts = (0 until instance.numTargets).map {
            targetDuals[it] - instance.targetScores[it]
        }
        val pricer =
            PricingProblemSolver(instance, vehicleCoverDual, reducedCosts, numReducedCostColumns)
        pricer.generateColumns()
        val routes = pricer.elementaryRoutes
        if (routes.isEmpty()) {
            logger.info("no reduced cost columns found")
        } else {
            val bestRoute = routes.minBy { it.reducedCost }
            logger.info("least reduced cost route: $bestRoute")
        }
        return pricer.elementaryRoutes
    }

    /**
     * Solves restricted mater problem with latest available columns.
     *
     * @param asMip true if model needs to be solved as integer, false otherwise.
     */
    private fun solveRestrictedMasterProblem(asMip: Boolean = false) {
        logger.info("starting to solve restricted master problem...")
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(instance, columns, asMip)
        setCoverModel.solve()
        if (asMip) {
            throw OrienteeringException("do not solve set cover as MIP!!")
        } else {
            // collect duals
            vehicleCoverDual = setCoverModel.getRouteDual()
            targetDuals = setCoverModel.getTargetDuals()
            for (i in 0 until targetDuals.size) {
                if (targetDuals[i].absoluteValue >= Constants.EPS) {
                    logger.debug("dual of target $i: ${targetDuals[i]}")
                }
            }

            // collect solution
            solution.clear()
            val setCoverSolution = setCoverModel.getSolution()
            for (i in 0 until setCoverSolution.size) {
                if (setCoverSolution[i] >= Constants.EPS) {
                    solution.add(Pair(columns[i], setCoverSolution[i]))
                }
            }
            logger.info("solved restricted master LP")
        }
        setCoverModel.clearModel()
        logger.info("cleared model")
    }

    /**
     * Logger object.
     */
    companion object : KLogging()
}