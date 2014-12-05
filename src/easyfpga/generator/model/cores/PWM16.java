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
 * A 16-bit pulse width modulation (PWM) core
 */
public class PWM16 extends PWM {

    private BufferedRegister regDutyCycleLo = new BufferedRegister(REG.DUTYCYCLE_LO);
    private BufferedRegister regDutyCycleHi = new BufferedRegister(REG.DUTYCYCLE_HI);

    /**
     * Register address offsets
     */
    public final class REG {
        public static final int DUTYCYCLE_LO = 0x00;
        public static final int DUTYCYCLE_HI = 0x01;
    }

    /**
     * Set the duty cycle
     *
     * @param dutyCycle to be set (0 .. 0xFFFF)
     * @throws CommunicationException
     * @throws IllegalArgumentException
     *          in case the duty cycle is outside the allowed range
     */
    public void setDutyCycle(int dutyCycle) throws CommunicationException, IllegalArgumentException {

        /* check duty cycle value */
        if (dutyCycle > 0xFFFF || dutyCycle < 0) {
            throw new IllegalArgumentException("Invalid duty cycle");
        }

        /* write duty cycle registers */
        int dutyCycleLow = dutyCycle & 0xFF;
        int dutyCycleHigh = (dutyCycle & 0xFF00) >>> 8;
        wrRegisterAAI(REG.DUTYCYCLE_LO, new int[] {dutyCycleLow, dutyCycleHigh});

        /* update BufferedRegisters */
        regDutyCycleLo.setValue(dutyCycleLow);
        regDutyCycleHi.setValue(dutyCycleHigh);
    }

    /**
     * Get the current duty cycle
     *
     * @return the current duty cycle (0 .. 0xFFFF)
     * @throws CommunicationException
     */
    public int getDutyCycle() throws CommunicationException {
        int valueLo = regDutyCycleLo.read();
        int valueHi = regDutyCycleHi.read();

        return ((valueLo & 0xFF) + (valueHi & 0xFF) << 8);
    }

    @Override
    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regDutyCycleLo.getOffset()) regDutyCycleLo.write(data);
        else if (offset == regDutyCycleHi.getOffset()) regDutyCycleHi.write(data);
        else throw new CommunicationException("Invalid register address");
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regDutyCycleLo.getOffset()) return regDutyCycleLo.read();
        else if (offset == regDutyCycleHi.getOffset()) return regDutyCycleHi.read();
        else throw new CommunicationException("Invalid register address");
    }
}
