package orienteering.solver

import ilog.concert.IloLinearNumExpr
import ilog.concert.IloNumVar
import ilog.cplex.IloCplex
import mu.KLogging
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.SimpleDirectedWeightedGraph

import orienteering.data.Instance
import orienteering.numVertices

class BoundingLP(
    private val instance: Instance,
    private var cplex: IloCplex,
    private val targetDuals: List<Double> = listOf(),
    private val updatedGraph: SimpleDirectedWeightedGraph<Int, DefaultWeightedEdge> = instance.graph
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
    private lateinit var vertexVariable: ArrayList<IloNumVar>
    /**
     * profit (reduced costs)
     */
    private lateinit var profit: ArrayList<Double>

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
        for (i in 0 until updatedGraph.numVertices()) {
            val adjacentVertices: MutableList<Int> = Graphs.successorListOf(updatedGraph, i)
            if (!adjacentVertices.isEmpty()) {
                edgeVariable[i] = mutableMapOf()
                for (j in adjacentVertices)
                    edgeVariable.getValue(i)[j] = cplex.boolVar("x_${i}_$j")
            }
        }
    }

    /**
     * Function to add the indicator variable for each vertex
     */
    private fun addVertexVariables() {
        vertexVariable = arrayListOf()
        profit = arrayListOf()
        for (i in 0 until updatedGraph.numVertices()) {
            vertexVariable.add(cplex.boolVar("y_$i"))
            val score = instance.getScore(i)
            val dual = targetDuals[instance.whichTarget(i)]
            profit.add(dual - score)
        }
    }

    /**
     * Function to add length variables
     */
    private fun addLengthVariables() {
        lengthVariable = mutableMapOf()
        for (i in 0 until updatedGraph.numVertices()) {
            val adjacentVertices: MutableList<Int> = Graphs.successorListOf(updatedGraph, i)
            if (!adjacentVertices.isEmpty()) {
                lengthVariable[i] = mutableMapOf()
                for (j in adjacentVertices)
                    lengthVariable.getValue(i)[j] = cplex.numVar(0.0, Double.MAX_VALUE, "z_${i}_$j")
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
        addVertexVisitConstraints()
    }

    /**
     * Function to add degree constraints
     * for all vertices v (in degree (v) = out degree (v) = y(v))
     * for source target (out degree (t) = m)
     * for destination target (in degree (t) = m)
     */
    private fun addDegreeConstraints() {
        for (i in 0 until updatedGraph.numVertices()) {
            if (instance.whichTarget(i) == instance.sourceTarget || i == instance.destinationTarget)
                continue

            val inExpression: IloLinearNumExpr = cplex.linearNumExpr()
            val predecessors: List<Int> = Graphs.predecessorListOf(updatedGraph, i)
            for (j in predecessors)
                inExpression.addTerm(1.0, edgeVariable[j]?.get(i))
            inExpression.addTerm(-1.0, vertexVariable[i])
            cplex.addEq(inExpression, 0.0, "in_degree_$i")
            inExpression.clear()

            val outExpression: IloLinearNumExpr = cplex.linearNumExpr()
            val successors: List<Int> = Graphs.successorListOf(updatedGraph, i)
            for (j in successors)
                outExpression.addTerm(1.0, edgeVariable[i]?.get(j))
            outExpression.addTerm(-1.0, vertexVariable[i])
            cplex.addEq(outExpression, 0.0, "out_degree_$i")
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
        cplex.addEq(destinationInExpression, instance.numVehicles.toDouble(), "in_degree_destination")

    }

    /**
     * Function to add assignment constraints
     * For each target t not in multiVisitTargets (y(v in t) <= 1)
     */
    private fun addAssignmentConstraints() {
        for (i in 0 until instance.numTargets) {
            if (i == instance.sourceTarget || i == instance.destinationTarget) continue
            val vertices = instance.getVertices(i)
            val targetExpression: IloLinearNumExpr = cplex.linearNumExpr()
            for (vertex in vertices)
                targetExpression.addTerm(1.0, vertexVariable[vertex])
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

        for (i in 0 until updatedGraph.numVertices()) {
            if (instance.whichTarget(i) == instance.sourceTarget) continue
            val adjacentVertices: MutableList<Int> = Graphs.successorListOf(updatedGraph, i)
            if (!adjacentVertices.isEmpty()) {
                for (j in adjacentVertices) {
                    val constraintExpression: IloLinearNumExpr = cplex.linearNumExpr()
                    constraintExpression.addTerm(1.0, lengthVariable[i]?.get(j))
                    constraintExpression.addTerm(
                        -instance.budget,
                        edgeVariable[i]?.get(j)
                    )
                    cplex.addLe(constraintExpression, 0.0, "fuel_bound_${i}_$j")
                }
            }
        }

        for (i in 0 until updatedGraph.numVertices()) {
            val target = instance.whichTarget(i)
            if (target == instance.sourceTarget || target == instance.destinationTarget)
                continue
            val successors: List<Int> = Graphs.successorListOf(updatedGraph, i)
            val predecessors: List<Int> = Graphs.predecessorListOf(updatedGraph, i)
            val constraintExpression: IloLinearNumExpr = cplex.linearNumExpr()
            for (j in successors) {
                val edge = updatedGraph.getEdge(i, j)
                constraintExpression.addTerm(1.0, lengthVariable[i]?.get(j))
                constraintExpression.addTerm(
                    -updatedGraph.getEdgeWeight(edge),
                    edgeVariable[i]?.get(j)
                )
            }
            for (j in predecessors)
                constraintExpression.addTerm(-1.0, lengthVariable[j]?.get(i))
            cplex.addEq(constraintExpression, 0.0, "length_$i")
        }

    }

    /** Function to visit mandatory vertices
     *
     */
    private fun addVertexVisitConstraints() {
        return
    }

    /**
     * Function to add objective for the MILP model
     */
    private fun addObjective() {
        val objective: IloLinearNumExpr = cplex.linearNumExpr()
        for (i in 0 until updatedGraph.numVertices())
            objective.addTerm(profit[i], vertexVariable[i])
        cplex.addMinimize(objective)
    }

    /**
     * Function to export the model
     */
    fun exportModel() {
        cplex.exportModel("temp.lp")
    }

    fun solve() {
        cplex.setParam(IloCplex.Param.MIP.Display, 2)
        cplex.setParam(IloCplex.Param.MIP.Limits.Nodes, 0)
        if (!cplex.solve())
            throw RuntimeException("No feasible solution found")
        logger.info("LP obj. value: ${cplex.bestObjValue}")
        logger.info("best MIP obj. value: ${cplex.objValue}")
    }
}