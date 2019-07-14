package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.InstanceDto

/**
 * Manages the entire solution process.
 */
class Controller {
    companion object: KLogging()
    private lateinit var cplex: IloCplex

    /**
     * Runs the solver.
     */
    fun run() {
        val instance = InstanceDto("", "").instance
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