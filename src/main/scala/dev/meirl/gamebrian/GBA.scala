package dev.meirl.gamebrian

import chisel3._

class GBAHostIO extends Bundle {
  val clk = Input(Clock())
  val nWR = Input(Bool())
  val nRD= Input(Bool())
  val nCS = Input(Bool())
  val AD = Input(UInt(16.W))
  val A = Input(UInt(8.W))
  val CS2 = Input(Bool())
  val nREQ = Output(Bool())
  val VDD = Input(Bool())
}

class GBACardIO extends Bundle {}

class GBAIO extends Bundle {
  val host = new GBAHostIO
  val card = new GBACardIO
}

class GBA extends Module {
  val io = IO(new GBAIO)

  io.host.nREQ := (io.host.nWR || io.host.nRD);
}
