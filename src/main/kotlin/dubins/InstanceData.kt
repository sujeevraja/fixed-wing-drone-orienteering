package dubins

/**
 * Class that holds a vertex for a set instance
 *
 * @param x x coordinate
 * @param y y coordinate
 */
data class Coords(val x: Double, val y: Double)

/**
 * Class that holds a Dubins vertex for a Dubins Orienteering instance
 *
 * @param x x coordinate
 * @param y y coordinate
 * @param theta orientation of the vehicle at the vertex
 */
data class DubinsCoords(val x: Double, val y: Double, val theta: Double)
