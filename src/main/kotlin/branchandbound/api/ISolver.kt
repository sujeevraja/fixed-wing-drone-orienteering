package branchandbound.api

import branchandbound.api.INode

/**
 * Interface for a branch-and-bound solver
 */

interface ISolver {
    /**
     * This [solve] function implements the algorithm to solve the LP corresponding to that node.
     * Can be as simple as a call to an LP solver or a custom algorithm to solve the LP.
     *
     * @param unsolvedNode immutable node that needs to be solved
     * @return a new solved node object with all the parameters populated
     */
    fun solve(unsolvedNode: INode): INode
}