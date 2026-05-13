package prefix.topology.library

import chisel3._
import chisel3.util.Cat

final case class PrefixModuleKind(name: String, elaborate: (Int, Option[PrefixTopology]) => RawModule)

object PrefixModuleKind {
  val Adder: PrefixModuleKind = PrefixModuleKind(
    "PrefixAdder",
    (width, topology) => new PrefixAdderHarness(width, topology)
  )

  val AbsDiff: PrefixModuleKind = PrefixModuleKind(
    "PrefixAbsDiff",
    (width, topology) => new PrefixAbsDiffHarness(width, topology)
  )

  val all: Vector[PrefixModuleKind] = Vector(Adder, AbsDiff)
}

private final class PrefixAdderHarness(width: Int, topology: Option[PrefixTopology]) extends RawModule {
  override def desiredName: String = "PrefixAdder"

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val cin = Input(Bool())
    val sum = Output(UInt(width.W))
    val cout = Output(Bool())
  })

  val (sum, cout) = PrefixArithmetic.add(io.a, io.b, io.cin, topology)
  io.sum := sum
  io.cout := cout
}

object Add {
  def apply(a: UInt, b: UInt, cin: Bool, implementation: String): (UInt, Bool) = {
    val topology = PrefixImplementations.select(implementation, PrefixModuleKind.Adder, a.getWidth).topology
    PrefixArithmetic.add(a, b, cin, topology)
  }

  def apply(a: UInt, b: UInt, implementation: String): UInt = {
    val (sum, cout) = apply(a, b, false.B, implementation)
    Cat(cout, sum)
  }
}

object Sub {
  def apply(a: UInt, b: UInt, implementation: String): UInt = {
    val (sum, cout) = Add(a, ~b, true.B, implementation)
    Cat(!cout, sum)
  }
}

private final class PrefixAbsDiffHarness(width: Int, topology: Option[PrefixTopology]) extends RawModule {
  override def desiredName: String = "PrefixAbsDiff"

  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val lt = Output(Bool())
    val absDiff = Output(UInt(width.W))
  })

  val (lt, absDiff) = PrefixArithmetic.absDiff(io.a, io.b, topology)
  io.lt := lt
  io.absDiff := absDiff
}

object AbsDiff {
  def apply(a: UInt, b: UInt, implementation: String): (Bool, UInt) = {
    val topology = PrefixImplementations.select(implementation, PrefixModuleKind.AbsDiff, a.getWidth).topology
    PrefixArithmetic.absDiff(a, b, topology)
  }
}

private object PrefixArithmetic {
  import PrefixNetwork.Signal

  def add(a: UInt, b: UInt, cin: Bool, topology: Option[PrefixTopology]): (UInt, Bool) = {
    val width = a.getWidth

    topology.fold {
      val result = a +& b + cin.asUInt
      (result(width - 1, 0), result(width))
    } { topology =>
      val inputs =
        Vector.tabulate(width)(bit => Signal(a(bit) & b(bit), a(bit) ^ b(bit)))

      val prefixes = PrefixNetwork.reduce(topology, inputs)

      val carries = cin +: Vector.tabulate(width) { bit =>
        val prefix = prefixes(PrefixTopology.rootRef(bit))
        prefix.generate | (prefix.propagate & cin)
      }

      val sum = VecInit(
        inputs.zip(carries).map { case (input, carry) => input.propagate ^ carry }
      ).asUInt

      (sum, carries.last)
    }
  }

  def absDiff(a: UInt, b: UInt, topology: Option[PrefixTopology]): (Bool, UInt) = {
    val width = a.getWidth

    topology.fold {
      val lt = a < b
      (lt, Mux(lt, b - a, a - b))
    } { topology =>
      val inputs =
        Vector.tabulate(width)(bit => Signal(!a(bit) & b(bit), !(a(bit) ^ b(bit))))

      val prefixes = PrefixNetwork.reduce(topology, inputs)

      val lt = prefixes(PrefixTopology.rootRef(width - 1)).generate
      val lowerPrefixes =
        Signal(false.B, true.B) +: Vector.tabulate(width - 1)(bit => prefixes(PrefixTopology.rootRef(bit)))

      val absDiff = VecInit(
        inputs.zip(lowerPrefixes).map { case (input, lower) =>
          val absBorrow = lower.generate ^ (lt & !lower.propagate)
          !input.propagate ^ absBorrow
        }
      ).asUInt

      (lt, absDiff)
    }
  }
}

private[library] object PrefixNetwork {
  import PrefixTree.Leaf

  final case class Signal(generate: Bool, propagate: Bool)

  def reduce(topology: PrefixTopology, inputs: IndexedSeq[Signal]): Map[CellRef, Signal] = {
    val leaves = inputs.zipWithIndex.map { case (input, index) => (Leaf(index): CellRef) -> input }.toMap

    topology.cells.foldLeft(leaves) { (prefixes, cell) =>
      prefixes.updated(cell.target, combine(prefixes(cell.left), prefixes(cell.right)))
    }
  }

  private def combine(lower: Signal, upper: Signal): Signal =
    Signal(
      upper.generate | (upper.propagate & lower.generate),
      upper.propagate & lower.propagate
    )
}
