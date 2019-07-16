package orienteering.solver

class State private constructor(
        val isForward: Boolean,
        val parent: State?,
        val vertex: Int,
        val pathLength: Double,
        val score: Double,
        val reducedCost: Double,
        val numTargetsVisited: Int,
        val numTargetVisits: MutableList<Int>
): Comparable<State> {
    override fun compareTo(other: State): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}