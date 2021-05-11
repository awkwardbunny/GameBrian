package dev.meirl.gamebrian

import chisel3._
import chisel3.util.switch

class InterconnectIO extends Bundle {
  val gba = Flipped(new GBABusMaster)
  val ram = new GBABusMaster
  val sd = new GBABusMaster
}

// TODO: Use Chisel parameterized generators?
class Interconnect extends Module {
  val io = IO(new InterconnectIO)

  // TODO: Use bitpat and mux instead
  when(io.gba.addr(15, 12) === 0xF.U(4.W)){
    io.gba <> io.sd

    io.ram.addr := 0.U
    io.ram.mosi := 0.U
    io.ram.write := false.B
  }.otherwise{
    io.gba <> io.ram

    io.sd.addr := 0.U
    io.sd.mosi := 0.U
    io.sd.write := false.B
  }
}
