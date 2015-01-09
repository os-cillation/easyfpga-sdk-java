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
import easyfpga.generator.model.Core;
import easyfpga.generator.model.Pin;
import easyfpga.generator.model.Pin.Type;

/**
 * A 16750 UART with auto CTS/RTS flow control
 */
public class UART extends Core {

    BufferedRegister regMCR = new BufferedRegister(REG.MCR);
    BufferedRegister regLCR = new BufferedRegister(REG.LCR);
    BufferedRegister regIER = new BufferedRegister(REG.IER);

    private int txBytesPending = 0;
    private int rxTriggerLevel = 1;

    /** number of bytes that can be stored in the transmit fifo */
    private final int TX_FIFO_SIZE = 64;

    private final int TRANSMITTER_EMPTY_TIMEOUT_MS = 3000;

    public final class PIN {
        /**
         * Receiver input/sink pin
         */
        public static final String RXD = "RXD_i";
        /**
         * Transmitter output/source pin
         */
        public static final String TXD = "TXD_o";
        /**
         *  Clear to send input/sink pin
         */
        public static final String CTSn = "CTSn_i";
        /**
         * Request to send output/source pin
         */
        public static final String RTSn = "RTSn_o";
        /**
         * Data set ready output/source pin
         */
        public static final String DSRn = "DSRn_o";
        /**
         * Ring indicator output/source pin
         */
        public static final String RIn = "RIn_o";
        /**
         * Data carrier detect output/source pin
         */
        public static final String DCDn = "DCDn_o";
        /**
         * Data terminal ready output/source pin
         */
        public static final String DTRn = "DTRn_o";
        /**
         * MCR auxiliary output/source pin 1
         */
        public static final String AUX1 = "AUX1_o";
        /**
         * MCR auxiliary output/source pin 2
         */
        public static final String AUX2 = "AUX2_o";

    }

    public final class REG {
        /**
         * Transmitter buffer register. Write only.
         */
        public static final int TX = 0;
        /**
         * Receiver buffer register. Read only.
         */
        public static final int RX = 0;
        /**
         * Interrupt enable register. R/W
         */
        public static final int IER = 1;
        /**
         * Interrupt identification register. Read only.
         */
        public static final int IIR = 2;
        /**
         * FIFO control register. Write only.
         */
        public static final int FCR = 2;
        /**
         * Line control register. R/W
         */
        public static final int LCR = 3;
        /**
         * Modem control register. R/W
         */
        public static final int MCR = 4;
        /**
         * Line status register. Read only.
         */
        public static final int LSR = 5;
        /**
         * Modem status register. Read only.
         */
        public static final int MSR = 6;
        /**
         * Scratch register. R/W
         */
        public static final int SCR = 7;
        /**
         * Divisor latch LSB (baud rate generator). R/W
         */
        public static final int DLL = 0;
        /**
         * Divisor latch MSB (baud rate generator). R/W
         */
        public static final int DLM = 1;

    }

    public final class INT {

        /**
         * Receive buffer has reached its trigger level. Cleared when buffer drops below the trigger
         * level.
         * @see UART#setRxTriggerLevel
         */
        public static final int RX_AVAILABLE = 0;

        /**
         * Transmit hold register empty interrupt. Cleared when writing to the transmitter
         * buffer register or reading interrupt identification register.
         */
        public static final int TX_EMPTY = 1;

        /**
         * Receiver line status interrupt. Parity, data overrun, framing error or break interrupt.
         * Cleared when reading line status register.
         */
        public static final int RX_LINE_STATUS = 2;

        /**
         * Modem status interrupt. Either CTS, SDR, RI or DCD have changed.
         * Cleared when reading modem status register.
         */
        public static final int MODEM_STATUS = 3;

        /**
         * No characters have been removed from the receive buffer during the last four character
         * times and there is at least one character in it during this time. Cleared when reading
         * receiver buffer register. This interrupt requires no activation.
         */
        public static final int CHARACTER_TIMEOUT = 4;
    }

    /** Receive buffer interrupt trigger level : 1 Byte */
    public static final int RX_TRIGGER_LEVEL_1 = 1;
    /** Receive buffer interrupt trigger level : 16 Bytes */
    public static final int RX_TRIGGER_LEVEL_16 = 16;
    /** Receive buffer interrupt trigger level : 32 Bytes */
    public static final int RX_TRIGGER_LEVEL_32 = 32;
    /** Receive buffer interrupt trigger level : 56 Bytes */
    public static final int RX_TRIGGER_LEVEL_56 = 56;

    /**
     * Transmit a single integer (0x00 .. 0xFF)
     *
     * @param data to be sent
     * @throws CommunicationException
     */
    public void transmit(int data) throws CommunicationException {

        /* parameter check */
        if (data < 0 || data > 0xFF) throw new IllegalArgumentException("Invalid data");

        /* check whether tx fifo is possibly full */
        if (txBytesPending >= TX_FIFO_SIZE) {
            waitUntilTransmitterEmpty();
        }

        /* prepare and send tx data */
        writeRegister(REG.TX, data & 0xff);
        txBytesPending++;
    }

    /**
     * Transmit a String
     *
     * @param str to be sent
     * @throws CommunicationException
     */
    public void transmit(String str) throws CommunicationException {

        /* split into portions that exactly fit into tx fifo */
        String splitRegex = String.format("(?<=\\G.{%d})", TX_FIFO_SIZE);
        String[] stringParts = str.split(splitRegex);

        /* transmit string parts using MWR command */
        for (String stringPart : stringParts) {

            waitUntilTransmitterEmpty();

            /* transmit string part */
            multiWrRegister(REG.TX, stringPart);
            txBytesPending = TX_FIFO_SIZE;
        }
    }

    private void waitUntilTransmitterEmpty() throws CommunicationException {

        int timeoutCounter = 0;

        while (!transmitterEmpty()) {
            try {
                Thread.sleep(1);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            /* throw exception if it takes too long */
            if (timeoutCounter++ == TRANSMITTER_EMPTY_TIMEOUT_MS) {
                throw new CommunicationException("UART transmit buffer empty timeout");
            }
        }
        txBytesPending = 0;
    }

    /**
     * Get a single packet from the UART's receive buffer
     *
     * @return Received packet, or binary zero if receive buffer is empty
     * @throws CommunicationException
     */
    public int receive() throws CommunicationException {
        return readRegister(REG.RX);
    }

    /**
     * Get multiple packets from the UART's receive buffer
     *
     * @param length number of bytes to read
     * @return Multiple packets received as an int[] which may contain binary zeroes if the
     *          receive buffer runs empty
     * @throws CommunicationException
     */
    public int[] receive(int length) throws CommunicationException {
        return rdRegister(REG.RX, length);
    }

    /**
     * Get multiple packets as a String. Stop reception at the first binary zero.
     *
     * @return The string in the receive buffer excluding the trailing binary zero.
     * @throws CommunicationException
     */
    public String receiveString() throws CommunicationException {

        StringBuilder sb = new StringBuilder();
        boolean binaryZeroReceived = false;
        int[] receivedInts;

        /* read chunks in the size of rxTriggerLevel */
        while (!binaryZeroReceived) {
            receivedInts = receive(rxTriggerLevel);
            for (int i = 0; i < rxTriggerLevel; i++) {
                if (receivedInts[i] != 0) {
                    sb.append((char) receivedInts[i]);
                }
                else {
                    binaryZeroReceived = true;
                    break;
                }
            }
        }

        return sb.toString();
    }

    /**
     * Get multiple packets as a String from the UART's receive buffer
     *
     * @param length number of characters to read
     * @return Multiple packets interpreted as a String
     * @throws CommunicationException
     */
    public String receiveString(int length) throws CommunicationException {
        int[] rxInts = rdRegister(REG.RX, length);
        StringBuilder sb = new StringBuilder();
        for (int intCharacter : rxInts) {
            char character = (char) intCharacter;
            sb.append(character);
        }
        return sb.toString();
    }

    /**
     * Clear receive and transmit buffers
     *
     * @throws CommunicationException
     */
    public void clearBuffers() throws CommunicationException {
        /* set self-clearing clear RX and TX bits */
        writeRegister(REG.FCR, 0x07);

        /* read receive register that might still hold a single byte */
        waitUntilTransmitterEmpty();
        readRegister(REG.RX);

        /* restore the trigger level */
        setRxTriggerLevel(rxTriggerLevel);
    }

    /**
     * Initialize the UART
     *
     * @param baudrate
     * @param wordLength
     *            5, 6, 7 or 8
     * @param parity
     *            N (no), O (odd) or E (even) parity
     * @param stopBits
     *            1 or 2 stop bits
     */
    public void init(int baudrate, int wordLength, char parity, int stopBits) throws CommunicationException {
        int lcrTemp = 0;

        /* check parameters */
        if (baudrate <= 0) {
            throw new IllegalArgumentException();
        }
        if (wordLength < 5 | wordLength > 8) {
            throw new IllegalArgumentException();
        }
        if (stopBits < 1 | stopBits > 2) {
            throw new IllegalArgumentException();
        }

        /* parity */
        parity = Character.toLowerCase(parity);
        switch (parity) {
        case 'o': /* set bit 3 */
            lcrTemp += 0x08;
            break;
        case 'e': /* set bits 3 and 4 */
            lcrTemp += 0x18;
            break;
        case 'n': /* dont't set any bits */
            break;
        default:
            throw new IllegalArgumentException("Illegal parity character. Allowed are: [o]dd, [e]ven and [n]o parity");
        }

        /* number of stop bits */
        if (stopBits == 2)
            lcrTemp += 0x04;

        /* word length */
        switch (wordLength) {
        case 6:
            lcrTemp += 0x01;
            break;
        case 7:
            lcrTemp += 0x02;
            break;
        case 8:
            lcrTemp += 0x03;
            break;
        }

        /* write line control register */
        regLCR.write(lcrTemp);

        /* set baudrate */
        setBaudrate(baudrate);

        /* init fifo control register (enable 64 byte fifo) */
        setDivisorLatchAccessBit();
        writeRegister(REG.FCR, 0x21);
        resetDivisorLatchAccessBit();

        /* disable tx empty interrupt */
        disableInterrupt(UART.INT.TX_EMPTY);
    }

    /**
     * Enable automatic hardware RTS/CTS flow control
     * @throws CommunicationException
     */
    public void enableAutoHardwareFlowControl() throws CommunicationException {
        /* set AFE and RTS bit */
        regMCR.changeBit(5, true);
        regMCR.changeBit(1, true);
    }

    /**
     * Set the receive buffer level that is required to generate a receive interrupt. When automatic
     * hardware flow control is enabled, the UART will stop receiving (i.e. not assert RTS) when
     * the trigger level is reached.
     *
     * @param level number of byte that have to be received. 1, 16, 32 or 56.
     * @throws CommunicationException
     * @see UART#RX_TRIGGER_LEVEL_1
     * @see UART#RX_TRIGGER_LEVEL_16
     * @see UART#RX_TRIGGER_LEVEL_32
     * @see UART#RX_TRIGGER_LEVEL_56
     * @see UART.INT#RX_AVAILABLE
     */
    public void setRxTriggerLevel(int level) throws CommunicationException {

        /* fcr is initially set to 0x21, bit 5 is protected by divisor latch access bit */
        switch (level) {
            case RX_TRIGGER_LEVEL_1:
                rxTriggerLevel = RX_TRIGGER_LEVEL_1;
                writeRegister(REG.FCR, 0x01);
                break;
            case RX_TRIGGER_LEVEL_16:
                rxTriggerLevel = RX_TRIGGER_LEVEL_16;
                writeRegister(REG.FCR, 0x41);
                break;
            case RX_TRIGGER_LEVEL_32:
                rxTriggerLevel = RX_TRIGGER_LEVEL_32;
                writeRegister(REG.FCR, 0x81);
                break;
            case RX_TRIGGER_LEVEL_56:
                rxTriggerLevel = RX_TRIGGER_LEVEL_56;
                writeRegister(REG.FCR, 0xC1);
                break;
            default:
                throw new IllegalArgumentException("Invalid RX trigger level. Allowed values:" +
                                                    "1, 16, 32 and 56");
        }
    }

    /**
     * Enables a certain interrupt
     *
     * @param interrupt identifier (or the bit number in interrupt enable register)
     * @throws CommunicationException
     */
    public void enableInterrupt(int interrupt) throws CommunicationException {
        if (interrupt >= 0 && interrupt <= 3) {
            regIER.changeBit(interrupt, true);
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Disables a certain interrupt
     *
     * @param interrupt identifier (or the bit number in interrupt enable register)
     * @throws CommunicationException
     */
    public void disableInterrupt(int interrupt) throws CommunicationException {
        regIER.changeBit(interrupt, false);
    }

    /**
     * Identify a pending interrupt
     *
     * @return interrupt identification number or -1 if no interrupt pending
     * @throws CommunicationException
     */
    public int identifyInterrupt() throws CommunicationException {
        /* read and truncate interrupt identification register */
        int iir_low = (readRegister(REG.IIR) & 0x0f);

        /* return  -1 when no interrupt is pending */
        if ((iir_low & 0x01) != 0) {
            return -1;
        }

        /* determine and return interrupt type */
        if (iir_low == 0x06) {
            return INT.RX_LINE_STATUS;
        }
        else if (iir_low == 0x04) {
            return INT.RX_AVAILABLE;
        }
        else if (iir_low == 0x0c) {
            return INT.CHARACTER_TIMEOUT;
        }
        else if (iir_low == 0x02) {
            return INT.TX_EMPTY;
        }
        else if (iir_low == 0x00) {
            return INT.MODEM_STATUS;
        }
        else {
            throw new CommunicationException("Unable to identify UART interrupt");
        }
    }

    /**
     * Set an auxiliary output pin
     *
     * @param output 1 or 2
     * @param value
     * @throws CommunicationException
     */
    public void setAuxiliaryOutput(int output, boolean value) throws CommunicationException {
        if (output == 1) {
            regMCR.changeBit(2, value);
        }
        else if (output == 2) {
            regMCR.changeBit(3, value);
        }
        else {
            throw new IllegalArgumentException("Invalid output parameter");
        }
    }

    /**
     * Check whether the transmitter buffer is empty
     *
     * @return True if empty
     * @throws CommunicationException
     */
    private boolean transmitterEmpty() throws CommunicationException {

        /* read line status register */
        int lsr = readRegister(REG.LSR);

        /* return true if bit 6 is asserted */
        if ((lsr & 0x20) != 0) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Setsthe desired baudrate
     *
     * @param baudrate
     * @throws CommunicationException
     */
    private void setBaudrate(int baudrate) throws CommunicationException {
        /* calculate baudrate divisor */
        int baudrateDivisor = WISHBONE_CLOCK_FREQUENCY / (baudrate * 16);

        /* sanity check of divisor value */
        if (baudrateDivisor > 0xFFFF) {
            throw new IllegalArgumentException();
        }

        /* prepare data */
        int dll_data = baudrateDivisor & 0x00FF;
        int dlm_data = (baudrateDivisor & 0xFF00) >>> 8;

        /* write registers */
        setDivisorLatchAccessBit();
        writeRegister(REG.DLL, dll_data);
        writeRegister(REG.DLM, dlm_data);
        resetDivisorLatchAccessBit();
    }

    /**
     * Set divisor latch access bit in order to set the baudrate
     *
     * @throws CommunicationException
     */
    private void setDivisorLatchAccessBit() throws CommunicationException {
        regLCR.changeBit(7, true);
    }

    /**
     * Reset divisor latch access bit in order to access the buffer registers
     *
     * @return True if successful
     * @throws CommunicationException
     */
    private void resetDivisorLatchAccessBit() throws CommunicationException {
        regLCR.changeBit(7, false);
    }

    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Type type = Type.OUT;
            if (name.equals(PIN.RXD) || name.equals(PIN.CTSn)) {
                type = Type.IN;
            }
            Pin pin = new Pin(this, name, type);
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
        if (offset == regMCR.getOffset()) regMCR.write(data);
        else if (offset == regLCR.getOffset()) regLCR.write(data);
        else if (offset == regIER.getOffset()) regIER.write(data);
        else wrRegister(offset, data);
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regMCR.getOffset()) return regMCR.read();
        else if (offset == regLCR.getOffset()) return regLCR.read();
        else if (offset == regIER.getOffset()) return regIER.read();
        else return rdRegister(offset);
    }
}
