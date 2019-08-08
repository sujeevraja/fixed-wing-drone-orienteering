package orienteering.solver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.selects.select
import orienteering.data.Instance
import orienteering.main.preProcess
import kotlin.coroutines.CoroutineContext

sealed class BranchingActorMessage
data class ProcessOpenNode(
    val node: Node,
    val instance: Instance,
    val openNodesActor: SendChannel<OpenNodesActorMessage>
) :
    BranchingActorMessage()

@ObsoleteCoroutinesApi
fun CoroutineScope.branchingActor(
    actorId: Int,
    monitorActor: SendChannel<MonitorActorMessage>,
    context: CoroutineContext,
    solverActors: List<SendChannel<Message>>
) =
    actor<BranchingActorMessage>(context = context) {
        for (message in channel) {
            when (message) {
                is ProcessOpenNode -> {
                    println("branchingActor ${Thread.currentThread().name}: processing ${message.node}")
                    monitorActor.sendBlocking(BranchingStarted(actorId))
                    val instance = message.instance
                    val childNodes = message.node.branch(instance)

                    val solvedNodes = mutableListOf<Node>()
                    val responses = mutableListOf<CompletableDeferred<Boolean>>()

                    childNodes.forEachIndexed { index, childNode ->
                        preProcess(
                            childNode.graph,
                            instance.budget,
                            instance.getVertices(instance.sourceTarget),
                            instance.getVertices(instance.destinationTarget)
                        )
                        if (childNode.isFeasible(instance)) {
                            solvedNodes.add(childNode)
                            responses.add(CompletableDeferred())

                            select<Unit> {
                                solverActors.forEach {
                                    it.onSend(
                                        Solve(index, childNode, instance, responses.last())
                                    ) {}
                                }
                            }
                        }
                    }

                    if (solvedNodes.isNotEmpty()) {
                        responses.awaitAll()
                        if (!TimeChecker.timeLimitReached() && solvedNodes.isNotEmpty()) {
                            message.openNodesActor.send(StoreSolvedNodes(solvedNodes))
                        }
                    }
                    println("branchingActor ${Thread.currentThread().name}: sending BranchingCompleted after processing ${message.node}")
                    monitorActor.sendBlocking(BranchingCompleted(actorId))
                }
            }
        }
    }