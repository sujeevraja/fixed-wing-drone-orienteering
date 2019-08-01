package orienteering.data

/**
 * Singleton to hold all the parameters of the algorithm/instance
 */
object Parameters {
    /**
     * name of instance file
     */
    var instanceName: String = "p2.2.a.txt"
        private set
    /**
     * path to folder with instance file
     */
    var instancePath: String = "./data/Set_21_234"
        private set
    /**
     * algorithm to use (1 for branch-and-cut, 2 for branch-and-price)
     */
    var algorithm: Int = 2  // 1 for BC, 2 for BP
        private set
    /**
     * turn radius of the vehicle
     */
    var turnRadius: Double = 1.0
        private set
    /**
     * number of discretizations of the heading angle at each target
     */
    var numDiscretizations: Int = 1
        private set
    /**
     * limit on number of reduced cost columns to stop pricing problem
     */
    var numReducedCostColumns: Int = 500
        private set
    /**
     * Maximum time allowed for algorithm
     */
    var timeLimitInSeconds: Int = 3600
        private set

    fun initialize(
        instanceName: String,
        instancePath: String,
        algorithm: Int,
        turnRadius: Double,
        numDiscretizations: Int,
        numReducedCostColumns: Int,
        timeLimitInSeconds: Int
    ) {
        Parameters.instanceName = instanceName
        Parameters.instancePath = instancePath
        Parameters.algorithm = algorithm
        Parameters.turnRadius = turnRadius
        Parameters.numDiscretizations = numDiscretizations
        Parameters.numReducedCostColumns = numReducedCostColumns
        Parameters.timeLimitInSeconds = timeLimitInSeconds
    }
}

