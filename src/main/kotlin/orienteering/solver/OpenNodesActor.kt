package orienteering.solver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.selects.select
import orienteering.data.Instance
import orienteering.data.Parameters
import orienteering.data.Route
import java.util.*
import kotlin.coroutines.CoroutineContext

sealed class OpenNodesActorMessage

data class ReleaseNode(val branchingActors: List<SendChannel<BranchingActorMessage>>) :
    OpenNodesActorMessage()

data class StoreSolvedNodes(val solvedNodes: List<Node>) : OpenNodesActorMessage()

@ObsoleteCoroutinesApi
fun CoroutineScope.openNodesActor(
    instance: Instance,
    context: CoroutineContext,
    solving: CompletableDeferred<Boolean>
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
                        println("openNodesActor ${Thread.currentThread().name}: openNode queue empty, stopping algorithm")
                        solving.complete(true)
                    } else {
                        val node = openNodes.remove()
                        if (node.lpIntegral) {
                            println("$node pruned by integrality in open nodes actor in ${Thread.currentThread().name}")
                            channel.send(ReleaseNode(message.branchingActors))
                        } else {
                            println("open nodes actor releasing node for branching in ${Thread.currentThread().name}")
                            select<Unit> {
                                message.branchingActors.forEach {
                                    it.onSend(ProcessOpenNode(node, instance, channel)) {}
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
                        if (upperBound - lowerBound <= Parameters.eps) {
                            solving.complete(true)
                        }
                    }
                    println("openNodesActor ${Thread.currentThread().name}: lower bound: $lowerBound")
                    println("openNodesActor ${Thread.currentThread().name}: upper bound: $upperBound")
                    println("openNodesActor ${Thread.currentThread().name}: #nodes: ${openNodes.size}")
                }
            }
        }
    }