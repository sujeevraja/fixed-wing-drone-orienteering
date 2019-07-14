package orienteering.data

/**
 * Holds global parameters.
 *
 * @property instanceName name of file with problem data
 * @property instancePath path to folder containing file [instanceName]
 */
data class Parameters(val instanceName: String,
                      val instancePath: String)