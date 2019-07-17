package orienteering.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.concert.IloRange
import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.data.Instance
import orienteering.data.Route

/**
 * Class that formulates the set-cover formulation given a list of routes
 *
 * @param cplex Cplex class object
 */
class SetCoverModel(private var cplex: IloCplex) {

    /**
     * Boolean list indicating if the model has a constraint corresponding to a target
     */
    private lateinit var hasTargetConstraint: MutableList<Boolean>
    /**
     * constraint id for route constraint
     */
    private var routeConstraintId: Int = 0
    /**
     * constraint id list for target constraint
     */
    private lateinit var targetConstraintId: MutableList<Int?>
    /**
     * array of decision variables
     */
    private lateinit var routeVariable: ArrayList<IloNumVar>
    /**
     * array of constraints
     */
    private lateinit var constraints: ArrayList<IloRange>

    /**
     * Companion object.
     */
    companion object : KLogging()

    /**
     * Function to create the Set-Covering model
     * @param instance object of the Instance Class
     * @param routes list of route objects
     */
    fun createModel(instance: Instance, routes: MutableList<Route>, binary: Boolean = false) {
        hasTargetConstraint = MutableList(instance.numTargets) { false }
        targetConstraintId = MutableList(instance.numTargets) { null }

        routeVariable = arrayListOf()
        val whichRoutes: MutableList<MutableList<Int>> =
            MutableList(instance.numTargets) { mutableListOf<Int>() }
        val routeExpr: IloLinearNumExpr = cplex.linearNumExpr()
        val objExpr: IloLinearNumExpr = cplex.linearNumExpr()

        for (i in 0 until routes.size) {
            if (binary)
                routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Bool, "z_$i"))
            else
                routeVariable.add(cplex.numVar(0.0, Double.MAX_VALUE, IloNumVarType.Float, "z_$i"))
            for (vertex in routes[i].path) {
                val targetId = instance.whichTarget(vertex)
                whichRoutes[targetId].add(i)
                if (instance.whichTarget(vertex) == instance.source ||
                    instance.whichTarget(vertex) == instance.destination
                )
                    continue
                hasTargetConstraint[targetId] = true
            }
            routeExpr.addTerm(1.0, routeVariable[i])
            objExpr.addTerm(routes[i].score, routeVariable[i])
        }


        cplex.addMaximize(objExpr)
        constraints = arrayListOf()
        constraints.add(cplex.addLe(routeExpr, instance.numVehicles.toDouble(), "route-cover"))
        routeConstraintId = 0

        for (i in 0 until instance.numTargets) {
            if (i == instance.source || i == instance.destination ||
                    i in instance.getTargetsToSkipCovering()) continue
            if (whichRoutes[i].size == 0) continue
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            for (routeId in whichRoutes[i])
                expr.addTerm(1.0, routeVariable[routeId])
            targetConstraintId[i] = constraints.size
            constraints.add(cplex.addLe(expr, 1.0, "target-cover-$i"))
        }

    }

    /**
     * Function to solve the model
     * @return optimal route index
     */
    fun solve(): List<Double> {
        cplex.setOut(null)
        if (!cplex.solve())
            throw RuntimeException("Set covering problem infeasible")
        return getOptimalSolution()
    }

    /**
     * Function to get the optimal solution value
     * @return list of values indexed by route index
     */
    private fun getOptimalSolution(): List<Double> {
        return routeVariable.map { cplex.getValue(it) }
    }

    /**
     * Function to obtain optimal route index
     * @return the optimal route index (throws RuntimeException if fractional solution is obtained)
     */
    fun getOptimalRouteIndex(): Int {
        var optimalRouteIndex: Int? = null
        val solution = getOptimalSolution()
        for (i in 0 until solution.size)
            if (solution[i] > 0.9)
                optimalRouteIndex = i
        if (optimalRouteIndex == null) {
            logger.info("set covering problem has fractional solution")
            throw RuntimeException("Fractional set covering solution")
        }
        return optimalRouteIndex
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
     * @return a list of dual values (if target constraint is not present, then a null is returned for that target)
     */
    fun getTargetDuals(): List<Double?> {
        return (0 until hasTargetConstraint.size).map {
            if (hasTargetConstraint[it])
                cplex.getDual(constraints[targetConstraintId[it]!!]) else null
        }
    }

    /**
     * Function to clear cplex object
     */
    fun clearModel() {
        cplex.clearModel()
    }
}