package orienteering.main

import branchandbound.api.BranchAndBoundApi
import branchandbound.api.SelectionStrategy
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.InstanceDto
import orienteering.data.Parameters
import orienteering.solver.*
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
            useBangForBuck = parser.useBangForBuck == 1,
            useNumTargetsForDominance = parser.useNumTargetsForDominance == 1,
            relaxDominanceRules = parser.relaxDominanceRules == 1,
            numSolverCoroutines = parser.numSolverCoroutines
        )

        results["instance_name"] = parser.instanceName
        results["instance_path"] = parser.instancePath
        results["algorithm"] = if (parser.algorithm == 1) "branch_and_cut" else "branch_and_price"
        results["time_limit_in_seconds"] = parser.timeLimitInSeconds
        results["turn_radius"] = parser.turnRadius
        results["number_of_discretizations"] = parser.numDiscretizations
        results["number_of_reduced_cost_columns"] = parser.numReducedCostColumns
        results["number_of_solver_coroutines"] = parser.numSolverCoroutines
        results["search"] = if (parser.useInterleavedSearch == 1) "interleaved" else "simple"
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
        results["budget"] = instance.budget
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
        ObjectMapper(YAMLFactory()).writeValue(File(resultsPath), results)
    }

    /**
     * Function to run branch-and-price algorithm
     */
    private fun runBranchAndPrice() {
        logger.info("algorithm: branch and price")
        val sln = solveWithBranchAndPrice(instance)
        results["root_lower_bound"] = sln.rootLowerBound
        results["root_upper_bound"] = if (sln.rootUpperBound >= 1e20) "infinity" else sln.rootUpperBound
        results["root_lp_optimal"] = sln.rootLpOptimal
        results["root_gap_percentage"] = computePercentGap(sln.rootLowerBound, sln.rootUpperBound)
        results["final_lower_bound"] = sln.lowerBound
        results["final_upper_bound"] = if (sln.upperBound >= 1e20) "infinity" else sln.upperBound
        results["final_gap_percentage"] = computePercentGap(sln.lowerBound, sln.upperBound)
        results["optimality_reached"] = sln.optimalityReached
        results["number_of_nodes_solved"] = sln.numNodesCreated
        results["maximum_parallel_solves"] = sln.maxParallelSolves
    }

    private fun computePercentGap(lb: Double, ub: Double): Double = ((ub - lb) / ub) * 100.0

    /**
     * Function to run the branch-and-cut algorithm
     */
    private fun runBranchAndCut() {
        logger.info("algorithm: branch and cut")
        initCPLEX()
        val bc = BranchAndCutSolver(instance, cplex)
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

private fun solveWithBranchAndPrice(instance: Instance): BranchAndPriceSolution {
    val idGenerator = generateSequence(0L) { it + 1 }.iterator()
    val solution = BranchAndBoundApi.runBranchAndBound(
        (0 until Parameters.numSolverCoroutines).map { NodeSolver(instance) },
        SelectionStrategy.BEST_BOUND,
        Node(id = idGenerator.next(), instance.graph),
        { TimeChecker.timeLimitReached() }
    ) {
        (it as Node).branch(instance, idGenerator)
    } ?: return BranchAndPriceSolution()

    val bestSolution = solution.incumbent?.let { (it as Node).mipSolution } ?: listOf()
    val solvedRootNode = solution.solvedRootNode as Node
    return BranchAndPriceSolution(
        optimalityReached = solution.optimalityReached,
        rootLowerBound = solvedRootNode.mipObjective ?: -Double.MAX_VALUE,
        rootUpperBound = solvedRootNode.lpObjective,
        rootLpOptimal = solvedRootNode.lpOptimal,
        lowerBound = solution.lowerBound,
        upperBound = solution.upperBound,
        bestFeasibleSolution = bestSolution,
        numNodesCreated = solution.numCreatedNodes,
        numFeasibleNodes = solution.numFeasibleNodes,
        maxParallelSolves = solution.maxParallelSolves
    )
}
