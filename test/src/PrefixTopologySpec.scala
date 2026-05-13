package prefix.topology.library

import PrefixTree.{Leaf, Node}
import org.scalatest.funsuite.AnyFunSuite

import TestSupport.{representativeTopologies, sharedSuffixTopology}

final class PrefixTopologySpec extends AnyFunSuite {
  test("representative topologies round-trip through json") {
    Vector(1, 2, 9, 37, PrefixTopology.MaxWidth).foreach { width =>
      representativeTopologies(width).foreach { topology =>
        withClue(s"width=${topology.width}") {
          assert(PrefixTopology.fromJson(topology.toJson) == topology)
        }
      }
    }
  }

  test("enumerates every legal topology through width 6 without duplicates") {
    val expectedCounts = Seq(1, 1, 2, 8, 72, 1656)

    (1 to 6).zip(expectedCounts).foreach { case (width, expectedCount) =>
      val enumerated = PrefixTopology.enumerate(width).toVector

      withClue(s"width=${width}") {
        assert(enumerated.distinct == enumerated)
        assert(enumerated.size == expectedCount)
      }
    }
  }

  test("dyck words encode leaf and branching suffix shapes") {
    val topology = PrefixTopology(
      5,
      Vector(
        PrefixTopology.Step(0, Leaf(1)),
        PrefixTopology.Step(0, Node(Leaf(1), Leaf(2))),
        PrefixTopology.Step(0, Node(Node(Leaf(1), Leaf(2)), Leaf(3))),
        PrefixTopology.Step(1, Node(Leaf(2), Node(Leaf(3), Leaf(4))))
      )
    )

    assert(topology.dyckJson("steps").arr.map(_("shape").str).toSeq == Seq("", "UD", "UUDD", "UDUD"))
  }

  test("cell counts account for ripple and shared-cell structures") {
    Seq(1, 4, PrefixTopology.MaxWidth).foreach { width =>
      val expected = if (width == 1) (0, 0) else (width - 2, 1)

      withClue(s"width=${width}") {
        val topology = PrefixTopology.ripple(width)
        assert(cellCounts(topology) == expected)
      }
    }

    assert(cellCounts(sharedSuffixTopology) == (3, 3))

    val topology = PrefixTopology(
      width = 4,
      steps = Vector(
        PrefixTopology.Step(dependency = 0, suffix = Leaf(1)),
        PrefixTopology.Step(dependency = 0, suffix = Node(Leaf(1), Leaf(2))),
        PrefixTopology.Step(dependency = 1, suffix = Node(Leaf(2), Leaf(3)))
      )
    )

    assert(cellCounts(topology) == (3, 2))
  }

  private def cellCounts(topology: PrefixTopology): (Int, Int) =
    (topology.cells.count(_.isBlack), topology.cells.count(cell => !cell.isBlack))
}
