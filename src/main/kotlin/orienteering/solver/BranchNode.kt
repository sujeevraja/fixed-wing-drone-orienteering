package orienteering.solver

import orienteering.Constants

class BranchNode: Comparable<BranchNode> {
    private val upperBound = 0.0

    override fun compareTo(other: BranchNode): Int {
        return when {
            upperBound >= other.upperBound + Constants.EPS -> -1
            upperBound <= other.upperBound - Constants.EPS -> 1
            else -> 0
        }
    }
}