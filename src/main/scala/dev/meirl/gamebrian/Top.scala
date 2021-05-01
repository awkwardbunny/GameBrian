package dev.meirl.gamebrian

import chisel3._

class TopIO extends Bundle {}

class Top extends Module {
  val io = IO(new TopIO)
}
