package orienteering.solver

import branchandbound.api.INode
import branchandbound.api.ISolver
import ilog.cplex.IloCplex
import mu.KotlinLogging
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.main.OrienteeringException
import orienteering.main.preProcess

private val log = KotlinLogging.logger {}

class NodeSolver(private val instance: Instance) : ISolver {
    private val sourceVertices = instance.getVertices(instance.sourceTarget)
    private val sinkVertices = instance.getVertices(instance.destinationTarget)
    private val cplex = IloCplex()

    override fun solve(unsolvedNode: INode): INode {
        (unsolvedNode as Node)
        preProcess(
            unsolvedNode.graph,
            instance.budget,
            sourceVertices,
            sinkVertices
        )
        log.debug { "solving node ${unsolvedNode.id}" }
        val cgSolver = ColumnGenSolver(
            instance,
            cplex,
            unsolvedNode.graph,
            unsolvedNode.mustVisitTargets,
            unsolvedNode.mustVisitTargetEdges
        )
        cgSolver.solve()
        val lpFeasible = !cgSolver.lpInfeasible
        if (!lpFeasible)
            return unsolvedNode.copy(lpSolved = true, lpFeasible = false)

        if (unsolvedNode.parentLpObjective <= cgSolver.lpObjective - Parameters.eps) {
            log.error { "best LP objective: ${unsolvedNode.parentLpObjective}" }
            log.error("node LP objective: ${cgSolver.lpObjective}")
            throw OrienteeringException("parent node LP objective smaller than child's")
        }
        return unsolvedNode.copy(
            lpSolved = true,
            lpFeasible = true,
            lpOptimal = cgSolver.lpOptimal,
            lpObjective = cgSolver.lpObjective,
            lpSolution = cgSolver.lpSolution,
            lpIntegral = cgSolver.lpSolution.all { it.second >= 1.0 - Parameters.eps },
            mipObjective = cgSolver.mipObjective,
            mipSolution = cgSolver.mipSolution,
            targetReducedCosts = cgSolver.targetReducedCosts
        )
    }
}

