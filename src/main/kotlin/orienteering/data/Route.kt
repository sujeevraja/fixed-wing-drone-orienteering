package orienteering.data

/**
 * Route data of a vehicle.
 *
 * @param path list of sequence of vertices.
 * @param score score for each route.
 */
data class Route(val path: List<List<Int>>, val score: List<Double>)