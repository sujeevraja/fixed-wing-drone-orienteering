package orienteering.data

/**
 * Class to hold all the parameters of the algorithm/instance
 *
 * @param instanceName name of the instance
 * @param instancePath path for the instance
 * @param algorithm 1 for enumeration, 2 for branch-and-cut, 3 for branch-and-price
 * @param turnRadius turn radius of the vehicle
 * @param numDiscretizations number of discretizations of the heading angle at each target
 */
data class Parameters(
        val instanceName: String,
        val instancePath: String,
        val algorithm: Int,
        val turnRadius: Double,
        val numDiscretizations: Int
)

