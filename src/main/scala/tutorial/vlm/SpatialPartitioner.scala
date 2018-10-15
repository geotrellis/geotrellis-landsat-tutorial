package tutorial.vlm

import geotrellis.spark._
import geotrellis.spark.io.index.zcurve._
import geotrellis.util._

import org.apache.spark._

class SpatialPartitioner[K: SpatialComponent](partitions: Int, bits: Int) extends Partitioner {
  def numPartitions: Int = partitions

  def getBits: Int = bits

  def getPartition(key: Any): Int = {
    val k = key.asInstanceOf[K]
    val SpatialKey(col, row) = k.getComponent[SpatialKey]
    ((Z2(col, row).z >> bits) % partitions).toInt
  }
}

object SpatialPartitioner {
  def apply[K: SpatialComponent](partitions: Int, bits: Int): SpatialPartitioner[K] =
    new SpatialPartitioner[K](partitions, bits)

  def apply[K: SpatialComponent](partitions: Int): SpatialPartitioner[K] =
    new SpatialPartitioner[K](partitions, 8)
}
