package prefix.topology.library

object Main {
  def main(args: Array[String]): Unit = {
    val output = run(args.toSeq)
    if (output.nonEmpty) println(output)
  }

  private[library] def run(
    args: Seq[String],
    cwd:  os.Path = os.pwd,
    env:  Map[String, String] = sys.env
  ): String = {
    def path(value: String) = os.Path(value, cwd)

    args match {
      case Seq("inspect", topology) =>
        val parsedTopology = PrefixTopology.fromJson(ujson.read(os.read(path(topology))))
        val blackCellCount = parsedTopology.cells.count(_.isBlack)
        ujson.write(
          ujson.Obj(
            "topology" -> parsedTopology.toJson,
            "cellCounts" -> ujson.Obj(
              "blackCellCount" -> blackCellCount,
              "grayCellCount" -> (parsedTopology.cells.length - blackCellCount)
            )
          )
        )
      case Seq("topologies", topologies, minWidth, maxWidth, workers) =>
        TopologyArtifacts
          .writeTopologyRoot(path(topologies), minWidth.toInt, maxWidth.toInt, workers.toInt, env)
          .toString
      case Seq("frontier", topologies, frontier, minWidth, maxWidth) =>
        ParetoFrontier
          .refreshFrontier(path(topologies), path(frontier), minWidth.toInt, maxWidth.toInt)
          .toString
      case Seq("worker", topologies, minWidth, maxWidth, workers, workerId) =>
        TopologyArtifacts.writeAssignedTopologies(
          path(topologies),
          minWidth.toInt,
          maxWidth.toInt,
          workers.toInt,
          workerId.toInt,
          env
        )
        ""
    }
  }
}
