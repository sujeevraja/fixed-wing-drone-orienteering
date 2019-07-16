package dubins

import mu.KLogging
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.PI

/**
 * Class that implements logic for computing Dubins curves.
 * @param source coordinates and orientation of the source.
 * @param destination coordinates and orientation of the destination.
 * @param rho turn radius of the vehicle
 * @constructor creates a new problem instance with given data and has an init
 * block which populates the canonical data using the source, destination, and rho
 * @throws IllegalArgumentException if rho (turn radius) is negative
 */
class DubinsCurve(private val source: Array<Double>,
                  private val destination: Array<Double>,
                  private val rho: Double) {

    /**
     * logger object initialization
     */
    companion object: KLogging() {
        /**
         * The usual mod function does not behave correctly for angular quantities, this one does.
         * @param angleInRadians angle value in radians
         * @return angle mod 2*pi
         */
        fun mod2pi(angleInRadians: Double) : Double {
            if (angleInRadians < 0 && angleInRadians > -1e-6)
                return 0.0
            return angleInRadians - (PI * 2 * floor(angleInRadians / PI / 2))
        }
    }

    /**
     * The DubinsPathType enum class holds all the possible path types.
     * @property LSL path with segments LEFT, STRAIGHT, LEFT
     * @property LSR path with segments LEFT, STRAIGHT, RIGHT
     * @property RSL path with segments RIGHT, STRAIGHT, LEFT
     * @property RSR path with segments RIGHT, STRAIGHT, RIGHT
     * @property RLR path with segments RIGHT, LEFT, RIGHT
     * @property LRL path with segments LEFT, RIGHT, LEFT
     */
    enum class DubinsPathType {LSL, LSR, RSL, RSR, RLR, LRL}

    /**
     * DubinsPath is a nested class of the DubinsCurve class.
     * @param _pathType holds the path type
     * @param _lengths holds the length of each segment (is of size 3)
     * @constructor one constructor is a default constructor,
     * the other one initializes the Segment array and the lengths.
     */
    class DubinsPath(_pathType: DubinsPathType?, _lengths: DoubleArray?) {
        private var pathType: DubinsPathType? = _pathType
        private val lengths: DoubleArray? = _lengths

        /**
         * Function to query the length of the path
         * @return length of the Dubins' path
         */
        fun getLength() : Double? { return lengths?.sum() }

        /**
         * Function to query the segment lengths of the path
         * @return array of segment length of the Dubins' path
         */
        fun getSegmentLengths(i: Int) : Double? { return lengths?.getOrNull(i) }

        /**
         * Function to query the segment types of the path
         * @return array of segment types of the Dubins' path
         */
        fun getPathType() : DubinsPathType? { return pathType }
    }

    private var path: DubinsPath? = null

    /**
     * Data class that holds the canonical Dubins' data
     * @param alpha transformed orientation of the vehicle at the source
     * @param beta transformed orientation of the vehicle at the destination
     * @param d distance between the initial and final points normalized by the turn radius
     * @param sAlpha sin(alpha)
     * @param sBeta sin(beta)
     * @param cAlpha cos(alpha)
     * @param cBeta cos(beta)
     * @param cAlphaBeta cos(alpha-beta)
     * @param dSquare square of the normalized distance
     */
    data class CanonicalDubinsData(val alpha: Double,
                                   val beta: Double,
                                   val d: Double,
                                   val sAlpha: Double,
                                   val sBeta: Double,
                                   val cAlpha: Double,
                                   val cBeta: Double,
                                   val cAlphaBeta: Double,
                                   val dSquare: Double)

    private var canonicalData: CanonicalDubinsData

    init {
        if (rho < 0)
            throw IllegalArgumentException("turn radius has to be >= 0.0")
        val dx: Double = destination[0] - source[0]
        val dy: Double = destination[1] - source[1]
        val distance: Double = sqrt(dx * dx + dy * dy)
        val d: Double = distance / rho
        var theta = 0.0
        if (d > 0)
            theta = mod2pi(atan2(dy, dx))
        val alpha: Double = mod2pi(source[2] - theta)
        val beta: Double = mod2pi(destination[2] - theta)
        val sAlpha: Double = sin(alpha)
        val sBeta: Double = sin(beta)
        val cAlpha: Double = cos(alpha)
        val cBeta: Double = cos(beta)
        val cAlphaBeta: Double = cos(alpha - beta)
        val dSquare: Double = d*d

        canonicalData = CanonicalDubinsData(alpha, beta, d, sAlpha, sBeta, cAlpha, cBeta,
                cAlphaBeta, dSquare)
    }

    /**
     * Function to compute the shortest Dubins path between two configurations
     */
    fun computeShortestPath() {
        var bestCost: Double = Double.POSITIVE_INFINITY
        val bestSegmentLengths: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        var bestPathType: DubinsPathType? = null
        for (pathType in DubinsPathType.values()) {
            val (status: Boolean, params : DoubleArray?) = computePath(pathType)
            if (status && params != null) {
                val cost: Double = params.sum()
                if (cost < bestCost) {
                    bestCost = cost
                    bestPathType = pathType
                    bestSegmentLengths[0] = params[0]
                    bestSegmentLengths[1] = params[1]
                    bestSegmentLengths[2] = params[2]
                }
            }
        }

        if (bestCost == Double.POSITIVE_INFINITY)
            logger.error("issue with Dubins path computation; no feasible Dubins path exists")

        path = DubinsPath(bestPathType, bestSegmentLengths)
    }

    /**
     * Function to compute a specified Dubins path
     * @param pathType Dubins path type; allowed values DubinsCurve.DubinsPathType.LSL etc.
     */
    fun computeSpecifiedPath(pathType: DubinsPathType) {
        val (status, params) = computePath(pathType)
        if (status && params != null)
            path = DubinsPath(pathType, params)
    }

    /**
     * Function to compute a Dubins path of given pathType
     * @param pathType take the Dubins path type as input, pathType is of DubinsPathType enum class
     * @return returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun computePath(pathType: DubinsPathType) : Pair<Boolean, DoubleArray?> {
        val (status, params) = when(pathType) {
            DubinsPathType.LSL -> getLSLPath()
            DubinsPathType.LSR -> getLSRPath()
            DubinsPathType.RSL -> getRSLPath()
            DubinsPathType.RSR -> getRSRPath()
            DubinsPathType.LRL -> getLRLPath()
            DubinsPathType.RLR -> getRLRPath()
        }

        return Pair(status, params)
    }

    /**
     * Function to compute LSL path
     * @return  returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun getLSLPath() : Pair<Boolean, DoubleArray?> {
        val params: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        val tmp0: Double = canonicalData.d + canonicalData.sAlpha - canonicalData.sBeta
        val pSquare: Double = 2.0 + canonicalData.dSquare - (2.0 * canonicalData.cAlphaBeta) +
                (2.0 * canonicalData.d * (canonicalData.sAlpha - canonicalData.sBeta))

        if (pSquare >= 0.0) {
            val tmp1: Double = atan2(canonicalData.cBeta - canonicalData.cAlpha, tmp0)
            params[0] = mod2pi(tmp1 - canonicalData.alpha)
            params[1] = sqrt(pSquare)
            params[2] = mod2pi(canonicalData.beta - tmp1)
            return Pair(true, params)
        }

        return Pair(false, null)
    }

    /**
     * Function to compute LSR path
     * @return  returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun getLSRPath() : Pair<Boolean, DoubleArray?> {
        val params: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        val pSquare: Double = -2.0 + (canonicalData.dSquare) + (2.0 * canonicalData.cAlphaBeta) +
                (2.0 * canonicalData.d * (canonicalData.sAlpha + canonicalData.sBeta))

        if (pSquare >= 0.0) {
            val p: Double = sqrt(pSquare)
            val tmp0: Double = atan2((-canonicalData.cAlpha - canonicalData.cBeta),
                    (canonicalData.d + canonicalData.sAlpha + canonicalData.sBeta)) - atan2(-2.0, p)
            params[0] = mod2pi(tmp0 - canonicalData.alpha)
            params[1] = p
            params[2] = mod2pi(tmp0 - mod2pi(canonicalData.beta))
            return Pair(true, params)
        }

        return Pair(false, null)
    }

    /**
     * Function to compute RSL path
     * @return  returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun getRSLPath() : Pair<Boolean, DoubleArray?> {
        val params: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        val pSquare: Double = -2.0 + canonicalData.dSquare + (2.0 * canonicalData.cAlphaBeta) -
                (2.0 * canonicalData.d * (canonicalData.sAlpha + canonicalData.sBeta))

        if (pSquare >= 0.0) {
            val p: Double = sqrt(pSquare)
            val tmp0: Double = atan2((canonicalData.cAlpha + canonicalData.cBeta),
                    (canonicalData.d - canonicalData.sAlpha - canonicalData.sBeta)) - atan2(2.0, p)
            params[0] = mod2pi(canonicalData.alpha - tmp0)
            params[1] = p
            params[2] = mod2pi(canonicalData.beta - tmp0)
            return Pair(true, params)
        }

        return Pair(false, null)
    }

    /**
     * Function to compute RSR path
     * @return  returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun getRSRPath() : Pair<Boolean, DoubleArray?> {
        val params: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)

        val tmp0: Double = canonicalData.d - canonicalData.sAlpha + canonicalData.sBeta
        val pSquare: Double = 2.0 + canonicalData.dSquare - (2.0 * canonicalData.cAlphaBeta) +
                (2.0 * canonicalData.d * (canonicalData.sBeta - canonicalData.sAlpha))

        if (pSquare >= 0.0) {
            val tmp1: Double = atan2((canonicalData.cAlpha - canonicalData.cBeta), tmp0)
            params[0] = mod2pi(canonicalData.alpha - tmp1)
            params[1] = sqrt(pSquare)
            params[2] = mod2pi(tmp1 -canonicalData.beta)
            return Pair(true, params)
        }

        return Pair(false, null)
    }

    /**
     * Function to compute LRL path
     * @return  returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun getLRLPath() : Pair<Boolean, DoubleArray?> {
        val params: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        val tmp0: Double = (6.0 - canonicalData.dSquare + 2.0 * canonicalData.cAlphaBeta +
                2.0 * canonicalData.d * (canonicalData.sBeta - canonicalData.sAlpha)) / 8.0
        val phi: Double = atan2(canonicalData.cAlpha - canonicalData.cBeta,
                canonicalData.d + canonicalData.sAlpha - canonicalData.sBeta)

        if (abs(tmp0) <= 1.0) {
            val p: Double = mod2pi(2.0 * PI - acos(tmp0))
            val t: Double = mod2pi(-canonicalData.alpha - phi + p / 2.0)
            params[0] = t
            params[1] = p
            params[2] = mod2pi(mod2pi(canonicalData.beta) - canonicalData.alpha - t + mod2pi(p))
            return Pair(true, params)
        }

        return Pair(false, null)
    }

    /**
     * Function to compute RLR path
     * @return  returns a pair of Boolean and DoubleArray?. Boolean indicates the status (true - path is
     * possible, false - path not possible), DoubleArray gives the segment length array if the path is possible
     */
    private fun getRLRPath() : Pair<Boolean, DoubleArray?> {
        val params: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        val tmp0: Double = (6.0 - canonicalData.dSquare + 2.0 * canonicalData.cAlphaBeta +
                2.0 * canonicalData.d * (canonicalData.sAlpha - canonicalData.sBeta)) / 8.0
        val phi: Double  = atan2(canonicalData.cAlpha - canonicalData.cBeta,
                canonicalData.d - canonicalData.sAlpha + canonicalData.sBeta)

        if (abs(tmp0) <= 1.0) {
            val p: Double = mod2pi((2.0 * PI) - acos(tmp0))
            val t: Double = mod2pi(canonicalData.alpha - phi + mod2pi(p / 2.0))
            params[0] = t
            params[1] = p
            params[2] = mod2pi(canonicalData.alpha - canonicalData.beta - t + mod2pi(p))
            return Pair(true, params)
        }

        return Pair(false, null)
    }

    /**
     * Function to query the Dubins path length
     * @return double value (null if path is not computed)
     */
    fun getPathLength() : Double? { return path?.getLength() }

    /**
     * Function to query the Dubins segment length
     * @return double value (null if path is not computed)
     */
    fun getSegmentLength(i: Int) : Double? { return path?.getSegmentLengths(i) }

    /**
     * Function to query path type
     * @return DubinsCurve.DubinsPathType
     */
    fun getPathType() : DubinsPathType? { return path?.getPathType() }
}