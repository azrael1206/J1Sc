/*
 * Author: <AUTHORNAME> (<AUTHOREMAIL>)
 * Committer: <COMMITTERNAME>
 *
 * Creation Date:  Tue Nov 1 14:34:09 GMT+1 2016
 * Module Name:    J1SoC - A small but complete system based on the J1-core
 * Project Name:   J1Sc - A simple J1 implementation in Scala using Spinal HDL
 *
 * Hash: ec2c5c04988bb976040975bae0e46966d21cc9e9
 * Date: Tue Nov 1 15:34:51 2016 +0100
 */
import spinal.core._

class J1SoC (config : J1Config) extends Component {

  val io = new Bundle {

    val leds = out Bits(ledBankWidth bits) // The physical pins for the connected FPGAs

  }.setName("")

  // Parameters for peripheral bus
  def ledBankWidth        = 16
  def peripheralWaitState =  1

  // Create a new CPU core
  val cpuCore = new J1(config)

  // Create a bus for the cpu core used for connecting simple IOs
  val cpuBus = SimpleBus(config.addrWidth, config.wordSize)

  // Connect to the cpu bus and enable it permanently
  cpuBus.enable       := cpuCore.io.readEnable || cpuCore.io.writeEnable
  cpuBus.writeMode    <> cpuCore.io.writeEnable
  cpuBus.address      <> cpuCore.io.dataAddress
  cpuCore.io.dataRead <> cpuBus.readData
  cpuBus.writeData    <> cpuCore.io.dataWrite

  // Create a delayed version of the cpu core
  val peripheralBus = cpuBus.delayed(peripheralWaitState)
  val peripheralBusCtrl = SimpleBusSlaveFactory(peripheralBus)

  // Create a LED bank at address 0x00 and connect it to the outside world
  val ledBank = new LEDBank(GPIOConfig.default.ledConfig.width,
                            GPIOConfig.default.ledConfig.lowActive)
  val ledBridge = ledBank.driveFrom(peripheralBusCtrl, 0x00)

  // Drive the leds from the LEDBank register
  io.leds := ledBank.io.leds

}

object J1SoC {

  // Make the reset synchron and use the rising edge
  val globalClockConfig = ClockDomainConfig(clockEdge        = RISING,
                                            resetKind        = SYNC,
                                            resetActiveLevel = HIGH)

  def main(args: Array[String]) {

    // Generate HDL files
    SpinalConfig(genVhdlPkg = true,
      defaultConfigForClockDomains = globalClockConfig,
      targetDirectory="gen/src/vhdl").generateVhdl({

      // Set name for the synchronous reset
      ClockDomain.current.reset.setName("clr")
      new J1SoC(J1Config.debug)

    }).printPruned()
    SpinalConfig(defaultConfigForClockDomains = globalClockConfig,
      targetDirectory="gen/src/verilog").generateVerilog({

      // Set name for the synchronous reset
      ClockDomain.current.reset.setName("clr")
      new J1SoC(J1Config.debug)

    }).printPruned()

  }

}
