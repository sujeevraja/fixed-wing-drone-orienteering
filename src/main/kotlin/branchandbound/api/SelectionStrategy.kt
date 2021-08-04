package branchandbound.api

/**
 * Indicator for how an unsolved node will be selected for solving and post-processing.
 */
enum class SelectionStrategy {
    /**
     * Select the node with the lowest LP objective.
     */
    BEST_BOUND,
    /**
     * Select the node with the highest LP objective.
     */
    WORST_BOUND
}