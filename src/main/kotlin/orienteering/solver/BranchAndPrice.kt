package orienteering.solver

import ilog.cplex.IloCplex
import orienteering.data.Instance
import orienteering.data.Route

/**
 * Solves the multi-vehicle orienteering problem with branch and price.
 */
class BranchAndPrice(private val instance: Instance,
                     private val cplex: IloCplex) {
    /**
     * Column pool (updated every time a pricing problem is solved).
     */
    private var columns: MutableList<Route> = mutableListOf()
    /**
     * Runs the branch and price algorithm.
     */
    fun solve(): List<Route> {
        columns = generateInitialColumns()
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
     */
    private fun generateInitialColumns(): MutableList<Route> {
        return mutableListOf()
    }

    /**
     * Generates negative reduced cost columns by solving the pricing problem. Optimality can be
     * declared if no columns can be found by this function.
     *
     * @return true if no negative cost columns are found, false otherwise.
     */
    private fun generateColumns(): Boolean {
        val reducedCosts = List(instance.numVertices) { 0.0 }
        val newColumns = Pricer(instance, reducedCosts).generateColumns()
        if (newColumns.isEmpty()) return true
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