package orienteering.solver.actor

import ilog.cplex.IloCplex
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import orienteering.data.Instance
import orienteering.main.preProcess
import orienteering.solver.Node
import kotlin.coroutines.CoroutineContext

sealed class NewSolverActorMessage
data class SolveNode(val node: Node) : NewSolverActorMessage()
object EndCplex : NewSolverActorMessage()

class NewSolverActorState(private val newNodeActor: NewNodeActor, private val instance: Instance) :
    ActorState<NewSolverActorMessage>() {
    private val cplex = IloCplex()

    override suspend fun handle(message: NewSolverActorMessage) {
        when (message) {
            is EndCplex -> {
                cplex.end()
            }
            is SolveNode -> solveNode(message.node)
        }
    }

    private suspend fun solveNode(node: Node) {
        logger.info("$node solve starting")
        preProcess(
            node.graph,
            instance.budget,
            instance.getVertices(instance.sourceTarget),
            instance.getVertices(instance.destinationTarget)
        )
        if (!node.isFeasible(instance)) {
            logger.info("$node infeasible before solving")
            return
        }
        node.solve(instance, cplex)

        logger.info("$node sending to NewNodeActor after solving")
        newNodeActor.send(ProcessSolvedNode(node))
        logger.info("$node sent to NewNodeActor after solving")
    }
}

@ObsoleteCoroutinesApi
fun CoroutineScope.newSolverActor(
    actorId: Int,
    newNodeActor: NewNodeActor,
    instance: Instance,
    context: CoroutineContext
) =
    actor<NewSolverActorMessage>(
        context = context + CoroutineName("SolverActor_${actorId}_")
    ) {
        val state = NewSolverActorState(newNodeActor, instance)
        for (message in channel) {
            state.handle(message)
        }
    }

typealias NewSolverActor = SendChannel<NewSolverActorMessage>
