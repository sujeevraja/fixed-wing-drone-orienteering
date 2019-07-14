package orienteering.solver

import orienteering.data.Instance
import orienteering.data.Route

/**
 * Pricing problem solver to find negative reduced cost paths for a branch and price algorithm.
 *
 * @property instance problem data
 * @property vertexReducedCosts latest reduced costs for each vertex
 */
class Pricer(private val instance: Instance,
             private val vertexReducedCosts: List<Double>) {
    /**
     * Generates negative reduced cost columns.
     *
     * @return list of columns with negative reduced cost.
     */
    fun generateColumns(): List<Route> {
        return listOf()
    }
}