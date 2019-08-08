package orienteering.solver.actor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.selects.select
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.solver.Node
import java.util.*
import kotlin.coroutines.CoroutineContext

sealed class OpenNodesActorMessage

data class ReleaseNode(val branchingActors: List<SendChannel<ProcessOpenNode>>) :
    OpenNodesActorMessage()

data class StoreSolvedNodes(val solvedNodes: List<Node>) : OpenNodesActorMessage()

class OpenNodesActorState(
    private val instance: Instance,
    private val monitorActor: SendChannel<MonitorActorMessage>,
    private val channel: SendChannel<OpenNodesActorMessage>
) : ActorState<OpenNodesActorMessage>() {
    private var upperBound: Double = Double.MAX_VALUE
    private var lowerBound: Double = -Double.MAX_VALUE
    private val openNodes = PriorityQueue<Node>()

    override suspend fun handle(message: OpenNodesActorMessage) {
        when (message) {
            is ReleaseNode -> {
                logger.info("received ReleaseNode message")
                if (openNodes.isEmpty()) {
                    logger.info("openNode queue empty")
                    monitorActor.sendBlocking(OpenNodesEmpty)
                } else {
                    val node = openNodes.remove()
                    if (node.lpIntegral) {
                        logger.info("$node pruned by integrality")
                        channel.send(ReleaseNode(message.branchingActors))
                    } else {
                        logger.info("releasing $node for branching")
                        select<Unit> {
                            message.branchingActors.forEach {
                                it.onSend(
                                    ProcessOpenNode(
                                        node,
                                        instance,
                                        channel
                                    )
                                ) {
                                    logger.info("sent $node to branchingActor for branching")
                                }
                            }
                        }
                    }
                }
            }
            is StoreSolvedNodes -> {
                logger.info("received StoreSolvedNodes message")
                for (node in message.solvedNodes) {
                    if (!node.feasible || node.lpObjective <= lowerBound) {
                        continue
                    }
                    if (node.mipObjective >= lowerBound + Parameters.eps) {
                        lowerBound = node.mipObjective
                    }
                    openNodes.add(node)
                }
                if (openNodes.isNotEmpty()) {
                    upperBound = openNodes.peek().lpObjective
                    monitorActor.sendBlocking(OpenNodesExist)
                } else {
                    monitorActor.sendBlocking(OpenNodesEmpty)
                }
                logger.info("lower bound: $lowerBound")
                logger.info("upper bound: $upperBound")
                logger.info("number of nodes: ${openNodes.size}")
                if (upperBound - lowerBound <= Parameters.eps) {
                    monitorActor.sendBlocking(TerminateAlgorithm)
                }
            }
        }
    }
}

@ObsoleteCoroutinesApi
fun CoroutineScope.openNodesActor(
    context: CoroutineContext,
    instance: Instance,
    monitorActor: SendChannel<MonitorActorMessage>
) =
    actor<OpenNodesActorMessage>(
        context = context + CoroutineName("OpenNodesActor_")
    ) {
        val state = OpenNodesActorState(instance, monitorActor, channel)
        for (message in channel) {
            state.handle(message)
        }
    }