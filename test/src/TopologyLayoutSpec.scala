package prefix.topology.library

import org.scalatest.funsuite.AnyFunSuite

import TestSupport.topologiesThrough

final class TopologyLayoutSpec extends AnyFunSuite {
  test("layout is globally minimal, collision free, and dependency ordered through width 6") {
    topologiesThrough(6).foreach { topology =>
      val placed = TopologyLayout.place(topology)
      val rowsById = placed.iterator.map(cell => cell.id -> cell.row).toMap
      val occupiedSlots = placed.drop(topology.width).map(cell => (cell.id.high, cell.row))
      val actualHeight = placed.iterator.map(_.row).max + 1

      withClue(s"width=${topology.width} steps=${topology.steps}") {
        assert(!TopologyLayout.canPlaceWithin(topology, actualHeight - 1))
        assert(occupiedSlots.distinct.size == occupiedSlots.size)
      }

      topology.cells.foreach { cell =>
        val targetRow = rowsById(cell.target)
        cell.nonLeafDependencies.foreach { dependency =>
          withClue(s"target=${cell.target} dependency=${dependency} width=${topology.width} steps=${topology.steps}") {
            assert(targetRow > rowsById(dependency))
          }
        }
      }
    }
  }
}
