package prefix.topology.library

import scala.collection.mutable

private[library] sealed trait CellRef {
  def low:          Int
  def high:         Int
  final def isLeaf: Boolean = low == high
}

private[library] sealed trait PrefixTree extends CellRef {
  def toJson: ujson.Value
  def shape:  String
}

private[library] object PrefixTree {
  final case class Leaf(index: Int) extends PrefixTree {
    val low = index
    val high = index
    def toJson: ujson.Value = ujson.Obj("leaf" -> index)
    val shape = ""
  }

  final case class Node(left: PrefixTree, right: PrefixTree) extends PrefixTree {
    val low = left.low
    val high = right.high
    def toJson: ujson.Value = ujson.Obj("node" -> ujson.Arr(left.toJson, right.toJson))
    def shape:  String = s"U${left.shape}D${right.shape}"
  }

  def fromJson(value: ujson.Value): PrefixTree =
    if (value.obj.contains("leaf")) Leaf(value("leaf").num.toInt)
    else {
      val children = value("node").arr
      Node(fromJson(children(0)), fromJson(children(1)))
    }
}

import PrefixTree.{Leaf, Node}

final case class PrefixTopology(width: Int, steps: Vector[PrefixTopology.Step]) {
  private[library] val cells: Vector[PrefixTopology.Cell] = PrefixTopology.deriveCells(steps)

  def toJson: ujson.Value =
    ujson.Obj("width" -> width, "steps" -> steps.map(_.toJson))

  def dyckJson: ujson.Value =
    ujson.Obj(
      "width" -> width,
      "steps" -> steps.map { step =>
        ujson.Obj("dependency" -> step.dependency, "shape" -> step.suffix.shape)
      }
    )
}

object PrefixTopology {
  val MaxWidth = 128

  private[library] final case class Root(high: Int) extends CellRef {
    val low = 0
  }

  private[library] def rootRef(index: Int): CellRef =
    if (index == 0) Leaf(0) else Root(index)

  private[library] final case class Cell(target: CellRef, left: CellRef, right: CellRef, isBlack: Boolean) {
    def nonLeafDependencies: Iterator[CellRef] = Iterator(left, right).filterNot(_.isLeaf)
  }

  final case class Step(dependency: Int, suffix: PrefixTree) {
    def toJson: ujson.Obj =
      ujson.Obj("dependency" -> dependency, "suffix" -> suffix.toJson)
  }

  def fromJson(value: ujson.Value): PrefixTopology =
    PrefixTopology(
      value("width").num.toInt,
      value("steps").arr.map { step =>
        Step(step("dependency").num.toInt, PrefixTree.fromJson(step("suffix")))
      }.toVector
    )

  def ripple(width: Int): PrefixTopology =
    PrefixTopology(width, Vector.tabulate(width - 1)(index => Step(index, Leaf(index + 1))))

  def enumerate(width: Int): Iterator[PrefixTopology] = {
    val treesByRange = mutable.HashMap.empty[(Int, Int), Vector[PrefixTree]]

    def trees(low: Int, high: Int): Vector[PrefixTree] = treesByRange.getOrElseUpdate(
      (low, high), {
        if (low == high) Vector(Leaf(low))
        else
          (
            for {
              split <- (high - 1 to low by -1).iterator
              left <- trees(low, split).iterator
              right <- trees(split + 1, high).iterator
            } yield Node(left, right)
          ).toVector
      }
    )

    (1 until width)
      .foldLeft(Iterator.single(Vector.empty[Step])) { (prefixes, high) =>
        for {
          prefix <- prefixes
          dependency <- (high - 1 to 0 by -1).iterator
          suffix <- trees(dependency + 1, high).iterator
        } yield prefix :+ Step(dependency, suffix)
      }
      .map(steps => apply(width, steps))
  }

  private def deriveCells(steps: Vector[Step]): Vector[Cell] = {
    val suffixCells = mutable.LinkedHashMap.empty[Node, Cell]
    val blackRoots = steps.map(_.dependency).toSet

    def visit(tree: PrefixTree): CellRef = tree match {
      case leaf: Leaf => leaf
      case node @ Node(left, right) =>
        suffixCells.getOrElseUpdate(node, Cell(node, visit(left), visit(right), true))
        node
    }

    val rootCells = steps.zipWithIndex.map { case (Step(dependency, suffix), index) =>
      Cell(Root(index + 1), rootRef(dependency), visit(suffix), blackRoots(index + 1))
    }

    suffixCells.valuesIterator.toVector ++ rootCells
  }
}
