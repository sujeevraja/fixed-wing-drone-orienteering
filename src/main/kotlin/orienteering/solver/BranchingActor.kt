package orienteering.solver

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.selects.select
import orienteering.data.Instance
import orienteering.main.preProcess
import kotlin.coroutines.CoroutineContext

data class ProcessOpenNode(
    val node: Node,
    val instance: Instance,
    val openNodesActor: SendChannel<OpenNodesActorMessage>
)

class BranchingActorState(
    private val actorId: Int,
    private val monitorActor: SendChannel<MonitorActorMessage>,
    private val solverActors: List<SendChannel<SolverActorMessage>>
) : ActorState<ProcessOpenNode>() {
    override suspend fun handle(message: ProcessOpenNode) {
        logger.info("starting to process ${message.node}")
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
                        ) {
                            logger.info("sent $childNode for solving LP")
                        }
                    }
                }
            }
        }

        logger.info("completed processing ${message.node}")
        if (solvedNodes.isNotEmpty()) {
            responses.awaitAll()
            if (!TimeChecker.timeLimitReached() && solvedNodes.isNotEmpty()) {
                message.openNodesActor.send(StoreSolvedNodes(solvedNodes))
            }
        }
        logger.info("sending BranchingCompleted after processing ${message.node}")
        monitorActor.sendBlocking(BranchingCompleted(actorId))
    }
}

@ObsoleteCoroutinesApi
fun CoroutineScope.branchingActor(
    actorId: Int,
    monitorActor: SendChannel<MonitorActorMessage>,
    context: CoroutineContext,
    solverActors: List<SendChannel<SolverActorMessage>>
) =
    actor<ProcessOpenNode>(
        context = context + CoroutineName("BranchingActor$actorId")
    ) {
        val state = BranchingActorState(actorId, monitorActor, solverActors)
        for (message in channel) {
            state.handle(message)
        }
    }