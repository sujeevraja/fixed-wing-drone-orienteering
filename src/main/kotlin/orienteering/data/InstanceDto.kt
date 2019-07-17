package orienteering.data

import dubins.DubinsCurve
import mu.KLogging
import java.io.File
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Class that holds team orienteering problem data.
 *
 * @param name name of the instance.
 * @param path file path for the instance.
 * @param numDiscretizations number of discretizations of the heading angle at each target
 * @param rho turn radius of the vehicle
 * @constructor creates a new problem instance with given data.
 */

class InstanceDto(
    private val name: String,
    private val path: String,
    private val numDiscretizations: Int,
    private val rho: Double
) {

    /**
     * @property instance object orienteering instance object
     * @property lines lines that will be read from given set instance file
     * @property source constant for source vertex of network
     * @property destination constant for destination vertices of network
     * @property pseudoSource additional pseudo source vertex to convert problem to a single source problem
     * @property pseudoDestination additional pseudo destination vertex to convert problem to single destination problem
     * @property numVertices number of vertices in network
     * @property numTargets number of targets in problem instance
     * @property numVehicles number of vehicles
     * @property budget total budget
     * @property verticesInTarget map of target id to vertices corresponding to the target
     * @property targetOfVertex target id indexed by vertex id
     */

    private var instance: Instance
    private lateinit var lines: List<String>
    private var source = 0
    private var destination = 1
    private var pseudoSource = 0
    private var pseudoDestination = 1
    private var numVertices = 0
    private var numTargets = 0
    private var numVehicles = 0
    private var budget = 0.0
    private var verticesInTarget = mutableListOf<List<Int>>()
    private var targetOfVertex = mutableListOf<Int>()

    /**
     * Logger object.
     */
    companion object : KLogging() {
        /**
         * Builds Coords objects from a String containing coordinates and score.
         *
         * @param line string that contains 3 doubles
         * @return Target object with given coordinates and score
         */
        private fun parseCoords(line: String): Coords {
            val values: List<Double> = line.split("[ \t]".toRegex()).map {
                it.toDouble()
            }
            return Coords(values[0], values[1])
        }

        /**
         * Builds score objects from a String containing coordinates and score.
         *
         * @param line string that contains 3 doubles
         * @return score
         */
        private fun parseScore(line: String): Double {
            val values: List<Double> = line.split("[ \t]".toRegex()).map {
                it.toDouble()
            }
            return values[2]
        }
    }

    init {
        logger.info("starting initialization of instance $name...")
        collectLinesFromFile()

        val numTargetsLine = lines[0].split("[ \t]".toRegex())
        numTargets = numTargetsLine.last().toInt()

        val numVehiclesLine = lines[1].split("[ \t]".toRegex())
        numVehicles = numVehiclesLine.last().toInt()

        val budgetLine = lines[2].split("[ \t]".toRegex())
        budget = budgetLine.last().toDouble()

        val vertexCoords = lines.subList(3, lines.size).map(::parseCoords)
        source = 0
        destination = vertexCoords.size - 1

        numVertices = numTargets * numDiscretizations
        val vertices: MutableList<DubinsCoords> =
            MutableList(numVertices) { DubinsCoords(-1.0, -1.0, -1.0) }

        targetOfVertex = MutableList(numVertices) { -1 }
        verticesInTarget = MutableList(numTargets) { emptyList<Int>() }

        val h: Double = 2.0 * PI / numDiscretizations.toDouble()
        val discretizations: Array<Double> = Array(numDiscretizations) { it * h }

        for (i: Int in 0 until numTargets) {
            verticesInTarget[i] = (i * discretizations.size
                    until
                    i * discretizations.size + numDiscretizations).toList()
            for (j in 0 until numDiscretizations) {
                targetOfVertex[i * discretizations.size + j] = i
                vertices[i * discretizations.size + j] =
                    DubinsCoords(
                        vertexCoords[i].x,
                        vertexCoords[i].y,
                        discretizations[j]
                    )
            }
        }

        pseudoSource = numTargets
        pseudoDestination = numTargets + 1
        numTargets += 2

        targetOfVertex.add(pseudoSource)
        targetOfVertex.add(pseudoDestination)

        verticesInTarget.add(listOf(numVertices))
        verticesInTarget.add(listOf(numVertices + 1))
        numVertices += 2


        instance = Instance(
            budget = budget,
            source = pseudoSource,
            destination = pseudoDestination,
            numVehicles = numVehicles,
            numTargets = numTargets,
            numVertices = numVertices,
            score = buildScoreMap(),
            edges = getEdges(vertices, budget),
            targetOfVertex = targetOfVertex,
            verticesInTarget = verticesInTarget
        )

        logger.info("completed instance initialization.")
    }

    /**
     * Getter for the Instance object built DTO constructor.
     *
     * @return built instance
     */
    fun getInstance(): Instance {
        return instance
    }

    /**
     * Collects lines from problem data file and update some variables for further parsing.
     */
    private fun collectLinesFromFile() {
        lines = File(path + name).readLines()
    }

    /**
     * Builds and returns a map of each target to its score (reward).
     *
     * The score of a vertex is calculated as a function of the target score
     *
     * @return scores indexed by vertex id.
     */
    private fun buildScoreMap(): MutableList<Double> {
        val targetScore: List<Double> = lines.subList(3, lines.size).map(::parseScore)
        val vertexScore: MutableList<Double> = MutableList(numVertices) { -1.0 }
        for (i in 0 until numTargets - 2)
            for (j in 0 until numDiscretizations)
                vertexScore[i * numDiscretizations + j] = targetScore[i]

        vertexScore[numVertices - 2] = 0.0
        vertexScore[numVertices - 1] = 0.0
        return vertexScore
    }

    /**
     * Function to get the length of an edge joining two vertices
     *
     * @param c1 Coordinate object 1
     * @param c2 Coordinate object 2
     * @return euclidean distance
     */
    private fun getEdgeLength(c1: DubinsCoords, c2: DubinsCoords): Double {
        val source = arrayOf(c1.x, c1.y, c1.theta)
        val destination = arrayOf(c2.x, c2.y, c2.theta)
        val path = DubinsCurve(source, destination, rho)
        path.computeShortestPath()
        if (path.getPathLength() == null)
            logger.error("path length returned null, error in Dubins path computation")
        return path.getPathLength()!!
    }

    /**
     * Function to get the length of an edge joining two vertices
     * @param c1 Coordinate object 1
     * @param c2 Coordinate object 2
     * @return euclidean distance
     */
    private fun getEdgeLength(c1: Coords, c2: Coords): Double {
        val x1: Double = c1.x
        val y1: Double = c1.y
        val x2: Double = c2.x
        val y2: Double = c2.y
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    /**
     * Function to get the edges map
     *
     *  @param vertices list of Dubins coordinate object for each vertex
     * @param budget orienteering problem budget
     * @return edges as a map of map
     */
    private fun getEdges(
        vertices: MutableList<DubinsCoords>,
        budget: Double
    ): MutableMap<Int, MutableMap<Int, Double>> {
        val edges = mutableMapOf<Int, MutableMap<Int, Double>>()
        /* add all the edges that do not correspond to pseudoSource or pseudoDestination */
        for (i in 0 until numVertices - 2) {
            edges[i] = mutableMapOf()
            val iTargetId = targetOfVertex[i]
            if (iTargetId == destination) continue
            val otherVertices = verticesInTarget[iTargetId]
            for (j in 0 until numVertices - 2) {
                val jTargetId = targetOfVertex[j]
                if (jTargetId == source) continue
                if (j in otherVertices) continue

                if (numDiscretizations == 1) {
                    val edgeLength: Double = getEdgeLength(
                        Coords(vertices[i].x, vertices[i].y),
                        Coords(vertices[j].x, vertices[j].y)
                    )
                    if (edgeLength > budget) continue
                    edges.getValue(i)[j] = edgeLength
                } else {
                    val edgeLength: Double = getEdgeLength(vertices[i], vertices[j])
                    if (edgeLength > budget) continue
                    edges.getValue(i)[j] = edgeLength
                }
            }

            if (edges[i]!!.isEmpty())
                edges.remove(i)

        }
        /* add pseudoSource and pseudoDestination edges*/
        for (i in verticesInTarget[pseudoSource]) {
            edges[i] = mutableMapOf()
            for (j in verticesInTarget[source]) {
                edges.getValue(i)[j] = 0.0
            }
        }

        for (i in verticesInTarget[destination]) {
            edges[i] = mutableMapOf()
            for (j in verticesInTarget[pseudoDestination])
                edges.getValue(i)[j] = 0.0
        }

        return edges
    }


}