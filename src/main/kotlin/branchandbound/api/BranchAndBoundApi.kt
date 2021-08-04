package branchandbound.api

import branchandbound.algorithm.BranchAndBoundSolver

/**
 * Namespace for function used to run branch and bound.
 */
object BranchAndBoundApi {
    /**
     * Use this function to run branch and bound in parallel. It requires a list of [solvers] to
     * solve continuous relaxations, a [selectionStrategy] to determine how to select unsolved
     * nodes, a [branch] function to use custom branching strategies on solved nodes, and a
     * [rootNode] to begin the algorithm.
     *
     * If the problem is feasible, the function will return a [Solution] object with the [INode]
     * selected as the incumbent and some statistics about the run. If the problem is infeasible,
     * the function will return null.
     */
    fun runBranchAndBound(
        solvers: List<ISolver>,
        selectionStrategy: SelectionStrategy,
        rootNode: INode,
        timeLimitHit: () -> Boolean,
        branch: (INode) -> List<INode>
    ): Solution? =
        BranchAndBoundSolver(solvers, selectionStrategy, timeLimitHit, branch).solve(rootNode)
}
