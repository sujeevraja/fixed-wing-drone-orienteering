package orienteering.solver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.selects.select
import orienteering.data.Instance
import orienteering.data.Parameters
import java.util.*
import kotlin.coroutines.CoroutineContext

sealed class OpenNodesActorMessage

data class ReleaseNode(val branchingActors: List<SendChannel<BranchingActorMessage>>) :
    OpenNodesActorMessage()

data class StoreSolvedNodes(val solvedNodes: List<Node>) : OpenNodesActorMessage()

@ObsoleteCoroutinesApi
fun CoroutineScope.openNodesActor(
    context: CoroutineContext,
    instance: Instance,
    monitorActor: SendChannel<MonitorActorMessage>
) =
    actor<OpenNodesActorMessage>(context = context) {
        var upperBound: Double = Double.MAX_VALUE
        var lowerBound: Double = -Double.MAX_VALUE
        val openNodes = PriorityQueue<Node>()

        for (message in channel) {
            when (message) {
                is ReleaseNode -> {
                    println("openNodesActor ${Thread.currentThread().name}: received ReleaseNode message")
                    if (openNodes.isEmpty()) {
                        println("openNodesActor ${Thread.currentThread().name}: openNode queue empty")
                        monitorActor.sendBlocking(OpenNodesEmpty)
                    } else {
                        val node = openNodes.remove()
                        if (node.lpIntegral) {
                            println("$node pruned by integrality in open nodes actor in ${Thread.currentThread().name}")
                            channel.send(ReleaseNode(message.branchingActors))
                        } else {
                            println("openNodesActor ${Thread.currentThread().name}: releasing $node for branching")
                            select<Unit> {
                                message.branchingActors.forEach {
                                    it.onSend(ProcessOpenNode(node, instance, channel)) {
                                        println("openNodesActor ${Thread.currentThread().name}: sent $node to branchingActor for branching")
                                    }
                                }
                            }
                        }
                    }
                }
                is StoreSolvedNodes -> {
                    println("openNodesActor ${Thread.currentThread().name}: received StoreSolvedNodes message")
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
                    }
                    else {
                        monitorActor.sendBlocking(OpenNodesEmpty)
                    }
                    println("openNodesActor ${Thread.currentThread().name}: lower bound: $lowerBound")
                    println("openNodesActor ${Thread.currentThread().name}: upper bound: $upperBound")
                    println("openNodesActor ${Thread.currentThread().name}: #nodes: ${openNodes.size}")
                    if (upperBound - lowerBound <= Parameters.eps) {
                        monitorActor.sendBlocking(TerminateAlgorithm)
                    }
                }
            }
        }
    }