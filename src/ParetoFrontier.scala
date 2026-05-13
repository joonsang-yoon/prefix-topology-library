package prefix.topology.library

private[library] object ParetoFrontier {
  private[library] val areaUm2 = "areaUm2"
  private[library] val clockPeriodNs = "clockPeriodNs"

  private[library] final case class Point(
    source:        String,
    areaUm2:       Double,
    clockPeriodNs: Double
  ) {
    def areaSortKey: (Double, Double, Int) =
      (areaUm2, clockPeriodNs, sourceRank)

    def timingSortKey: (Double, Double, Int) =
      (clockPeriodNs, areaUm2, sourceRank)

    private def sourceRank: Int =
      if (source == "ripple") 0 else source.stripPrefix("variant_").toInt + 1
  }

  def refreshFrontier(
    topologies: os.Path,
    frontier:   os.Path,
    minWidth:   Int,
    maxWidth:   Int
  ): Int =
    (for {
      width <- minWidth to maxWidth
      moduleKind <- PrefixModuleKind.all
    } yield refreshWidth(topologies, frontier, width, moduleKind)).sum

  private[library] def writeWidth(topologyWidthDir: os.Path, width: Int, moduleKind: PrefixModuleKind): Unit = {
    val points = loadPoints(topologyWidthDir, moduleKind)
    val frontier = frontierOf(points)
    val moduleFrontierDir = topologyWidthDir / moduleKind.name

    TopologyArtifacts.writeJson(moduleFrontierDir / "pareto_frontier.json", json(width, frontier))
    os.write.over(moduleFrontierDir / "pareto_frontier.svg", ParetoFrontierDiagram.render(width, points, frontier))
  }

  private[library] def json(width: Int, frontier: Seq[Point]): ujson.Obj =
    ujson.Obj(
      "width" -> width,
      "frontier" -> entries(frontier).map { case (area, timing, point) =>
        ujson.Obj(
          "area" -> area,
          "timing" -> timing,
          "source" -> point.source,
          "metrics" -> ujson.Obj(
            areaUm2 -> point.areaUm2,
            clockPeriodNs -> point.clockPeriodNs
          )
        )
      }
    )

  private[library] def entries(frontier: Seq[Point]): Vector[(String, String, Point)] = {
    val timingIndexByPoint = frontier.sortBy(_.timingSortKey).zipWithIndex.toMap

    frontier
      .sortBy(_.areaSortKey)
      .zipWithIndex
      .map { case (point, index) =>
        (s"area_$index", s"timing_${timingIndexByPoint(point)}", point)
      }
      .toVector
  }

  private def frontierOf(points: Seq[Point]): Vector[Point] =
    points.sortBy(_.areaSortKey).foldLeft(Vector.empty[Point]) { (frontier, point) =>
      if (frontier.lastOption.exists(_.clockPeriodNs <= point.clockPeriodNs)) frontier
      else frontier :+ point
    }

  private def refreshWidth(
    topologies: os.Path,
    frontier:   os.Path,
    width:      Int,
    moduleKind: PrefixModuleKind
  ): Int = {
    val topologyWidthDir = TopologyArtifacts.widthDir(topologies, width)
    val moduleSummaryDir = topologyWidthDir / moduleKind.name
    val frontierWidthDir = TopologyArtifacts.widthDir(frontier / moduleKind.name, width)
    val entries = ujson.read(os.read(moduleSummaryDir / "pareto_frontier.json"))("frontier").arr

    if (os.exists(frontierWidthDir)) os.remove.all(frontierWidthDir)
    copyChildren(moduleSummaryDir, frontierWidthDir, "pareto_frontier.json", "pareto_frontier.svg")
    entries.foreach { entry =>
      val sourceDir = topologyWidthDir / entry("source").str
      val areaDir = frontierWidthDir / entry("area").str

      copyChildren(sourceDir, areaDir, "topology.json", "dyck.json", "topology.svg")
      copyChildren(sourceDir / moduleKind.name, areaDir, "rtl")
      val prelayout = ujson.read(os.read(sourceDir / moduleKind.name / "sc" / "prelayout.json"))

      prelayout.obj("rtlFiles") = ujson.Arr(s"rtl/${moduleKind.name}.sv")
      TopologyArtifacts.writeJson(areaDir / "sc" / "prelayout.json", prelayout)
    }
    entries.size
  }

  private def copyChildren(source: os.Path, dest: os.Path, names: String*): Unit =
    names.foreach(name => os.copy(source / name, dest / name, createFolders = true))

  private def loadPoints(topologyWidthDir: os.Path, moduleKind: PrefixModuleKind): Vector[Point] =
    os.list(topologyWidthDir)
      .filter(topologyDir => os.exists(topologyDir / "topology.json"))
      .map { topologyDir =>
        val metrics = ujson.read(os.read(topologyDir / moduleKind.name / "sc" / "prelayout.json"))("metrics")
        Point(topologyDir.last, metrics(areaUm2).num, metrics(clockPeriodNs).num)
      }
      .toVector
}
