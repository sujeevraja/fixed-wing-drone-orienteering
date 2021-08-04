package branchandbound.examples

import branchandbound.api.INode

/**
 * Implements the node interface with additional fields
 */
data class KnapsackNode(
    override val id: Long,
    /**
     * Keys are variable ids. Values are integers to which variables are locked.
     */
    val restrictions: Map<Int, Int> = mapOf(),
    override val parentLpObjective: Double = Double.MAX_VALUE,
    val lpSolved: Boolean = false,
    override val lpFeasible: Boolean = false,
    override val lpIntegral: Boolean = false,
    override val lpObjective: Double = 0.0,
    val lpSolution: Map<Int, Double> = mapOf(),
    val remainingCapacity: Double = 0.0
) : INode {
    override val mipObjective: Double? = null

    override fun toString(): String {
        val clauses = mutableListOf("id=$id")
        if (parentLpObjective == Double.MAX_VALUE)
            clauses.add("parentLp=$parentLpObjective")
        else
            clauses.add("parentLp=%.2f".format(parentLpObjective))
        if (!lpSolved)
            clauses.add("unsolved")
        else if (lpFeasible) {
            clauses.add("lp=%.2f".format(lpObjective))
            clauses.add(if (lpIntegral) "integral" else "fractional")
        } else clauses.add("infeasible")

        return clauses.joinToString(",", "Node(", ")")
    }
}
