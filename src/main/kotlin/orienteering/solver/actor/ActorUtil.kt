package orienteering.solver.actor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import mu.KLogging
import kotlin.coroutines.CoroutineContext

abstract class ActorState<MessageType> {
    abstract suspend fun handle(message: MessageType)

    companion object : KLogging()
}

@ObsoleteCoroutinesApi
fun <MessageType> CoroutineScope.statefulActor(
    context: CoroutineContext,
    state: ActorState<MessageType>
) = actor<MessageType>(context = context) {
    for (message in channel) {
        state.handle(message)
    }
}

