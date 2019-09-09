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
    /**
     * If true, states can dominate others only if they visit a fewer number of critical targets.
     */
    var useNumTargetsForDominance = false
        private set
    /**
     * If true, dominance rules will be relaxed in early iterations of DSSR.
     */
    var relaxDominanceRules = true
        private set
    /**
     * Number of solver coroutines to use in branch-and-price (1 means sequential).
     */
    var numSolverCoroutines: Int = 8
        private set
    /**
     * Use I-DSSR if true and original DSSR (from Righini, Salani paper) if false
     */
    var useInterleavedSearch: Boolean = true
        private set
    /**
     * If the most negative reduced cost column of a DSSR search iteration is not elementary, but
     * the number of elementary routes with negative reduced cost collected during the search is
     * greater than or equal to this value, that DSSR iteration will be terminated.
     */
    const val numElementaryRoutesForExit = 10
    /**
     * Tolerance used to compare double values.
     */
    const val eps = 1e-5
    /**
     * Number of bits on system
     */
    const val numBits = 64

    fun initialize(
        instanceName: String,
        instancePath: String,
        algorithm: Int,
        turnRadius: Double,
        numDiscretizations: Int,
        numReducedCostColumns: Int,
        timeLimitInSeconds: Int,
        useInterleavedSearch: Boolean,
        useNumTargetsForDominance: Boolean,
        relaxDominanceRules: Boolean,
        numSolverCoroutines: Int
    ) {
        Parameters.instanceName = instanceName
        Parameters.instancePath = instancePath
        Parameters.algorithm = algorithm
        Parameters.turnRadius = turnRadius
        Parameters.numDiscretizations = numDiscretizations
        Parameters.numReducedCostColumns = numReducedCostColumns
        Parameters.timeLimitInSeconds = timeLimitInSeconds
        Parameters.useInterleavedSearch = useInterleavedSearch
        Parameters.useNumTargetsForDominance = useNumTargetsForDominance
        Parameters.relaxDominanceRules = relaxDominanceRules
        Parameters.numSolverCoroutines = numSolverCoroutines
    }
}

