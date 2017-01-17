/*
 * Author: <AUTHORNAME> (<AUTHOREMAIL>)
 * Committer: <COMMITTERNAME>
 *
 * Creation Date:  Tue Nov 1 14:34:09 GMT+1 2016
 * Module Name:    J1SoC - A small but complete system based on the J1-core
 * Project Name:   J1Sc - A simple J1 implementation in Scala using Spinal HDL
 *
 * Hash: 4f0f185ce5d5b072464389a58c4630ac8f660104
 * Date: Tue Jan 3 02:56:50 2017 +0100
 */
import spinal.core._
import spinal.lib.com.uart._

// Provide a MMCM
class MMCM extends BlackBox {

  val io = new Bundle {

    val providedClk = in Bool
    val synthClk = out Bool

  }

  noIoPrefix()

}

class J1SoC (j1Cfg   : J1Config,
             gpioCfg : GPIOConfig) extends Component {

  val io = new Bundle {

    // Asynchronous interrupts for the outside world
    val extInt = in Bits (j1Cfg.irqConfig.numOfInterrupts - j1Cfg.irqConfig.numOfInternalInterrupts bits)

    // The physical pins for the connected FPGAs
    val leds = out Bits(gpioCfg.ledBankConfig.width bits)

    // I/O pins for the UART
    val rx =  in Bool // UART input
    val tx = out Bool // UART output

  }.setName("")

  // Physical clock area
  val clkCtrl = new Area {

    // Create a MMCM instance and connect it to the current clock
    val mmcm = new MMCM
    mmcm.io.providedClk := ClockDomain.current.readClockWire

    // Create clock area which is related to the clock synthesized by the MMCM
    val coreClockDomain = ClockDomain(

      // Use the synthesized clock for this domain
      clock = mmcm.io.synthClk,

      // Use the current reset
      reset = ClockDomain.current.reset,

      // Scale the frequency according to the frequency provided by the MMCM
      frequency = FixedFrequency(ClockDomain.current.frequency.getValue*4/5)

    )

  }

  // Generate the application specific clocking area
  val coreArea = new ClockingArea(clkCtrl.coreClockDomain) {

    // Create a new CPU core
    val cpu = new J1(j1Cfg)

    // Create a delayed version of the cpu core interface to GPIO
    val peripheralBus = cpu.io.cpuBus.delayed(gpioCfg.gpioWaitStates)
    val peripheralBusCtrl = SimpleBusSlaveFactory(peripheralBus)

    // Create a LED array at base address 0x40
    val ledArray = new LEDArray(gpioCfg.ledBankConfig)
    val ledBridge = ledArray.driveFrom(peripheralBusCtrl, 0x40)

    // Connect the physical LED pins to the outside world
    io.leds := ledArray.io.leds

    // Create two timer and map it at 0xC0 and 0xD0
    val timerA = new Timer(gpioCfg.timerConfig)
    val timerABridge = timerA.driveFrom(peripheralBusCtrl, 0xC0)
    val timerB = new Timer(gpioCfg.timerConfig)
    val timerBBridge = timerB.driveFrom(peripheralBusCtrl, 0xD0)

    // Create an UART interface with fixed capabilities
    val uartCtrlGenerics = UartCtrlGenerics(dataWidthMax = gpioCfg.uartConfig.dataWidthMax,
      clockDividerWidth = gpioCfg.uartConfig.clockDividerWidth,
      preSamplingSize = gpioCfg.uartConfig.preSamplingSize,
      samplingSize = gpioCfg.uartConfig.samplingSize,
      postSamplingSize = gpioCfg.uartConfig.postSamplingSize)
    val uartCtrlInitConfig = UartCtrlInitConfig(baudrate = gpioCfg.uartConfig.baudrate,
      dataLength = gpioCfg.uartConfig.dataLength,
      parity = gpioCfg.uartConfig.parity,
      stop = gpioCfg.uartConfig.stop)
    val uartCtrlMemoryMappedConfig = UartCtrlMemoryMappedConfig(uartCtrlConfig = uartCtrlGenerics,
      initConfig = uartCtrlInitConfig,
      busCanWriteClockDividerConfig = false,
      busCanWriteFrameConfig = false,
      txFifoDepth = gpioCfg.uartConfig.fifoDepth,
      rxFifoDepth = gpioCfg.uartConfig.fifoDepth)
    val uartCtrl = new UartCtrl(uartCtrlGenerics)

    // Map the UART to 0x80 and enable the generation of read interrupts
    val uartBridge = uartCtrl.driveFrom16(peripheralBusCtrl, uartCtrlMemoryMappedConfig, baseAddress = 0x80)
    uartBridge.interruptCtrl.readIntEnable := True

    // Tell Spinal that some unneeded signals are allowed to be pruned to avoid warnings
    uartBridge.interruptCtrl.interrupt.allowPruning()
    uartBridge.write.streamUnbuffered.ready.allowPruning()

    // Create an interrupt controller, map it to 0xE0 and connect all interrupts
    val intCtrl = new InterruptCtrl(j1Cfg)
    val intCtrlBridge = intCtrl.driveFrom(peripheralBusCtrl, 0xE0)
    intCtrl.io.intsE(intCtrl.io.intsE.high downto j1Cfg.irqConfig.numOfInternalInterrupts) <> io.extInt
    intCtrl.io.intsE(0) <> uartBridge.interruptCtrl.readInt
    intCtrl.io.intsE(1) <> timerA.io.interrupt
    intCtrl.io.intsE(2) <> timerB.io.interrupt
    cpu.io.intNo <> intCtrl.io.intNo
    cpu.io.irq <> intCtrl.io.irq

    // Connect the physical UART pins to the outside world
    io.tx := uartCtrl.io.uart.txd
    uartCtrl.io.uart.rxd := io.rx

  }

}

object J1SoC {

  // Make the reset synchron and use the rising edge
  val globalClockConfig = ClockDomainConfig(clockEdge        = RISING,
                                            resetKind        = SYNC,
                                            resetActiveLevel = HIGH)

  def main(args : Array[String]) {

    // Configuration of CPU-core and GPIO system
    val j1Cfg = J1Config.debugIO
    val gpioCfg = GPIOConfig.default

    // Generate all VHDL files
    SpinalConfig(genVhdlPkg = true,
                 defaultConfigForClockDomains = globalClockConfig,
                 defaultClockDomainFrequency = FixedFrequency(80 MHz),
                 targetDirectory="gen/src/vhdl").generateVhdl({

                   // Set name for the synchronous reset
                   ClockDomain.current.reset.setName("clr")

                   // A new system instance
                   new J1SoC(j1Cfg, gpioCfg)

                 }).printPruned()

    // Generate all Verilog files
    SpinalConfig(defaultConfigForClockDomains = globalClockConfig,
                 defaultClockDomainFrequency = FixedFrequency(80 MHz),
                 targetDirectory="gen/src/verilog").generateVerilog({

                   // Set name for the synchronous reset
                   ClockDomain.current.reset.setName("clr")

                   // A new system instance
                   new J1SoC(j1Cfg, gpioCfg)

                 }).printPruned()

  }

}
