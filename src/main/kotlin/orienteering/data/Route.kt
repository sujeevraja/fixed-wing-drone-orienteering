package orienteering.data

/**
 * Route data of a vehicle.
 *
 * @param path sequence of vertices.
 * @param score score for.
 */
data class Route(val path: List<Int>, val score: Double)