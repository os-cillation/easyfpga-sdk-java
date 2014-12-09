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

import easyfpga.communicator.RegisterReadCallback;
import easyfpga.exceptions.*;
import easyfpga.generator.model.Core;
import easyfpga.generator.model.Pin;
import easyfpga.generator.model.Pin.Type;

/**
 * An I2C master core
 */
public class I2CMaster extends Core {

    private BufferedRegister regPrescalerLo = new BufferedRegister(REG.PRER_L);
    private BufferedRegister regPrescalerHi = new BufferedRegister(REG.PRER_H);
    private BufferedRegister regControl = new BufferedRegister(REG.CTRL);

    /** standard mode, SCL: 90 kHz */
    public static final int MODE_STANDARD = 100000;
    /** fast mode, SCL: 350 kHz */
    public static final int MODE_FAST = 400000;

    private final long TRANSMISSION_DURATION_MAX_MILLIS = 3000;

    public final class PIN {
        /**
         * Bidirectional I2C data pin
         */
        public static final String SDA = "i2c_sda_io";
        /**
         * Bidirectional I2C clock pin
         */
        public static final String SCL = "i2c_scl_io";
    }

    public final class REG {
        /**
         * Clock prescale register low-byte. R/W
         */
        public static final int PRER_L = 0x00;
        /**
         * Clock prescale register high-byte. R/W
         */
        public static final int PRER_H = 0x01;
        /**
         * Control register. R/W
         */
        public static final int CTRL = 0x02;
        /**
         * Transmit register. Write only
         */
        public static final int TXR = 0x03;
        /**
         * Receive register. Read only
         */
        public static final int RXR = 0x03;
        /**
         * Command register. Write only
         */
        public static final int CR = 0x04;
        /**
         * Status register. Read only
         */
        public static final int SR = 0x04;
    }

    /**
     * Initialize the I2C master core and set a certain transmission speed
     *
     * @param speed Transmission speed. The following modes are available:<br>
     * 	MODE_STANDARD ( < 100 kbit/s)<br>
     * 	MODE_FAST ( < 400 kbit/s)
     * @throws CommunicationException
     */
    public void init(int speed) throws CommunicationException {
        if (speed == MODE_STANDARD || speed == MODE_FAST) {
            setupPrescaler(speed);
            enable();
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Initialize with standard transmission speed
     *
     * @throws CommunicationException
     */
    public void init() throws CommunicationException {
        init(MODE_STANDARD);
    }

    /**
     * Generic transfer method
     *
     * @param data 8 bits of data, or 7 bit slave address with R/W bit (LSB)
     * @param write true when writing data to a slave, false when reading
     * @param start if true, assert start condition before transmission. If false, assert stop
     * condition after transmission. Set to null for transmission in between.
     * @param nack if true send nack after receiving (write = false)
     * @return read value when a receiver (write = false), else 1 if ACK received or 0 if NACK
     * received after transmitting data (write = true)
     * @throws CommunicationException
     */
    public int transfer(int data, boolean write, Boolean start, boolean nack)
            throws CommunicationException {

        int command = 0;
        /* check data */
        if (data < 0 || data > 0xff) {
            throw new IllegalArgumentException("Data out of range (0 .. 255)");
        }

        /* when writing, write data/address + WR-bit to TX register */
        if (write) writeRegister(REG.TXR, data);

        /* WR, RD */
        if (write) command |= (1 << 4);
        else command |= (1 << 5);

        /* STA, STO */
        if (start != null) {
            if (start) command |= (1 << 7);
            else command |= (1 << 6);
        }

        /* ACK, NACK */
        if (write && nack) {
            throw new IllegalArgumentException("Nack can only be asserted in read transmissions");
        }
        else if (nack) {
            command |= (1 << 3);
        }

        /* write command register */
        writeRegister(REG.CR, command);

        /* busy wait until finished */
        long transmissionStartMillis = System.currentTimeMillis();
        while (transmissionInProgress()) {
            if ((System.currentTimeMillis() - transmissionStartMillis)
                    >= TRANSMISSION_DURATION_MAX_MILLIS) {
                throw new CommunicationException("I2C transmission in progress for too long. "
                        + "No slave device attached?");
            }
        }

        /* asynchronously read status and receive register */
        RegisterReadCallback callback = new RegisterReadCallback(2);
        rdRegisterAsync(REG.SR, callback);
        rdRegisterAsync(REG.RXR, callback);

        if (write) {
            /* return pseudo-boolean representation of ACK bit if write transmission */
            int status = callback.getData(0);
            if ((status & 0x80) != 0) return 0;
            else return 1;
        }
        else {
            /* return read value, when receiving data */
            return callback.getData(1);
        }
    }

    /**
     * Typical single byte read operation
     *
     * @param deviceAddress 7 bit device address
     * @param registerAddress 8 bit register address
     * @return the received data, i.e. a register's content
     * @throws CommunicationException
     * @throws I2CException
     */
    public int readByte(int deviceAddress, int registerAddress)
            throws CommunicationException, I2CException {

        int ack0, ack1, ack2;

        /* check addresses */
        if (deviceAddress < 0 || deviceAddress > 127) {
            throw new IllegalArgumentException("Device address out of range (0 .. 127)");
        }
        if (registerAddress < 0 || registerAddress > 255) {
            throw new IllegalArgumentException("Register address out of range (0 .. 255)");
        }

        /* transmit slave address. Start condition, R/W-Bit: 0 */
        ack0 = transfer(deviceAddress << 1, true, true, false);

        /* transmit register address */
        ack1 = transfer(registerAddress, true, null, false);

        /* transmit slave address. Repeated start condition, R/W-Bit: 1 */
        ack2 = transfer((deviceAddress << 1) | 0x01 , true, true, false);

        /* receive register content. Stop condition, send NACK */
        int data = transfer(0x00, false, false, true);

        /* check acknowledge bits */
        if (ack0 != 1 || ack1 != 1 || ack2 != 1) {
            throw new I2CException("NACK during read byte operation");
        }

        return data;
    }

    /**
     * Typical single byte write operation
     *
     * @param deviceAddress 7 bit device address
     * @param registerAddress 8 bit register address
     * @param data to be transmitted, i.e. written to a register
     * @throws CommunicationException
     * @throws I2CException
     */
    public void writeByte(int deviceAddress, int registerAddress, int data)
            throws CommunicationException, I2CException {

        int ack0, ack1, ack2;

        /* check addresses */
        if (deviceAddress < 0 || deviceAddress > 127) {
            throw new IllegalArgumentException("Device address out of range (0 .. 127)");
        }
        if (registerAddress < 0 || registerAddress > 255) {
            throw new IllegalArgumentException("Register address out of range (0 .. 255)");
        }

        /* transmit slave address. Start condition, R/W-Bit: 0 */
        ack0 = transfer(deviceAddress << 1 , true, true, false);

        /* transmit register address */
        ack1 = transfer(registerAddress, true, null, false);

        /* transmit data. Stop condition. */
        ack2 = transfer(data, true, false, false);

        /* check acknowledge bits */
        if (ack0 != 1 || ack1 != 1 || ack2 != 1) {
            throw new I2CException("NACK during write byte operation");
        }
    }

    /**
     * Check whether the core is enabled
     *
     * @return
     * @throws CommunicationException
     */
    private boolean isEnabled() throws CommunicationException {
        return regControl.getBit(7);
    }

    /**
     * Enable the core
     *
     * @throws CommunicationException
     */
    private void enable() throws CommunicationException {
        regControl.changeBit(7, true);
    }

    /**
     * Setup the prescaler registers for a certain SCL frequency
     *
     * @param sclFrequency 100000 or 400000
     * @throws CommunicationException
     */
    private void setupPrescaler(int sclFrequency) throws CommunicationException {
        /* check if core is enabled */
        boolean wasEnabled = false;
        if (isEnabled()) {
            wasEnabled = true;
            regControl.changeBit(6, false);
        }

        /* calculate register values */
        final int prescale = (WISHBONE_CLOCK_FREQUENCY / (5 * sclFrequency)) - 1;
        final int prescaleLo = prescale & 0xff;
        final int prescaleHi = (prescale & 0xff00) >>> 8;

        /* write */
        regPrescalerLo.write(prescaleLo);
        regPrescalerHi.write(prescaleHi);

        /* re-enable core if enabled before */
        if (wasEnabled) {
            regControl.changeBit(6, true);
        }
    }

    /**
     * Returns the TIP (Transmission In Progress) bit
     *
     * @return
     * @throws CommunicationException
     */
    private boolean transmissionInProgress() throws CommunicationException {
        int status = readRegister(REG.SR);
        return ((status & 0x02) != 0);
    }

    /*
     * Mark both pins as INOUT-types
     */
    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Pin pin = new Pin(this, name, Type.INOUT);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

    @Override
    public String getGenericMap() {
        return null;
    }

    @Override
    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regPrescalerLo.getOffset()) regPrescalerLo.write(data);
        else if (offset == regPrescalerHi.getOffset()) regPrescalerHi.write(data);
        else if (offset == regControl.getOffset()) regControl.write(data);
        else wrRegister(offset, data);
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regPrescalerLo.getOffset()) return regPrescalerLo.read();
        else if (offset == regPrescalerHi.getOffset()) return regPrescalerHi.read();
        else if (offset == regControl.getOffset()) return regControl.read();
        else return rdRegister(offset);
    }
}
