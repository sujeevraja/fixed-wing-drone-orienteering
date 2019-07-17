package orienteering.solver

import ilog.cplex.IloCplex
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
     * Runs the branch and price algorithm.
     */
    fun solve(): List<Route> {
        columns.addAll(generateInitialColumns())
        var solution: List<Route> = listOf()
        while (true) {
            solveRestrictedMasterProblem()
            val optimalityReached = generateColumns()
            if (optimalityReached)
                break
        }
        return solution
    }

    /**
     * Provides an initial set of columns for branch and price.
     *
     * Both the route cover and target cover constraints are of the <= form. So, for a primal
     * solution of zero, all primal slacks are non-zero. So, the dual solution is also zero. For
     * this dual solution, the reduced cost of each target is simply the negative of the target
     * score. We use these reduced costs to run the pricing problem and generate some negative
     * reduced cost columns.
     */
    private fun generateInitialColumns(): List<Route> {
        val vehicleCoverDual = 0.0
        val reducedCosts: List<Double> = (0 until instance.numVertices).map {
            -instance.getScore(it)
        }.toList()
        val pricer = Pricer(instance, vehicleCoverDual, reducedCosts, numReducedCostColumns)
        pricer.generateColumns()
        return pricer.elementaryRoutes
    }

    /**
     * Generates negative reduced cost columns by solving the pricing problem. Optimality can be
     * declared if no columns can be found by this function.
     *
     * @return true if no negative cost columns are found, false otherwise.
     */
    private fun generateColumns(): Boolean {
        val vehicleCoverDual = 0.0
        val reducedCosts = List(instance.numVertices) { 0.0 }
        val pricer = Pricer(instance, vehicleCoverDual, reducedCosts, numReducedCostColumns)
        pricer.generateColumns()
        val newColumns = pricer.elementaryRoutes
        if (newColumns.isEmpty()) {
            return true
        }
        columns.addAll(newColumns)
        return false
    }

    /**
     * Solves restricted mater problem with latest available columns.
     */
    private fun solveRestrictedMasterProblem() {
        val setCoverModel = SetCoveringFormulation(cplex)
        setCoverModel.createModel(instance, columns)
        setCoverModel.solve()
    }
}