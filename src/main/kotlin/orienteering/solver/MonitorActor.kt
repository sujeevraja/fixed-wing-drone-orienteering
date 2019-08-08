package orienteering.solver

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import mu.KLogging
import kotlin.coroutines.CoroutineContext

sealed class MonitorActorMessage
class AlgorithmStatus : MonitorActorMessage() {
    val branchingActorAvailable = CompletableDeferred<Boolean>()
    val branchingOngoing = CompletableDeferred<Boolean>()
    val openNodesAvailable = CompletableDeferred<Boolean>()
    val optimalityReached = CompletableDeferred<Boolean>()
}

data class BranchingCompleted(val branchingActorId: Int) : MonitorActorMessage()
data class BranchingStarted(val branchingActorId: Int) : MonitorActorMessage()
object OpenNodesExist : MonitorActorMessage()
object OpenNodesEmpty : MonitorActorMessage()
object TerminateAlgorithm : MonitorActorMessage()

class MonitorActorState(numBranchingActors: Int) {
    private val branchingActorRunning = MutableList(numBranchingActors) { false }
    private var openNodesExist = false
    private var optimalityReached = false

    fun handle(message: MonitorActorMessage) {
        when (message) {
            is AlgorithmStatus -> provideAlgorithmStatus(message)
            is BranchingCompleted -> updateBranchingStatus(message.branchingActorId, busy = false)
            is BranchingStarted -> updateBranchingStatus(message.branchingActorId, busy = true)
            is OpenNodesExist -> markOpenNodesExist(true)
            is OpenNodesEmpty -> markOpenNodesExist(false)
            is TerminateAlgorithm -> optimalityReached = true
        }
    }

    private fun provideAlgorithmStatus(algorithmStatus: AlgorithmStatus) {
        logger.debug("branchingActorRunning: $branchingActorRunning")
        logger.debug("openNodesExist: $openNodesExist")
        logger.debug("optimalityReached: $optimalityReached")

        algorithmStatus.branchingActorAvailable.complete(branchingActorRunning.any { !it })
        algorithmStatus.branchingOngoing.complete(branchingActorRunning.any { it })
        algorithmStatus.openNodesAvailable.complete(openNodesExist)
        algorithmStatus.optimalityReached.complete(optimalityReached)
    }

    private fun updateBranchingStatus(branchingActorId: Int, busy: Boolean) {
        branchingActorRunning[branchingActorId] = busy
        logger.debug("branchingActorRunning $branchingActorRunning")
    }

    private fun markOpenNodesExist(exist: Boolean) {
        openNodesExist = exist
        logger.debug("open nodes exist: $openNodesExist")
    }

    companion object : KLogging()
}

@ObsoleteCoroutinesApi
fun CoroutineScope.monitorActor(context: CoroutineContext, numBranchingActors: Int) =
    actor<MonitorActorMessage>(context = context) {
        val state = MonitorActorState(numBranchingActors)
        for (message in channel) {
            state.handle(message)
        }
    }