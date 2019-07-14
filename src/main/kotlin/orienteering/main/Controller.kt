package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging

/**
 * Clas that manages the entire solution process
 */
class Controller {
    companion object: KLogging()
    private lateinit var cplex: IloCplex

    /**
     * Runs the solver.
     */
    fun run() {
    }

    /**
     * Initializes CPLEX object.
     */
    private fun initCplex() {
        cplex = IloCplex()
        logger.info("initialized CPLEX")
    }

    /**
     * Clears CPLEX object.
     */
    private fun endCplex() {
        cplex.clearModel()
        cplex.end()
    }
}