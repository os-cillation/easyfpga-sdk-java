# Handling Interrupts

Some easyCores such as the UART or GPIO core are able to generate interrupts on certain events such as the reception of data or change of an input pin.


## Enable Interrupts
The easyCores that are able to generate interrupts require an activation of these. This can be done using the core's `enableInterrupt()` method. The interrupts that can be activated can be found in the inner class called INT.

```java
myUart.enableInterrupt(UART.INT.RX_AVAILABLE);
```


## Implement Interrupt Listener
For undertake certain actions on the reception of an interrupt, implement the interface called InterruptListener. This interface defines a method named interruptHandler which will be called after an interrupt occured. The InterruptEvent parameter contains information about the core that generated the interrupt. This information can either be gathered by calling the method `getCoreAddress()` or calling `getCore()` as shown in the example below. After processing an interrupt, you always have to call `fpga.enableInterrupts()` in order to receive further interrupts.

```java
class ReceiveListener implements InterruptListener {

    @Override
    public void interruptHandler(InterruptEvent event) {

        UART uart = (UART) event.getCore();
        char received = (char) uart.receive();
        System.out.print(received);

        fpga.enableInterrupts();
    }
}
```

## Register Interrupt Listener
The implementations of the InterruptListener interface have to be registered to the FPGA object using the `addInterruptListener()` method. To enable global interrupt reception, the FPGA object's `enableInterrupts()` has to be called.

```
InterruptListener myListener = new ReceiveListener();
fpga.addInterruptListener(myListener);
fpga.enableInterrupts();
```

## Summarizing Example
Finally, here is an example regarding the usage of interrupts. Since the FPGA definition is similar to the one shown in the [getting started](getting_started.md) article it is omitted here. The example shows how interrupts of a GPIO8 easyCore are activated and detected:

```java
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
```
