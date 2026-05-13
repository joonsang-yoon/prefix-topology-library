package prefix.topology.library

import PrefixTree.Leaf

private[library] object TopologyLayout {
  final case class PlacedCell(id: CellRef, row: Int, isBlack: Boolean)

  def place(topology: PrefixTopology): Vector[PlacedCell] = {
    val cells = topology.cells
    val (columns, dependencies) = cellInputs(cells)
    val placedRows = Iterator
      .from(heightLowerBound(topology.width, columns, dependencies))
      .flatMap(rowsWithin(topology.width, columns, dependencies, _))
      .next()

    Vector.tabulate(topology.width)(index => PlacedCell(Leaf(index), 0, false)) ++
      cells.zip(placedRows).map { case (cell, row) => PlacedCell(cell.target, row, cell.isBlack) }
  }

  private[library] def canPlaceWithin(topology: PrefixTopology, height: Int): Boolean = {
    val (columns, dependencies) = cellInputs(topology.cells)
    height > 0 && rowsWithin(topology.width, columns, dependencies, height).isDefined
  }

  private def cellInputs(cells: Vector[PrefixTopology.Cell]): (Array[Int], Array[Array[Int]]) = {
    val indexById = cells.map(_.target).zipWithIndex.toMap
    (cells.map(_.target.high).toArray, cells.map(_.nonLeafDependencies.map(indexById).toArray).toArray)
  }

  private def heightLowerBound(width: Int, columns: Array[Int], dependencies: Array[Array[Int]]): Int = {
    val rows = Array.ofDim[Int](columns.length)
    val columnHeights = Array.fill(width)(1)
    var height = 1

    columns.indices.foreach { index =>
      val column = columns(index)
      val row = earliestRow(dependencies(index), rows)

      rows(index) = row
      columnHeights(column) += 1
      height = height.max(row + 1).max(columnHeights(column))
    }

    height
  }

  private def rowsWithin(
    width:        Int,
    columns:      Array[Int],
    dependencies: Array[Array[Int]],
    height:       Int
  ): Option[Array[Int]] = {
    val rows = Array.ofDim[Int](columns.length)
    val occupied = Array.ofDim[Boolean](width, height)

    def search(index: Int): Boolean =
      if (index == columns.length) true
      else {
        val column = columns(index)
        val usedRows = occupied(column)

        (earliestRow(dependencies(index), rows) until height).exists { row =>
          !usedRows(row) && {
            rows(index) = row
            usedRows(row) = true
            val found = search(index + 1)
            usedRows(row) = false
            found
          }
        }
      }

    Option.when(search(0))(rows)
  }

  private def earliestRow(dependencies: Array[Int], rows: Array[Int]): Int =
    dependencies.foldLeft(1)((row, dependency) => math.max(row, rows(dependency) + 1))
}
