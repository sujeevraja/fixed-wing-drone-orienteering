package branchandbound.algorithm

import branchandbound.api.INode

/**
 * Best Bound node selection strategy
 */
class BestBoundComparator : Comparator<INode> {
    override fun compare(p0: INode, p1: INode): Int {
        var c = p0.lpObjective.compareTo(p1.lpObjective)
        if (c == 0) c = p0.id.compareTo(p1.id)
        return c
    }
}

/**
 * Worst Bound node selection strategy
 * (this to illustrate how other node selection strategies can be implemented)
 */

class WorstBoundComparator : Comparator<INode> {
    override fun compare(p0: INode, p1: INode): Int {
        var c = -p0.lpObjective.compareTo(p1.lpObjective)
        if (c == 0) c = p0.id.compareTo(p1.id)
        return c
    }
}