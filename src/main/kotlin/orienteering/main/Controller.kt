package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.CliParser
import orienteering.data.Instance
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.BranchAndPrice

/**
 * Manages the entire solution process.
 */
class Controller {
    companion object: KLogging()

    private lateinit var instance: Instance
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
                instancePath = parser.instancePath,
                algorithm = parser.algorithm,
                turnRadius = parser.turnRadius,
                numDiscretizations = parser.numDiscretizations)
        logger.info("finished parsing command line arguments and populating parameters")
    }

    /**
     * function to populate the instance
     */
    fun populateInstance() {
        instance = InstanceDto(parameters.instanceName, parameters.instancePath,
                    parameters.numDiscretizations, parameters.turnRadius).getInstance()
    }

    /**
     * Initializes CPLEX container.
     */
    private fun initCPLEX() {
        cplex = IloCplex()
    }

    /**
     * Clears CPLEX container.
     */
    private fun clearCPLEX() {
        cplex.clearModel()
        cplex.end()
    }

    /**
     * Function to start the solver
     */
    fun run() {
        when (parameters.algorithm) {
            1 -> runDssrAlgorithm()
            2 -> runBranchAndCutAlgorithm()
            3 -> runBranchAndPriceAlgorithm()
        }
    }


    /**
     * Runs standalone DSSR solver
     */
    private fun runDssrAlgorithm() {

    }

    /**
     * Function to run the branch-and-cut algorithm
     */
    private fun runBranchAndCutAlgorithm() {
        logger.info("starting the branch-and-cut algorithm")
        initCPLEX()

        clearCPLEX()
    }

    /**
     * Function to run branch-and-price algorithm
     */
    private fun runBranchAndPriceAlgorithm() {
        logger.info("starting the branch-and-price algorithm")
        initCPLEX()

        clearCPLEX()
    }
}