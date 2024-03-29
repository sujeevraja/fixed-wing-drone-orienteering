/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package orienteering

import org.junit.jupiter.api.Test
import orienteering.data.InstanceDto
import orienteering.main.numVertices
import kotlin.test.assertEquals

class InstanceTests {
    @Test fun `instance reading`() {
        val instance = InstanceDto(
                "p2.2.a.txt",
                "./data/Set_21_234/",
                2, 1.0).getInstance()
        assertEquals(7.5, instance.budget, "budget incorrect")
        assertEquals(21, instance.numTargets, "target count incorrect")
        assertEquals(7, instance.graph.numVertices(), "vertex count incorrect")
        assertEquals(0, instance.sourceTarget, "source target incorrect")
        assertEquals(20, instance.destinationTarget, "destination target incorrect")
    }
}
