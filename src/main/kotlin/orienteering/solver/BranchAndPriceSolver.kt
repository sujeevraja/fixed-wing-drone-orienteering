package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.Constants
import orienteering.data.Instance
import orienteering.data.Route
import java.util.PriorityQueue

class BranchAndPriceSolver(
    private val instance: Instance,
    private val numReducedCostColumns: Int,
    private val cplex: IloCplex
) {
    private val lowerBound: Double = -Double.MAX_VALUE
    private val upperBound: Double = Double.MAX_VALUE
    private val openNodes = PriorityQueue<Node>()

    fun solve(): List<Route> {
        logger.info("starting branch and price...")
        val cgSolver = ColumnGenSolver(instance, numReducedCostColumns, cplex)
        val columnsAndValues = cgSolver.solve()
        if (isIntegral(columnsAndValues)) {
            logger.info("optimality reached")
            return columnsAndValues.map { it.first }
        }

        logger.info("completed branch and price.")
        return listOf()
    }

    private fun isIntegral(columnsAndValues: List<Pair<Route, Double>>): Boolean {
        return columnsAndValues.all {
            it.second >= 1.0 - Constants.EPS
        }
    }

    private fun selectVertexForBranching(): Int? {
        return null
    }

    private fun selectEdgeForBranching(): Pair<Int, Int> {
        return Pair(-1, -1)
    }

    companion object : KLogging()
}
