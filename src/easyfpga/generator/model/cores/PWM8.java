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

import easyfpga.exceptions.CommunicationException;

/**
 * An 8-bit pulse width modulation core
 */
public class PWM8 extends PWM {

    private BufferedRegister regDutyCycle = new BufferedRegister(REG.DUTYCYCLE);

    /**
     * Register address offsets
     */
    public final class REG {
        public static final int DUTYCYCLE = 0;
    }

    public void setDutyCycle(int dutyCycle) throws CommunicationException, IllegalArgumentException {

        /* check duty cycle value */
        if (dutyCycle > 0xFF) {
            throw new IllegalArgumentException("Invalid duty cycle");
        }
        /* set duty cycle */
        regDutyCycle.write(dutyCycle);
    }

    public int getDutyCycle() throws CommunicationException {
        return regDutyCycle.read();
    }

    @Override
    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regDutyCycle.getOffset()) regDutyCycle.write(data);
        else throw new CommunicationException("Invalid register address");
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regDutyCycle.getOffset()) return regDutyCycle.read();
        else throw new CommunicationException("Invalid register address");
    }
}
