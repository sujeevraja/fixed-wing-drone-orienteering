package orienteering.main

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import mu.KLogging
import java.io.File

/**
 * Class to parse the command line arguments
 *
 */
class CliParser : CliktCommand() {
    /**
     * Logger object.
     */
    companion object : KLogging()

    /**
     * name of the instance
     */
    val instanceName: String by option("-n", help = "instance name")
        .default("p3.3.l.txt")

    /**
     * path to folder with instance file
     */
    val instancePath: String by option("-p", help = "instance path")
        .default("./data/Set_33_234/")
        .validate {
            require(File(instancePath + instanceName).exists()) {
                "file does not exist, check the file path and name"
            }
        }

    val algorithm: Int by option(
        "-a",
        help = "1 for branch-and-cut, 2 for branch-and-price"
    )
        .int().default(2).validate {
            require(it in 1 until 3) {
                "algorithm option can be in the set {1, 2}"
            }
        }

    val numReducedCostColumns: Int by option(
        "-c",
        help = "limit on number of reduced columns to collect during pricing"
    )
        .int().default(500).validate {
            require(it >= 1) {
                "limit should be at least 1"
            }
        }

    val numDiscretizations: Int by option(
        "-d",
        help = "number of discretizations of the heading angle"
    )
        .int().default(1).validate {
            require(it >= 1) {
                "number of discretizations has to be >= 1 and integral"
            }
        }

    val turnRadius: Double by option("-r", help = "turn radius of the vehicle")
        .double().default(1.0).validate {
            require(it > 0.0) {
                "turn radius has to be a positive number"
            }
        }

    override fun run() {
        logger.debug("reading command line arguments...")
    }
}

