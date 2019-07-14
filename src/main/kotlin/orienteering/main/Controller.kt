package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.BranchAndPrice

/**
 * Manages the entire solution process.
 */
class Controller {
    companion object: KLogging()
    private lateinit var cplex: IloCplex
    private lateinit var parameters: Parameters

    /**
     * Parses [args], the given command-line arguments.
     */
    fun parseArgs(args: Array<String>) {
        val parser = CliParser()
        parser.main(args)
        parameters = Parameters(
                instanceName = parser.instanceName,
                instancePath = parser.instancePath)
        logger.info("finished parsing command line arguments")
    }

    /**
     * Runs the branch and price solver.
     */
    fun run() {
        val instance = InstanceDto(parameters.instanceName, parameters.instancePath).instance
        initCplex()
        val bpSolver = BranchAndPrice(instance, cplex)
        bpSolver.solve()
        endCplex()
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