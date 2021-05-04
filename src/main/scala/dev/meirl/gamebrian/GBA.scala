package dev.meirl.gamebrian

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

class GBACardIO extends Bundle {}
class GBABoardIO extends Bundle {
  val debug = Output(UInt(8.W))
}

class GBAHostIO extends Bundle {
  val CLK = Input(Clock())
  val nWR = Input(Bool())
  val nRD= Input(Bool())
  val nCS = Input(Bool())
  val nCS2 = Input(Bool())
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

class GBAIO extends Bundle {
  val host = new GBAHostIO
  val card = new GBACardIO
  val board = new GBABoardIO
}

class GBA(romFilename: String) extends Module {
  val io = IO(new GBAIO)

  dontTouch(io.host.CLK)
  dontTouch(io.host.nREQ)

  io.host.nREQ := false.B
  io.host.A_oe := (!io.host.nCS2) && (!io.host.nRD) && io.host.VDD
  io.host.AD_oe := (!io.host.nCS) && (!io.host.nRD) && io.host.VDD

  io.host.AD_out := io.host.AD_in
  io.host.A_out := io.host.A_in

  val resyncRD = RegInit(0.U(2.W))
  val resyncCS = RegInit(0.U(2.W))
  val resyncWR = RegInit(0.U(2.W))
  when(true.B){
    resyncRD := Cat(resyncRD(0), io.host.nRD)
    resyncCS := Cat(resyncCS(0), io.host.nCS)
    resyncWR := Cat(resyncWR(0), io.host.nWR)
  }

  val rRD = !resyncRD(1) && resyncRD(0)
  val fCS = resyncCS(1) && !resyncCS(0)
  val fWR = resyncWR(1) && !resyncWR(0)

  import java.io.FileInputStream
  val romFile = new FileInputStream(romFilename)
  val romData = Iterator.continually(romFile.read).takeWhile(_ != -1).toList.grouped(2).map {
    case List(a,b) => (b*256+a).U(16.W)
  }.toList
  val rom_mem = VecInit(romData)
  val ram_mem = Mem(8, UInt(8.W))

  val rom_addr = Reg(UInt(16.W))
  io.host.AD_out := rom_mem(rom_addr)
  io.host.A_out := ram_mem.read(io.host.A_in(3,0))

  when(!io.host.nCS && rRD){
    rom_addr := rom_addr + 1.U
  }.elsewhen(fCS){
    rom_addr := io.host.AD_in
  }

  when(!io.host.nCS2 && fWR) {
    ram_mem.write(io.host.AD_in(3, 0), io.host.A_in)
  }

  //io.board.debug := 0.U(8.W)
  io.board.debug := Cat(io.host.nRD, io.host.nCS, io.host.AD_out(2,0), rom_addr(2,0))
}
