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
import easyfpga.generator.model.cores.MIDIMessage.MessageType;

/**
 * An easyCore for MIDI applications. Uses the same HDL as the UART but limits its functionality
 * to what is useful for MIDI.
 */
public class MIDI extends Core {

    /* buffered registers */
    private BufferedRegister regLCR = new BufferedRegister(REG.LCR);
    private BufferedRegister regIER = new BufferedRegister(REG.IER);

    private int txBytesPending = 0;
    private final int TX_FIFO_SIZE = 64;

    public final class PIN {
        /**
         * MIDI input/sink pin
         */
        public static final String MIDI_IN = "RXD_i";
        /**
         * MIDI output/source pin
         */
        public static final String MIDI_OUT = "TXD_o";
    }

    @SuppressWarnings("unused")
    private final class REG {
        /**
         * Transmitter FIFO register. Write only.
         */
        public static final int TX = 0;
        /**
         * Receiver FIFO register. Read only.
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

    /**
     * Initialize the MIDI core. Has to be called before the core can be used
     *
     * @throws CommunicationException
     */
    public void init() throws CommunicationException {
        /* frame format: 8N1 */
        regLCR.write(0x03);

        /* calculate baudrate divisors for 31250 bps */
        int baudrateDivisor = WISHBONE_CLOCK_FREQUENCY / (31250 * 16);
        int dll_data = baudrateDivisor & 0x00FF;
        int dlm_data = (baudrateDivisor & 0xFF00) >>> 8;

        /* set DLAB */
        regLCR.changeBit(7, true);

        /* set baudrate */
        wrRegister(REG.DLL, dll_data);
        wrRegister(REG.DLM, dlm_data);

        /* enable and clear 64-byte fifos */
        wrRegister(REG.FCR, 0x27);

        /* reset DLAB */
        regLCR.changeBit(7, false);

        /* disable rx interrupts */
        regIER.changeBit(0, true);
    }

    /**
     * Transmit a MIDI message
     *
     * @param message of type MIDIMessage
     * @throws CommunicationException
     */
    public void transmit(MIDIMessage message) throws CommunicationException {
        int[] rawMessage = message.getRawMessage();

        /* wait until empty if not sure that tx buffer will not overflow */
        if (txBytesPending + rawMessage.length >= TX_FIFO_SIZE) {
            while (!transmitterEmpty());
            txBytesPending = 0;
        }

        /* transmit */
        multiWrRegister(REG.TX, rawMessage);
        txBytesPending += rawMessage.length;
    }

    /**
     * Get a received MIDI message
     *
     * @return the oldest received message as MIDIMessage object or null if receiver is empty
     * @throws CommunicationException
     */
    public MIDIMessage receive() throws CommunicationException {

        int length;

        /* get 2 bytes */
        int[] rx2 = rdRegister(REG.RX, 2);

        /* determine type */
        MessageType type = MessageType.fromInteger(rx2[0]);
        if (type == null) return null;

        /* determine length */
        length = type.getLength();

        /* 2-byte messages */
        if (length == 2) {
            return new MIDIMessage(rx2);
        }

        /* 3-byte messages */
        else {
            int lastByte = rdRegister(REG.RX);
            int[] rx3 = new int[] {rx2[0], rx2[1], lastByte};
            return new MIDIMessage(rx3);
        }
    }

    /**
     * Enable interrupt on MIDI message reception
     *
     * @throws CommunicationException
     */
    public void enableInterrupt() throws CommunicationException {
        regIER.changeBit(0, true);
    }

    private boolean transmitterEmpty() throws CommunicationException {
        /* read line status register */
        int lsr = rdRegister(REG.LSR);

        /* return true if bit 6 is asserted */
        if ((lsr & 0x20) != 0) {
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regLCR.getOffset()) {
            regLCR.write(data);
        }
        else if (offset == regIER.getOffset()) {
            regIER.write(data);
        }
        else {
            wrRegister(offset, data);
        }
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regLCR.getOffset()) {
            return regLCR.read();
        }
        else if (offset == regIER.getOffset()) {
            return regIER.read();
        }
        else {
            return rdRegister(offset);
        }
    }

    @Override
    public String getGenericMap() {
        return null;
    }

    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Type type = Type.OUT;
            if (name.equals(PIN.MIDI_IN)) {
                type = Type.IN;
            }
            Pin pin = new Pin(this, name, type);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

}
