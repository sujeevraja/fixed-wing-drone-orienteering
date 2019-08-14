package orienteering.main

/**
 * Main function.
 */
fun main(args: Array<String>) {
    val controller = Controller()
    controller.parseArgs(args)
    controller.populateInstance()
    controller.run()
    controller.writeResults()
}
