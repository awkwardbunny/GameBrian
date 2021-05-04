package main

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import dev.meirl.gamebrian.Top

object main extends App {
  val path = try args(0) catch { case _: Throwable => "generated_output" }

  val chiselArgs = Array("-E", "verilog", "-td", path)
  (new ChiselStage).execute(chiselArgs, Seq(ChiselGeneratorAnnotation(() => new Top("roms/fire.gba"))))
}
