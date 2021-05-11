package dev.meirl.gamebrian

import chisel3._

class TopIO extends Bundle {
  val host = new Bundle {
    val gba = new GBAHostIO
    val blinky = new BlinkyHostIO
    val gbaram = new GBARamHostIO
    val sd = new SDCardHostIO
  }

  val board = new Bundle {
    val gba = new GBABoardIO
    val blinky = new BlinkyBoardIO
    val gbaram = new GBARamBoardIO
    val sd = new SDCardBoardIO
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

    val sd = Module(new SDCard)
    io.host.sd <> sd.io.host
    io.board.sd <> sd.io.board

    val intercon = Module(new Interconnect)
    intercon.io.gba <> gba.io.card.bus
    intercon.io.ram <> gbaram.io.card.bus
    intercon.io.sd <> sd.io.card.bus
  }
}
