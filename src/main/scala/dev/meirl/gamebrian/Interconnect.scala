package dev.meirl.gamebrian

import chisel3._

class InterconnectIO extends Bundle {
  val gba = Flipped(new GBABusMaster)
  val ram = new GBABusMaster
}

class Interconnect extends Module {
  val io = IO(new InterconnectIO)

  io.gba <> io.ram
}
