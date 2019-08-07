package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import orienteering.data.Instance
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 *  Actor messages
 */
sealed class Message

data class Solve(
    val index: Int,
    val node: Node,
    val instance: Instance,
    val response: CompletableDeferred<Boolean>
) : Message()

object ClearCPLEX : Message()

/**
 * Builds an actor that owns a IloCplex instance and can solve LP/MIP models of a given node.
 */
@ObsoleteCoroutinesApi
fun CoroutineScope.solverActor(
    actorId: Int,
    context: CoroutineContext = EmptyCoroutineContext
) =
    actor<Message>(context = context) {
        val cplex = IloCplex()
        for (message in channel) {
            when (message) {
                is ClearCPLEX -> cplex.end()
                is Solve -> {
                    println("actor $actorId received message ${message.index} in ${Thread.currentThread().name}")
                    message.node.solve(message.instance, cplex)
                    message.response.complete(true)
                }
            }
        }
    }

