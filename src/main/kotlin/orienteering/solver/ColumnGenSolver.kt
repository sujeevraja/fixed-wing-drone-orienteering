package orienteering.solver

import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.util.SetGraph
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route

/**
 * Solves the multi-vehicle orienteering problem with column generation.
 */
class ColumnGenSolver(
    private val instance: Instance,
    private val numReducedCostColumns: Int,
    private val cplex: IloCplex,
    private val graph: SetGraph,
    private val mustVisitTargets: IntArray,
    private val mustVisitEdges: List<Pair<Int, Int>>
) {
    /**
     * Column pool (updated every time a pricing problem is solved).
     */
    private var columns: MutableList<Route> = mutableListOf()
    /**
     * Dual of vehicle limit constraint.
     */
    private var vehicleCoverDual = 0.0
    /**
     * Target reduced costs indexed by target id.
     */
    var targetReducedCosts = MutableList(instance.numTargets) { 0.0 }
        private set

    var targetEdgeDuals = List(instance.numTargets) { MutableList(instance.numTargets) { 0.0 } }
        private set
    /**
     * Final LP objective value
     */
    var lpObjective = 0.0
        private set
    /**
     * Final LP lpSolution
     */
    var lpSolution = mutableListOf<Pair<Route, Double>>()
        private set
    /**
     * Final value of auxiliary variable for infeasibility detection
     */
    var lpInfeasible = true
        private set
    /**
     * Final MIP objective value
     */
    var mipObjective = 0.0
        private set
    /**
     * Final MIP solution
     */
    var mipSolution = listOf<Route>()
        private set

    /**
     * Runs the column generation algorithm.
     *
     * This algorithm works by iterating between solving a set cover LP of a Restricted Master
     * Problem (RMP) with a limited number of columns and solving a pricing problem to find a
     * pre-specified number of reduced cost columns. Optimality is reached with the pricing problem
     * solver fails to find any negative reduced cost columns.
     */
    fun solve() {
        var columnGenIteration = 0
        while (true) {
            logger.debug("----- START column generation iteration $columnGenIteration")
            solveRestrictedMasterProblem()
            val newColumns = generateColumns()
            if (newColumns.isEmpty()) {
                logger.debug("----- TERMINATE due to optimality")
                break
            }
            columns.addAll(newColumns)
            logger.debug("----- END column generation iteration $columnGenIteration")
            columnGenIteration++
        }

        if (!lpInfeasible) {
            solveRestrictedMasterProblem(asMip = true)
        }
    }

    /**
     * Generates negative reduced cost columns by solving the pricing problem. Optimality can be
     * declared if no columns can be found by this function.
     *
     * @return generated list of negative reduced cost elementary paths.
     */
    private fun generateColumns(): List<Route> {
        val pricingProblemSolver =
            PricingProblemSolver(
                instance,
                vehicleCoverDual,
                targetReducedCosts,
                targetEdgeDuals,
                numReducedCostColumns,
                graph,
                mustVisitTargets,
                mustVisitEdges
            )
        pricingProblemSolver.generateColumns()
        return pricingProblemSolver.elementaryRoutes
    }

    /**
     * Solves restricted mater problem with latest available columns.
     *
     * @param asMip true if model needs to be solved as integer, false otherwise.
     */
    private fun solveRestrictedMasterProblem(asMip: Boolean = false) {
        logger.debug("number of columns: ${columns.size}")
        val setCoverModel = SetCoverModel(cplex)
        setCoverModel.createModel(
            instance,
            columns,
            asMip,
            mustVisitTargets = mustVisitTargets.toList(),
            mustVisitTargetEdges = mustVisitEdges
        )
        setCoverModel.solve()
        if (asMip) {
            // collect MIP solution
            mipObjective = setCoverModel.objective
            logger.debug("MIP objective: $mipObjective")
            val setCoverSolution = setCoverModel.getSolution()
            val selectedRoutes = mutableListOf<Route>()
            for (i in 0 until setCoverSolution.size) {
                if (setCoverSolution[i] >= Parameters.eps) {
                    selectedRoutes.add(columns[i])
                }
            }
            mipSolution = selectedRoutes
            // logger.info("solved restricted master MIP")
        } else {
            // Collect dual of num-vehicles constraint.
            vehicleCoverDual = setCoverModel.getRouteDual()

            // Store reduced costs of all targets using their coverage constraint duals and scores.
            val targetDuals = setCoverModel.getTargetDuals()
            for (i in 0 until targetDuals.size) {
                targetReducedCosts[i] = targetDuals[i] - instance.targetScores[i]
            }

            // Update reduced costs of targets to which visits are enforced.
            for ((target, dual) in setCoverModel.getMustVisitTargetDuals()) {
                targetReducedCosts[target] += dual
            }

            // Store duals of target edges with enforced use.
            targetEdgeDuals.forEach { it.fill(0.0) }
            for ((vertexPair, dual) in setCoverModel.getMustVisitTargetEdgeDuals()) {
                targetEdgeDuals[vertexPair.first][vertexPair.second] = dual
            }

            // collect LP solution
            lpObjective = setCoverModel.objective
            logger.debug("LP objective: $lpObjective")
            lpSolution.clear()
            val setCoverSolution = setCoverModel.getSolution()
            for (i in 0 until setCoverSolution.size) {
                if (setCoverSolution[i] >= Parameters.eps) {
                    lpSolution.add(Pair(columns[i], setCoverSolution[i]))
                }
            }
            lpInfeasible = setCoverModel.getAuxiliaryVariableSolution() >= Parameters.eps
            // logger.info("solved restricted master LP")
        }
        cplex.clearModel()
    }

    /**
     * Logger object.
     */
    companion object : KLogging()
}