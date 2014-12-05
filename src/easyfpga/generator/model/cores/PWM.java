/*
 *  This file is part of easyFPGA.
 *  Copyright 2013,2014 os-cillation GmbH
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

public abstract class PWM extends Core {

    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Type type = name.equals(PIN.CLK) ? Type.IN : Type.OUT;
            Pin pin = new Pin(this, name, type);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

    public final class PIN {
        /**
         * PWM output, source pin
         */
        public static final String OUT = "pwm_out";
        /**
         * Optional clock input, sink pin
         */
        public static final String CLK = "clk_in";

    }

    /**
     * Set the duty cycle
     *
     * @param dutyCycle
     * @throws CommunicationException
     * @throws IllegalArgumentException
     *             in case the duty cycle is outside the allowed range
     */
    public abstract void setDutyCycle(int dutyCycle) throws CommunicationException, IllegalArgumentException;

    /**
     * Get the current duty cycle
     *
     * @return Current duty cycle
     * @throws CommunicationException
     */
    public abstract int getDutyCycle() throws CommunicationException;

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
}
