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
import easyfpga.exceptions.CommunicationException;
import easyfpga.generator.model.Core;
import easyfpga.generator.model.Pin;
import easyfpga.generator.model.Pin.Type;

/**
 * An SPI master core
 */
public class SPI extends Core {

    /* stores how many bytes have been transmitted without reading the rx register */
    private int transmitOnly = 0;

    /* registers that do not change their values by themselves */
    private BufferedRegister regSPCR = new BufferedRegister(REG.SPCR);
    private BufferedRegister regSPER = new BufferedRegister(REG.SPER);

    public final class PIN {
        /**
         * Serial clock, output
         */
        public static final String SCK = "sck_out";
        /**
         * Master out slave in, output
         */
        public static final String MOSI = "mosi_out";
        /**
         * Master in slave out, input
         */
        public static final String MISO = "miso_in";
    }

    public final class REG {
        /**
         * Control register. R/W
         */
        public static final int SPCR = 0x00;
        /**
         * Status register. R/W
         */
        public static final int SPSR = 0x01;
        /**
         * Data register. R/W
         */
        public static final int SPDR = 0x02;
        /**
         * Extensions register. R/W
         */
        public static final int SPER = 0x03;
    }

    /*
     * serial clock speed constants for wishbone clock frequency 80 MHz
     */

    /** SCK = 40 MHz */
    public static final int SCK_40_MHz = 2;
    /** SCK = 20 MHz */
    public static final int SCK_20_MHz = 4;
    /** SCK = 10 MHz */
    public static final int SCK_10_MHz = 8;
    /** SCK = 5 MHz */
    public static final int SCK_5_MHz = 16;
    /** SCK = 2.5 MHz */
    public static final int SCK_2500_kHz = 32;
    /** SCK = 1.25 MHz */
    public static final int SCK_1250_kHz = 64;
    /** SCK = 625 kHz */
    public static final int SCK_625_kHz = 128;

    /* for the sake of completeness ... */
    public static final int SCK_312500_Hz = 256;
    public static final int SCK_156250_Hz = 512;
    public static final int SCK_78125_Hz = 1024;
    public static final int SCK_39063_Hz = 2048;
    public static final int SCK_19531_Hz = 4096;

    /*
     * spi mode constants
     */
    /** SPI mode 0: CPOL = 0, CPHA = 0 */
    public static final int MODE_0 = 0;
    /** SPI mode 0: CPOL = 0, CPHA = 1 */
    public static final int MODE_1 = 1;
    /** SPI mode 0: CPOL = 1, CPHA = 0 */
    public static final int MODE_2 = 2;
    /** SPI mode 0: CPOL = 1, CPHA = 1 */
    public static final int MODE_3 = 3;

    /**
     * Initialize and enable spi core. Clear both FIFOs.
     *
     * @param mode
     * Mode 0: CPOL=0, CPHA=0;
     * Mode 1: CPOL=0, CPHA=1;
     * Mode 2: CPOL=1, CPHA=0;
     * Mode 3: CPOL=1, CPHA=1;
     * @param clockDiv
     * 2, 4, 8, 16, 32, 64, 128, 265, 512, 1024, 2048 or 4096
     * @throws CommunicationException
     */
    public void init(int mode, int clockDiv) throws CommunicationException {
        setEnabled(false);
        setMode(mode);
        setClockDiv(clockDiv);
        setEnabled(true);
    }

    /**
     * Transmit and receive a single byte
     * @param data to be transmitted over the MOSI line
     * @return The data received over the MISO line
     * @throws CommunicationException
     */
    public int transceive(int data) throws CommunicationException {
        /* check data */
        if (data < 0 || data > 0xff) {
            throw new IllegalArgumentException();
        }

        /* perform asynchronous dummy reads if necessary */
        int dummyReads = transmitOnly % 4;
        if (dummyReads > 0) {
            RegisterReadCallback callback = new RegisterReadCallback(dummyReads);
            for (int i = 0; i < dummyReads; i++) {
                rdRegisterAsync(REG.SPDR, callback);
            }
        }

        /* receive fifo is now aligned */
        transmitOnly = 0;

        /* transmit */
        writeRegister(REG.SPDR, data);

        //TODO: async
        /* wait until read fifo is not empty anymore */
        int spsr;
        while (true) {
            spsr = readRegister(REG.SPSR);
            if ((spsr & 0x01) == 0) break;
        }
        /* read rx fifo */
        int received = readRegister(REG.SPDR);

        return received;
    }

    /**
     * SPI transmission-only method. Faster than the transceive method, but the received
     * bytes will be lost.
     * @param data to be transmitted over the MOSI line
     * @throws CommunicationException
     */
    public void transmit(int data) throws CommunicationException {
        /* check data */
        if (data < 0 || data > 0xff) {
            throw new IllegalArgumentException();
        }

        /* transmit */
        writeRegister(REG.SPDR, data);
        transmitOnly++;
    }

    /**
     * Transmit dummy byte (0x00) and receive data
     * @return Received data
     * @throws CommunicationException
     */
    public int receive() throws CommunicationException {
        return transceive(0x00);
    }

    /**
     * Reset the receive and transmit fifo buffers
     * @throws CommunicationException
     */
    public void resetBuffers() throws CommunicationException {
        setEnabled(false);
        setEnabled(true);
    }

    /**
     * Enable or disable the core.
     * When disabled, the core will not transfer data.
     *
     * @param enable
     * @throws CommunicationException
     */
    private void setEnabled(boolean enable) throws CommunicationException {
        regSPCR.changeBit(6, enable);
    }

    /**
     * Set the SPI mode
     *
     * @param mode 0..3
     * @throws CommunicationException
     */
    private void setMode(int mode) throws CommunicationException {

        /*
         * Mode | CPOL | CPHA
         *   0  |  0   |  0
         *   1  |  0   |  1
         *   2  |  1   |  0
         *   3  |  1   |  1
         */

        if (mode < MODE_0 || mode > MODE_3) {
            throw new IllegalArgumentException("SPI mode has to be between 0 and 3");
        }
        else {
            switch (mode) {
                case MODE_0:
                    regSPCR.changeBit(3, false);    /* CPOL */
                    regSPCR.changeBit(2, false);    /* CPHA */
                    break;
                case MODE_1:
                    regSPCR.changeBit(3, false);    /* CPOL */
                    regSPCR.changeBit(2, true);     /* CPHA */
                    break;
                case MODE_2:
                    regSPCR.changeBit(3, true);     /* CPOL */
                    regSPCR.changeBit(2, false);    /* CPHA */
                    break;
                case MODE_3:
                    regSPCR.changeBit(3, true);     /* CPOL */
                    regSPCR.changeBit(2, true);     /* CPHA */
                    break;
            }
        }
    }

    /**
     * Set the clock divisor
     *
     * @param clockDiv 2, 4, 8, 16, 32, 64, 128, 265, 512, 1024, 2048 or 4096
     * @throws CommunicationException
     */
    private void setClockDiv(int clockDiv) throws CommunicationException {
        switch (clockDiv) {
            case 2:
                setSPR(0);
                setESPR(0);
                break;
            case 4:
                setSPR(1);
                setESPR(0);
                break;
            case 8:
                setSPR(0);
                setESPR(1);
                break;
            case 16:
                setSPR(2);
                setESPR(0);
                break;
            case 32:
                setSPR(3);
                setESPR(0);
                break;
            case 64:
                setSPR(1);
                setESPR(1);
                break;
            case 128:
                setSPR(2);
                setESPR(1);
                break;
            case 256:
                setSPR(3);
                setESPR(1);
                break;
            case 512:
                setSPR(0);
                setESPR(2);
                break;
            case 1024:
                setSPR(1);
                setESPR(2);
                break;
            case 2048:
                setSPR(2);
                setESPR(2);
                break;
            case 4096:
                setSPR(3);
                setESPR(2);
                break;
            default:
                throw new IllegalArgumentException("Invalid clock divider");
        }
    }

    private void setSPR(int spr) throws CommunicationException {
        if ((spr & 0x01) != 0) {
            regSPCR.changeBit(0, true);
        }
        else {
            regSPCR.changeBit(0, false);
        }
        if ((spr & 0x02) != 0) {
            regSPCR.changeBit(1, true);
        }
        else {
            regSPCR.changeBit(1, false);
        }
    }

    private void setESPR(int espr) throws CommunicationException {
        if ((espr & 0x01) != 0) {
            regSPER.changeBit(0, true);
        }
        else {
            regSPER.changeBit(0, false);
        }
        if ((espr & 0x02) != 0) {
            regSPER.changeBit(1, true);
        }
        else {
            regSPER.changeBit(1, false);
        }
    }

    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Type type = name.equals(PIN.MISO) ? Type.IN : Type.OUT;
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
        if (offset == regSPCR.getOffset()) regSPCR.write(data);
        else if (offset == regSPER.getOffset()) regSPER.write(data);
        else wrRegister(offset, data);
    }

    @Override
    public int readRegister(int offset) throws CommunicationException {
        if (offset == regSPCR.getOffset()) return regSPCR.read();
        else if (offset == regSPER.getOffset()) return regSPER.read();
        else return rdRegister(offset);
    }
}
