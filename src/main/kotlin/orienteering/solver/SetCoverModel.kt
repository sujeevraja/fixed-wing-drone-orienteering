package orienteering.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.concert.IloNumVarType
import ilog.concert.IloRange
import ilog.cplex.IloCplex
import mu.KLogging
import orienteering.Constants
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
     * Function to create the Set-Covering model
     * @param instance object of the Instance Class
     * @param routes list of route objects
     */
    fun createModel(instance: Instance, routes: MutableList<Route>, binary: Boolean) {
        hasTargetConstraint = MutableList(instance.numTargets) { false }
        targetConstraintId = MutableList(instance.numTargets) { null }

        routeVariable = arrayListOf()
        val whichRoutes = MutableList(instance.numTargets) { mutableListOf<Int>() }
        val routeExpr: IloLinearNumExpr = cplex.linearNumExpr()
        val objExpr: IloLinearNumExpr = cplex.linearNumExpr()

        for (i in 0 until routes.size) {
            if (binary)
                routeVariable.add(cplex.numVar(0.0, 1.0, IloNumVarType.Bool, "z_$i"))
            else
                routeVariable.add(cplex.numVar(0.0, Double.MAX_VALUE, IloNumVarType.Float, "z_$i"))
            for (vertex in routes[i].path) {
                val target = instance.whichTarget(vertex)
                whichRoutes[target].add(i)
                if (target == instance.sourceTarget || target == instance.destinationTarget) {
                    continue
                }
                hasTargetConstraint[target] = true
            }
            routeExpr.addTerm(1.0, routeVariable[i])
            objExpr.addTerm(routes[i].score, routeVariable[i])
        }


        cplex.addMaximize(objExpr)
        constraints = arrayListOf()
        constraints.add(cplex.addLe(routeExpr, instance.numVehicles.toDouble(), "route_cover"))
        routeConstraintId = 0

        for (i in 0 until instance.numTargets) {
            if (i == instance.sourceTarget || i == instance.destinationTarget) {
                continue
            }
            if (whichRoutes[i].size == 0) {
                continue
            }
            val expr: IloLinearNumExpr = cplex.linearNumExpr()
            for (routeId in whichRoutes[i])
                expr.addTerm(1.0, routeVariable[routeId])
            targetConstraintId[i] = constraints.size
            constraints.add(cplex.addLe(expr, 1.0, "target_cover_$i"))
        }

    }

    /**
     * Function to solve the model
     * @return optimal route index
     */
    fun solve() {
        cplex.setOut(null)
        // cplex.exportModel("logs/set_cover.lp")
        if (!cplex.solve()) {
            throw OrienteeringException("Set covering problem infeasible")
        }
        logger.debug("set cover objective: ${cplex.objValue}")
        logger.debug("----- solution print start")
        for (i in 0 until routeVariable.size) {
            val solutionValue = cplex.getValue(routeVariable[i])
            if (solutionValue >= Constants.EPS) {
                logger.debug("column $i: $solutionValue")
            }
        }
        logger.debug("----- solution print end")
    }

    fun getOptimalRouteIndices(): List<Int> {
        return (0 until routeVariable.size).filter {
            cplex.getValue(routeVariable[it]) >= Constants.EPS
        }
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
     * Function to clear cplex object
     */
    fun clearModel() {
        cplex.clearModel()
    }

    /**
     * Companion object.
     */
    companion object : KLogging()
}