package orienteering.data

/**
 * Route data of a vehicle.
 *
 * @param path sequence of vertices.
 * @param score sum of scores of targets visited by route.
 */
data class Route(val path: List<Int>, val score: Double)