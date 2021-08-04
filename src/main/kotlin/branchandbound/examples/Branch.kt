package branchandbound.examples

/**
 * Branching function on first fractional variable.
 */
fun branch(solvedNode: KnapsackNode, idGenerator: Iterator<Long>): List<KnapsackNode> {
    val eps = 1e-6
    for ((index, value) in solvedNode.lpSolution) {
        if (value >= 1 - eps)
            continue

        val restrictions = solvedNode.restrictions
        return listOf(0, 1).map {
            KnapsackNode(
                id = idGenerator.next(),
                restrictions = restrictions.plus(Pair(index, it)),
                parentLpObjective = solvedNode.lpObjective
            )
        }
    }
    return listOf()
}
