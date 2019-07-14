package orienteering.data

/**
 * Holds data of a single multi-vehicle orienteering instance.
 *
 * @property budget maximum path length of a vehicle
 * @property sourceTarget starting target for all vehicles
 * @property destinationTarget ending target for all vehicles
 * @property numTargets number of targets including source and destination
 */
class Instance(val budget: Double,
               val sourceTarget: Int,
               val destinationTarget: Int,
               val numTargets: Int)
