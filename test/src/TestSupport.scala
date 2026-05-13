package prefix.topology.library

import PrefixTree.{Leaf, Node}

private object TestSupport {
  def withWorkspace[A](prefix: String)(testCode: os.Path => A): A = {
    val workspace = os.temp.dir(prefix = prefix)
    try testCode(workspace)
    finally os.remove.all(workspace)
  }

  def readJson(path: os.Path): ujson.Value =
    ujson.read(os.read(path))

  def writeJson(path: os.Path, value: ujson.Value): Unit =
    TopologyArtifacts.writeJson(path, value)

  def topologiesThrough(maxWidth: Int): Iterator[PrefixTopology] =
    (1 to maxWidth).iterator.flatMap(PrefixTopology.enumerate)

  val sharedSuffixTopology: PrefixTopology = {
    val shared = Node(Leaf(1), Leaf(2))
    PrefixTopology(
      width = 5,
      steps = Vector(
        PrefixTopology.Step(dependency = 0, suffix = Leaf(1)),
        PrefixTopology.Step(dependency = 0, suffix = shared),
        PrefixTopology.Step(dependency = 0, suffix = Node(shared, Leaf(3))),
        PrefixTopology.Step(dependency = 3, suffix = Leaf(4))
      )
    )
  }

  def representativeTopologies(width: Int): Vector[PrefixTopology] =
    if (width < 3) Vector(PrefixTopology.ripple(width))
    else Vector(PrefixTopology.ripple(width), leftDeepTopology(width))

  private def leftDeepTopology(width: Int): PrefixTopology =
    PrefixTopology(
      width,
      (2 until width)
        .scanLeft[PrefixTree](Leaf(1))((tree, index) => Node(tree, Leaf(index)))
        .map(PrefixTopology.Step(0, _))
        .toVector
    )
}
