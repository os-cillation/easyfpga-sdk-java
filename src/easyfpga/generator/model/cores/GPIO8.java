/*
 *  This file is part of easyFPGA.
 *  Copyright 2013-2015 os-cillation GmbH
 *
 *  easyFPGA is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  easyFPGA is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with easyFPGA.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package easyfpga.generator.model.cores;

import easyfpga.exceptions.CommunicationException;
import easyfpga.generator.model.Bus;
import easyfpga.generator.model.Core;
import easyfpga.generator.model.Pin;
import easyfpga.generator.model.Pin.Type;

/**
 * An 8-Bit GPIO core. Note that the pins are bidirectional and cannot be connected internally.
 */
public class GPIO8 extends Core {

    private BufferedRegister regOutEnable = new BufferedRegister(REG.OE);
    private BufferedRegister regOut = new BufferedRegister(REG.OUT);
    private BufferedRegister regIntEnable = new BufferedRegister(REG.INTE);
    private BufferedRegister regIntPtrig = new BufferedRegister(REG.PTRIG);

    private boolean globalInterruptsEnabled = false;

    public final class PIN {
        public static final String GPIO0 = "gpio0";
        public static final String GPIO1 = "gpio1";
        public static final String GPIO2 = "gpio2";
        public static final String GPIO3 = "gpio3";
        public static final String GPIO4 = "gpio4";
        public static final String GPIO5 = "gpio5";
        public static final String GPIO6 = "gpio6";
        public static final String GPIO7 = "gpio7";
    }

    public final class REG {
        /**
         * GPIO inputs
         */
        public static final int IN = 0x00;
        /**
         * GPIO outputs
         */
        public static final int OUT = 0x04;
        /**
         * Output enable
         */
        public static final int OE = 0x08;
        /**
         * Interrupt enable
         */
        public static final int INTE = 0x0C;
        /**
         * Type of edge that triggers an interrupt. Positive edge when set, negative edge when
         * cleared.
         */
        public static final int PTRIG = 0x10;
        /**
         * Control register<br>
         * Bit #0: Global interrupt enable<br>
         * Bit #1: When set, interrupt is pending
         */
        public static final int CTRL = 0x18;
        /**
         * Interrupt status. Indicates which input has caused the interrupt
         */
        public static final int INTS = 0x1C;
    }

    /**
     * Make a certain pin given by pinNumber act as an output
     * @param pinNumber 0..7
     * @throws CommunicationException
     */
    public void makeOutput(int pinNumber) throws CommunicationException {
        checkPinNo(pinNumber);
        regOutEnable.changeBit(pinNumber, true);
    }

    /**
     * Set a logic level to a single output pin
     * @param pinNumber 0..7
     * @param logicLevel true: high (3.3V); false: low (0V)
     * @throws CommunicationException
     */
    public void setOutput(int pinNumber, boolean logicLevel) throws CommunicationException {
        checkPinNo(pinNumber);
        regOut.changeBit(pinNumber, logicLevel);
    }

    /**
     * Set all 8 outputs according to an 8-bit integer. Only affects pins that are configured as
     * outputs.
     * @param value 0 .. 0xFF
     * @throws CommunicationException
     */
    public void setAllPins(int value) throws CommunicationException {
        regOut.write(value);
    }

    /**
     * Make a certain pin given by pinNumber act as an input (which is the default behavior)
     * @param pinNumber 0..7
     * @throws CommunicationException
     */
    public void makeInput(int pinNumber) throws CommunicationException {
        checkPinNo(pinNumber);
        regOutEnable.changeBit(pinNumber, false);
    }

    /**
     * Get the level of a certain input pin given by pinNumber. Will also return a valid level when
     * called on pins that are configured as outputs.
     * @param pinNumber 0..7
     * @return True if pin is high
     * @throws CommunicationException
     */
    public boolean getInput(int pinNumber) throws CommunicationException {
        checkPinNo(pinNumber);
        int data = readRegister(REG.IN);
        if ((data & (1 << pinNumber)) != 0) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Get all 8 inputs as an integer. Pins configured as output pins will be included in the
     * result.
     * @return integer representation of current state
     * @throws CommunicationException
     */
    public int getAllPins() throws CommunicationException {
        return readRegister(REG.IN);
    }

    /**
     * Enable interrupt generation of a certain pin given by pinNumber. The interrupt is triggered
     * on the rising edge of the pin.
     * @param pinNumber 0..7
     * @throws CommunicationException
     */
    public void enableInterrupt(int pinNumber) throws CommunicationException {
        enableInterrupt(pinNumber, true);
    }

    /**
     * Enable interrupt generation of a certain pin given by pinNumber.
     * @param pinNumber 0..7
     * @param risingEdge True: Rising Edge triggered; False: Falling edge triggered
     * @throws CommunicationException
     */
    public void enableInterrupt(int pinNumber, boolean risingEdge) throws CommunicationException {
        checkPinNo(pinNumber);
        enableGlobalInterrupts();

        if (isInput(pinNumber)) {
            regIntEnable.changeBit(pinNumber, true);
            regIntPtrig.changeBit(pinNumber, risingEdge);
        }
        else {
            throw new IllegalArgumentException("Can only enable interrupts of input pins");
        }
    }

    /**
     * Get a bitmask of pins with pending interrupts
     * @return integer representation of the bitmask
     * @throws CommunicationException
     */
    public int getInterruptStatus() throws CommunicationException {
        return readRegister(REG.INTS);
    }

    /**
     * Clear all pending interrupts
     * @throws CommunicationException
     */
    public void clearInterrupts() throws CommunicationException {
        writeRegister(GPIO8.REG.INTS, 0x00);
    }

    private void enableGlobalInterrupts() throws CommunicationException {
        if (!globalInterruptsEnabled) {
            /* read, modify, write */
            int tmp = readRegister(REG.CTRL);
            tmp |= 0x01;
            writeRegister(REG.CTRL, tmp);
            this.globalInterruptsEnabled = true;
        }
    }

    private void checkPinNo(int pinNumber) {
        if (pinNumber > 7 || pinNumber < 0) {
            throw new IllegalArgumentException("Invalid pin number");
        }
    }

    private boolean isInput(int pinNumber) throws CommunicationException {
        checkPinNo(pinNumber);
        return !regOutEnable.getBit(pinNumber);
    }

    /*
     * Mark all gpio pins as INOUT-types
     */
    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Pin pin = new Pin(this, name, Type.INOUT);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

    /**
     * Get a bus containing all GPIO pins
     * @return 8-pin bus
     */
    public Bus getBus() {
        Bus bus = new Bus();
        bus.add(0, getPin(PIN.GPIO0));
        bus.add(1, getPin(PIN.GPIO1));
        bus.add(2, getPin(PIN.GPIO2));
        bus.add(3, getPin(PIN.GPIO3));
        bus.add(4, getPin(PIN.GPIO4));
        bus.add(5, getPin(PIN.GPIO5));
        bus.add(6, getPin(PIN.GPIO6));
        bus.add(7, getPin(PIN.GPIO7));
        return bus;
    }

    @Override
    public String getGenericMap() {
        return null;
    }

    @Override
    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regOutEnable.getOffset()) regOutEnable.write(data);
        else if (offset == regOut.getOffset()) regOut.write(data);
        else if (offset == regIntEnable.getOffset()) regIntEnable.write(data);
        else if (offset == regIntPtrig.getOffset()) regIntPtrig.write(data);
        else wrRegister(offset, data);
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regOutEnable.getOffset()) return regOutEnable.read();
        else if (offset == regOut.getOffset()) return regOut.read();
        else if (offset == regIntEnable.getOffset()) return regIntEnable.read();
        else if (offset == regIntPtrig.getOffset()) return regIntPtrig.read();
        else return rdRegister(offset);
    }
}
