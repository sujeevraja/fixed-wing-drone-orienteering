package orienteering.data

import orienteering.main.format

/**
 * Route data of a vehicle.
 *
 * @param vertexPath sequence of vertices.
 * @param targetPath sequence of targets.
 * @param score sum of scores of targets visited by route.
 * @param length sum of lengths of edges on route.
 * @param reducedCost sum of reduced costs of targets visited by route.
 * @param isElementary boolean indicating whether the target path is elementary or not
 */
data class Route(
    val vertexPath: List<Int>,
    val targetPath: List<Int>,
    val score: Double,
    val length: Double,
    val reducedCost: Double,
    val isElementary: Boolean
) {
    override fun equals(other: Any?): Boolean =
        other != null && (other is Route) && vertexPath == other.vertexPath

    override fun hashCode(): Int = vertexPath.hashCode()

    override fun toString(): String = listOf(
        "vertexPath=$vertexPath",
        "targetPath=$targetPath",
        "score=${score.format(2)}",
        "length=${length.format(2)}",
        "reducedCost=${reducedCost.format(2)}"
    ).joinToString(", ", "Route(", ")")
}
