package tutorial.vlm.avro

import geotrellis.contrib.vlm.PaddedTile
import geotrellis.raster.Tile
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs.Implicits._

import org.apache.avro._
import org.apache.avro.generic._

object Implicits {
  implicit def paddedTileCodec: AvroRecordCodec[PaddedTile] = new AvroRecordCodec[PaddedTile] {
    def schema = SchemaBuilder
      .record("PaddedTile").namespace("geotrellis.contrib.vlm")
      .fields()
      .name("chunk").`type`(tileUnionCodec.schema).noDefault()
      .name("colOffset").`type`().intType().noDefault()
      .name("rowOffset").`type`().intType().noDefault()
      .name("cols").`type`().intType().noDefault()
      .name("rows").`type`().intType().noDefault()
      .endRecord()

    def encode(tile: PaddedTile, rec: GenericRecord) = {
      rec.put("chunk", tileUnionCodec.encode(tile.chunk))
      rec.put("colOffset", tile.colOffset)
      rec.put("rowOffset", tile.rowOffset)
      rec.put("cols", tile.cols)
      rec.put("rows", tile.rows)
    }

    def decode(rec: GenericRecord) = {
      val chunk     = tileUnionCodec.decode(rec[GenericRecord]("chunk"))
      val colOffset = rec[Int]("colOffset")
      val rowOffset = rec[Int]("rowOffset")
      val cols      = rec[Int]("cols")
      val rows      = rec[Int]("rows")

      PaddedTile(chunk, colOffset, rowOffset, cols, rows)
    }
  }

  implicit def extendedTileUnionCodec = new AvroUnionCodec[Tile](
    byteArrayTileCodec,
    floatArrayTileCodec,
    doubleArrayTileCodec,
    shortArrayTileCodec,
    intArrayTileCodec,
    bitArrayTileCodec,
    uByteArrayTileCodec,
    uShortArrayTileCodec,
    byteConstantTileCodec,
    floatConstantTileCodec,
    doubleConstantTileCodec,
    shortConstantTileCodec,
    intConstantTileCodec,
    bitConstantTileCodec,
    uByteConstantTileCodec,
    uShortConstantTileCodec,
    paddedTileCodec
  )

}
