package orienteering.solver.actor

import ilog.cplex.IloCplex
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import mu.KLogging
import orienteering.data.Instance
import orienteering.main.preProcess
import orienteering.solver.Node

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
) = statefulActor("SolverActor_${actorId}_", SolverActorState(nodeActor, instance))

typealias SolverActor = SendChannel<SolverActorMessage>


class SolverState(private val instance: Instance, private val solvedNodes: SendChannel<Node>) {
    private val cplex = IloCplex()

    suspend fun solveNode(node: Node) {
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
        logger.info("$node sending to solvedNodes channel after solving")
        solvedNodes.send(node)
        logger.info("$node sent to solvedNodes channel after solving")
    }

    companion object: KLogging()
}

@ObsoleteCoroutinesApi
fun CoroutineScope.solver(
    solverId: Int,
    instance: Instance,
    unsolvedNodes: ReceiveChannel<Node>,
    solvedNodes: SendChannel<Node>
    ) = launch(coroutineContext + CoroutineName("SolverActor_${solverId}_")) {
    val actorState = SolverState(instance, solvedNodes)
    for (unsolvedNode in unsolvedNodes) {
        actorState.solveNode(unsolvedNode)
    }
}