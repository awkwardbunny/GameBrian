package dev.meirl.gamebrian

import chisel3._

class TopIO extends Bundle {
  val gbaHost = new GBAHostIO
  val blinkyHost = new BlinkyHostIO
}

class Top extends Module {
  val io = IO(new TopIO)
  withReset(!(reset asBool)) {
    val gba = Module(new GBA)
    io.gbaHost <> gba.io.host

    val blinky = Module(new Blinky)
    io.blinkyHost <> blinky.io.host
  }
}
