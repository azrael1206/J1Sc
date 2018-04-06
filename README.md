# J1Sc - A simple reimplementation of the [J1 CPU](http://www.excamera.com/sphinx/fpga-j1.html) in Scala using Spinal HDL

## How to build J1Sc and Swapforth

To build the J1Sc you need first to create the VDHL / Verilog sources. The implementation is written by using *Spinal HDL* (https://github.com/SpinalHDL/SpinalHDL), hence this sources are generated by a Scala program (Spinal HDL is a powerful Scala library which can be used to generate VHDL and Verilog code.

* Install a running Scala system and sbt

* Clone the latest version of Spinal HDL
   (see https://github.com/SpinalHDL/SpinalHDL)

* Setup Spinal HDL
   (see http://spinalhdl.github.io/SpinalDoc/spinal_getting_started/)

* Change directory to the new clone of SpinalHDL and run `sbt publish-local` inside

* Change directory to J1Sc

* Install gforth (e.g. `sudo apt-get install gforth`)

* Run `cd toolchain/forth && make && cd ../..` to build the forth core system

## J1Sc for a Digilent Nexys4 and Nexys4DDR board

* Build J1Sc (either using the VHDL or the Verilog version) by `sbt run` (select the Nexys4X configuration to be generated). The generated files can be found in `gen/src/vhdl/J1SoC.vhd` and `gen/src/verilog/J1SoC.v`. You need `Board_<BOARDNAME>.vhd` and `PLL.vhd` in `src/main/vhdl/arch` or the corresponding Verilog versions in `src/main/verilog/arch` as toplevel for synthesis.
A Xilinx Vivado project file `J1Sc.xpr` for the VHDL version can be found in `vprj/vhdl/J1Sc` and the Verilog version is in `vprj/verilog/J1Sc`. Note that J1Sc runs fine with a 100Mhz Clock on a Nexys4 DDR from Digilent. Constraint files for the Nexys4 DDR can be found in `/src/main/xilinx/nexys4ddr` the corresponding files for the Nexys4 can be found in `/src/main/xilinx/nexys4`.

* Build J1Sc (see `gen/src/vhdl` or `gen/src/verilog`) and send the .bit file to your FPGA/board (use either `src/main/vhdl/arch/Nexys4DDR/BoardNexys4DDR.vhd` or `src/main/verilog/arch/Nexys4DDR/BoardNexys4DDR.v` as toplevel module)

* `cd toolchain/forth`

* Become root or set the the permissions of your serial devices properly and run `bin/confsX`, where X is the number of the used serial port. Hence X is 0 if `/dev/ttyUSB0` should be used.

* Press the reset button (default is the "CPU reset" button on the Nexys4 DDR). You should see something like: `Contacting... established` `Loaded 142 words`

* Type `#include swapforth.fs` to load the complete FORTH system

* Turn the leds on by `$ffff leds!`

* Use the first RBG led by `5 10 10 rgbled!`

* Have fun with a working FORTH system

## J1Sc for the icoBoard

At the moment only one hardware configuration is supported

- icoBoard Version 1.1
- icoUSBaseboard: FTDI Interfacebasis (first FTDI interface for programming the FPGA)
- Pmod USBUART (second FTDI interface for serial connection)
- Pmod 8LD (simply eight leds)

Attach the [icoUSBBaseBoard](https://shop.trenz-electronic.de/en/TE0889-02-icoUSBaseboard-FTDI-Interfacebasis-fuer-das-icoBoard) to the icoBoard and connect the Pmods as shown [here](https://github.com/SteffenReith/J1Sc/blob/master/doc/misc/J1Sc_AES_IcoBoard.jpg). Hence use PMod P1 for the leds and the *upper row* (!) of PMod P3 for the UART.

Clone and install the latest version of

* yosys (https://github.com/cliffordwolf/yosys.git)

* Arachne-PNR (https://github.com/cseed/arachne-pnr.git)

* IceStorm (https://github.com/cliffordwolf/icestorm.git)

* IcoTools (https://github.com/cliffordwolf/icotools.git)

Now change to the cloned directory of J1Sc

* Build J1Sc (either using the VHDL or the Verilog version) by `sbt run` (select the IcoBoard configuration to be generated).

* go to the IcoBoard project directory by `cd lprj/IcoBoard`

* type `make fixit` (not necessarily needed, but avoids a lot of warnings)

* build everything by `make`. The last line should by similar to `Checking 25.00 ns (40.00 MHz) clock constraint: PASSED.`

* Download the configuration by `make prog`

* Become root or set the the permissions of your serial devices properly and run `bin/confsX`, where X is the number of the used serial port. Hence X is 0 if `/dev/ttyUSB0` should be used.

* Press the reset button (the button S1 near the PMod P2 is used by default). You should see something like: `Contacting... established` `Loaded 142 words`

* Type `#include swapforth.fs` to load the complete FORTH system

* Turn the leds on by `$ff leds!`

* Have fun with a running FORTH system on your icoBoard!

In principle it is possible to use the IcoBoard together with a Raspberry PI to run J1Sc. In the case you do this, please send me (EMail: streit@streit.cc) the needed steps, because I don´t have / use this configuration.

## Gatelevel simulation of J1Sc
The latest versions for SpinalHDL (you need at least version 1.1.2) offer a complete gatelevel simulation of the generated designs. In the background SpinalHDL uses Verilator as a simulation framework and hide all the C++ stuff by another Scala library called *SpinalSim*. Moreover to connect the simulation to the host a virtual null-model cable is used. Hence

* Clone and install the latest version of Verilator from http://git.veripool.org/git/verilator

* Clone and install the latest version of tty0tty from https://github.com/freemed/tty0tty.git. Load the kernel module by `insmod tty0tty.ko`. You have to set the permission of `/dev/tnt0` and `/dev/tnt1` such that they are user read- and writeable.
To make the installation permanently, install the kernel module according to your distribution (for Ubuntu 16.04.3 LTS ``cp tty0tty.ko /lib/modules/`uname -r`/kernel/drivers/misc`` and `depmod -a`). You can set the permissions of ´/dev/tnt0´ and ´/dev/tnt1´ by udev after every reboot automatically. For this see the udev-rule `55-tty-tnt.rules` in the directory `doc/udev`, modify it to your needs (e.g. the dialout group) and copy it e.g. to `/etc/udev/rules.d`.

* Go to you cloned J1Sc copy and type `cd toolchain/forth`

* start your terminal by `bin/confhost`

* open another shell window go to your copy of J1Sc and type `sbt test:run` to compile and run the simulation. If everything went well a small gui with some leds and a reset button will occur.

* Press the reset button. You should see something like: `Contacting... established` `Loaded 142 words` in your terminal window.

* Type `#include swapforth.fs` to load the complete FORTH system

* Turn the simulated leds on by `$ff leds!`

* Have fun with a running FORTH on the simulated J1Sc!

## A FORTH Shell/Terminal for J1Sc
Manfred Mahlow offers a really great terminal for embedded FORTH systems, which supports linux on X86 and Raspberry/Raspbian. Besides 430CamelForth , 430eForth , 4e4th, AmForth, anyForth , Mecrisp , Mecrisp-Stellaris , noForth and STM8 eForth it now supports the J1Sc with Swapforth. This solution gives a much higher comfort than the original Python-based terminals from Swapforth, hence it is suggested to use e4thcom.

Simply download e4thcom (https://wiki.forth-ev.de/doku.php/en:projects:e4thcom) and install (simply copy it to the installation directory) the e4thcom-plugin swapforth-j1sc.efc from support/e4thcom/.

Start enjoying e4thcom by the following command-line by `e4thcom-0.6.3.1 -d ttyUSB1 -b B115200 -t swapforth-j1sc`. In case anything does not work, please check for the correct transmission rate (B11520 for the Nexys4 and B38400 for the simulation) and the serial device (e.g. `/dev/tnt0` for the simulation). Make sure that the PATH-variable is set correctly and your J1Sc instance is connected to `/dev/ttyUSB1` or modify the command-line accordingly to your situation.