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

object SDCard {
  object State extends ChiselEnum {
    val idle,
    start,
    sendingCmd,
    waitingResp,
    readingResp,
    waitingData,
    readingData = Value
  }
}

import SDCard.State._

class SDCard extends Module {
  val io = IO(new SDCardIO)

  // 200kHz clock from 100MHz
  val (_, tSCK) = Counter(true.B, 250)
  val clk200 = RegInit(false.B)
  when(tSCK) { clk200 := ~clk200 }
  io.board.spi_sck := clk200

  val posedge = WireDefault(tSCK && !clk200)
  val negedge = WireDefault(tSCK && clk200)

  val prevWrite = RegInit(false.B)
  val rWrite = !prevWrite && io.card.bus.write
  prevWrite := io.card.bus.write

  val REG_CMD = RegInit(0.U(8.W))
  val REG_ARG0 = RegInit(0.U(8.W))
  val REG_ARG1 = RegInit(0.U(8.W))
  val REG_ARG2 = RegInit(0.U(8.W))
  val REG_ARG3 = RegInit(0.U(8.W))
  val REG_CRC = RegInit(0.U(8.W))
  val REG_NUMH = RegInit(0.U(8.W))
  val REG_NUML = RegInit(0.U(8.W))

  val REG_RESP = RegInit(0.U(8.W))
  val REG_STAT = RegInit(0x02.U(8.W))
  io.board.spi_cs := REG_STAT(1)

  val state = RegInit(idle)

  // Do SPI stuff
  val cmdBuffer = RegInit(0x1000000000000L.U(49.W))
  io.board.spi_mosi := cmdBuffer(48)

  val cmdCounter = RegInit(0.U(6.W))
  val respCounter = RegInit(0.U(3.W))
  val dataMem = Mem(2048, UInt(8.W))


  io.card.bus.miso := Mux(io.card.bus.addr(11), dataMem.read(io.card.bus.addr),
    MuxLookup(io.card.bus.addr(3, 0), 0.U, Array(
      0x0.U -> REG_CMD,
      0x1.U -> REG_ARG0,
      0x2.U -> REG_ARG1,
      0x3.U -> REG_ARG2,
      0x4.U -> REG_ARG3,
      0x5.U -> REG_CRC,
      0x6.U -> REG_NUMH,
      0x7.U -> REG_NUML,
      0x8.U -> REG_RESP,
      0x9.U -> REG_STAT
    ))
  )

  when(rWrite) {
    when(io.card.bus.addr(3, 0) === 0.U) { REG_CMD := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 1.U) { REG_ARG0 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 2.U) { REG_ARG1 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 3.U) { REG_ARG2 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 4.U) { REG_ARG3 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 5.U) { REG_CRC  := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 6.U) { REG_NUMH := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3, 0) === 7.U) { REG_NUML := io.card.bus.mosi }
      // 0x08: REG_RESP is read-only
      .elsewhen(io.card.bus.addr(3, 0) === 9.U) { REG_STAT := io.card.bus.mosi
        // LSB on REG_STAT triggers command
        when(io.card.bus.mosi(0)){
          state := start
          cmdCounter := 0.U
          cmdBuffer := Cat(true.B, REG_CMD, REG_ARG0, REG_ARG1, REG_ARG2, REG_ARG3, REG_CRC)
        }
      }
  }

  //  val dataCounter = RegInit(0.U(11.W))
  //  val bitCounter = RegInit(0.U(3.W))
  //  val byteBuffer = RegInit(0.U(8.W))

  val waitCounter = RegInit(0.U(8.W))

  switch(state) {
    is(start){
      when(negedge){
        state := sendingCmd
      }
    }
    is(idle) {
      when(REG_STAT(0)){
        REG_STAT := Cat(REG_STAT(7, 1), false.B)
      }
      // When triggered, put into SENDING mode and load shift buffer
      //when(negedge && rWrite && (io.card.bus.addr(11, 0) === 0x009.U(12.W)) && io.card.bus.mosi(0)) {
      //  cmdCounter := 0.U
      //  cmdBuffer := Cat(true.B, REG_CMD, REG_ARG0, REG_ARG1, REG_ARG2, REG_ARG3, REG_CRC)

      //  state := sendingCmd
      //}
    }
    // When sending, shift out buffer (MOSI is connected to MSB of buffer)
    // When done sending, put into waiting mode
    is(sendingCmd) {
      // TRANSMIT is done on negedge
      when(negedge) {
        when(cmdCounter === 49.U) {
          waitCounter := 0.U
          state := waitingResp
        }.otherwise {
          cmdCounter := cmdCounter + 1.U
          cmdBuffer := Cat(cmdBuffer(47, 0), true.B)
        }
      }
    }
    is(waitingResp) {
      // RECEIVE is done on posedge
      // Go to READING when miso goes LOW
      when(posedge){
        when(!io.board.spi_miso) {
          respCounter := 0.U
          REG_RESP := 0xFE.U(8.W) // Read first bit in

          state := readingResp
        }.otherwise{
          waitCounter := waitCounter + 1.U
          when(waitCounter === 0xFF.U){
            REG_RESP := 0xFF.U
            REG_STAT := Cat(REG_STAT(7, 1), false.B)
            state := idle
          }
        }
      }
    }
    is(readingResp) {
      // RECEIVE is done on posedge
      when(posedge) {
        // When done, clear LSB of REG_STAT
        when(respCounter === 7.U) {
          //          when(REG_NUMH.orR() || REG_NUML.orR()){
          //            state := waitingData
          //          }.otherwise{
          REG_STAT := Cat(REG_STAT(7, 1), false.B)
          state := idle
          //          }
          // While reading, shift in miso to REG_RESP
        }.otherwise {
          respCounter := respCounter + 1.U
          REG_RESP := Cat(REG_RESP(6, 0), io.board.spi_miso)
        }
      }
    }
    is(waitingData) {
      state := idle
      //      when(posedge && !io.board.spi_miso){
      //        dataCounter := 0.U
      //        bitCounter := 0.U
      //        state := readingData
      //      }
    }
    is(readingData) {
      state := idle
//      when(posedge) {
//        when(dataCounter > Cat(REG_NUMH(2, 0), REG_NUML)) {
//          REG_STAT := Cat(REG_STAT(7, 1), false.B)
//          state := idle
//        }.otherwise {
//          byteBuffer := Cat(byteBuffer(6, 0), io.board.spi_miso)
//          bitCounter := bitCounter + 1.U
//
//          when(bitCounter === 0.U) {
//            when(!(dataCounter === 0.U)) {
//              dataMem.write(dataCounter - 1.U, byteBuffer)
//            }
//            dataCounter := dataCounter + 1.U
//          }
//        }
//      }
    }
  }

  //  io.board.debug := Cat(start, sending, waiting, reading, io.board.spi_sck, io.board.spi_cs, io.board.spi_mosi, io.board.spi_miso)
  io.board.debug := Cat(state === idle, state === sendingCmd, state === waitingResp, state === readingResp, io.board.spi_sck, io.board.spi_cs, io.board.spi_mosi, io.board.spi_miso)
}