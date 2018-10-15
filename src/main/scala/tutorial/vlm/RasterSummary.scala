/*
 * Copyright 2018 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tutorial.vlm

import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, CellType, CellGrid}
import geotrellis.spark._
import geotrellis.util._
import geotrellis.vector.{Extent, ProjectedExtent}
import org.apache.spark.rdd.RDD

case class RasterSummary(
  crs: CRS,
  cellType: CellType,
  cellSize: CellSize,
  extent: Extent,
  cells: Long,
  count: Long
) {
  def combine(other: RasterSummary) = {
    require(other.crs == crs, s"Can't combine LayerExtent for different CRS: $crs, ${other.crs}")
    val smallestCellSize = if (cellSize.resolution < other.cellSize.resolution) cellSize else other.cellSize
    RasterSummary(
      crs,
      cellType.union(other.cellType),
      smallestCellSize,
      extent.combine(other.extent),
      cells + other.cells,
      count + other.count
    )
  }

  def toTileLayerMetadata(layoutType: LayoutType) = {
    val (ld, zoom) = layoutType.layoutDefinitionWithZoom(crs, extent, cellSize)
    val dataBounds: Bounds[SpatialKey] = KeyBounds(ld.mapTransform.extentToBounds(extent))
    TileLayerMetadata[SpatialKey](cellType, ld, extent, crs, dataBounds) -> zoom
  }
}

object RasterSummary {
  /** Collect [[RasterSummary]] from unstructred rasters, grouped by CRS */
  def collect[V <: CellGrid](rdd: RDD[V])
   (implicit
    ev1: GetComponent[V, ProjectedExtent]
   ): Seq[RasterSummary] = {
    rdd
      .map { case grid =>
        val ProjectedExtent(extent, crs) = grid.getComponent[ProjectedExtent]
        val cellSize = CellSize(extent, grid.cols, grid.rows)
        (crs, RasterSummary(crs, grid.cellType, cellSize, extent, grid.size, 1))
      }
      .reduceByKey { _ combine _ }
      .values
      .collect
      .toSeq
  }
}
