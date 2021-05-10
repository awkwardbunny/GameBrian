package dev.meirl.gamebrian

import chisel3._

class TopIO extends Bundle {
  val host = new Bundle {
    val gba = new GBAHostIO
    val blinky = new BlinkyHostIO
    val gbaram = new GBARamHostIO
  }

  val board = new Bundle {
    val gba = new GBABoardIO
    val blinky = new BlinkyBoardIO
    val gbaram = new GBARamBoardIO
  }
}

class Top extends Module {
  val io = IO(new TopIO)
  withReset(!(reset asBool)) {
    val gba = Module(new GBA)
    io.host.gba <> gba.io.host
    io.board.gba <> gba.io.board

    val blinky = Module(new Blinky)
    io.host.blinky <> blinky.io.host
    io.board.blinky <> blinky.io.board

    val gbaram = Module(new GBARam)
    io.host.gbaram <> gbaram.io.host
    io.board.gbaram <> gbaram.io.board

    // No interconnect/arbiter for now
    gba.io.card.bus <> gbaram.io.card.bus
  }
}
