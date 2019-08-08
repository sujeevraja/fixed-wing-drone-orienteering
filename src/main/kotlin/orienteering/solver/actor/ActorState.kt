package orienteering.solver.actor

import mu.KLogging

abstract class ActorState<MessageType> {
    abstract suspend fun handle(message: MessageType)
    companion object: KLogging()
}