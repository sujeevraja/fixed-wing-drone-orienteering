package orienteering.solver

import kotlinx.coroutines.CompletableDeferred
import orienteering.data.Instance

class NodeProcessor(
    solvedRootNode: Node,
    private val instance: Instance,
    private val numSolvers: Int,
    private val deferredSolution: CompletableDeferred<BranchAndPriceSolution>
)

