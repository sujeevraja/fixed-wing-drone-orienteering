package orienteering.main

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import mu.KLogging
import java.io.File


class CliParser: CliktCommand() {
    /**
     * Logger object.
     */
    companion object: KLogging()

    /**
     * File name of instance.
     */
    val instanceName: String by option("-n", help="instance name")
            .default("todo.txt")
            .validate {
                require(it.isNotEmpty()) {
                    "instance name cannot be empty"
                }
            }

    /**
     * Path to folder containing file specified by [instanceName].
     */
    val instancePath: String by option("-p", help="instance path")
            .default("todo")
            .validate {
                require(File(instancePath + instanceName).exists()) {
                    "file does not exist, check path and file name"
                }
            }

    /**
     * Runs the CLI parser.
     */
    override fun run() {
        logger.info("parsing command line arguments...")
    }
}