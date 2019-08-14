package orienteering.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.concert.IloRange
import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Route
import orienteering.main.OrienteeringException

/**
 * Class that formulates the set-cover formulation given a list of routes
 *
 * @param cplex Cplex class object
 */
class SetCoverModel(private var cplex: IloCplex) {
    /**
     * route constraint id
     */
    private var routeConstraintId: Int = 0
    /**
     * Boolean list indicating if the model has a target cover constraint.
     */
    private lateinit var hasTargetCoverConstraint: MutableList<Boolean>
    /**
     * list of constraint ids for target cover constraints
     */
    private lateinit var targetCoverConstraintId: MutableList<Int?>
    /**
     * map of must-visit target to constraint id
     */
    private var mustVisitTargetConstraintId: MutableMap<Int, Int> = mutableMapOf()
    /**
     * map of must-visit target-edge to constraint id
     */
    private var mustVisitTargetEdgeConstraintId: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()
    /**
     * map of must-visit vertex to constraint id
     */
    private var mustVisitVertexConstraintId: MutableMap<Int, Int> = mutableMapOf()
    /**
     * map of must-visit vertex edge to constraint id
     */
    private var mustVisitVertexEdgeConstraintId: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()
    /**
     * list of route variables
     */
    private var routeVariable: ArrayList<IloNumVar> = arrayListOf()
    /**
     * auxiliary variable to detect infeasibility
     */
    private lateinit var auxiliaryVariable: IloNumVar
    /**
     * list of all constraints
     */
    private var constraints: ArrayList<IloRange> = arrayListOf()

    /**
     * CPLEX objective value
     */
    var objective = 0.0
        private set

    /**
     * Function to create the Set-Covering model
     * @param instance object of the Instance Class
     * @param routes list of route objects
     * @param binary if true, the model is created as a MIP and as a LP otherwise.
     * @param mustVisitTargets targets to which visits are enforced.
     * @param mustVisitTargetEdges target-edges to which visits are enforced.
     * @param mustVisitVertices vertices to which visits are enforced.
     * @param mustVisitVertexEdges actual edges between vertices to which visits are enforced.
     */
    fun createModel(
        instance: Instance,
        routes: MutableList<Route>,
        binary: Boolean = false,
        mustVisitTargets: IntArray = intArrayOf(),
        mustVisitTargetEdges: List<Pair<Int, Int>> = mutableListOf(),
        mustVisitVertices: List<Int> = mutableListOf(),
        mustVisitVertexEdges: List<Pair<Int, Int>> = mutableListOf()
    ) {
        hasTargetCoverConstraint = MutableList(instance.numTargets) { false }
        targetCoverConstraintId = MutableList(instance.numTargets) { null }

        val routeExpr: IloLinearNumExpr = cplex.linearNumExpr()
        val objExpr: IloLinearNumExpr = cplex.linearNumExpr()
        val targetRoutes = MutableList(instance.numTargets) { mutableListOf<Int>() }

        auxiliaryVariable = cplex.numVar(0.0, Double.MAX_VALUE, IloNumVarType.Float, "w")

        for (i in 0 until routes.size) {
            if (binary)
                routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Bool, "z_$i"))
            else
                routeVariable.add(cplex.numVar(0.0, Double.MAX_VALUE, IloNumVarType.Float, "z_$i"))

            routeExpr.addTerm(1.0, routeVariable[i])
            objExpr.addTerm(routes[i].score, routeVariable[i])

            routes[i].targetPath.forEach {
                if (it == instance.sourceTarget || it == instance.destinationTarget)
                    return@forEach
                targetRoutes[it].add(i)
                hasTargetCoverConstraint[it] = true
            }
        }

        /**
         *  add objective: { max sum(r in route) score(r) * z(r) - M * w }
         */
        objExpr.addTerm(-100000.0, auxiliaryVariable)
        cplex.addMaximize(objExpr)

        /**
         *  route cover constraint: { sum(r in route) z(r) <= numVehicles }
         */
        constraints.add(cplex.addLe(routeExpr, instance.numVehicles.toDouble(), "route_cover"))
        routeConstraintId = 0

        /**
         *  populate target cover constraint ids and create target cover constraints
         *  { sum(r in route) I(route contains target) * z(r) <= 1 }
         */
        for (i in 0 until instance.numTargets) {
            if (i == instance.sourceTarget || i == instance.destinationTarget || targetRoutes[i].isEmpty())
                continue
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            targetRoutes[i].forEach {
                expr.addTerm(1.0, routeVariable[it])
            }
            targetCoverConstraintId[i] = constraints.size
            constraints.add(cplex.addLe(expr, 1.0, "target_cover_$i"))
        }

        /**
         * mustVisit target constraints { sum(r in route) I(route contains target) * z(r) + w >= 1 }
         */
        mustVisitTargets.forEach {
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            targetRoutes[it].forEach { it1 ->
                expr.addTerm(1.0, routeVariable[it1])
            }
            expr.addTerm(1.0, auxiliaryVariable)
            mustVisitTargetConstraintId[it] = constraints.size
            constraints.add(cplex.addGe(expr, 1.0, "must_visit_target_$it"))
        }

        /**
         * populate vertex incidence on routes
         */
        val vertexRoutes = mustVisitVertices.map { it to mutableListOf<Int>() }.toMap()
        mustVisitVertices.forEach {
            for (i in 0 until routes.size) {
                if (it in routes[i].vertexPath)
                    vertexRoutes.getValue(it).add(i)
            }
        }

        /**
         * mustVisit vertex constraints { sum(r in route) I(route contains vertex) * z(r) + w >= 1 }
         */
        mustVisitVertices.forEach {
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            vertexRoutes.getValue(it).forEach { it1 ->
                expr.addTerm(1.0, routeVariable[it1])
            }
            expr.addTerm(1.0, auxiliaryVariable)
            mustVisitVertexConstraintId[it] = constraints.size
            constraints.add(cplex.addGe(expr, 1.0, "must_visit_vertex_$it"))
        }

        /**
         * populate targetEdge incidence on routes
         */
        val targetEdgeRoutes = mustVisitTargetEdges.map { it to mutableListOf<Int>() }.toMap()
        mustVisitTargetEdges.forEach {
            for (i in 0 until routes.size) {
                if (it in routes[i].targetPath.zipWithNext())
                    targetEdgeRoutes.getValue(it).add(i)
            }
        }
        /**
         * mustVisitTargetEdge constraints { sum(r in route) I(route contains targetEdge) * z(r) + w >= 1 }
         */
        mustVisitTargetEdges.forEach {
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            targetEdgeRoutes.getValue(it).forEach { it1 ->
                expr.addTerm(1.0, routeVariable[it1])
            }
            expr.addTerm(1.0, auxiliaryVariable)
            mustVisitTargetEdgeConstraintId[it] = constraints.size
            constraints.add(
                cplex.addGe(
                    expr,
                    1.0,
                    "must_visit_target_edge_${it.first}_${it.second}"
                )
            )
        }

        /**
         * populate vertexEdge incidence on routes
         */
        val vertexEdgeRoutes = mustVisitVertexEdges.map { it to mutableListOf<Int>() }.toMap()
        mustVisitVertexEdges.forEach {
            for (i in 0 until routes.size) {
                if (it in routes[i].vertexPath.zipWithNext())
                    vertexEdgeRoutes.getValue(it).add(i)
            }
        }

        mustVisitVertexEdges.forEach {
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            vertexEdgeRoutes.getValue(it).forEach { it1 ->
                expr.addTerm(1.0, routeVariable[it1])
            }
            expr.addTerm(1.0, auxiliaryVariable)
            mustVisitVertexEdgeConstraintId[it] = constraints.size
            constraints.add(
                cplex.addGe(
                    expr,
                    1.0,
                    "must_visit_vertex_edge_${it.first}_${it.second}"
                )
            )
        }

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
    }

    /**
     * Returns solution of set cover model.
     */
    fun getSolution(): List<Double> {
        return (0 until routeVariable.size).map {
            cplex.getValue(routeVariable[it])
        }
    }

    /**
     * Returns solution value of auxiliary variable.
     *
     * If this value is non-zero, the solution is infeasible and needs more columns.
     */
    fun getAuxiliaryVariableSolution(): Double {
        return cplex.getValue(auxiliaryVariable)
    }

    /**
     * Function to get the route dual
     * @return Dual value
     */
    fun getRouteDual(): Double = cplex.getDual(constraints[routeConstraintId])


    /**
     * Function to get the dual value of constraint corresponding to the targets
     * @return a list of dual values with a default of 0.0 if a target constraint is not present.
     */
    fun getTargetDuals(): List<Double> = (0 until hasTargetCoverConstraint.size).map {
        if (hasTargetCoverConstraint[it])
            cplex.getDual(constraints[targetCoverConstraintId[it]!!]) else 0.0
    }


    /**
     * Function to get dual of must visit target constraints
     * @return map of dual values keyed by target id
     */
    fun getMustVisitTargetDuals(): Map<Int, Double> = mustVisitTargetConstraintId.map {
        it.key to cplex.getDual(constraints[it.value])
    }.toMap()

    /**
     * Function to get dual of must visit vertex constraints
     * @return map of dual values keyed by vertex id
     */
    fun getMustVisitVertexDuals(): Map<Int, Double> = mustVisitVertexConstraintId.map {
        it.key to cplex.getDual(constraints[it.value])
    }.toMap()

    /**
     * Function to get dual of must visit targetEdges
     * @return map of dual values keyed by target edge pairs
     */
    fun getMustVisitTargetEdgeDuals(): Map<Pair<Int, Int>, Double> =
        mustVisitTargetEdgeConstraintId.map {
            it.key to cplex.getDual(constraints[it.value])
        }.toMap()

    /**
     * Function to get dual of must visit targetEdges
     * @return map of dual values keyed by target edge pairs
     */
    fun getMustVisitVertexEdgeDuals(): Map<Pair<Int, Int>, Double> =
        mustVisitVertexEdgeConstraintId.map {
            it.key to cplex.getDual(constraints[it.value])
        }.toMap()

    /**
     * Companion object.
     */
    companion object : KLogging()
}