package orienteering.main

fun main(args: Array<String>) {

    val controller = Controller()
    controller.parseArgs(args)
    controller.populateInstance()
    controller.run()
    controller.populateRunStatistics()
}
