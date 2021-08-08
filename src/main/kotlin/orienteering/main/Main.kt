package orienteering.main

import branchandbound.api.BranchAndBoundApi
import branchandbound.api.SelectionStrategy
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import ilog.cplex.IloCplex
import mu.KotlinLogging
import orienteering.data.Instance
import orienteering.data.InstanceBuilder
import orienteering.data.Parameters
import orienteering.solver.*
import java.io.File
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) {
    val parser = CliParser()
    parser.main(args)

    val parameters = buildParameters(parser)
    val instance = InstanceBuilder(
        parameters.instanceName,
        parameters.instancePath,
        parameters.numDiscretizations,
        parameters.turnRadius
    ).instance
    val inputKPIs = prepareInputKPIs(parameters, instance.budget)
    val solutionKPIs = run(instance, parameters)
    ObjectMapper(YAMLFactory()).writeValue(
        File(parameters.outputPath), inputKPIs + solutionKPIs
    )
}

private fun buildParameters(parser: CliParser): Parameters = Parameters(
    instanceName = parser.instanceName,
    instancePath = parser.instancePath,
    outputPath = parser.outputPath,
    algorithm = parser.algorithm,
    turnRadius = parser.turnRadius,
    numDiscretizations = parser.numDiscretizations,
    maxPathsInsideSearch = parser.numReducedCostColumns,
    timeLimitInSeconds = parser.timeLimitInSeconds,
    useInterleavedSearch = parser.useInterleavedSearch == 1,
    useBangForBuck = parser.useBangForBuck == 1,
    useNumTargetsForDominance = parser.useNumTargetsForDominance == 1,
    relaxDominanceRules = parser.relaxDominanceRules == 1,
    numSolverCoroutines = parser.numSolverCoroutines
)

private fun prepareInputKPIs(parameters: Parameters, budget: Double): Map<String, Any> = mapOf(
    "algorithm" to if (parameters.algorithm == 1) "branch_and_cut" else "branch_and_price",
    "budget" to budget,
    "instance_name" to parameters.instanceName,
    "instance_path" to parameters.instancePath,
    "number_of_discretizations" to parameters.numDiscretizations,
    "number_of_reduced_cost_columns" to parameters.maxPathsInsideSearch,
    "number_of_solver_coroutines" to parameters.numSolverCoroutines,
    "time_limit_in_seconds" to parameters.timeLimitInSeconds,
    "turn_radius" to parameters.turnRadius,
    "search" to if (parameters.useInterleavedSearch) "interleaved" else "simple"
)

private fun run(instance: Instance, parameters: Parameters): Map<String, Any> {
    TimeChecker.startTracking()
    val results = mutableMapOf<String, Any>()
    val timeInSec = measureTimeMillis {
        results.putAll(
            when (parameters.algorithm) {
                1 -> runBranchAndCut(instance)
                2 -> runBranchAndPrice(instance, parameters)
                else -> throw OrienteeringException("unknown algorithm type")
            }
        )
    } / 1000.0
    log.info { "run completed in ${timeInSec.format(2)} seconds" }
    results["solution_time_in_seconds"] = timeInSec
    return results
}

/**
 * Function to run the branch-and-cut algorithm
 */
private fun runBranchAndCut(instance: Instance): Map<String, Any> {
    log.info { "algorithm: branch and cut" }
    val cplex = IloCplex()
    val bc = BranchAndCutSolver(instance, cplex)
    bc.createModel()
    bc.exportModel()
    bc.solve()
    cplex.clearModel()
    cplex.end()
    return mapOf()
}

private fun runBranchAndPrice(instance: Instance, parameters: Parameters): Map<String, Any> {
    log.info { "algorithm: branch and price" }
    val sln = solveWithBranchAndPrice(instance, parameters)
    return mapOf(
        "root_lower_bound" to sln.rootLowerBound,
        "root_upper_bound" to if (sln.rootUpperBound >= 1e20) "infinity" else sln.rootUpperBound,
        "root_lp_optimal" to sln.rootLpOptimal,
        "root_gap_percentage" to computePercentGap(sln.rootLowerBound, sln.rootUpperBound),
        "final_lower_bound" to sln.lowerBound,
        "final_upper_bound" to if (sln.upperBound >= 1e20) "infinity" else sln.upperBound,
        "final_gap_percentage" to computePercentGap(sln.lowerBound, sln.upperBound),
        "optimality_reached" to sln.optimalityReached,
        "number_of_nodes_solved" to sln.numNodesCreated,
        "maximum_parallel_solves" to sln.maxParallelSolves
    )
}

private fun solveWithBranchAndPrice(
    instance: Instance,
    parameters: Parameters
): BranchAndPriceSolution {
    val idGenerator = generateSequence(0L) { it + 1 }.iterator()
    val solution = BranchAndBoundApi.runBranchAndBound(
        (0 until parameters.numSolverCoroutines).map { NodeSolver(instance, parameters) },
        SelectionStrategy.BEST_BOUND,
        Node(id = idGenerator.next(), instance.graph),
        { TimeChecker.timeLimitReached(parameters.timeLimitInSeconds) }
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

private fun computePercentGap(lb: Double, ub: Double): Double = ((ub - lb) / ub) * 100.0
