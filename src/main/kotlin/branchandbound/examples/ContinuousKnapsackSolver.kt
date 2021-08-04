package branchandbound.examples

import branchandbound.api.INode
import branchandbound.api.ISolver
import kotlin.math.min

/**
 * Implementation of greedy algorithm to solve continuous knapsack problems to optimality. Given
 * [profits] (item utilities) and [weights] (item weights) and the knapsack [capacity], the
 * algorithm will find the combination of items with maximum value to place in the knapsack without
 * exceeding its capacity. It is assumed that the proportion of each selected item can vary
 * continuously in [0,1].
 */
class ContinuousKnapsackSolver(
    private val profits: List<Double>,
    private val weights: List<Double>,
    private val capacity: Double
) : ISolver {
    private val eps = 1e-6

    override fun solve(unsolvedNode: INode): INode {
        (unsolvedNode as KnapsackNode)
        val restrictions = unsolvedNode.restrictions
        val node = applyRestrictions(unsolvedNode)
        return if (node.remainingCapacity < 0.0) node.copy(lpSolved = true, lpFeasible = false)
        else runGreedyAlgorithm(node, getIndicesSortedByUtility(restrictions.keys.toSet()))
    }

    private fun applyRestrictions(node: KnapsackNode): KnapsackNode =
        node.restrictions.entries.fold(node.copy(remainingCapacity = capacity, lpIntegral = true))
        { n, entry ->
            if (entry.value == 0) n
            else n.copy(
                remainingCapacity = n.remainingCapacity - weights[entry.key],
                lpObjective = n.lpObjective + profits[entry.key]
            )
        }

    private fun getIndicesSortedByUtility(fixed: Set<Int>) =
        profits.indices.asSequence().filter { it !in fixed }.map { i ->
            Pair(i, profits[i] / weights[i])
        }.sortedByDescending { it.second }.asSequence().map { it.first }.toList()

    private fun runGreedyAlgorithm(initialNode: KnapsackNode, sortedIndices: List<Int>): KnapsackNode =
        sortedIndices.fold(initialNode) { node, i ->
            val w = weights[i]
            val proportion = min(node.remainingCapacity, w) / w
            if (proportion <= eps) node
            else node.copy(
                remainingCapacity = node.remainingCapacity - (w * proportion),
                lpObjective = node.lpObjective + (profits[i] * proportion),
                lpSolution = node.lpSolution.plus(Pair(i, proportion)),
                lpIntegral = node.lpIntegral && proportion >= 1 - eps
            )
        }.copy(lpSolved = true, lpFeasible = true)
}
