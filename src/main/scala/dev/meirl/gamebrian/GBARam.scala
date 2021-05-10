package dev.meirl.gamebrian

import Chisel.Cat
import chisel3._

class GBARamBoardIO extends Bundle {}
class GBARamHostIO extends Bundle {}
class GBARamCardIO extends Bundle {
  val bus = Flipped(new GBABusMaster)
}

class GBARamIO extends Bundle {
  val card = new GBARamCardIO
  val host = new GBARamHostIO
  val board = new GBARamBoardIO
}

class GBARam extends Module {
  val io = IO(new GBARamIO)

  val ram = Mem(16, UInt(8.W))

  io.card.bus.miso := ram.read(io.card.bus.addr)

  val prevWrite = RegInit(false.B)
  val rWrite = !prevWrite && io.card.bus.write
  when(true.B) { prevWrite := io.card.bus.write }

  when(rWrite){
    ram.write(io.card.bus.addr, io.card.bus.mosi)
  }
}
