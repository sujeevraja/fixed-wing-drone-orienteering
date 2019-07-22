package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.ColumnGenSolver
import kotlin.system.measureTimeMillis

/**
 * Manages the entire solution process.
 */
class Controller {
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
            numDiscretizations = parser.numDiscretizations,
            numReducedCostColumns = parser.numReducedCostColumns
        )
        logger.info("finished parsing command line arguments and populating parameters")
    }

    /**
     * function to populate the instance
     */
    fun populateInstance() {
        instance = InstanceDto(
            parameters.instanceName, parameters.instancePath,
            parameters.numDiscretizations, parameters.turnRadius
        ).getInstance()
    }

    /**
     * Initializes CPLEX container.
     */
    private fun initCPLEX() {
        cplex = IloCplex()
        logger.info("initialized CPLEX")
    }

    /**
     * Clears CPLEX container.
     */
    private fun clearCPLEX() {
        cplex.clearModel()
        cplex.end()
        logger.info("cleared CPLEX")
    }

    /**
     * Function to start the solver
     */
    fun run() {
        val timeElapsedMillis = measureTimeMillis {
            when (parameters.algorithm) {
                1 -> runBranchAndCutAlgorithm()
                2 -> runColumnGenAlgorithm()
                3 -> TODO("runBranchAndPrice()")
                else -> throw OrienteeringException("unknown algorithm type")
            }
        }
        logger.info("run completed, time: ${timeElapsedMillis / 1000.0} seconds")
    }

    /**
     * Function to run branch-and-price algorithm
     */
    private fun runColumnGenAlgorithm() {
        logger.info("starting the branch-and-price algorithm")
        initCPLEX()
        val bp = ColumnGenSolver(instance, parameters.numReducedCostColumns, cplex)
        val solution = bp.solve()
        logger.info("final solution:")
        for (route in solution) {
            logger.info(route.toString())
        }
        val totalScore = solution.sumByDouble { it.score }
        logger.info("final score: $totalScore")
        clearCPLEX()
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
     * Logger object.
     */
    companion object : KLogging()
}