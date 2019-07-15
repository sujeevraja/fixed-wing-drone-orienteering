package dubins

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

data class Inputs(val word : DubinsCurve.DubinsPathType,
                  val alpha: Double,
                  val beta: Double,
                  val d: Double)

data class Outputs(val status: Int,
                   val segmentLengthOne: Double,
                   val segmentLengthTwo: Double,
                   val segmentLengthThree: Double,
                   val length: Double)

data class Parameters(val input: Inputs,
                 val output: Outputs) {
    constructor(word : DubinsCurve.DubinsPathType,
                alpha: Double,
                beta: Double,
                d: Double,
                status: Int,
                segmentLengthOne: Double,
                segmentLengthTwo: Double,
                segmentLengthThree: Double,
                length: Double) :
            this(Inputs(word, alpha, beta, d),
                    Outputs(status, segmentLengthOne, segmentLengthTwo, segmentLengthThree, length))
}

class DubinsMonteCarloTests {
    @ParameterizedTest
    @MethodSource("parameterProvider")
    fun `Monte carlo tests`(argument: Parameters) {
        val source: Array<Double> = arrayOf(0.0, 0.0, argument.input.alpha)
        val destination: Array<Double> = arrayOf(argument.input.d, 0.0, argument.input.beta)
        val rho = 1.0
        val path = DubinsCurve(source, destination, rho)
        val status = argument.output.status
        path.computeSpecifiedPath(argument.input.word)
        if (status != 0)
            assertNull(path.getPathType())
        if (status == 0) {
            val length: Double? = path.getPathLength()
            val segmentLengthOne: Double? = path.getSegmentLength(0)
            val segmentLengthTwo: Double? = path.getSegmentLength(1)
            val segmentLengthThree: Double? = path.getSegmentLength(2)

            assertNotNull(length)
            assertEquals(length, argument.output.length, 1e-2)

            assertNotNull(segmentLengthOne)
            assertEquals(segmentLengthOne, argument.output.segmentLengthOne, 1e-2)

            assertNotNull(segmentLengthTwo)
            assertEquals(segmentLengthTwo, argument.output.segmentLengthTwo, 1e-2)

            assertNotNull(segmentLengthThree)
            assertEquals(segmentLengthThree, argument.output.segmentLengthThree, 1e-2)
        }

    }

    companion object {
        @JvmStatic
        fun parameterProvider(): Stream<Parameters> = Stream.of(
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,-1.570796327,0.2,0,1.570796327,0.2,4.71238898, 6.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,-1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,-1.570796327,0.2,0,5.853485641,0.916515139,5.853485641, 12.6234864210475),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,-1.570796327,0.2,0,4.71238898,0.2,1.570796327, 6.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,-1.570796327,0.2,0,1.52077547,6.183143594,4.662368124, 12.3662871871361),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,-1.570796327,0.2,0,4.662368124,6.183143594,1.52077547, 12.3662871871361),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,-1.570796327,0.6,0,1.570796327,0.6,4.71238898, 6.88318530717959),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,-1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,-1.570796327,0.6,0,5.5900254,1.661324773,5.5900254, 12.8413755717893),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,-1.570796327,0.6,0,4.71238898,0.6,1.570796327, 6.88318530717959),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,-1.570796327,0.6,0,1.420228054,5.982048762,4.561820708, 11.9640975232524),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,-1.570796327,0.6,0,4.561820708,5.982048762,1.420228054, 11.9640975232524),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,-1.570796327,1.2,0,1.570796327,1.2,4.71238898, 7.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,-1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,-1.570796327,1.2,0,5.387520513,2.497999199,5.387520513, 13.2730402260028),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,-1.570796327,1.2,0,4.71238898,1.2,1.570796327, 7.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,-1.570796327,1.2,0,1.266103673,5.673799999,4.407696326, 11.3475999982976),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,-1.570796327,1.2,0,4.407696326,5.673799999,1.266103673, 11.3475999982976),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,-1.570796327,10.0,0,1.570796327,10.0,4.71238898, 16.2831853071796),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,-1.570796327,10.0,0,1.823476582,7.745966692,1.823476582, 11.3929198562888),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,-1.570796327,10.0,0,4.87983706,11.83215957,4.87983706, 21.5918336854080),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,-1.570796327,10.0,0,4.71238898,10.0,1.570796327, 16.2831853071796),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,-1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,-1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,0.0,0.2,0,3.816333596,1.280624847,4.037648038, 9.13460648146105),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,0.0,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,0.0,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,0.0,0.2,0,5.407127257,1.562049935,5.588447031, 12.5576242227456),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,0.0,0.2,0,1.86434639,5.480808881,2.045666164, 9.39082143557524),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,0.0,0.2,0,0.348846569,5.63139656,0.570161011, 6.55040413938824),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,0.0,0.6,0,3.522099031,1.077032961,4.331882603, 8.93101459540138),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,0.0,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,0.0,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,0.0,0.6,0,5.270988296,1.886796226,5.724585992, 12.8823705139756),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,0.0,0.6,0,1.638178962,5.300751947,2.091776658, 9.03070756624769),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,0.0,0.6,0,0.107883634,5.73793982,0.917667206, 6.76349065953723),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,0.0,1.2,0,2.944197094,1.019803903,4.90978454, 8.87378553669304),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,0.0,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,0.0,1.2,0,5.260590438,1.356465997,0.548201457, 7.16525789196279),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,0.0,1.2,0,5.139016474,2.416609195,5.856557814, 13.4121834822832),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,0.0,1.2,0,1.348722172,4.985782012,2.066263513, 8.40076769690943),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,0.0,1.2,0,5.827992744,5.767591301,1.510394884, 13.1059789286619),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,0.0,10.0,0,1.681453548,9.055385138,6.172528086, 16.9093667721119),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,0.0,10.0,0,1.682838393,8.831760866,0.112042066, 10.6266413247664),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,0.0,10.0,0,4.803804937,10.86278049,0.091415957, 15.7580013848375),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,0.0,10.0,0,4.803048868,11.04536102,6.19252542, 22.0409353047515),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,0.0,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,0.0,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,1.570796327,0.2,0,4.71238898,1.8,4.71238898, 11.2247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,1.570796327,0.2,0,4.71238898,2.2,4.71238898, 11.6247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,1.570796327,0.2,0,0.988432089,5.118456831,0.988432089, 7.09532100929441),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,1.570796327,0.2,0,1.104030988,5.349654629,1.104030988, 7.55771660458019),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,1.570796327,0.6,0,4.71238898,1.4,4.71238898, 10.8247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,1.570796327,0.6,0,4.71238898,2.6,4.71238898, 12.0247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,1.570796327,0.6,0,0.86321189,4.868016434,0.86321189, 6.59444021386796),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,1.570796327,0.6,0,1.213225223,5.5680431,1.213225223, 7.99449354618734),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,1.570796327,1.2,0,4.71238898,0.8,4.71238898, 10.2247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,1.570796327,1.2,0,4.71238898,3.2,4.71238898, 12.6247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,1.570796327,1.2,0,0.643501109,4.428594871,0.643501109, 5.71559708876293),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,1.570796327,1.2,0,1.369438406,5.880469466,1.369438406, 8.61934627760806),
                Parameters(DubinsCurve.DubinsPathType.LSL,-1.570796327,1.570796327,10.0,0,1.570796327,8.0,1.570796327, 11.1415926535898),
                Parameters(DubinsCurve.DubinsPathType.LSR,-1.570796327,1.570796327,10.0,0,1.772154248,9.797958971,4.913746901, 16.4838601198930),
                Parameters(DubinsCurve.DubinsPathType.RSL,-1.570796327,1.570796327,10.0,0,4.913746901,9.797958971,1.772154248, 16.4838601198930),
                Parameters(DubinsCurve.DubinsPathType.RSR,-1.570796327,1.570796327,10.0,0,4.71238898,12.0,4.71238898, 21.4247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,-1.570796327,1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,-1.570796327,1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,-1.570796327,0.2,0,5.588447031,1.562049935,5.407127257, 12.5576242227456),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,-1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,-1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,-1.570796327,0.2,0,4.037648038,1.280624847,3.816333596, 9.13460648146105),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,-1.570796327,0.2,0,0.570161011,5.63139656,0.348846569, 6.55040413938824),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,-1.570796327,0.2,0,2.045666164,5.480808881,1.86434639, 9.39082143557524),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,-1.570796327,0.6,0,5.724585992,1.886796226,5.270988296, 12.8823705139756),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,-1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,-1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,-1.570796327,0.6,0,4.331882603,1.077032961,3.522099031, 8.93101459540138),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,-1.570796327,0.6,0,0.917667206,5.73793982,0.107883634, 6.76349065953723),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,-1.570796327,0.6,0,2.091776658,5.300751947,1.638178962, 9.03070756624769),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,-1.570796327,1.2,0,5.856557814,2.416609195,5.139016474, 13.4121834822832),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,-1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,-1.570796327,1.2,0,0.548201457,1.356465997,5.260590438, 7.16525789196279),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,-1.570796327,1.2,0,4.90978454,1.019803903,2.944197094, 8.87378553669304),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,-1.570796327,1.2,0,1.510394884,5.767591301,5.827992744, 13.1059789286619),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,-1.570796327,1.2,0,2.066263513,4.985782012,1.348722172, 8.40076769690943),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,-1.570796327,10.0,0,6.19252542,11.04536102,4.803048868, 22.0409353047515),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,-1.570796327,10.0,0,0.112042066,8.831760866,1.682838393, 10.6266413247664),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,-1.570796327,10.0,0,0.091415957,10.86278049,4.803804937, 15.7580013848375),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,-1.570796327,10.0,0,6.172528086,9.055385138,1.681453548, 16.9093667721119),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,-1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,-1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,0.0,0.2,0,0.0,0.2,0.0, 0.200000000000000),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,0.0,0.2,0,0.0,0.2,0.0, 0.200000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,0.0,0.2,0,0.0,0.2,0.0, 0.200000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,0.0,0.2,0,0.0,0.2,0.0, 0.200000000000000),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,0.0,0.2,0,3.091571797,6.183143594,3.091571797, 12.3662871871361),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,0.0,0.2,0,3.091571797,6.183143594,3.091571797, 12.3662871871361),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,0.0,0.6,0,0.0,0.6,0.0, 0.600000000000000),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,0.0,0.6,0,0.0,0.6,0.0, 0.600000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,0.0,0.6,0,0.0,0.6,0.0, 0.600000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,0.0,0.6,0,0.0,0.6,0.0, 0.600000000000000),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,0.0,0.6,0,2.991024381,5.982048762,2.991024381, 11.9640975232524),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,0.0,0.6,0,2.991024381,5.982048762,2.991024381, 11.9640975232524),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,0.0,1.2,0,0.0,1.2,0.0, 1.20000000000000),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,0.0,1.2,0,0.0,1.2,0.0, 1.20000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,0.0,1.2,0,0.0,1.2,0.0, 1.20000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,0.0,1.2,0,0.0,1.2,0.0, 1.20000000000000),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,0.0,1.2,0,2.8369,5.673799999,2.8369, 11.3475999982976),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,0.0,1.2,0,2.8369,5.673799999,2.8369, 11.3475999982976),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,0.0,10.0,0,0.0,10.0,0.0, 10.0000000000000),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,0.0,10.0,0,0.0,10.0,0.0, 10.0000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,0.0,10.0,0,0.0,10.0,0.0, 10.0000000000000),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,0.0,10.0,0,0.0,10.0,0.0, 10.0000000000000),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,0.0,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,0.0,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,1.570796327,0.2,0,4.037648038,1.280624847,3.816333596, 9.13460648146105),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,1.570796327,0.2,0,5.588447031,1.562049935,5.407127257, 12.5576242227456),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,1.570796327,0.2,0,2.045666164,5.480808881,1.86434639, 9.39082143557524),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,1.570796327,0.2,0,0.570161011,5.63139656,0.348846569, 6.55040413938824),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,1.570796327,0.6,0,4.331882603,1.077032961,3.522099031, 8.93101459540138),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,1.570796327,0.6,0,5.724585992,1.886796226,5.270988296, 12.8823705139756),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,1.570796327,0.6,0,2.091776658,5.300751947,1.638178962, 9.03070756624769),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,1.570796327,0.6,0,0.917667206,5.73793982,0.107883634, 6.76349065953723),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,1.570796327,1.2,0,4.90978454,1.019803903,2.944197094, 8.87378553669304),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,1.570796327,1.2,0,0.548201457,1.356465997,5.260590438, 7.16525789196279),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,1.570796327,1.2,0,5.856557814,2.416609195,5.139016474, 13.4121834822832),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,1.570796327,1.2,0,2.066263513,4.985782012,1.348722172, 8.40076769690943),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,1.570796327,1.2,0,1.510394884,5.767591301,5.827992744, 13.1059789286619),
                Parameters(DubinsCurve.DubinsPathType.LSL,0.0,1.570796327,10.0,0,6.172528086,9.055385138,1.681453548, 16.9093667721119),
                Parameters(DubinsCurve.DubinsPathType.LSR,0.0,1.570796327,10.0,0,0.091415957,10.86278049,4.803804937, 15.7580013848375),
                Parameters(DubinsCurve.DubinsPathType.RSL,0.0,1.570796327,10.0,0,0.112042066,8.831760866,1.682838393, 10.6266413247664),
                Parameters(DubinsCurve.DubinsPathType.RSR,0.0,1.570796327,10.0,0,6.19252542,11.04536102,4.803048868, 22.0409353047515),
                Parameters(DubinsCurve.DubinsPathType.RLR,0.0,1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,0.0,1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,-1.570796327,0.2,0,4.71238898,2.2,4.71238898, 11.6247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,-1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,-1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,-1.570796327,0.2,0,4.71238898,1.8,4.71238898, 11.2247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,-1.570796327,0.2,0,1.104030988,5.349654629,1.104030988, 7.55771660458019),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,-1.570796327,0.2,0,0.988432089,5.118456831,0.988432089, 7.09532100929441),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,-1.570796327,0.6,0,4.71238898,2.6,4.71238898, 12.0247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,-1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,-1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,-1.570796327,0.6,0,4.71238898,1.4,4.71238898, 10.8247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,-1.570796327,0.6,0,1.213225223,5.5680431,1.213225223, 7.99449354618734),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,-1.570796327,0.6,0,0.86321189,4.868016434,0.86321189, 6.59444021386796),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,-1.570796327,1.2,0,4.71238898,3.2,4.71238898, 12.6247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,-1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,-1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,-1.570796327,1.2,0,4.71238898,0.8,4.71238898, 10.2247779607694),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,-1.570796327,1.2,0,1.369438406,5.880469466,1.369438406, 8.61934627760806),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,-1.570796327,1.2,0,0.643501109,4.428594871,0.643501109, 5.71559708876293),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,-1.570796327,10.0,0,4.71238898,12.0,4.71238898, 21.4247779607694),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,-1.570796327,10.0,0,4.913746901,9.797958971,1.772154248, 16.4838601198930),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,-1.570796327,10.0,0,1.772154248,9.797958971,4.913746901, 16.4838601198930),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,-1.570796327,10.0,0,1.570796327,8.0,1.570796327, 11.1415926535898),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,-1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,-1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,0.0,0.2,0,5.407127257,1.562049935,5.588447031, 12.5576242227456),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,0.0,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,0.0,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,0.0,0.2,0,3.816333596,1.280624847,4.037648038, 9.13460648146105),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,0.0,0.2,0,0.348846569,5.63139656,0.570161011, 6.55040413938824),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,0.0,0.2,0,1.86434639,5.480808881,2.045666164, 9.39082143557524),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,0.0,0.6,0,5.270988296,1.886796226,5.724585992, 12.8823705139756),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,0.0,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,0.0,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,0.0,0.6,0,3.522099031,1.077032961,4.331882603, 8.93101459540138),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,0.0,0.6,0,0.107883634,5.73793982,0.917667206, 6.76349065953723),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,0.0,0.6,0,1.638178962,5.300751947,2.091776658, 9.03070756624769),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,0.0,1.2,0,5.139016474,2.416609195,5.856557814, 13.4121834822832),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,0.0,1.2,0,5.260590438,1.356465997,0.548201457, 7.16525789196279),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,0.0,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,0.0,1.2,0,2.944197094,1.019803903,4.90978454, 8.87378553669304),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,0.0,1.2,0,5.827992744,5.767591301,1.510394884, 13.1059789286619),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,0.0,1.2,0,1.348722172,4.985782012,2.066263513, 8.40076769690943),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,0.0,10.0,0,4.803048868,11.04536102,6.19252542, 22.0409353047515),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,0.0,10.0,0,4.803804937,10.86278049,0.091415957, 15.7580013848375),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,0.0,10.0,0,1.682838393,8.831760866,0.112042066, 10.6266413247664),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,0.0,10.0,0,1.681453548,9.055385138,6.172528086, 16.9093667721119),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,0.0,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,0.0,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,1.570796327,0.2,0,4.71238898,0.2,1.570796327, 6.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,1.570796327,0.2,0,5.853485641,0.916515139,5.853485641, 12.6234864210475),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,1.570796327,0.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,1.570796327,0.2,0,1.570796327,0.2,4.71238898, 6.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,1.570796327,0.2,0,4.662368124,6.183143594,1.52077547, 12.3662871871361),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,1.570796327,0.2,0,1.52077547,6.183143594,4.662368124, 12.3662871871361),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,1.570796327,0.6,0,4.71238898,0.6,1.570796327, 6.88318530717959),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,1.570796327,0.6,0,5.5900254,1.661324773,5.5900254, 12.8413755717893),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,1.570796327,0.6,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,1.570796327,0.6,0,1.570796327,0.6,4.71238898, 6.88318530717959),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,1.570796327,0.6,0,4.561820708,5.982048762,1.420228054, 11.9640975232524),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,1.570796327,0.6,0,1.420228054,5.982048762,4.561820708, 11.9640975232524),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,1.570796327,1.2,0,4.71238898,1.2,1.570796327, 7.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,1.570796327,1.2,0,5.387520513,2.497999199,5.387520513, 13.2730402260028),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,1.570796327,1.2,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,1.570796327,1.2,0,1.570796327,1.2,4.71238898, 7.48318530717959),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,1.570796327,1.2,0,4.407696326,5.673799999,1.266103673, 11.3475999982976),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,1.570796327,1.2,0,1.266103673,5.673799999,4.407696326, 11.3475999982976),
                Parameters(DubinsCurve.DubinsPathType.LSL,1.570796327,1.570796327,10.0,0,4.71238898,10.0,1.570796327, 16.2831853071796),
                Parameters(DubinsCurve.DubinsPathType.LSR,1.570796327,1.570796327,10.0,0,4.87983706,11.83215957,4.87983706, 21.5918336854080),
                Parameters(DubinsCurve.DubinsPathType.RSL,1.570796327,1.570796327,10.0,0,1.823476582,7.745966692,1.823476582, 11.3929198562888),
                Parameters(DubinsCurve.DubinsPathType.RSR,1.570796327,1.570796327,10.0,0,1.570796327,10.0,4.71238898, 16.2831853071796),
                Parameters(DubinsCurve.DubinsPathType.RLR,1.570796327,1.570796327,10.0,1,0.0,0.0,0.0,0.0),
                Parameters(DubinsCurve.DubinsPathType.LRL,1.570796327,1.570796327,10.0,1,0.0,0.0,0.0,0.0)
        )
    }
}



