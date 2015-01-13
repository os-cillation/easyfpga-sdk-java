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

package easyfpga.generator.model;

import easyfpga.communicator.MultiRegisterReadCallback;
import easyfpga.communicator.RegisterReadCallback;
import easyfpga.exceptions.CommunicationException;

/**
 * A Core is a Component with registers and a Wishbone interface. Superclass of all classes in the
 * package easyfpga.generator.model.cores. All cores have to implement methods for writing to and
 * reading from registers while ensuring the integrity of all BufferedRegister. The public
 * implementation of async and multi R/W method is not mandatory.
 *
 * @see BufferedRegister
 */
public abstract class Core extends Component {

    /**
     * Get the component's core address
     *
     * @return core address (0xCC00)
     */
    public int getCoreAddress() {
        return index << 8;
    }

    /**
     * Write to a component's register. This method has to be implemented by the cores, avoiding
     * interference with BufferedRegisters.
     *
     * @param offset of the register to be written
     * @param data to be written
     * @throws CommunicationException
     */
    public abstract void writeRegister(int offset, int data) throws CommunicationException;

    /**
     * Internal low-level register write method
     *
     * @param offset of the register to be written
     * @param data to be written
     * @throws CommunicationException
     */
    protected void wrRegister(int offset, int data) throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + offset;
        fpga.getCommunicator().writeRegister(address, data);
    }

    /**
     * Internal register multi-write method. Used to write multiple times to one register
     *
     * @param offset of the register to be written
     * @param data integer array to be written in the array order
     * @throws CommunicationException
     */
    protected void multiWrRegister(int offset, int[] data) throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + offset;
        fpga.getCommunicator().writeRegister(address, data);
    }

    /**
     * Internal register multi-write method. Used to write multiple times to one register
     *
     * @param offset of the register to be written
     * @param data char array to be written in the array order
     * @throws CommunicationException
     */
    protected void multiWrRegister(int offset, char[] data) throws CommunicationException {

        if (fpga == null) throwFPGANullException();

        /* convert to int array */
        int[] intData = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            intData[i] = (int) data[i];
        }

        multiWrRegister(offset, intData);
    }

    /**
     * Internal auto-address-increment write method. Used to write multiple registers with
     * consecutive addresses of a single core.
     *
     * @param startOffset of the first register to be written
     * @param data integer array to be written. Its length defines the number of registers that
     *         will be written
     * @throws CommunicationException
     */
    protected void wrRegisterAAI(int startOffset, int[] data) throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int startAddress = getCoreAddress() + startOffset;
        fpga.getCommunicator().writeRegisterAAI(startAddress, data);
    }

    /**
     * Internal register multi-write method. Used to write multiple times to one register.
     *
     * @param offset of the register to be written
     * @param data String to be written character wise
     * @throws CommunicationException
     */
    protected void multiWrRegister(int offset, String data) throws CommunicationException {
        multiWrRegister(offset,data.toCharArray());
    }

    /**
     * Read from a component's register. This method has to be implemented by the cores, avoiding
     * interference with BufferedRegisters.
     *
     * @param offset of the register to read
     * @return the register's content
     * @throws CommunicationException
     */
    public abstract int readRegister(int offset) throws CommunicationException;

    /**
     * Internal synchronous low-level register read method.
     *
     * @param offset of the register to read
     * @return the register's content
     * @throws CommunicationException
     */
    protected int rdRegister(int offset) throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + offset;
        return fpga.getCommunicator().readRegister(address);
    }

    /**
     * Internal low-level register multi-read method. Reads multiple times from a single register.
     *
     * @param offset of the register to read
     * @param numberOfReads how many reads should be performed
     * @return int[] containing the results of all reads
     * @throws CommunicationException
     */
    protected int[] rdRegister(int offset, int numberOfReads) throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + offset;
        return fpga.getCommunicator().readRegister(address, numberOfReads);
    }

    /**
     * Read from a component's register asynchronously.
     *
     * @param offset of the register to read
     * @param callback of type RegisterReadCallback with a given number of read operations
     * @throws CommunicationException
     */
    protected void rdRegisterAsync(int offset, RegisterReadCallback callback)
            throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + offset;
        fpga.getCommunicator().readRegisterAsync(address, callback);
    }

    /**
     * Internal low-level asynchronous register multi-read method. Reads multiple times from a
     * single register.
     *
     * @param offset of the register to read
     * @param numberOfReads how many reads should be performed
     * @param callback of type MultiRegisterReadCallback
     * @throws CommunicationException
     */
    protected void rdRegisterAsync(int offset, int numberOfReads, MultiRegisterReadCallback callback)
            throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + offset;
        fpga.getCommunicator().readRegisterAsync(address, numberOfReads, callback);
    }

    /**
     * Internal synchronous auto-address-increment read method.
     *
     * @param startOffset of the first register to read
     * @param length number of adjacent registers to read from
     * @return int[] containing the values
     * @throws CommunicationException
     */
    protected int[] rdRegisterAAI(int startOffset, int length) throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int address = getCoreAddress() + startOffset;
        return fpga.getCommunicator().readRegisterAAI(address, length);
    }

    /**
     * Internal asynchronous auto-address-increment read method.
     *
     * @param startOffset of the first register to read
     * @param length number of adjacent registers to read from
     * @param callback implementation to handle reception of reply
     * @throws CommunicationException
     */
    protected void rdRegisterAAIAsync(int startOffset, int length, MultiRegisterReadCallback callback)
            throws CommunicationException {
        if (fpga == null) throwFPGANullException();
        int startAddress = getCoreAddress() + startOffset;
        fpga.getCommunicator().readRegisterAAIAsync(startAddress, length, callback);
    }

    /**
     * Print a register's content to standard output
     *
     * @param offset register offset (0 .. 0xFF)
     * @param name descriptor to be printed
     * @throws CommunicationException
     */
    public void displayRegister(int offset, String name) throws CommunicationException {
        System.out.println(String.format("%s (@0x%04X) = 0x%02X", name,
                offset+getCoreAddress(), readRegister(offset)));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + String.format("0x%02X", index);
    }

    /**
     * Throw a NullPointerException with message asking whether the connect method has been
     * called. Reason for null-checking the fpga reference: In case a core is instantiated
     * in the annotated fpga definition class but the connect method is never called, the
     * core will not get an fpga reference leading to a nullpointer exception.
     *
     * @throws CommunicationException
     */
    private static void throwFPGANullException() {
        throw new NullPointerException("Communication error due to missing FPGA reference."
                + "Did you call the connect method in the annotated FPGA class?");
    }

    /**
     * Represents a single register. Manages local buffering and buffer synchronization. This class
     * may not be used for registers that can change their values by themselves!
     */
    protected class BufferedRegister {

        private final int addressOffset;
        private int data;
        private boolean dataSynced = false;

        /**
         * Construct with a certain register address.
         *
         * @param addressOffset register address part (0 .. 0xFF)
         */
        public BufferedRegister(int addressOffset) {
            this.addressOffset = addressOffset;
        }

        /**
         * Modify a single bit.
         *
         * @param position which bit should be modified (0 .. 7)
         * @param value true to set bit to '1', false to set it to '0'
         * @throws CommunicationException
         */
        public void changeBit(int position, boolean value) throws CommunicationException {
            checkBitPosition(position);
            if (!dataSynced) {
                synchronize();
            }

            /* abort, if bit already set to the desired value */
            if (getBit(position) == value) {
                return;
            }

            /* modify local data buffer */
            if (value) {
                this.data |= 1 << position;
            }
            else {
                this.data &= ~(1 << position);
            }

            wrRegister(addressOffset, data);
        }

        /**
         * Get a single bit.
         *
         * @param position of the bit (0 .. 7)
         * @return True if bit is '1', else false
         * @throws CommunicationException
         */
        public boolean getBit(int position) throws CommunicationException {
            checkBitPosition(position);
            if (!dataSynced) {
                synchronize();
            }

            if ((data & (1 << position)) != 0) {
                return true;
            }
            else {
                return false;
            }
        }

        /**
         * Read the entire buffered content.
         *
         * @return 8-Bit integer (0 .. 0xFF)
         * @throws CommunicationException
         */
        public int read() throws CommunicationException {
            if (!dataSynced) {
                synchronize();
            }
            return data;
        }

        /**
         * Write data to the entire register.
         *
         * @param data (0 .. 0xFF)
         * @throws CommunicationException
         */
        public void write(int data) throws CommunicationException {
            /* skip, if already at this value */
            if (dataSynced && data == this.data) return;

            /* write register and update fields */
            wrRegister(addressOffset, data);
            this.data = data;
            this.dataSynced = true;
        }

        /**
         * Set the value without writing to register. Use only when sure, that the register
         * contains the given data!
         *
         * @param data (0 .. 0xFF)
         */
        public void setValue(int data) {
            /* parameter check */
            if (data < 0 || data > 0xFF) {
                throw new IllegalArgumentException();
            }
            this.data = data;
            this.dataSynced = true;
        }

        /**
         * Get the register's address offset (relative to the core address).
         *
         * @return offset (0 .. 0xFF)
         */
        public int getOffset() {
            return addressOffset;
        }

        /** Read register to synchronize buffered content */
        private void synchronize() throws CommunicationException {
            data = rdRegister(addressOffset);
            this.dataSynced = true;
        }

        private void checkBitPosition(int position) {
            if (position < 0 || position > 7) {
                throw new IllegalArgumentException("Invalid bit position");
            }
        }
    }
}
