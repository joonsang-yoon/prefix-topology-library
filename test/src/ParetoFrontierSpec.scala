package prefix.topology.library

import org.scalatest.funsuite.AnyFunSuite

import TestSupport.{readJson, withWorkspace, writeJson}

final class ParetoFrontierSpec extends AnyFunSuite {
  test("writeWidth keeps only the non-dominated topology metrics") {
    withWorkspace("prefix-topology-library-pareto-frontier-") { workspace =>
      val topologyWidthDir = TopologyArtifacts.widthDir(workspace, 4)
      val moduleKind = PrefixModuleKind.Adder
      val points = Vector(
        ParetoFrontier.Point("ripple", 5.0, 5.0),
        ParetoFrontier.Point("variant_0", 3.0, 6.0),
        ParetoFrontier.Point("variant_1", 4.0, 4.0),
        ParetoFrontier.Point("variant_2", 6.0, 3.0),
        ParetoFrontier.Point("variant_3", 7.0, 3.0)
      )

      points.foreach { point =>
        writeJson(
          topologyWidthDir / point.source / "topology.json",
          ujson.Obj("width" -> 4, "topology" -> point.source)
        )
        writeJson(
          topologyWidthDir / point.source / moduleKind.name / "sc" / "prelayout.json",
          ujson.Obj(
            "metrics" -> ujson.Obj(
              ParetoFrontier.areaUm2 -> point.areaUm2,
              ParetoFrontier.clockPeriodNs -> point.clockPeriodNs
            )
          )
        )
      }

      ParetoFrontier.writeWidth(topologyWidthDir, width = 4, moduleKind)

      assert(
        readJson(topologyWidthDir / moduleKind.name / "pareto_frontier.json") ==
          ujson.Obj(
            "width" -> 4,
            "frontier" -> Seq(
              ("area_0", "timing_2", points(1)),
              ("area_1", "timing_1", points(2)),
              ("area_2", "timing_0", points(3))
            ).map { case (area, timing, point) =>
              ujson.Obj(
                "area" -> area,
                "timing" -> timing,
                "source" -> point.source,
                "metrics" -> ujson.Obj(
                  ParetoFrontier.areaUm2 -> point.areaUm2,
                  ParetoFrontier.clockPeriodNs -> point.clockPeriodNs
                )
              )
            }
          )
      )

      val svg = os.read(topologyWidthDir / moduleKind.name / "pareto_frontier.svg")
      assert(svg.contains("<svg"))
      assert(svg.contains("Pareto frontier for width 4"))
      assert(svg.contains(s"${ParetoFrontier.areaUm2} (minimize)"))
      Seq("area_0", "area_1", "area_2").foreach(name => assert(svg.contains(name)))
    }
  }
}
