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
