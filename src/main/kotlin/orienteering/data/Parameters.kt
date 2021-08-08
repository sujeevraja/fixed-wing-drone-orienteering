package orienteering.data

/**
 * Singleton to hold all the parameters of the algorithm/instance
 */
data class Parameters(
    /**
     * name of instance file
     */
    val instanceName: String,
    /**
     * path to folder with instance file
     */
    val instancePath: String,
    /**
     * path to YAML file where input and solution KPIs will be written
     */
    val outputPath: String,
    /**
     * algorithm to use (1 for branch-and-cut, 2 for branch-and-price)
     */
    val algorithm: Int,
    /**
     * turn radius of the vehicle
     */
    val turnRadius: Double,
    /**
     * number of discretizations of the heading angle at each target
     */
    val numDiscretizations: Int,
    /**
     * limit on number of reduced cost columns to stop pricing problem
     */
    val maxPathsInsideSearch: Int,
    /**
     * Maximum time allowed for algorithm
     */
    val timeLimitInSeconds: Int,
    /**
     * If true, states can dominate others only if they visit a fewer number of critical targets.
     */
    val useNumTargetsForDominance: Boolean,
    /**
     * If true, dominance rules will be relaxed in early iterations of DSSR.
     */
    val relaxDominanceRules: Boolean,
    /**
     * Number of solver coroutines to use in branch-and-price (1 means sequential).
     */
    val numSolverCoroutines: Int,
    /**
     * Use I-DSSR if true and original DSSR (from Righini, Salani paper) if false
     */
    val useInterleavedSearch: Boolean,
    /**
     * If true, use best bang for buck to select labels. Use the least reduced cost otherwise.
     */
    val useBangForBuck: Boolean,
    /**
     * If the most negative reduced cost column of a DSSR search iteration is not elementary, but
     * the number of elementary routes with negative reduced cost collected during the search is
     * greater than or equal to this value, that DSSR iteration will be terminated.
     */
    val maxPathsAfterSearch: Int = 10
)
