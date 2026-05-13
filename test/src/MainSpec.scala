package prefix.topology.library

import PrefixTree.{Leaf, Node}
import org.scalatest.funsuite.AnyFunSuite

import TestSupport.{readJson, withWorkspace, writeJson}

final class MainSpec extends AnyFunSuite {
  test("inspect reads topology.json and prints canonical JSON plus cell counts") {
    withWorkspace("prefix-topology-library-main-inspect-") { workspace =>
      val topology = PrefixTopology(
        width = 3,
        steps = Vector(
          PrefixTopology.Step(dependency = 0, suffix = Leaf(1)),
          PrefixTopology.Step(dependency = 0, suffix = Node(Leaf(1), Leaf(2)))
        )
      )
      val topologyFile = workspace / "topology.json"

      writeJson(topologyFile, topology.toJson)

      val stdout = runMain(workspace)("inspect", topologyFile.toString)
      val json = ujson.read(stdout)

      assert(
        json == ujson.Obj(
          "topology" -> topology.toJson,
          "cellCounts" -> ujson.Obj(
            "blackCellCount" -> topology.cells.count(_.isBlack),
            "grayCellCount" -> topology.cells.count(cell => !cell.isBlack)
          )
        )
      )
    }
  }

  test("topologies generates all supported widths with variants only in requested interval") {
    withWorkspace("prefix-topology-library-main-update-interval-") { workspace =>
      val barrierDir = workspace / "barrier"
      val env = fakePrefixTopologyLibraryEnv(workspace) +
        ("PREFIX_TOPOLOGY_LIBRARY_FAKE_RUNNER_BARRIER_DIR" -> barrierDir.toString)
      val topologies = workspace / "generated"

      writeText(TopologyArtifacts.widthDir(topologies, 1) / "variant_0" / "topology.json", "stale narrow\n")
      writeText(TopologyArtifacts.widthDir(topologies, 4) / "variant_0" / "topology.json", "stale wide\n")

      assert(
        runMain(workspace, env)("topologies", topologies.toString, "2", "3", "4") ==
          expectedTopologyCount(minWidth = 2, maxWidth = 3).toString
      )
      assertGeneratedTopologies(topologies, minWidth = 2, maxWidth = 3)
      assert(os.list(barrierDir).size >= 2)
    }
  }

  test("frontier copies frontier topologies for the requested interval") {
    withWorkspace("prefix-topology-library-main-frontier-dir-") { workspace =>
      val topologies = workspace / "generated" / "topologies"
      val frontier = workspace / "pareto_frontier_topologies"

      writeSourceWidthTopologies(
        topologies,
        width = 2,
        frontier = Seq("ripple"),
        other = Seq("variant_0")
      )
      writeSourceWidthTopologies(topologies, width = 3, frontier = Seq("ripple", "variant_0"))

      assert(
        runMain(workspace)(
          "frontier",
          topologies.toString,
          frontier.toString,
          "2",
          "3"
        ) == "6"
      )

      PrefixModuleKind.all.foreach { moduleKind =>
        val moduleFrontierWidth2 = frontier / moduleKind.name / "width_2"
        val moduleFrontierWidth3 = frontier / moduleKind.name / "width_3"

        assert(readJson(moduleFrontierWidth2 / "pareto_frontier.json") == paretoJson(2, Seq("ripple")))
        assert(
          os.read(moduleFrontierWidth2 / "pareto_frontier.svg") == s"Pareto frontier for ${moduleKind.name} width 2\n"
        )
        assert(os.read(moduleFrontierWidth2 / "area_0" / "topology.json") == "source width 2 ripple\n")
        assert(os.read(moduleFrontierWidth2 / "area_0" / "rtl" / "filelist.f") == s"${moduleKind.name} ripple rtl\n")
        assert(
          readJson(moduleFrontierWidth2 / "area_0" / "sc" / "prelayout.json") == ujson.Obj(
            "rtlFiles" -> ujson.Arr(s"rtl/${moduleKind.name}.sv")
          )
        )
        assert(!os.exists(moduleFrontierWidth2 / "ripple"))
        assert(!os.exists(moduleFrontierWidth2 / "variant_0"))

        assert(readJson(moduleFrontierWidth3 / "pareto_frontier.json") == paretoJson(3, Seq("ripple", "variant_0")))
        assert(os.read(moduleFrontierWidth3 / "area_0" / "topology.json") == "source width 3 ripple\n")
        assert(os.read(moduleFrontierWidth3 / "area_1" / "topology.json") == "source width 3 variant_0\n")
        assert(!os.exists(moduleFrontierWidth3 / "ripple"))
        assert(!os.exists(moduleFrontierWidth3 / "variant_0"))
      }
    }
  }

  private def runMain(workspace: os.Path, extraEnv: Map[String, String] = Map.empty)(args: String*): String =
    Main.run(args, cwd = workspace, env = sys.env ++ extraEnv)

  private def assertGeneratedTopologies(topologies: os.Path, minWidth: Int, maxWidth: Int): Unit = {
    (1 to PrefixTopology.MaxWidth).foreach { width =>
      val topologyWidthDir = TopologyArtifacts.widthDir(topologies, width)
      val expectedTopologies = expectedTopologiesForWidth(width, minWidth, maxWidth)

      assert(sourceNames(topologyWidthDir) == expectedTopologies.map(_._2).toSet)

      expectedTopologies.foreach { case (topology, topologyName) =>
        val topologyDir = topologyWidthDir / topologyName

        assert(readJson(topologyDir / "topology.json") == topology.toJson)
        assert(readJson(topologyDir / "dyck.json") == topology.dyckJson)
        assert(os.exists(topologyDir / "topology.svg"))
        PrefixModuleKind.all.foreach { moduleKind =>
          Seq(
            topologyDir / moduleKind.name / "rtl" / s"${moduleKind.name}.sv",
            topologyDir / moduleKind.name / "rtl" / "filelist.f",
            topologyDir / moduleKind.name / "sc" / "prelayout.json"
          ).foreach(path => assert(os.exists(path)))
        }
      }

      PrefixModuleKind.all.foreach { moduleKind =>
        assert(readJson(topologyWidthDir / moduleKind.name / "pareto_frontier.json") == expectedPareto(width))
        assert(
          os.read(topologyWidthDir / moduleKind.name / "pareto_frontier.svg")
            .contains(s"Pareto frontier for width $width")
        )
      }
    }

    val rippleDir = TopologyArtifacts.widthDir(topologies, 1) / TopologyArtifacts.topologyName(0)

    PrefixModuleKind.all.foreach { moduleKind =>
      assertPrelayout(readJson(rippleDir / moduleKind.name / "sc" / "prelayout.json"), moduleKind)
    }
  }

  private def expectedTopologiesForWidth(
    width:    Int,
    minWidth: Int,
    maxWidth: Int
  ): Vector[(PrefixTopology, String)] =
    if (width >= minWidth && width <= maxWidth)
      PrefixTopology
        .enumerate(width)
        .zipWithIndex
        .map { case (topology, localIndex) =>
          topology -> TopologyArtifacts.topologyName(localIndex)
        }
        .toVector
    else Vector(PrefixTopology.ripple(width) -> "ripple")

  private def sourceNames(topologyWidthDir: os.Path): Set[String] =
    os.list(topologyWidthDir).filter(path => os.exists(path / "topology.json")).map(_.last).toSet

  private def expectedTopologyCount(minWidth: Int, maxWidth: Int): Int =
    PrefixTopology.MaxWidth + (minWidth to maxWidth).iterator.map { width =>
      PrefixTopology.enumerate(width).size - 1
    }.sum

  private def expectedPareto(width: Int): ujson.Obj =
    ParetoFrontier.json(
      width,
      Vector(
        ParetoFrontier.Point(
          "ripple",
          areaUm2 = 43.358,
          clockPeriodNs = 0.09047690630572279
        )
      )
    )

  private def writeSourceWidthTopologies(
    topologies: os.Path,
    width:      Int,
    frontier:   Seq[String],
    other:      Seq[String] = Seq.empty
  ): Unit = {
    val topologyWidthDir = TopologyArtifacts.widthDir(topologies, width)

    PrefixModuleKind.all.foreach { moduleKind =>
      writeJson(topologyWidthDir / moduleKind.name / "pareto_frontier.json", paretoJson(width, frontier))
      writeText(
        topologyWidthDir / moduleKind.name / "pareto_frontier.svg",
        s"Pareto frontier for ${moduleKind.name} width $width\n"
      )
    }
    (frontier ++ other).foreach { topologyName =>
      val topologyDir = topologyWidthDir / topologyName
      Seq(
        "topology.json" -> s"source width $width $topologyName\n",
        "dyck.json" -> s"source width $width $topologyName dyck\n",
        "topology.svg" -> s"source width $width $topologyName svg\n"
      ).foreach { case (file, content) => writeText(topologyDir / file, content) }
      PrefixModuleKind.all.foreach { moduleKind =>
        writeText(
          topologyDir / moduleKind.name / "rtl" / "filelist.f",
          s"${moduleKind.name} $topologyName rtl\n"
        )
        writeJson(
          topologyDir / moduleKind.name / "sc" / "prelayout.json",
          ujson.Obj("rtlFiles" -> ujson.Arr(s"${moduleKind.name}/rtl/${moduleKind.name}.sv"))
        )
      }
    }
  }

  private def writeText(path: os.Path, content: String): Unit =
    os.write.over(path, content, createFolders = true)

  private def paretoJson(width: Int, topologies: Seq[String]): ujson.Obj =
    ujson.Obj(
      "width" -> width,
      "frontier" -> topologies.zipWithIndex.map { case (source, index) =>
        ujson.Obj(
          "area" -> s"area_$index",
          "source" -> source
        )
      }
    )

  private val fakePayload = ujson.Obj(
    "toolchain" -> ujson.Obj(
      "siliconcompiler" -> "main-spec-sc",
      "lambdapdk" -> "main-spec-lambdapdk"
    ),
    "metrics" -> ujson.Obj(
      ParetoFrontier.areaUm2 -> 43.358000000000004,
      ParetoFrontier.clockPeriodNs -> 0.09047690630572279
    )
  )

  private def assertPrelayout(prelayout: ujson.Value, moduleKind: PrefixModuleKind): Unit = {
    assert(prelayout("module").str == moduleKind.name)
    assert(prelayout("flow")("name").str == "synflow")
    assert(prelayout("constraints")("clockPeriodNs").num == 1.0)
    assert(prelayout("toolchain") == fakePayload("toolchain"))
    assert(prelayout("metrics")(ParetoFrontier.areaUm2).num == 43.358)
    assert(prelayout("metrics")(ParetoFrontier.clockPeriodNs).num == 0.09047690630572279)
    assert(prelayout("rtlFiles").arr.map(_.str) == Seq(s"${moduleKind.name}/rtl/${moduleKind.name}.sv"))
  }

  private def fakePrefixTopologyLibraryEnv(workspace: os.Path): Map[String, String] = {
    val payloadFile = workspace / "runner-payload.json"

    writeJson(payloadFile, fakePayload)
    Map(TopologyArtifacts.FakeRunnerJsonEnv -> payloadFile.toString)
  }
}
