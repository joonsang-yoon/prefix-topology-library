package prefix.topology.library

import org.scalatest.funsuite.AnyFunSuite

import TestSupport.sharedSuffixTopology

final class TopologyDiagramSpec extends AnyFunSuite {
  test("rendering keeps shared suffixes deduplicated") {
    val svg = TopologyDiagram.render(sharedSuffixTopology)

    assert(svg.contains("<svg"))
    assert(svg.contains("<path "))
    assert(!svg.contains("<line "))
    Seq("[0, 1]", "[0, 2]", "[0, 3]", "[0, 4]", "[1, 2]", "[1, 3]").foreach { label =>
      assert(svg.contains(s">${label}</text>"))
    }
    assert(count(svg, ">[1, 2]</text>") == 1)
    assert(count(svg, "<circle ") == sharedSuffixTopology.width + sharedSuffixTopology.cells.size)
  }

  test("wires use quadratic Bezier control points") {
    val svg = TopologyDiagram.render(sharedSuffixTopology)
    val wireCount = sharedSuffixTopology.cells.size * 2

    assert(count(svg, "<path ") == wireCount)
    assert(
      raw"""<path d="M -?\d+ -?\d+ Q -?\d+ -?\d+ -?\d+ -?\d+" fill="none"""".r
        .findAllIn(svg)
        .size == wireCount
    )
  }

  test("cell radius grows with the widest rendered label") {
    val narrow = TopologyDiagram.render(PrefixTopology.ripple(9))
    val wide = TopologyDiagram.render(PrefixTopology.ripple(PrefixTopology.MaxWidth))

    assert(cellRadius(wide) > cellRadius(narrow))
    assert(cellRadius(wide) == expectedCellRadius("[127, 127]"))
    assert(cellRadius(wide) > expectedCellRadius("[0, 127]"))
  }

  private def cellRadius(svg: String): Int =
    raw"""<circle [^>]* r="(\d+)"""".r.findFirstMatchIn(svg).get.group(1).toInt

  private def count(svg: String, fragment: String): Int =
    svg.sliding(fragment.length).count(_ == fragment)

  private def expectedCellRadius(label: String): Int =
    math.ceil((math.hypot(label.length * 8 + 8, 16) + 2) / 2).toInt
}
