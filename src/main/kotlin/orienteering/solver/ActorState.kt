package orienteering.solver

import mu.KLogging

abstract class ActorState<MessageType> {
    abstract fun handle(message: MessageType)
    companion object: KLogging()
}