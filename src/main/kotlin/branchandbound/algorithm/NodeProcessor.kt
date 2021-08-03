package branchandbound.algorithm

import branchandbound.BranchAndBoundException
import branchandbound.api.INode
import branchandbound.api.Solution
import kotlinx.coroutines.channels.SendChannel
import mu.KotlinLogging
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * Private static logger object accessible only within this file.
 */
private val log = KotlinLogging.logger {}

class NodeProcessor(private val numSolvers: Int, comparator: Comparator<INode>) {
    private val eps = 1e-6
    /**
     * Best integer solution.
     */
    private var incumbent: INode? = null

    /**
     * Nodes created by branching and yet to be solved. This is a min-priority queue, that is, it
     * selects the "least" node in the queue based on the given comparator.
     */
    private val unsolvedNodes = PriorityQueue(comparator)

    /**
     * The number of unsolved nodes sent for solving. It is initialized with 1 as the root node
     * is created directly outside of the node processor and not by branching. This way, when we
     * receive the root node, we will correctly decrease this count to 0.
     */
    private var numSolving = 1

    /**
     * Number of branch-and-bound nodes created, initialized with 1 to count the root node created
     * outside of this coroutine.
     */
    private var numCreated = 1

    /**
     * Number of branch-and-bound nodes solved.
     */
    private var numFeasible = 0

    /**
     * Maximum number of branch-and-bound nodes solved in parallel.
     */
    private var maxParallelSolves = 0

    /**
     * This map can be used to find the upper bound at any time. Its values are the upper bounds
     * of LP solutions of all unsolved (or solving) leaf nodes that may have a fractional solution.
     * This map is modified in two cases:
     *
     * - When we branch on a node to create child nodes, the node's LP objective is an upper bound
     *   to the solution of all child nodes. This LP objective is tagged with each child node's id
     *   and placed in this mpa.
     *
     * - When we receive a solved node in [processNode], it will cease to be a leaf node as we will
     *   either prune it or branch from it. So, its id (and corresponding value) will be removed
     *   from this map.
     *
     * The main idea of correctness of this upper bound is that at any time during the evolution of
     * the branch and bound tree, the leaf nodes always describe the solution space completely. If
     * leaf nodes are known to be integral, they contribute to the lower bound. If leaf nodes may
     * be fractional (as they haven't been solved yet), upper bounds on their objectives
     * (identified as the LP objective of their immediate parents) become candidates for upper
     * bounds. The global upper bound then becomes the maximum of upper bounds of all unsolved
     * nodes.
     */
    private val leafUpperBounds = mutableMapOf<Long, Double>()

    private var lowerBound: Double = -Double.MAX_VALUE
    private var upperBound: Double = Double.MAX_VALUE

    /**
     * Process the given [solvedNode] assuming that its LP solution or infeasibility is known.
     *
     * This processing includes deciding to prune or branch the node, and updating lower/upper
     * bounds and the incumbent solution.
     */
    suspend fun processNode(
        solvedNode: INode,
        unsolvedChannel: SendChannel<INode>,
        solutionChannel: SendChannel<Solution?>,
        branch: (INode) -> List<INode>
    ) {
        log.info { "processing $solvedNode" }
        leafUpperBounds.remove(solvedNode.id)
        --numSolving

        if (solvedNode.lpFeasible)
            ++numFeasible

        solvedNode.mipObjective?.let {
            if (it >= lowerBound + eps)
                updateLowerBound(solvedNode)
        }

        if (!prune(solvedNode))
            branchFrom(solvedNode, branch)

        leafUpperBounds.values.maxOrNull()?.let {
            upperBound = it
        }
        if (lowerBound >= upperBound + eps)
            throw BranchAndBoundException("LB $lowerBound bigger than UB $upperBound")

        while (unsolvedNodes.isNotEmpty() && numSolving < numSolvers) {
            unsolvedChannel.send(unsolvedNodes.remove())
            ++numSolving
        }

        maxParallelSolves = max(maxParallelSolves, numSolving)

        if (unsolvedNodes.isEmpty() && numSolving == 0)
            sendSolution(solutionChannel)
    }

    /**
     * Prune [solvedNode] if required. Return true if pruned and false otherwise.
     *
     * Pruning occurs if [solvedNode] is infeasible or has a smaller LP objective than that of
     * [incumbent], i.e. the best known integer solution. For these cases, the node can be
     * discarded without further processing. If [solvedNode] has an integer LP solution, we don't
     * need to branch on it as we already know the best possible integer solution from the node.
     * So, it can be pruned by integrality. Here, the incumbent needs to be replaced with
     * [solvedNode] if the latter's objective is smaller than the former's.
     *
     * As the upper bound is maintained as the maximum of the LP upper bounds of all leaf nodes,
     * one can ask whether the objective of [solvedNode] should be maintained as one of the
     * candidates from which the upper bound is calculated. The answer is no. The objective of
     * [solvedNode] is in fact a lower bound that is automatically maintained because of the update
     * of [incumbent]. As any leaf node with a LP objective less than or equal to the lower bound
     * gets pruned by bound, leaves with integer solutions do not need to contribute to the upper
     * bound.
     */
    private fun prune(solvedNode: INode): Boolean {
        if (!solvedNode.lpFeasible) {
            log.debug { "Node ${solvedNode.id} pruned by infeasibility" }
            return true
        }

        if (solvedNode.lpObjective <= lowerBound - eps) {
            log.debug { "Node ${solvedNode.id} pruned by bound" }
            return true
        }

        if (solvedNode.lpIntegral) {
            log.debug { "Node ${solvedNode.id} pruned by integrality" }
            updateLowerBound(solvedNode)
            return true
        }

        if (solvedNode.mipObjective != null &&
            (solvedNode.lpObjective - solvedNode.mipObjective!!).absoluteValue <= eps) {
            log.debug {"Node ${solvedNode.id} pruned by LP objective matching MIP objective"}
            return true
        }

        return false
    }

    /**
     * Perform all tasks related to updating the global lower bound.
     *
     * If we improve [incumbent] and hence [lowerBound], we have to update upper bounds too. As all
     * unsolved nodes with LP bounds smaller than the new lower bound are going to be pruned when
     * they come back, we have to remove them as candidates from the map. This will avoid the
     * upper bound becoming smaller than the lower bound.
     */
    private fun updateLowerBound(node: INode) {
        incumbent = node
        lowerBound = node.mipObjective ?: node.lpObjective
        leafUpperBounds.entries.removeAll {
            it.value <= lowerBound
        }
    }

    /**
     * Create child nodes of [solvedNode] using the specified branching function [branch].
     *
     * As the LP objective of [solvedNode] is an upper bound of all child nodes created here, it
     * needs to be stored in the [leafUpperBounds] map to dynamically update the upper bound. Refer
     * to the documentation of [leafUpperBounds] for details about upper bound maintenance.
     */
    private fun branchFrom(solvedNode: INode, branch: (INode) -> List<INode>) {
        log.debug { "Node ${solvedNode.id} branched" }
        val children = branch(solvedNode)
        numCreated += children.size
        for (childNode in children) {
            unsolvedNodes.add(childNode)
            leafUpperBounds[childNode.id] = childNode.parentLpObjective
        }
    }

    private suspend fun sendSolution(solutionChannel: SendChannel<Solution?>) {
        log.info { "number of nodes created: $numCreated" }
        log.info { "number of feasible nodes: $numFeasible" }
        log.info { "maximum parallel solves: $maxParallelSolves" }
        log.info { "sending solution to solution channel..." }
        log.info { "Final upper bound: $upperBound"}
        solutionChannel.send(
            incumbent?.let {
                Solution(
                    objective = it.lpObjective,
                    incumbent = incumbent,
                    numCreatedNodes = numCreated,
                    numFeasibleNodes = numFeasible,
                    maxParallelSolves = maxParallelSolves
                )
            }
        )
        log.info { "sent to solution channel" }
    }
}