package orienteering.solver.actor

import ilog.cplex.IloCplex
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import orienteering.data.Instance
import orienteering.main.preProcess
import orienteering.solver.Node
import kotlin.coroutines.CoroutineContext

sealed class SolverActorMessage
data class SolveNode(val node: Node) : SolverActorMessage()
object EndCplex : SolverActorMessage()

class SolverActorState(private val nodeActor: NodeActor, private val instance: Instance) :
    ActorState<SolverActorMessage>() {
    private val cplex = IloCplex()

    override suspend fun handle(message: SolverActorMessage) {
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

        logger.info("$node sending to NodeActor after solving")
        nodeActor.send(ProcessSolvedNode(node))
        logger.info("$node sent to NodeActor after solving")
    }
}

@ObsoleteCoroutinesApi
fun CoroutineScope.solverActor(
    actorId: Int,
    nodeActor: NodeActor,
    instance: Instance
) = statefulActor(
    coroutineContext + CoroutineName("SolverActor_${actorId}_"),
    SolverActorState(nodeActor, instance)
)

typealias SolverActor = SendChannel<SolverActorMessage>
