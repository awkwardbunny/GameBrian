package dev.meirl.gamebrian

import chisel3._

class TopIO extends Bundle {
  val host = new Bundle {
    val gba = new GBAHostIO()
    val blinky = new BlinkyHostIO()
  }

  val board = new Bundle {
    val gba = new GBABoardIO()
    val blinky = new BlinkyBoardIO()
  }
}

class Top(romFilename: String) extends Module {
  val io = IO(new TopIO)
  withReset(!(reset asBool)) {
    val gba = Module(new GBA(romFilename))
    io.host.gba <> gba.io.host
    io.board.gba <> gba.io.board

    val blinky = Module(new Blinky)
    io.host.blinky <> blinky.io.host
    io.board.blinky <> blinky.io.board
  }
}
