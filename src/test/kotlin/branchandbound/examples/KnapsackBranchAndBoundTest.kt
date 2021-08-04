package branchandbound.examples

import branchandbound.api.BranchAndBoundApi
import branchandbound.api.SelectionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnapsackBranchAndBoundTest {
    private val instances = listOf(
        KnapsackInstance(
            profits = listOf(24.0, 2.0, 20.0, 4.0),
            weights = listOf(8.0, 1.0, 5.0, 4.0),
            capacity = 9.0,
            objective = 26.0
        ),
        KnapsackInstance(
            profits = listOf(16.0, 22.0, 12.0, 8.0, 11.0, 19.0),
            weights = listOf(5.0, 7.0, 4.0, 3.0, 4.0, 6.0),
            capacity = 14.0,
            objective = 43.0
        )
    )
    private val eps = 1e-5

    @Test
    fun `tests for serial and parallel B&B solves`() {
        for (instance in instances) {
            for (numSolvers in listOf(1, 5)) {
                val idGenerator = generateSequence(0L) { it + 1 }.iterator()
                val solvers = List(numSolvers) {
                    ContinuousKnapsackSolver(instance.profits, instance.weights, instance.capacity)
                }
                val rootNode = KnapsackNode(id = idGenerator.next())
                val solution = BranchAndBoundApi.runBranchAndBound(
                    solvers, SelectionStrategy.BEST_BOUND, rootNode
                ) {
                    branch((it as KnapsackNode), idGenerator)
                }
                assertNotNull(solution)
                assertTrue(solution.numCreatedNodes > 1)
                assertTrue(solution.numFeasibleNodes <= solution.numCreatedNodes)

                assertEquals(instance.objective, solution.objective, eps)

                if (numSolvers > 1)
                    assertTrue(solution.maxParallelSolves > 1)
                else
                    assertEquals(1, solution.maxParallelSolves)
            }
        }
    }
}

private data class KnapsackInstance(
    val profits: List<Double>,
    val weights: List<Double>,
    val capacity: Double,
    val objective: Double
)
