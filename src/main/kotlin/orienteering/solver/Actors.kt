package orienteering.solver

import ilog.cplex.IloCplex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.actor
import orienteering.data.Instance

/**
 * Payload data class to send the encapsulate the data that needs to sent to the actor
 */
data class Payload(val node: Node, val instance: Instance)

/**
 * Result data class
 */


/**
 *  Message types
 */
sealed class Message
class Solve(val response: CompletableDeferred<Boolean>) : Message()
object ClearCPLEX: Message()

/**
 * Envelope data class that envelopes message with payload
 */
data class Envelope(val payload: Payload?, val message: Message)

/**
 * This function launches the SolverActors
 */

@kotlinx.coroutines.ObsoleteCoroutinesApi
fun CoroutineScope.solverActor() = actor<Envelope> {
    val cplex = IloCplex()
    for (envelope in channel) {
        val payload = envelope.payload
        when (envelope.message) {
            is ClearCPLEX -> cplex.end()
            is Solve -> {
                val node = payload!!.node
                val instance = payload.instance
                node.solve(instance, cplex)
                envelope.message.response.complete(true)
            }
        }
    }
}

