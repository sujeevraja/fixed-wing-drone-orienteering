package orienteering.main

import ilog.cplex.IloCplex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import mu.KLogging
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import orienteering.data.Instance
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.BoundingLP
import orienteering.solver.BranchAndPriceSolver
import orienteering.solver.TimeChecker
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Manages the entire lpSolution process.
 */
class Controller {
    private lateinit var instance: Instance
    private lateinit var cplex: IloCplex
    private lateinit var resultsPath: String
    private val results = sortedMapOf<String, Any>()

    /**
     * Parses [args], the given command-line arguments.
     */
    fun parseArgs(args: Array<String>) {
        val parser = CliParser()
        parser.main(args)
        resultsPath = parser.outputPath

        Parameters.initialize(
            instanceName = parser.instanceName,
            instancePath = parser.instancePath,
            algorithm = parser.algorithm,
            turnRadius = parser.turnRadius,
            numDiscretizations = parser.numDiscretizations,
            numReducedCostColumns = parser.numReducedCostColumns,
            timeLimitInSeconds = parser.timeLimitInSeconds,
            useInterleavedSearch = parser.useInterleavedSearch == 1,
            useNumTargetsForDominance = parser.useNumTargetsForDominance == 1,
            relaxDominanceRules = parser.relaxDominanceRules == 1,
            numSolverCoroutines = parser.numSolverCoroutines
        )

        results["instance_name"] = parser.instanceName
        results["instance_path"] = parser.instancePath
        results["algorithm"] = if (parser.algorithm == 1) "BC" else "BP"
        results["time_limit_in_seconds"] = parser.timeLimitInSeconds
        results["turn_radius"] = parser.turnRadius
        results["number_of_discretizations"] = parser.numDiscretizations
        results["number_of_reduced_cost_columns"] = parser.numReducedCostColumns
        results["number_of_solver_coroutines"] = parser.numSolverCoroutines
        logger.debug("finished parsing command line arguments and populating parameters")
    }

    /**
     * function to populate the instance
     */
    fun populateInstance() {
        instance = InstanceDto(
            Parameters.instanceName,
            Parameters.instancePath,
            Parameters.numDiscretizations,
            Parameters.turnRadius
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
    @ObsoleteCoroutinesApi
    fun run() {
        TimeChecker.startTracking()
        val timeElapsedMillis = measureTimeMillis {
            when (Parameters.algorithm) {
                1 -> runBranchAndCut()
                2 -> runBranchAndPrice()
                else -> throw OrienteeringException("unknown algorithm type")
            }
        }

        val timeInSeconds = timeElapsedMillis / 1000.0
        results["solution_time_in_seconds"] = timeInSeconds
        logger.info("run completed, time: $timeInSeconds seconds")
    }

    /**
     * Function to dump the results in a YAML file
     */
    fun writeResults() {
        val dumperOptions = DumperOptions()
        dumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        dumperOptions.isPrettyFlow = true

        val yaml = Yaml(dumperOptions)
        val writer = File(resultsPath).bufferedWriter()
        yaml.dump(results, writer)
        writer.close()
    }

    /**
     * Function to run branch-and-price algorithm
     */
    @ObsoleteCoroutinesApi
    private fun runBranchAndPrice() {
        logger.info("algorithm: branch and price")
        val context = (if (Parameters.numSolverCoroutines == 1)
            newSingleThreadContext("OneThread") else Dispatchers.Default)
        val bps = BranchAndPriceSolver(instance, context)
        bps.solve()
        val bpSolution = bps.finalSolution

        results["root_lower_bound"] = bps.rootLowerBound
        results["root_upper_bound"] = bps.rootUpperBound
        results["root_gap_percentage"] =
            computePercentGap(bps.rootLowerBound, bps.rootUpperBound)

        results["final_lower_bound"] = bpSolution.lowerBound
        results["final_upper_bound"] = bpSolution.upperBound
        results["final_gap_percentage"] =
            computePercentGap(bpSolution.lowerBound, bpSolution.upperBound)

        results["optimality_reached"] = bpSolution.optimalityReached
        results["number_of_nodes_solved"] = bpSolution.numNodesSolved
        results["maximum_concurrent_solves"] = bpSolution.maxConcurrentSolves
        results["average_concurrent_solves"] = bpSolution.averageConcurrentSolves
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
