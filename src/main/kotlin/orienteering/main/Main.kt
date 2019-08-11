package orienteering.main

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
fun main(args: Array<String>) {

    val controller = Controller()
    controller.parseArgs(args)
    controller.populateInstance()
    controller.run()
    controller.writeResults()
}
