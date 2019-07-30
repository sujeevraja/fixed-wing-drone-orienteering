package orienteering.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.concert.IloRange
import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.OrienteeringException
import orienteering.data.Instance
import orienteering.data.Route

/**
 * Class that formulates the set-cover formulation given a list of routes
 *
 * @param cplex Cplex class object
 */
class SetCoverModel(private var cplex: IloCplex) {

    /**
     * @property routeConstraintId route constraint id
     * @property hasTargetCoverConstraint Boolean list indicating if the model has a target cover constraint
     * @property targetCoverConstraintId list of constraint ids for target cover constraints
     * @property mustVisitTargetConstraintId map of target to constraint id
     * @property mustVisitTargetEdgeConstraintId map of target-edge to constraint id
     * @property mustVisitVertexConstraintId map of vertex to constraint id
     * @property mustVisitVertexEdgeConstraintId map of vertex-edge constraint id
     * @property routeVariable list of route variables
     * @property auxiliaryVariable for infeasibility
     * @property constraints list of constraints
     * @property objective objective
     */
    private var routeConstraintId: Int = 0
    private lateinit var hasTargetCoverConstraint: MutableList<Boolean>
    private lateinit var targetCoverConstraintId: MutableList<Int?>
    private var mustVisitTargetConstraintId: MutableMap<Int, Int> = mutableMapOf()
    private var mustVisitTargetEdgeConstraintId: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()
    private var mustVisitVertexConstraintId: MutableMap<Int, Int> = mutableMapOf()
    private var mustVisitVertexEdgeConstraintId: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()
    private var routeVariable: ArrayList<IloNumVar> = arrayListOf()
    private lateinit var auxiliaryVariable: IloNumVar
    private var constraints: ArrayList<IloRange> = arrayListOf()

    /**
     * Boolean list indicating if the model has a constraint corresponding to a target
     */
    private lateinit var hasTargetConstraint: MutableList<Boolean>
    /**
     * constraint id list for target constraint
     */
    private lateinit var targetConstraintId: MutableList<Int?>

    /**
     * CPLEX objective value
     */
    var objective = 0.0
        private set

    /**
     * Function to create the Set-Covering model
     * @param instance object of the Instance Class
     * @param routes list of route objects
     */
    fun createModel(
        instance: Instance,
        routes: MutableList<Route>,
        binary: Boolean = false,
        mustVisitTargets: List<Int> = mutableListOf(),
        mustVisitTargetEdges: List<Pair<Int, Int>> = mutableListOf(),
        mustVisitVertices: List<Int> = mutableListOf(),
        mustVisitVertexEdges: List<Pair<Int, Int>> = mutableListOf()
    ) {
        hasTargetCoverConstraint = MutableList(instance.numTargets) { false }
        targetCoverConstraintId = MutableList(instance.numTargets) { null }

        val routeExpr: IloLinearNumExpr = cplex.linearNumExpr()
        val objExpr: IloLinearNumExpr = cplex.linearNumExpr()
        val targetRoutes = MutableList(instance.numTargets) { mutableListOf<Int>() }

        for (i in 0 until routes.size) {
            if (binary)
                routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Bool, "z_$i"))
            else
                routeVariable.add(cplex.numVar(0.0, Double.MAX_VALUE, IloNumVarType.Float, "z_$i"))

            routeExpr.addTerm(1.0, routeVariable[i])
            objExpr.addTerm(routes[i].score, routeVariable[i])

            routes[i].targetPath.iterator().forEach {
                if (it == instance.sourceTarget || it == instance.destinationTarget)
                    return@forEach
                targetRoutes[it].add(i)
                hasTargetCoverConstraint[it] = true
            }
        }

        cplex.addMaximize(objExpr)
        constraints.add(cplex.addLe(routeExpr, instance.numVehicles.toDouble(), "route_cover"))
        routeConstraintId = 0


        for (i in 0 until instance.numTargets) {
            if (i == instance.sourceTarget || i == instance.destinationTarget || whichRoutes[i].isEmpty()) {
                continue
            }
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            for (routeId in whichRoutes[i])
                expr.addTerm(1.0, routeVariable[routeId])
            targetConstraintId[i] = constraints.size
            constraints.add(cplex.addLe(expr, 1.0, "target_cover_$i"))
        }

        val vertexRoutes = mustVisitVertices.map { it to mutableListOf<Int>() }.toMap()
        val targetEdgeRoutes = mustVisitTargetEdges.map { it to mutableListOf<Int>() }.toMap()
        val vertexEdgeRoutes = mustVisitVertexEdges.map { it to mutableListOf<Int>() }.toMap()

    }

    /**
     * Solves model built with [createModel].
     */
    fun solve() {
        cplex.setOut(null)
        // cplex.exportModel("logs/set_cover.lp")
        if (!cplex.solve()) {
            throw OrienteeringException("Set covering problem infeasible")
        }
        objective = cplex.objValue
        /*
        logger.debug("set cover objective: $objective")
        logger.debug("----- lpSolution print start")
        for (i in 0 until routeVariable.size) {
            val solutionValue = cplex.getValue(routeVariable[i])
            if (solutionValue >= Constants.EPS) {
                logger.debug("column $i: $solutionValue")
            }
        }
        logger.debug("----- lpSolution print end")
         */
    }

    fun getSolution(): List<Double> {
        return (0 until routeVariable.size).map {
            cplex.getValue(routeVariable[it])
        }
    }

    /**
     * Function to get the route dual
     * @return Dual value
     */
    fun getRouteDual(): Double {
        return cplex.getDual(constraints[routeConstraintId])
    }

    /**
     * Function to get the dual value of constraint corresponding to the targets
     * @return a list of dual values with a default of 0.0 if a target constraint is not present.
     */
    fun getTargetDuals(): List<Double> {
        return (0 until hasTargetConstraint.size).map {
            if (hasTargetConstraint[it])
                cplex.getDual(constraints[targetConstraintId[it]!!]) else 0.0
        }
    }

    /**
     * Companion object.
     */
    companion object : KLogging()
}