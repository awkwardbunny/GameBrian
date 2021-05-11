package dev.meirl.gamebrian

import Chisel.Cat
import chisel3._
import chisel3.util.{Counter, MuxLookup}

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
  val (_, inc) = Counter(true.B, 250)
  val clk200 = RegInit(false.B)
  io.board.spi_sck := clk200

  val prevWrite = RegInit(false.B)
  val rWrite = !prevWrite && io.card.bus.write
  when(true.B) { prevWrite := io.card.bus.write }

  val REG_CMD = RegInit(0.U(8.W))
  val REG_ARG0 = RegInit(0.U(8.W))
  val REG_ARG1 = RegInit(0.U(8.W))
  val REG_ARG2 = RegInit(0.U(8.W))
  val REG_ARG3 = RegInit(0.U(8.W))
  val REG_CRC = RegInit(0.U(8.W))
  val REG_NUMH = RegInit(0.U(8.W))
  val REG_NUML = RegInit(0.U(8.W))

  val REG_RESP = RegInit(0.U(8.W))
  val REG_STAT = RegInit(0.U(8.W))

  val start = RegInit(false.B)

  io.card.bus.miso := MuxLookup(io.card.bus.addr(3,0), 0.U, Array(
    0x0.U(4.W) -> REG_CMD,
    0x1.U(4.W) -> REG_ARG0,
    0x2.U(4.W) -> REG_ARG1,
    0x3.U(4.W) -> REG_ARG2,
    0x4.U(4.W) -> REG_ARG3,
    0x5.U(4.W) -> REG_CRC,
    0x6.U(4.W) -> REG_NUMH,
    0x7.U(4.W) -> REG_NUML,
    0x8.U(4.W) -> REG_RESP,
    0x9.U(4.W) -> REG_STAT
  ))

  when(rWrite){
    when(io.card.bus.addr(3,0) === 0.U){ REG_CMD := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 1.U){ REG_ARG0 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 2.U){ REG_ARG1 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 3.U){ REG_ARG2 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 4.U){ REG_ARG3 := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 5.U){ REG_CRC := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 6.U){ REG_NUMH := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 7.U){ REG_NUML := io.card.bus.mosi }
      .elsewhen(io.card.bus.addr(3,0) === 9.U){ REG_STAT := io.card.bus.mosi }

    // LSB on REG_STAT triggers command
    when(io.card.bus.addr(3,0) === 0x9.U(8.W) && io.card.bus.mosi(0)){
      start := true.B
    }
  }

  // Do SPI stuff
  val sending = RegInit(false.B)
  val waiting = RegInit(false.B)
  val reading = RegInit(false.B)

  val cmdBuffer = RegInit(0x1000000000000L.U(49.W))
  io.board.spi_mosi := cmdBuffer(48)
  io.board.spi_cs := !(sending || reading || waiting)

  // Counter for sending CMD and receiving RESP
  val cmdCounter = RegInit(0.U(6.W))
  val respCounter = RegInit(0.U(3.W))

//  io.board.debug := Cat(start, sending, waiting, reading, io.board.spi_sck, io.board.spi_cs, io.board.spi_mosi, io.board.spi_miso)
  io.board.debug := Cat(io.card.bus.write, io.card.bus.mosi(7), start, sending, io.board.spi_sck, io.board.spi_cs, io.board.spi_mosi, io.board.spi_miso)

  when(inc){
    clk200 := ~clk200

    // TRANSMIT is done on negedge
    when(clk200) {
      // When start is triggered, put into SENDING mode and load shift buffer
      when(start) {
        start := false.B
        sending := true.B

        cmdCounter := 0.U
        cmdBuffer := Cat(true.B, REG_CMD, REG_ARG0, REG_ARG1, REG_ARG2, REG_ARG3, REG_CRC)
      }

      // When sending, shift out buffer (MOSI is connected to MSB of buffer)
      // When done sending, put into waiting mode
      when(sending) {
        when(cmdCounter === 50.U) {
          sending := false.B
          waiting := true.B
        }.otherwise {
          cmdCounter := cmdCounter + 1.U
          cmdBuffer := Cat(cmdBuffer(47, 0), true.B)
        }
      }
    }

    // RECEIVE is done on posedge
    when(!clk200){
      // When waiting and data in goes low, start reading
      when(waiting && !io.board.spi_miso) {
        waiting := false.B
        reading := true.B
        respCounter := 0.U
        REG_RESP := 0xFE.U(8.W)
      }

      // While reading, shift in miso to REG_RESP
      // When done, clear LSB of REG_STAT
      when(reading){
        // TODO: Continue reading if NUMH and NUML are not zero
        when(respCounter === 7.U){
          reading := false.B
          REG_STAT := Cat(REG_STAT(7, 1), false.B)
        }.otherwise{
          respCounter := respCounter + 1.U
          REG_RESP := Cat(REG_RESP(6,0), io.board.spi_miso)
        }
      }
    }
  }
}
