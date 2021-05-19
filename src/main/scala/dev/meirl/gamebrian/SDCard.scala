package dev.meirl.gamebrian

import Chisel.{Cat, switch}
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{BitPat, Counter, MuxLookup, is}

class SDCardHostIO extends Bundle {}
class SDCardCardIO extends Bundle {
  val bus = Flipped(new GBABusMaster)
}
class SDCardBoardIO extends Bundle {
  val spi_cs = Output(Bool())
  val spi_sck = Output(Bool())
  val spi_mosi = Output(Bool())
  val spi_miso = Input(Bool())

  val debug = Output(UInt(8.W))
}

class SDCardIO extends Bundle {
  val host = new SDCardHostIO
  val board = new SDCardBoardIO
  val card = new SDCardCardIO
}

class SDCard extends Module {
  val io = IO(new SDCardIO)

  // 200kHz clock from 100MHz
  val (_, tSCK) = Counter(true.B, 250)
  val clk200 = RegInit(false.B)
  when(tSCK) { clk200 := ~clk200 }

  val SPI_DATA = RegInit(0.U(8.W))
  val SPI_CTRL = RegInit(0x02.U(8.W))

  val busy = WireDefault(SPI_CTRL(0))
  val csel = WireDefault(SPI_CTRL(1))

  // READ
  io.card.bus.miso := MuxLookup(io.card.bus.addr(3, 0), 0.U, Array(
    0x0.U -> SPI_DATA,
    0x1.U -> SPI_CTRL
  ))

  // WRITE
  val prevWrite = RegInit(0.U(2.W))
  val rWrite = !prevWrite(1) && prevWrite(0)
  prevWrite := Cat(prevWrite(0), io.card.bus.write)

  when(rWrite) {
    when(io.card.bus.addr(3, 0) === 0.U) { SPI_DATA := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 1.U) { SPI_CTRL := io.card.bus.mosi }
  }

  // Do SPI stuff
  val posedge = WireDefault(tSCK && !clk200)
  val negedge = WireDefault(tSCK && clk200)

  // busy signal sync'd to clk200
  val busyS = RegInit(false.B)
  when(negedge){ busyS := busy }

  io.board.spi_cs := csel
  io.board.spi_sck := Mux(busyS, clk200, false.B)
  io.board.spi_mosi := Mux(busyS, SPI_DATA(7), true.B)

  val counter = RegInit(0.U(3.W))
  val misoBuf = RegInit(false.B)

  // Send on negedge
  when(negedge){
    when(busyS){
      // Shift out
      SPI_DATA := Cat(SPI_DATA(6,0), misoBuf)
      counter := counter + 1.U
      when(counter.andR()){
        // Finish
        SPI_CTRL := Cat(SPI_CTRL(7,1), false.B)
        busyS := false.B
      }
    }
  }

  // Receive on posedge
  when(posedge){
    when(busyS){
      misoBuf := io.board.spi_miso
    }
  }

  io.board.debug := Cat(io.card.bus.write, rWrite, clock.asBool, busy, io.board.spi_sck, io.board.spi_cs, io.board.spi_mosi, io.board.spi_miso)
}