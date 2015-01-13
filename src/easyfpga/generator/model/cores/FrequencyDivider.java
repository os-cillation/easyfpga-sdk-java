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

import java.util.Iterator;

import easyfpga.exceptions.CommunicationException;
import easyfpga.generator.model.Connection;
import easyfpga.generator.model.Core;
import easyfpga.generator.model.Pin;
import easyfpga.generator.model.Pin.Type;

/**
 * A configurable frequency divider core capable of dividing a clock signal by an 8-bit
 * integer or deactivating the output. Leaving the input pin unconnected results in a connection
 * to the global 80 MHz clock.
 */
public class FrequencyDivider extends Core {

    BufferedRegister regDivider = new BufferedRegister(REG.DIV);

    public final class REG {
        public static final int DIV = 0;
    }

    public final class PIN {
        /**
         * Divided or deactivated clock output
         */
        public static final String OUT = "clk_out";
        /**
         * Clock input. 80 MHz if left unconnected.
         */
        public static final String IN = "clk_in";
    }


    /**
     * Set the clock divisor
     * @param divisor 0: stop the clock output, 1: forward input clock, 2..255: divide clock
     * @throws CommunicationException
     */
    public void setDivisor(int divisor) throws CommunicationException {
        if (divisor < 0 || divisor > 0xff) {
            throw new IllegalArgumentException("Illegal divisor value");
        }
        regDivider.write(divisor);
    }

    /**
     * Stop the output clock by setting divisor to zero
     * @throws CommunicationException
     */
    public void stopOutputClock() throws CommunicationException {
        regDivider.write(0x00);
    }

    /**
     * Bypass the core by setting the divisor to 1
     * @throws CommunicationException
     */
    public void bypass() throws CommunicationException {
        regDivider.write(0x01);
    }

    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Type type = name.equals(PIN.IN) ? Type.IN : Type.OUT;
            Pin pin = new Pin(this, name, type);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

    @Override
    public String getGenericMap() {
        for (Iterator<Connection> it = fpga.getConnectionsIn(this).iterator(); it.hasNext();) {
            for (Pin inputPin : it.next().getSinks()) {
                if (inputPin.getName() == "clk_in") {
                    return "USER_CLK => true";
                }
            }
        }
        return null;
    }

    @Override
    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regDivider.getOffset()) {
            regDivider.write(data);
        }
        else {
            wrRegister(offset, data);
        }
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regDivider.getOffset()) {
            return regDivider.read();
        }
        else {
            return rdRegister(offset);
        }
    }
}
