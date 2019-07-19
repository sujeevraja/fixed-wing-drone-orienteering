package orienteering.data

/**
 * Route data of a vehicle.
 *
 * @param path sequence of vertices.
 * @param score sum of scores of targets visited by route.
 * @param length sum of lengths of edges on route.
 * @param reducedCost sum of reduced costs of targets visited by route.
 */
data class Route(
    val path: List<Int>,
    val score: Double,
    val length: Double,
    val reducedCost: Double
) {
    override fun equals(other: Any?): Boolean {
        return other != null && (other is Route) && path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}
