package dev.meirl.gamebrian

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

class GBABusMaster extends Bundle {
  val addr = Output(UInt(16.W))
  val mosi = Output(UInt(8.W))
  val miso = Input(UInt(8.W))
  val write = Output(Bool())
}

class GBACardIO extends Bundle {
  val bus = new GBABusMaster
}

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

class GBA extends Module {
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
  when(true.B){
    resyncRD := Cat(resyncRD(0), io.host.nRD)
    resyncCS := Cat(resyncCS(0), io.host.nCS)
  }

  val rRD = !resyncRD(1) && resyncRD(0)
  val fCS = resyncCS(1) && !resyncCS(0)

  val rom_mem = Mem(8, UInt(16.W))

  /** readmemh is put inside a "ifndef SYNTHESIS" block so have to manually move it */
  /** ROM is now loaded in through GameLink for speedier testing */
  //annotate(new ChiselAnnotation {
  //  override def toFirrtl = new LoadMemoryAnnotation(rom_mem.toNamed, "fire.mem")
  //})
  //loadMemoryFromFileInline(rom_mem, "ram.mem")

  // ROM logic
  val rom_addr = Reg(UInt(16.W))
  io.host.AD_out := rom_mem.read(rom_addr)

  when(!io.host.nCS && rRD){
    rom_addr := rom_addr + 1.U
  }.elsewhen(fCS){
    rom_addr := io.host.AD_in
  }

  // RAM logic goes out to bus to GBARam module
  // (and later through an interconnect)
  io.card.bus.addr := io.host.AD_in
  io.host.A_out := io.card.bus.miso
  io.card.bus.mosi := io.host.A_in
  io.card.bus.write := !io.host.nWR

  io.board.debug := 0.U(8.W)
}
