package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Route

/**
 * Used to solve set covering LP/MIP models with a specified set of columns.
 *
 * @property cplex re-usable object to build and solve model with CPLEX
 */
class SetCoverModel(private val cplex: IloCplex) {
    /**
     * Logger object
     */
    companion object: KLogging()

    /**
     * Creates a set covering model with the given [instance] and [columns]. The model will be
     * stored in the [cplex] object.
     */
    fun createModel(instance: Instance, columns: List<Route>) {
    }

    /**
     * Solves the set covering model built with [createModel]
     */
    fun solve() {
        cplex.setOut(null)
    }
}