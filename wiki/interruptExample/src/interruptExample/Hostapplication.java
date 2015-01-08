package interruptExample;

import easyfpga.generator.model.FPGA;
import easyfpga.generator.model.cores.GPIO8;
import easyfpga.communicator.*;
import easyfpga.exceptions.*;

public class Hostapplication {

    private GPIOFPGA fpga;
    private GPIO8 gpio;

    public static void main(String[] args) {
        Hostapplication myApplication = new Hostapplication();
        myApplication.setupAndToggle();
    }

    private void setupAndToggle() {
        try {
            // Open connection
            fpga = new GPIOFPGA();
            fpga.init();

            // Get GPIO core and make pin 0 act as output
            gpio = fpga.getGPIO();
            gpio.makeOutput(0);

            // Enable rising-edge interrupt on pin 1
            gpio.enableInterrupt(1, true);

            // Register interrupt listener
            InterruptListener myListener = new InputChangeListener();
            fpga.addInterruptListener(myListener);
            fpga.enableInterrupts();

            // Toggle pin 0 outputs
            for (int i = 0; i < 3; i++) {
                System.out.println("ON");
                gpio.setOutput(0, true);
                Thread.sleep(1000);

                System.out.println("OFF");
                gpio.setOutput(0, false);
                Thread.sleep(1000);
            }

            // Close the connection
            fpga.quit();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Implement the interface InterruptListener
    class InputChangeListener implements InterruptListener {

        @Override
        public void interruptHandler(InterruptEvent event) {

            System.out.println("Rising edge caused interrupt.");

            // Get the core that caused the interrupt
            GPIO8 gpio = (GPIO8) event.getCore();

            // Clear interrupt of GPIO core
            try {
                gpio.clearInterrupts();
            }
            catch (CommunicationException e) {
                e.printStackTrace();
            }

            // Re-enable global interrupts
            fpga.enableInterrupts();
        }
    }

}
