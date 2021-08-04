package branchandbound.api

/**
 * An interface for a node in a branch-and-bound tree
 *
 * @property id ID of the node, this has to be unique and increasing in terms of new node creation
 * @property parentLpObjective objective value of the parent node
 * @property lpFeasible a boolean indicating if the LP of the node is feasible
 * @property lpIntegral a boolean indicating if LP of the node is integral
 * @property lpObjective LP objective value
 * @property mipObjective (nullable) If an implementation has a local search method
 * that allows finding MIP solutions it can populate this value,
 * which will then be used to update lower bounds during branch and bound
 *
 */
interface INode {
    val id: Long
    val parentLpObjective: Double
    val lpFeasible: Boolean
    val lpIntegral: Boolean
    val lpObjective: Double
    val mipObjective: Double?
}