# Configuration file

When using easyFPGA for the first time, a configuration file will be created in ~/.config/easyfpga.conf. Since the parameters are commented this file should be self-explanatory. Mind to check the first entry (XILINX_DIR) in order to be able to synthesize FPGA binaries. Initially the configuration file will look the following:

```
###############################
# easyFPGA configuration file #
###############################

# Location of Xilinx toolchain binaries
XILINX_DIR = /opt/Xilinx/14.7/ISE_DS/ISE/bin/lin64

# Default USB device the board is connected to. If commented out, easyFPGA
# will use /dev/ttyUSBn with the lowest n found.
#USB_DEVICE = /dev/ttyUSB0

# For using the CAN bus controller core, you have to download the sources
# from opencores.com and copy them to a location of your choice:
#CAN_SOURCES = /absolute/path/to/sources

# Uncomment to show entire output of FPGA toolchain during build
# FPGA_BUILD_VERBOSE = TRUE`

```
