package orienteering.main

import kotlinx.coroutines.ObsoleteCoroutinesApi


@ObsoleteCoroutinesApi
fun main(args: Array<String>) {

    val controller = Controller()
    controller.parseArgs(args)
    controller.populateInstance()
    controller.run()
    controller.writeResults()
}
