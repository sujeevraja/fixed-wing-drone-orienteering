package branchandbound.examples

/**
 * Branching function on first fractional variable.
 */
fun branch(solvedNode: Node, idGenerator: Iterator<Long>): List<Node> {
    val eps = 1e-6
    for ((index, value) in solvedNode.lpSolution) {
        if (value >= 1 - eps)
            continue

        val restrictions = solvedNode.restrictions
        return listOf(0, 1).map {
            Node(
                id = idGenerator.next(),
                restrictions = restrictions.plus(Pair(index, it)),
                parentLpObjective = solvedNode.lpObjective
            )
        }
    }
    return listOf()
}
