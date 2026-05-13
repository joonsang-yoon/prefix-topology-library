package prefix.topology.library

import chisel3._
import circt.stage.ChiselStage
import org.scalatest.funsuite.AnyFunSuite

import TestSupport.{representativeTopologies, topologiesThrough, withWorkspace, writeJson}

final class PrefixModuleSpec extends AnyFunSuite {
  PrefixModuleKind.all.foreach { moduleKind =>
    test(s"${moduleKind.name} elaborates every topology through width 4") {
      topologiesThrough(4).foreach(verifyRtl(moduleKind, _))
    }

    test(s"${moduleKind.name} elaborates representative wider topologies into rtl") {
      Seq(9, 37, PrefixTopology.MaxWidth).flatMap(representativeTopologies).foreach(verifyRtl(moduleKind, _))
    }
  }

  test("prefix function helpers elaborate into rtl") {
    withWorkspace("prefix-function-helpers-") { workspace =>
      val frontierDir = workspace / "frontier"
      writeJson(frontierDir / "PrefixAdder" / "width_8" / "area_0" / "topology.json", PrefixTopology.ripple(8).toJson)
      writeJson(
        frontierDir / "PrefixAbsDiff" / "width_8" / "area_0" / "topology.json",
        PrefixTopology.ripple(8).toJson
      )

      Seq(
        "Add with cin behavioral" -> ChiselStage.emitSystemVerilog(
          new AddFunctionHarness(8, useCin = true, "behavioral")
        ),
        "Add without cin behavioral" -> ChiselStage.emitSystemVerilog(
          new AddFunctionHarness(8, useCin = false, "behavioral")
        ),
        "Sub behavioral" -> ChiselStage.emitSystemVerilog(new SubFunctionHarness(8, "behavioral")),
        "AbsDiff behavioral" -> ChiselStage.emitSystemVerilog(new AbsDiffFunctionHarness(8, "behavioral")),
        "Add curated" -> PrefixImplementations.withFrontierDir(frontierDir) {
          ChiselStage.emitSystemVerilog(new AddFunctionHarness(8, useCin = true, "area_0"))
        },
        "AbsDiff curated" -> PrefixImplementations.withFrontierDir(frontierDir) {
          ChiselStage.emitSystemVerilog(new AbsDiffFunctionHarness(8, "area_0"))
        }
      ).foreach { case (name, rtl) =>
        withClue(name) {
          assert(rtl.contains("module FunctionHarness"))
          assert(!rtl.contains("module PrefixAdder"))
          assert(!rtl.contains("module PrefixAbsDiff"))
        }
      }
    }
  }

  private def verifyRtl(moduleKind: PrefixModuleKind, topology: PrefixTopology): Unit = {
    val rtl = ChiselStage.emitSystemVerilog(moduleKind.elaborate(topology.width, Some(topology)))
    val ports =
      if (moduleKind == PrefixModuleKind.Adder) Seq("io_a", "io_b", "io_cin", "io_sum", "io_cout")
      else Seq("io_a", "io_b", "io_lt", "io_absDiff")

    withClue(s"module=${moduleKind.name} width=${topology.width}") {
      assert((s"module ${moduleKind.name}" +: ports).forall(rtl.contains))
      assert(topology.width == 1 || rtl.contains(s"[${topology.width - 1}:0]"))
    }
  }

  private final class AddFunctionHarness(width: Int, useCin: Boolean, implementation: String) extends RawModule {
    override def desiredName: String = "FunctionHarness"

    val io = IO(new Bundle {
      val a = Input(UInt(width.W))
      val b = Input(UInt(width.W))
      val cin = Input(Bool())
      val out = Output(UInt(width.W))
      val cout = Output(Bool())
    })

    if (useCin) {
      val (sum, cout) = Add(io.a, io.b, io.cin, implementation)
      io.out := sum
      io.cout := cout
    } else {
      val sum = Add(io.a, io.b, implementation)
      io.out := sum(width - 1, 0)
      io.cout := sum(width)
    }
  }

  private final class SubFunctionHarness(width: Int, implementation: String) extends RawModule {
    override def desiredName: String = "FunctionHarness"

    val io = IO(new Bundle {
      val a = Input(UInt(width.W))
      val b = Input(UInt(width.W))
      val out = Output(UInt(width.W))
      val cout = Output(Bool())
    })

    val diff = Sub(io.a, io.b, implementation)

    io.out := diff(width - 1, 0)
    io.cout := diff(width)
  }

  private final class AbsDiffFunctionHarness(width: Int, implementation: String) extends RawModule {
    override def desiredName: String = "FunctionHarness"

    val io = IO(new Bundle {
      val a = Input(UInt(width.W))
      val b = Input(UInt(width.W))
      val lt = Output(Bool())
      val out = Output(UInt(width.W))
    })

    val (lt, absDiff) = AbsDiff(io.a, io.b, implementation)

    io.lt := lt
    io.out := absDiff
  }
}
