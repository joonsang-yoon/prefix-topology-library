package prefix.topology.library

import scala.util.DynamicVariable

object PrefixImplementations {
  final case class Selection(name: String, file: Option[os.Path]) {
    def topology: Option[PrefixTopology] = file.map(path => PrefixTopology.fromJson(ujson.read(os.read(path))))
    def metadata(moduleKind: PrefixModuleKind, width: Int): ujson.Obj =
      ujson.Obj(
        "module" -> moduleKind.name,
        "width" -> width,
        "source" -> file.fold("behavioral")(_ => "curated"),
        "implementation" -> name,
        "file" -> file.fold[ujson.Value](ujson.Null)(path => ujson.Str(path.toString))
      )
  }

  private val currentFrontierDir = new DynamicVariable[Option[os.Path]](None)

  def withFrontierDir[A](frontierDir: os.Path)(body: => A): A =
    currentFrontierDir.withValue(Some(frontierDir))(body)

  def select(
    implementation: String,
    moduleKind:     PrefixModuleKind,
    width:          Int,
    frontierDir:    os.Path = activeFrontierDir
  ): Selection =
    if (implementation == "behavioral") Selection(implementation, None)
    else {
      val frontierWidthDir = frontierDir / moduleKind.name / s"width_$width"
      val area =
        if (implementation.startsWith("timing_"))
          ujson
            .read(os.read(frontierWidthDir / "pareto_frontier.json"))("frontier")
            .arr
            .find(_("timing").str == implementation)
            .map(_("area").str)
            .get
        else implementation

      Selection(implementation, Some(frontierWidthDir / area / "topology.json"))
    }

  private def activeFrontierDir: os.Path =
    currentFrontierDir.value.getOrElse(os.pwd / "pareto_frontier_topologies")
}
