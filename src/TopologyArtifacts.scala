package prefix.topology.library

import chisel3.RawModule
import circt.stage.ChiselStage

object TopologyArtifacts {
  final case class RtlArtifact(rtlFiles: Vector[os.Path]) {
    def topModule: String = rtlFiles.last.last.stripSuffix(".sv")
  }

  val FakeRunnerJsonEnv = "PREFIX_TOPOLOGY_LIBRARY_FAKE_RUNNER_JSON"
  private val supportedWidths = 1 to PrefixTopology.MaxWidth
  private val prelayoutScript = os.Path(getClass.getClassLoader.getResource("prelayout.py").toURI)
  private val FirtoolOptions = Array(
    "-disable-all-randomization",
    "-strip-debug-info",
    "-default-layer-specialization=disable",
    "--lowering-options=disallowLocalVariables,disallowPackedArrays"
  )

  private[library] def writeTopologyRoot(
    topologies: os.Path,
    minWidth:   Int,
    maxWidth:   Int,
    workers:    Int,
    env:        Map[String, String]
  ): Int = {
    supportedWidths.iterator.map(widthDir(topologies, _)).filter(os.exists).foreach(os.remove.all)
    if (!env.contains(FakeRunnerJsonEnv)) runPrelayout(env, "--prepare")

    val childWorkers = (1 until workers).map(startWorker(topologies, minWidth, maxWidth, workers, _, env))
    val topologyCount = writeAssignedTopologies(topologies, minWidth, maxWidth, workers, 0, env)

    childWorkers.foreach(worker => if (!worker.waitFor()) throw new RuntimeException("topology worker failed"))

    for {
      width <- supportedWidths
      moduleKind <- PrefixModuleKind.all
    } ParetoFrontier.writeWidth(widthDir(topologies, width), width, moduleKind)
    topologyCount
  }

  private[library] def writeAssignedTopologies(
    topologies: os.Path,
    minWidth:   Int,
    maxWidth:   Int,
    workers:    Int,
    workerId:   Int,
    env:        Map[String, String]
  ): Int = {
    var topologyCount = 0
    for {
      width <- supportedWidths
      (topology, localIndex) <- topologiesForWidth(width, minWidth, maxWidth)
    } {
      if (topologyCount % workers == workerId) {
        writeTopology(widthDir(topologies, width) / topologyName(localIndex), topology, env)
      }
      topologyCount += 1
    }
    topologyCount
  }

  private[library] def widthDir(root: os.Path, width: Int): os.Path =
    root / s"width_$width"

  private def topologiesForWidth(
    width:    Int,
    minWidth: Int,
    maxWidth: Int
  ): Iterator[(PrefixTopology, Int)] =
    if (width >= minWidth && width <= maxWidth) PrefixTopology.enumerate(width).zipWithIndex
    else Iterator.single(PrefixTopology.ripple(width) -> 0)

  def writeRtlArtifact(design: => RawModule, rtlDir: os.Path): RtlArtifact = {
    ChiselStage.emitSystemVerilogFile(
      design,
      Array("--target-dir", rtlDir.toString),
      FirtoolOptions
    )

    RtlArtifact(os.read.lines(rtlDir / "filelist.f").map(file => rtlDir / file).toVector)
  }

  def writePrelayoutReport(
    env:         Map[String, String],
    reportFile:  os.Path,
    rtlArtifact: RtlArtifact,
    summary:     ujson.Obj,
    constraints: ujson.Obj = ujson.Obj()
  ): Unit = {
    val request = ujson.Obj(
      "module" -> rtlArtifact.topModule,
      "rtlFiles" -> rtlArtifact.rtlFiles.map(_.toString),
      "constraints" -> constraints,
      "summary" -> summary
    )

    runPrelayout(env, reportFile, ujson.write(request))
  }

  private def runPrelayout(env: Map[String, String], args: os.Shellable*): Unit = {
    val python: os.Shellable =
      if (env.contains(FakeRunnerJsonEnv)) "python3"
      else os.Path(env("HOME")) / "siliconcompiler" / ".venv" / "bin" / "python"
    os.proc(python, prelayoutScript, args).call(env = env)
  }

  private[library] def topologyName(localIndex: Int): String =
    if (localIndex == 0) "ripple" else s"variant_${localIndex - 1}"

  private def startWorker(
    topologies: os.Path,
    minWidth:   Int,
    maxWidth:   Int,
    workers:    Int,
    workerId:   Int,
    env:        Map[String, String]
  ): os.SubProcess =
    os.proc(
      os.Path(sys.props("java.home")) / "bin" / "java",
      "-cp",
      sys.props("java.class.path"),
      "prefix.topology.library.Main",
      "worker",
      topologies.toString,
      minWidth.toString,
      maxWidth.toString,
      workers.toString,
      workerId.toString
    ).spawn(env = env, stdout = os.Inherit, stderr = os.Inherit)

  private def writeTopology(
    topologyDir: os.Path,
    topology:    PrefixTopology,
    env:         Map[String, String]
  ): Unit = {
    writeJson(topologyDir / "topology.json", topology.toJson)
    writeJson(topologyDir / "dyck.json", topology.dyckJson)
    os.write.over(topologyDir / "topology.svg", TopologyDiagram.render(topology))

    PrefixModuleKind.all.foreach { moduleKind =>
      val moduleDir = topologyDir / moduleKind.name
      val rtlArtifact = writeRtlArtifact(moduleKind.elaborate(topology.width, Some(topology)), moduleDir / "rtl")
      writePrelayoutReport(
        env,
        moduleDir / "sc" / "prelayout.json",
        rtlArtifact,
        ujson.Obj("rtlFiles" -> rtlArtifact.rtlFiles.map(path => s"${moduleKind.name}/rtl/${path.last}"))
      )
    }
  }

  private[library] def writeJson(path: os.Path, value: ujson.Value): Unit =
    os.write.over(path, ujson.write(value) + "\n", createFolders = true)
}
