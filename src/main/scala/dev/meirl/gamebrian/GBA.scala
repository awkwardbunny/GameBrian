package dev.meirl.gamebrian

import chisel3._

class GBAHostIO extends Bundle {
  val CLK = Input(Clock())
  val nWR = Input(Bool())
  val nRD= Input(Bool())
  val nCS = Input(Bool())
  val CS2 = Input(Bool())
  val nREQ = Output(Bool())
  val VDD = Input(Bool())

//  val AD = Analog(16.W)
//  val A = Analog(8.W)
  val AD_in = Input(UInt(16.W))
  val AD_out = Output(UInt(16.W))
  val AD_oe = Output(Bool())

  val A_in = Input(UInt(16.W))
  val A_out = Output(UInt(16.W))
  val A_oe = Output(Bool())
}

class GBACardIO extends Bundle {}

class GBAIO extends Bundle {
  val host = new GBAHostIO
  val card = new GBACardIO
}

class GBA extends Module {
  val io = IO(new GBAIO)

  io.host.nREQ := false.B
  io.host.A_oe := (!io.host.CS2) || io.host.nRD || io.host.VDD
  io.host.AD_oe := (!io.host.nCS) || io.host.nRD || io.host.VDD

  io.host.AD_out := io.host.AD_in
  io.host.A_out := io.host.A_in
}
