package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.ColumnGenSolver
import kotlin.system.measureTimeMillis
import orienteering.solver.BoundingLP
import orienteering.solver.BranchAndPriceSolver

/**
 * Manages the entire lpSolution process.
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
        logger.debug("finished parsing command line arguments and populating parameters")
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
        val timeElapsedMillis = measureTimeMillis {
            when (parameters.algorithm) {
                1 -> runBranchAndCutAlgorithm()
                2 -> runColumnGenAlgorithm()
                else -> throw OrienteeringException("unknown algorithm type")
            }
        }
        logger.info("run completed, time: ${timeElapsedMillis / 1000.0} seconds")
    }

    /**
     * Function to dump the results in a YAML file
     */
    fun populateRunStatistics() {

    }

    /**
     * Function to run branch-and-price algorithm
     */
    private fun runColumnGenAlgorithm() {
        logger.info("algorithm: branch and price")
        initCPLEX()
        val bps = BranchAndPriceSolver(instance, parameters.numReducedCostColumns, cplex)
        val solution = bps.solve()
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
        logger.info("algorithm: branch and cut")
        initCPLEX()
        val bc = BoundingLP(instance, cplex, targetDuals = List(instance.numTargets) { 0.0 })
        bc.createModel()
        bc.exportModel()
        bc.solve()
        clearCPLEX()
    }

    /**
     * Logger object.
     */
    companion object : KLogging()
}