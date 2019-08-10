package orienteering.solver.actor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import mu.KLogging

abstract class ActorState<MessageType> {
    abstract suspend fun handle(message: MessageType)

    companion object : KLogging()
}

@ObsoleteCoroutinesApi
fun <MessageType> CoroutineScope.statefulActor(
    name: String,
    state: ActorState<MessageType>
) = actor<MessageType>(context = coroutineContext + CoroutineName(name)) {
    for (message in channel) {
        state.handle(message)
    }
}

