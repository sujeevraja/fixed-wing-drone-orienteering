package orienteering.solver.actor

import ilog.cplex.IloCplex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import orienteering.data.Instance
import orienteering.solver.Node
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 *  Actor messages
 */
sealed class SolverActorMessage

data class Solve(
    val index: Int,
    val node: Node,
    val instance: Instance,
    val response: CompletableDeferred<Boolean>
) : SolverActorMessage()

object ClearCPLEX : SolverActorMessage()

class SolverActorState: ActorState<SolverActorMessage>() {
    private val cplex = IloCplex()

    override suspend fun handle(message: SolverActorMessage) {
        when (message) {
            is ClearCPLEX -> {
                cplex.end()
                logger.info("CPLEX object ended")
            }
            is Solve -> {
                logger.info("starting to solve ${message.node}")
                message.node.solve(message.instance, cplex)
                logger.info("completed solving ${message.node}")

                message.response.complete(true)
            }
        }
    }
}

/**
 * Builds an actor that owns a IloCplex instance and can solve LP/MIP models of a given node.
 */
@ObsoleteCoroutinesApi
fun CoroutineScope.solverActor(
    actorId: Int,
    context: CoroutineContext = EmptyCoroutineContext
) =
    actor<SolverActorMessage>(context = context + CoroutineName("SolverActor_${actorId}_")) {
        val state = SolverActorState()
        for (message in channel) {
            state.handle(message)
        }
    }

