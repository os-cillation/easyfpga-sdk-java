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
