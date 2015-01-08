# Prerequisites
This article covers the software that is required to use the easyFPGA SDK. Some requirements only concern the development system (the computer you use to build an executable jar file) and can be omitted for the host system (the one that actually connects to the board).

## Operating System
By now, easyFPGA is tested on Ubuntu 12.04 LTS (Precise Pangolin) but will probably run on most Linux based systems.

## Java
On the development system, easyFPGA requires a Java 7 JDK. It is tested on the open-source implementation openjdk-7. On the host system a runtime environment (JRE) is sufficient. On the development system also you may want to install an IDE such as Eclipse or NetBeans.

## Xilinx ISE
For generating binary files for the FPGA, the Xilinx toolchain is required. It is included in the [ISE WebPACK](http://www.xilinx.com/products/design-tools/ise-design-suite/ise-webpack.htm) which is free of charge after registration. The SDK is tested to work with Version 14.7. Since the FPGA binary can be included in an executable jar file, the ISE is only required on the development system. It is not necessary to install the Xilinx cable drivers.

## Permissions
An easyFPGA board connected via USB will appear as a ttyUSB device. For getting permissions to access the device add your user to the `dialout` group as follows:

```
$ sudo usermod -aG dialout USERNAME
```
