package orienteering.solver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlin.coroutines.CoroutineContext

sealed class MonitorActorMessage
class AlgorithmStatus: MonitorActorMessage() {
    val branchingActorAvailable = CompletableDeferred<Boolean>()
    val branchingOngoing = CompletableDeferred<Boolean>()
    val openNodesAvailable = CompletableDeferred<Boolean>()
    val optimalityReached = CompletableDeferred<Boolean>()
}

data class BranchingCompleted(val branchingActorId: Int) : MonitorActorMessage()
data class BranchingStarted(val branchingActorId: Int) : MonitorActorMessage()
object OpenNodesExist : MonitorActorMessage()
object OpenNodesEmpty : MonitorActorMessage()
object TerminateAlgorithm: MonitorActorMessage()

@ObsoleteCoroutinesApi
fun CoroutineScope.monitorActor(context: CoroutineContext, numBranchingActors: Int) =
    actor<MonitorActorMessage>(context = context) {
        val branchingActorRunning = MutableList(numBranchingActors) { false }
        var openNodesExist = false
        var optimalityReached = false

        for (message in channel) {
            when (message) {
                is AlgorithmStatus -> {
                    println("monitorActor ${Thread.currentThread().name}: branchingActorRunning $branchingActorRunning")
                    println("monitorActor ${Thread.currentThread().name}: openNodesExist $openNodesExist")
                    println("monitorActor ${Thread.currentThread().name}: optimalityReached $optimalityReached")
                    message.branchingActorAvailable.complete(branchingActorRunning.any { !it })
                    message.branchingOngoing.complete(branchingActorRunning.any { it })
                    message.openNodesAvailable.complete(openNodesExist)
                    message.optimalityReached.complete(optimalityReached)
                }
                is BranchingCompleted -> {
                    println("monitorActor ${Thread.currentThread().name}: received BranchingCompleted from ${message.branchingActorId}")
                    branchingActorRunning[message.branchingActorId] = false
                    println("monitorActor ${Thread.currentThread().name}: branchingActorRunning $branchingActorRunning")
                }
                is BranchingStarted -> {
                    println("monitorActor ${Thread.currentThread().name}: received BranchingStarted from ${message.branchingActorId}")
                    branchingActorRunning[message.branchingActorId] = true
                    println("monitorActor ${Thread.currentThread().name}: branchingActorRunning $branchingActorRunning")
                }
                is OpenNodesExist -> {
                    println("monitorActor ${Thread.currentThread().name}: received OpenNodesExist")
                    openNodesExist = true
                }
                is OpenNodesEmpty -> {
                    println("monitorActor ${Thread.currentThread().name}: received OpenNodesEmpty")
                    openNodesExist = false
                }
                is TerminateAlgorithm -> optimalityReached = true
            }
        }
    }