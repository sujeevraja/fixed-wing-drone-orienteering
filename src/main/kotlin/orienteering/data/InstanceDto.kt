package orienteering.data

import mu.KLogging

/**
 * Parses problem data from a file and uses it to build an Instance object
 * for use by the solver.
 *
 * @property name name of instance
 * @property path path to instance file
 */
class InstanceDto(
    private val name: String,
    private val path: String) {
    /**
     * Problem data for use in solving.
     */
    val instance: Instance

    /**
     * Logger object.
     */
    companion object: KLogging()

    init {
        logger.info("starting initialization of instance $name...")
        instance = Instance(
                budget=0.0,
                sourceTarget = 0,
                destinationTarget = 1,
                numTargets = 0)
        logger.info("completed instance initialization.")
    }
}