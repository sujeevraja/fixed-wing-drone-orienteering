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
    val instanceName: String by option(
        "-n",
        help = "instance name"
    )
        .default("p3.2.k.txt")

    /**
     * path to folder with instance file
     */
    val instancePath: String by option(
        "-p",
        help = "instance path"
    )
        .default("./data/Set_33_234/")
        .validate {
            require(File(instancePath + instanceName).exists()) {
                "file does not exist, check the file path and name"
            }
        }

    val outputPath: String by option(
        "-o",
        help = "path to file with output KPIs"
    )
        .default("./logs/results.yaml")
        .validate {
            require(it.length > 5 && it.endsWith(".yaml")) {
                "output path should end with a non-empty file name and .yaml extension"
            }
        }

    val algorithm: Int by option(
        "-a",
        help = "use branch-and-cut (1) or branch-and-price (2)"
    )
        .int().default(2).validate {
            require(it in 1 until 3) {
                "algorithm option can be in the set {1, 2}"
            }
        }

    val numReducedCostColumns: Int by option(
        "-c",
        help = "limit on number of reduced cost columns to collect during pricing"
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
        .int().default(2).validate {
            require(it >= 1) {
                "number of discretizations has to be >= 1 and integral"
            }
        }

    val useInterleavedSearch: Int by option(
        "-i",
        help = "use inter-leaved search (1) or simple search (0)"
    )
        .int().default(0).validate {
            require(it == 1 || it == 0) {
                "should be 1 or 0"
            }
        }

    val turnRadius: Double by option(
        "-r",
        help = "turn radius of the vehicle"
    )
        .double().default(1.0).validate {
            require(it > 0.0) {
                "turn radius has to be a positive number"
            }
        }

    val relaxDominanceRules: Int by option(
        "-rd",
        help = "relax dominance rules (1) or not (0)"
    ).int().default(1).validate {
        require(it == 1 || it == 0) {
            "should be 1 or 0"
        }
    }

    val numSolverCoroutines: Int by option(
        "-s",
        help = "number of concurrent solves"
    )
        .int().default(8).validate {
            require(it > 0) {
                "number should be a strictly positive integer"
            }
        }

    val timeLimitInSeconds: Int by option(
        "-t",
        help = "time limit in seconds"
    )
        .int().default(3600).validate {
            require(it > 0) {
                "time limit should be a strictly positive integer"
            }
        }

    val useNumTargetsForDominance: Int by option(
        "-u",
        help = "use number of targets in dominance check"
    )
        .int().default(0).validate {
            require(it == 1 || it == 0) {
                "should be 1 or 0"
            }
        }

    override fun run() {
        logger.debug("reading command line arguments...")
    }
}

