package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Route

/**
 * Solves the multi-vehicle orienteering problem with branch and price.
 */
class BranchAndPrice(
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
    lateinit var solution: List<Route>

    /**
     * Runs the branch and price algorithm.
     */
    fun solve(): List<Route> {
        // Both the route cover and target cover constraints are of the <= form. So, for a primal
        // solution of zero, all primal slacks are non-zero. So, the dual solution is also zero.
        // For this dual solution, the reduced cost of each target is simply the negative of the
        // target score. We use these reduced costs to run the pricing problem and generate some
        // negative reduced cost columns.
        columns.addAll(generateColumns())

        while (true) {
            solveRestrictedMasterProblem()
            val newColumns = generateColumns()
            if (newColumns.isEmpty()) {
                break
            }
            columns.addAll(newColumns)
        }

        solveRestrictedMasterProblem(asMip = true)
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
        val pricer = Pricer(instance, vehicleCoverDual, reducedCosts, numReducedCostColumns)
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
            solution = setCoverModel.getOptimalRouteIndices().map {
                columns[it]
            }
            logger.info("solved restricted master MIP and stored solution")
        } else {
            vehicleCoverDual = setCoverModel.getRouteDual()
            targetDuals = setCoverModel.getTargetDuals()
            logger.info("solved restricted master LP and stored duals")
        }
        setCoverModel.clearModel()
        logger.info("cleared model")
    }

    /**
     * Logger object.
     */
    companion object: KLogging()
}