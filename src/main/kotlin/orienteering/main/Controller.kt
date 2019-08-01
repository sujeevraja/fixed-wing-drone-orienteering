package orienteering.main

import ilog.cplex.IloCplex
import mu.KLogging
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import orienteering.util.OrienteeringException
import orienteering.data.Instance
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.BoundingLP
import orienteering.solver.BranchAndPriceSolver
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Manages the entire lpSolution process.
 */
class Controller {
    private lateinit var instance: Instance
    private lateinit var cplex: IloCplex
    private lateinit var parameters: Parameters
    private val results = sortedMapOf<String, Any>()

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

        val inputData = sortedMapOf<String, Any>()
        inputData["instance_name"] = parser.instanceName
        inputData["instance-path"] = parser.instancePath
        inputData["algorithm"] = if (parser.algorithm == 1) "BC" else "BP"
        inputData["turn_radius"] = parser.turnRadius
        inputData["number_of_discretizations"] = parser.numDiscretizations
        inputData["number_of_reduced_cost_columns"] = parser.numReducedCostColumns
        results["input"] = inputData

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
                1 -> runBranchAndCut()
                2 -> runBranchAndPrice()
                else -> throw OrienteeringException("unknown algorithm type")
            }
        }
        logger.info("run completed, time: ${timeElapsedMillis / 1000.0} seconds")
    }

    /**
     * Function to dump the results in a YAML file
     */
    fun writeResults() {
        val dumperOptions = DumperOptions()
        dumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        dumperOptions.isPrettyFlow = true

        val yaml = Yaml(dumperOptions)
        val writer = File("logs/results.yaml").bufferedWriter()
        yaml.dump(results, writer)
        writer.close()
    }

    /**
     * Function to run branch-and-price algorithm
     */
    private fun runBranchAndPrice() {
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

        val outputResults = sortedMapOf<String, Any>()
        outputResults["root_lower_bound"] = bps.rootLowerBound
        outputResults["root_upper_bound"] = bps.rootUpperBound
        outputResults["root_gap_percentage"] =
            computePercentGap(bps.rootLowerBound, bps.rootUpperBound)

        outputResults["final_lower_bound"] = bps.lowerBound
        outputResults["final_upper bound"] = bps.upperBound
        outputResults["final_gap_percentage"] = computePercentGap(bps.lowerBound, bps.upperBound)

        outputResults["number_of_nodes"] = bps.numNodes
        results["output"] = outputResults
    }

    private fun computePercentGap(lb: Double, ub: Double): Double {
        return ((ub - lb) / ub) * 100.0
    }

    /**
     * Function to run the branch-and-cut algorithm
     */
    private fun runBranchAndCut() {
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