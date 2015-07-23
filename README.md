# easyfpga-sdk-java
This is the easyFPGA software-development-kit for Java that simplifies the design of FPGA-based systems. Detailed information on how to use the SDK can be found in the [wiki](wiki/README.md). 

## Cloning
Since there is a submodule included in this repository, you have to clone it recursively:

```
$ git clone --recursive https://github.com/os-cillation/easyfpga-sdk-java.git
```

## Changelog

#### v1.1.0 (2015-07-23)
* Make HDL generation platform independent
* Fix device detection on Windows systems
* Make easyfpga-soc a submodule of the sdk repository
* Add version numbers

#### v1.0.3 (2015-04-09)
* Small wiki changes

#### v1.0.2 (2015-02-17)
* Add upload tool GUI
* Optimize SoC area usage
* Handle FPGA-Toolchain without shellscript
* Revise board device detection

#### v1.0.1 (2015-01-14)
* Add upload tool that allows uploading any FPGA binary without using the SDK
* Add standalone application template for developing custom HDL projects
* Revise logging facility

#### v1.0.0 (2015-01-08)
* Revise build process: Skip synthesis when HDL is unchanged. Merge build and buildFPGA targets
* Revise UART core class and documentation
