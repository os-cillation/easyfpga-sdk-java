# Getting started

This article describes the steps to build a simple "HelloFPGA" application including a GPIO core. Before you start, read the article regarding the [prerequisites](prerequisites.md).

## Test your Setup
* Get the project template from [here](projectTemplate/). For this purpose it is most convenient to clone this repository.
* Get a recent copy of the [easyFPGA.jar](../dist/easyFPGA.jar) archive from the [dist directory](../dist/). In the template, create a directory named `lib` and move the jar into it.
* Ensure that Java 7 is your default. This is how you can check your current default Java version:

```
$ java -version
java version "1.7.0_65"
```

If necessary, the default Java version used can be changed with

```
$ sudo update-alternatives --config java
```

* Before you start to modify the sources, try to build them as they are:

```
$ ant buildFPGA
$ ant jar
```

The first Ant target will generate VHDL code for the FPGA and use the Xilinx tools to generate a binary file for the FPGA. In case the Xilinx tools have not been installed in the default location, this step will fail. When you run or compile for the first time, a configuration file will be created in `~/.config/easyfpga.conf`. Here, you can set the directory where easyFPGA will look for the Xilinx tools and try again. For further details see the article regarding the [configuration](configuration.md).

The second Ant target will compile the Java code and generate a deployable jar file including the FPGA binary.

* If everything went fine you will see a directory named dist including the file Application.jar
* After connecting your board, you can now run the application:

```
$ java -jar dist/Application.jar
```

This should start uploading the FPGA binary to the board an then run the application that toggles pins 0..7 of bank 0 using a GPIO8 easyCore. You can now start creating a custom FPGA application.

## Implement FPGA
In order to give a general guideline on how to implement an FPGA we first discuss the FPGA definition of the project template:

```java
package myEasyFPGAApplication;

import easyfpga.generator.annotation.EasyFPGA;
import easyfpga.generator.model.FPGA;
import easyfpga.generator.model.cores.GPIO8;
import easyfpga.exceptions.*;

@EasyFPGA
public class HelloFPGA extends FPGA {
    private GPIO8 gpio;

    @Override
    public void build() throws BuildException {
        gpio = new GPIO8();
        connect(gpio.getPin(GPIO8.PIN.GPIO0), getPin(0, 0));
        connect(gpio.getPin(GPIO8.PIN.GPIO1), getPin(0, 1));
        connect(gpio.getPin(GPIO8.PIN.GPIO2), getPin(0, 2));
        connect(gpio.getPin(GPIO8.PIN.GPIO3), getPin(0, 3));
        connect(gpio.getPin(GPIO8.PIN.GPIO4), getPin(0, 4));
        connect(gpio.getPin(GPIO8.PIN.GPIO5), getPin(0, 5));
        connect(gpio.getPin(GPIO8.PIN.GPIO6), getPin(0, 6));
        connect(gpio.getPin(GPIO8.PIN.GPIO7), getPin(0, 7));
    }

    public GPIO8 getGPIO() {
        return gpio;
    }
}
```

* In your easyFPGA project you will always have one class anotated with `@EasyFPGA`. This is a hint to the VHDL-Generator which class should be used to generate a hardware description.
* The annotated class always extends (inherits from) the `FPGA` class.
* This inheritance requires the implementation of the `build()` method. This method should include the instantiation of all easyCores and calls of the connect method.
* The connect method connects a source- to a sink-pin. In order to get a certain pin, the easyCores as well as the FPGA have `getPin()` methods.
* The getter method `getGPIO()` will be called by the host application for referencing the GPIO easyCore

## Implement Host Application
In the following we will discuss the host application included in the template:

```java
package myEasyFPGAApplication;

import easyfpga.generator.model.FPGA;
import easyfpga.generator.model.cores.GPIO8;

public class Hostapplication {

    private HelloFPGA fpga;
    private GPIO8 gpio;

    public static void main(String[] args) {
        Hostapplication myApplication = new Hostapplication();
        myApplication.blink();
    }

    private void blink() {
        try {
            /* open connection */
            fpga = new HelloFPGA();
            fpga.init();

            /* get the GPIO core (needs to be called after fpga.init()) */
            gpio = fpga.getGPIO();

            /* make all pins act as outputs */
            for (int i = 0; i < 7; i++) {
                gpio.makeOutput(i);
            }

            /* toggle all outputs */
            for (int i = 0; i < 3; i++) {
                System.out.println("ON");
                gpio.setAllPins(0xFF);
                Thread.sleep(1000);

                System.out.println("OFF");
                gpio.setAllPins(0x00);
                Thread.sleep(1000);
            }

            /* close the connection */
            fpga.quit();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

* Here you see an ordinary class including a main method that calls `blink()`.
* After constructing an object of HelloFPGA, the `init()` method (which is inherited from FPGA) is called. This opens a connection to the board and uploads the FPGA binary.
* After getting the GPIO core from the HelloFPGA instance, all pins of the core are configured to act as outputs.
* Using the GPIO core's `setAllPins()` method all pins are switched on and off for three times.
* Before the applications exits, the `quit()` method is called on the HelloFPGA instance. This method should always be called in order to ensure a clean disconnection of the board.

## What's next?
Before you start using further easyCores it is recommended to get familiar with how to handle [interrupts](interrupts.md) generated by certain cores in the FPGA.
