package orienteering.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.cplex.IloCplex
import mu.KLogging
import org.jgrapht.Graphs
import orienteering.SetGraph
import orienteering.data.Instance

class BoundingLP(
    private val instance: Instance,
    private var cplex: IloCplex,
    private val targetDuals: List<Double> = listOf(),
    private val updatedGraph: SetGraph = instance.graph,
    private val mustVisitTargets: List<Int> = listOf(),
    private val mustVisitEdges: List<Pair<Int, Int>> = listOf()
) {
    /**
     * Binary decision variables for each edge
     */
    private lateinit var edgeVariable: MutableMap<Int, MutableMap<Int, IloNumVar>>
    /**
     * lengthVariable Path length variables for each edge
     */
    private lateinit var lengthVariable: MutableMap<Int, MutableMap<Int, IloNumVar>>
    /**
     * vertexVariable indicator variable for each vertex
     */
    private lateinit var vertexVariable: MutableMap<Int, IloNumVar>
    /**
     * profit
     */
    private lateinit var profit: MutableMap<Int, Double>

    /**
     * Companion object.
     */
    companion object : KLogging()

    /**
     * Function to create the MILP model for orienteering problem
     */
    fun createModel() {
        addVariables()
        addConstraints()
        addObjective()
    }

    /**
     * Function to add the variables to the MILP model
     */
    private fun addVariables() {
        addEdgeVariables()
        addVertexVariables()
        addLengthVariables()
    }

    /**
     * Function to add edge variables
     */
    private fun addEdgeVariables() {
        edgeVariable = mutableMapOf()
        updatedGraph.vertexSet().iterator().forEach {
            val adjacentVertices: MutableList<Int> = Graphs.successorListOf(updatedGraph, it)
            if (!adjacentVertices.isEmpty()) {
                edgeVariable[it] = mutableMapOf()
                for (j in adjacentVertices)
                    edgeVariable.getValue(it)[j] = cplex.boolVar("x_${it}_$j")
            }
        }
    }

    /**
     * Function to add the indicator variable for each vertex
     */
    private fun addVertexVariables() {
        vertexVariable = mutableMapOf()
        profit = mutableMapOf()
        updatedGraph.vertexSet().iterator().forEach {
            vertexVariable[it] = cplex.boolVar("y_$it")
            profit[it] = instance.getScore(it)
        }
    }

    /**
     * Function to add length variables
     */
    private fun addLengthVariables() {
        lengthVariable = mutableMapOf()
        updatedGraph.vertexSet().iterator().forEach {
            val adjacentVertices: MutableList<Int> = Graphs.successorListOf(updatedGraph, it)
            if (!adjacentVertices.isEmpty()) {
                lengthVariable[it] = mutableMapOf()
                for (j in adjacentVertices)
                    lengthVariable.getValue(it)[j] =
                        cplex.numVar(0.0, Double.MAX_VALUE, "z_${it}_$j")
            }
        }
    }

    /**
     * Function to add the constraints to the MILP model
     */
    private fun addConstraints() {
        addDegreeConstraints()
        addAssignmentConstraints()
        addLengthConstraints()
        addEdgeVisitConstraints()
    }

    /**
     * Function to add degree constraints
     * for all vertices v (in degree (v) = out degree (v) = y(v))
     * for source target (out degree (t) = m)
     * for destination target (in degree (t) = m)
     */
    private fun addDegreeConstraints() {
        updatedGraph.vertexSet().iterator().forEach {
            if (instance.whichTarget(it) == instance.sourceTarget ||
                instance.whichTarget(it) == instance.destinationTarget
            )
                return@forEach

            val inExpression: IloLinearNumExpr = cplex.linearNumExpr()
            val predecessors: List<Int> = Graphs.predecessorListOf(updatedGraph, it)
            for (j in predecessors)
                inExpression.addTerm(1.0, edgeVariable[j]?.get(it))
            inExpression.addTerm(-1.0, vertexVariable[it])
            cplex.addEq(inExpression, 0.0, "in_degree_$it")
            inExpression.clear()

            val outExpression: IloLinearNumExpr = cplex.linearNumExpr()
            val successors: List<Int> = Graphs.successorListOf(updatedGraph, it)
            for (j in successors)
                outExpression.addTerm(1.0, edgeVariable[it]?.get(j))
            outExpression.addTerm(-1.0, vertexVariable[it])
            cplex.addEq(outExpression, 0.0, "out_degree_$it")
            outExpression.clear()
        }

        val sourceVertices = instance.getVertices(instance.sourceTarget)
        val sourceOutExpression: IloLinearNumExpr = cplex.linearNumExpr()
        for (i in sourceVertices) {
            val successors: List<Int> = Graphs.successorListOf(updatedGraph, i)
            for (j in successors)
                sourceOutExpression.addTerm(1.0, edgeVariable[i]?.get(j))
        }
        cplex.addEq(sourceOutExpression, instance.numVehicles.toDouble(), "out_degree_source")

        val destinationVertices = instance.getVertices(instance.destinationTarget)
        val destinationInExpression: IloLinearNumExpr = cplex.linearNumExpr()
        for (i in destinationVertices) {
            val predecessors: List<Int> = Graphs.predecessorListOf(updatedGraph, i)
            for (j in predecessors)
                destinationInExpression.addTerm(1.0, edgeVariable[j]?.get(i))
        }
        cplex.addEq(
            destinationInExpression,
            instance.numVehicles.toDouble(),
            "in_degree_destination"
        )

    }

    /**
     * Function to add assignment constraints
     * For each target t not in multiVisitTargets (y(v in t) <= 1)
     */
    private fun addAssignmentConstraints() {
        for (i in 0 until instance.numTargets) {
            if (i == instance.sourceTarget || i == instance.destinationTarget) continue
            val vertices = instance.getVertices(i)
            val updatedVertices: MutableList<Int> = mutableListOf()
            for (vertex in vertices) {
                if (updatedGraph.containsVertex(vertex))
                    updatedVertices.add(vertex)
            }
            if (updatedVertices.isEmpty()) continue
            val targetExpression: IloLinearNumExpr = cplex.linearNumExpr()
            for (vertex in updatedVertices) {
                if (updatedGraph.containsVertex(vertex))
                    targetExpression.addTerm(1.0, vertexVariable[vertex])
            }
            if (i in mustVisitTargets)
                cplex.addEq(targetExpression, 1.0, "assignment_$i")
            else
                cplex.addLe(targetExpression, 1.0, "assignment_$i")
            targetExpression.clear()
        }
    }

    /**
     * Function to add path length constraints
     */
    private fun addLengthConstraints() {
        val sourceVertices = instance.getVertices(instance.sourceTarget)
        for (i in sourceVertices) {
            val successors: List<Int> = Graphs.successorListOf(updatedGraph, i)
            for (j in successors) {
                val edge = updatedGraph.getEdge(i, j)
                val constraintExpression: IloLinearNumExpr = cplex.linearNumExpr()
                constraintExpression.addTerm(1.0, lengthVariable[i]?.get(j))
                constraintExpression.addTerm(
                    -updatedGraph.getEdgeWeight(edge),
                    edgeVariable[i]?.get(j)
                )
                cplex.addEq(constraintExpression, 0.0, "source_fuel_${i}_$j")
                constraintExpression.clear()
            }
        }

        updatedGraph.vertexSet().iterator().forEach {
            if (instance.whichTarget(it) == instance.sourceTarget) return@forEach
            val adjacentVertices: MutableList<Int> = Graphs.successorListOf(updatedGraph, it)
            if (!adjacentVertices.isEmpty()) {
                for (j in adjacentVertices) {
                    val constraintExpression: IloLinearNumExpr = cplex.linearNumExpr()
                    constraintExpression.addTerm(1.0, lengthVariable[it]?.get(j))
                    constraintExpression.addTerm(
                        -instance.budget,
                        edgeVariable[it]?.get(j)
                    )
                    cplex.addLe(constraintExpression, 0.0, "fuel_bound_${it}_$j")
                }
            }
        }

        updatedGraph.vertexSet().iterator().forEach {
            val target = instance.whichTarget(it)
            if (target == instance.sourceTarget || target == instance.destinationTarget)
                return@forEach
            val successors: List<Int> = Graphs.successorListOf(updatedGraph, it)
            val predecessors: List<Int> = Graphs.predecessorListOf(updatedGraph, it)
            val constraintExpression: IloLinearNumExpr = cplex.linearNumExpr()
            for (j in successors) {
                val edge = updatedGraph.getEdge(it, j)
                constraintExpression.addTerm(1.0, lengthVariable[it]?.get(j))
                constraintExpression.addTerm(
                    -updatedGraph.getEdgeWeight(edge),
                    edgeVariable[it]?.get(j)
                )
            }
            for (j in predecessors)
                constraintExpression.addTerm(-1.0, lengthVariable[j]?.get(it))
            cplex.addEq(constraintExpression, 0.0, "length_$it")
        }

    }

    /** Function to visit mandatory vertices
     *
     */
    private fun addEdgeVisitConstraints() {
        mustVisitEdges.forEach { cplex.addEq(edgeVariable[it.first]!![it.second], 1.0) }
        return
    }

    /**
     * Function to add objective for the MILP model
     */
    private fun addObjective() {
        val objective: IloLinearNumExpr = cplex.linearNumExpr()
        updatedGraph.vertexSet().iterator().forEach {
            objective.addTerm(profit[it]!!, vertexVariable[it])
        }
        cplex.addMaximize(objective)
    }

    /**
     * Function to export the model
     */
    fun exportModel() {
        cplex.exportModel("logs/branch_and_cut_model.lp")
    }

    fun solve() {
        cplex.setParam(IloCplex.Param.MIP.Display, 2)
        cplex.setParam(IloCplex.Param.MIP.Limits.Nodes, 0)
        if (!cplex.solve())
            throw RuntimeException("No feasible lpSolution found")
        logger.info("LP obj. value: ${cplex.bestObjValue}")
        logger.info("best MIP obj. value: ${cplex.objValue}")
    }
}