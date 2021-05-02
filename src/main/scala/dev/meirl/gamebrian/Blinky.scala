package dev.meirl.gamebrian

import chisel3._
import chisel3.util.Counter

class BlinkyHostIO extends Bundle {
  val led = Output(UInt(4.W))
}

class BlinkyIO extends Bundle {
  val host = new BlinkyHostIO
}

class Blinky extends Module {
  val io = IO(new BlinkyIO)

  val (_, inc) = Counter(true.B, 50000000)
  val status = RegInit(0.U(4.W))
  when(inc) { status := status + 1.U }
  io.host.led := status
}
