package dubins

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class DubinsAPITests {
    @Test
    fun `shortest path test`() {
        val source = arrayOf(0.0, 0.0, 0.0)
        val destination = arrayOf(1.0, 0.0, 0.0)
        val path = DubinsCurve(source, destination, 1.0)
        path.computeShortestPath()
        assertEquals(path.getPathLength(), 1.0)
    }


    @Test
    fun `test segment lengths`() {
        val source = arrayOf(0.0, 0.0, 0.0)
        val destination = arrayOf(4.0, 0.0, 0.0)
        val path = DubinsCurve(source, destination, 1.0)
        path.computeSpecifiedPath(DubinsCurve.DubinsPathType.LSL)
        assertEquals(path.getPathLength(), 4.0)
        assertEquals(path.getSegmentLength(0), 0.0)
        assertEquals(path.getSegmentLength(1), 4.0)
        assertEquals(path.getSegmentLength(2), 0.0)
        assertEquals(path.getSegmentLength(3), null)
    }
}
