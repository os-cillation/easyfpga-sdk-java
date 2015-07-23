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

package easyfpga.communicator;

import java.util.Arrays;
import java.util.zip.Adler32;

/**
 * Protocol definition class containing:<br>
 * - Static constants with frame lengths and opcodes <br>
 * - Frame generator methods returning type Frame for FPGA communication and
 *   byte arrays for MCU communication
 *
 */
public class Protocol {
    /* opcodes */
    public static final byte OPC_ACK            = (byte) 0x00;
    public static final byte OPC_NACK           = (byte) 0x11;
    public static final byte OPC_DETECT         = (byte) 0xEE;
    public static final byte OPC_DETECT_RE      = (byte) 0xFF;
    public static final byte OPC_SECTOR_WR      = (byte) 0x22;
    public static final byte OPC_CONF_FPGA      = (byte) 0x33;
    public static final byte OPC_SOC_SEL        = (byte) 0x44;
    public static final byte OPC_MCU_SEL        = (byte) 0x55;
    public static final byte OPC_REGISTER_WR    = (byte) 0x66;
    public static final byte OPC_REGISTER_MWR   = (byte) 0x65;
    public static final byte OPC_REGISTER_AWR   = (byte) 0x69;
    public static final byte OPC_REGISTER_RD    = (byte) 0x77;
    public static final byte OPC_REGISTER_RDRE  = (byte) 0x88;
    public static final byte OPC_REGISTER_MRD   = (byte) 0x73;
    public static final byte OPC_REGISTER_MRDRE = (byte) 0x93;
    public static final byte OPC_REGISTER_ARD   = (byte) 0x79;
    public static final byte OPC_REGISTER_ARDRE = (byte) 0x90;
    public static final byte OPC_SOC_INT        = (byte) 0x99;
    public static final byte OPC_SOC_INT_EN     = (byte) 0xAA;
    public static final byte OPC_STATUS_WR      = (byte) 0xCC;
    public static final byte OPC_STATUS_RD      = (byte) 0xC3;
    public static final byte OPC_STATUS_RDRE    = (byte) 0xC9;
    public static final byte OPC_SERIAL_WR      = (byte) 0xDD;
    public static final byte OPC_SERIAL_RD      = (byte) 0xD3;
    public static final byte OPC_SERIAL_RDRE    = (byte) 0xD9;

    /* frame lengths */
    public static final short LEN_ACK                   = 3;
    public static final short LEN_NACK                  = 4;
    public static final short LEN_DETECT                = 1;
    public static final short LEN_DETECT_RE             = 3;
    public static final short LEN_SECTOR_WR             = 4103;
    public static final short LEN_CONF_FPGA             = 1;
    public static final short LEN_SOC_SEL               = 1;
    public static final short LEN_MCU_SEL               = 3;
    public static final short LEN_REGISTER_WR           = 6;
    public static final short LEN_REGISTER_MWR          = 6; /* length without data */
    public static final short LEN_REGISTER_AWR          = 6; /* length without data */
    public static final short LEN_REGISTER_RD           = 5;
    public static final short LEN_REGISTER_RDRE         = 4;
    public static final short LEN_REGISTER_MRD          = 6;
    public static final short LEN_REGISTER_MRDRE        = 3; /* length without data */
    public static final short LEN_REGISTER_ARD          = 6;
    public static final short LEN_REGISTER_ARDRE        = 3; /* length without data */
    public static final short LEN_SOC_INT               = 3;
    public static final short LEN_SOC_INT_EN            = 3;
    public static final short LEN_STATUS_WR             = 13;
    public static final short LEN_STATUS_RD             = 1;
    public static final short LEN_STATUS_RDRE           = 13;
    public static final short LEN_SHORTEST_SOC_REPLY    = 3;
    public static final short LEN_SERIAL_WR             = 6;
    public static final short LEN_SERIAL_RD             = 1;
    public static final short LEN_SERIAL_RDRE           = 6;

    /* detect reply constants */
    public static final byte DETECT_RE_FPGA     = (byte) 0xEF;
    public static final byte DETECT_RE_MCU      = (byte) 0x22;
    public static final byte DETECT_RE_MCU_CONF = (byte) 0x33;

    /* timeout and retry constants */
    /** duration (in ms) to wait for detect reply until probing next device */
    public static final long DETECT_TIMEOUT_MILLIS = 200;

    /** timeout (in ms) for sending the detect message (required on Windows) */
    public static final long SEND_DETECT_MESSAGE_TIMEOUT_MILLIS = 500;

    /** timeout (in ms) for switching to MCU */
    public static final long SELECT_MCU_TIMEOUT_MILLIS = 500;

    /** timeout (in ms) for reading the MCU status */
    public static final long STATUS_READ_TIMEOUT_MILLIS = 300;

    /** timeout (in ms) for writing a sector to the non-volatile memory */
    public final static long SECTOR_WRITE_TIMEOUT_MILLIS = 500;

    /** timeout (in ms) for reading serial */
    public static final long SERIAL_READ_TIMEOUT_MILLIS = 200;

    /** maximum duration (in ms) allowed for configuration */
    public static final long CURRENTLY_CONFIGURING_TIMEOUT_MILLIS = 10000;

    /** duration (in ms) until polling whether the board is still configuring */
    public static final long CURRENTLY_CONFIGURING_SLEEP_MILLIS = 200;

    /** timeout (in ms) for reading a single register */
    public static final long REGISTER_READ_TIMEOUT_MILLIS = 500;

    /** timeout (in ms) for closing the connection to a board */
    public static final long CLOSE_CONNECTION_TIMEOUT_MILLIS = 3000;

    /** number of retries on parity errors */
    public static final short RETRIES = 5;


    /**
     * Error codes the FPGA transmits
     */
    public enum Error {
        UNKNOWN(0x00),
        OPCODE_UNKNOWN(0x11),
        PARITY(0x22),
        WISHBONE_TIMEOUT(0x33),
        DATA_LENGTH(0x44);

        private Error(int errorCode) {
        }

        /**
         * Get an Error from an error code byte
         *
         * @param errorCode the byte as is it received with a NACK frame
         * @return Error enumeration type
         */
        public static Error fromByte(byte errorCode) {
            switch (errorCode) {
                case (byte) 0x11:
                    return OPCODE_UNKNOWN;
                case (byte) 0x22:
                    return PARITY;
                case (byte) 0x33:
                    return WISHBONE_TIMEOUT;
                case (byte) 0x44:
                    return DATA_LENGTH;

                /* unknown error core = unknown error */
                default:
                    return UNKNOWN;
            }
        }

    }

    /** application memory sector size */
    public static final short APPMEM_SECTOR_SIZE = 4096;

    /** maximum core address */
    public static final int CORE_ADDRESS_MAX = 0xFF00;

    /**
     * Generate CONF_FPGA frame
     *
     * @return frame object
     */
    public static Frame getFrameCONF_FPGA() {
        Frame frame = new Frame(-1, LEN_CONF_FPGA, new Byte[] {OPC_CONF_FPGA});
        return frame;
    }

    /**
     * Generate MCU_SEL frame
     *
     * @param id Package ID
     * @return frame object
     */
    public static Frame getFrameMCU_SEL(int id) {
        byte[] raw = new byte[LEN_MCU_SEL];
        raw[0] = OPC_MCU_SEL;
        raw[1] = (byte) id;
        raw[2] = xor_parity(new byte[]{raw[0], raw[1]});

        Frame frame = new Frame(id, LEN_MCU_SEL, new Byte[] {raw[0], raw[1], raw[2]});
        return frame;
    }

    /**
     * Generate SOC_INT_EN frame
     *
     * @param id Package ID
     * @return frame object
     */
    public static Frame getFrameSOC_INT_EN(int id) {
        /* create raw frame data */
        Byte[] bytes = new Byte[LEN_SOC_INT_EN];
        bytes[0] = OPC_SOC_INT_EN;
        bytes[1] = (byte) id;
        bytes[2] = xor_parity(new byte[] {bytes[0], bytes[1]});

        /* return frame */
        return new Frame(id, LEN_SOC_INT_EN, bytes);
    }

    /**
     * Generate SOC_SEL frame
     *
     * @return frame object
     */
    public static Frame getFrameSOC_SEL() {
        Frame frame = new Frame(-1, LEN_SOC_SEL, new Byte[] {OPC_SOC_SEL});
        return frame;
    }

    /**
     * Generate REGISTER_WR frame
     *
     * @param id frame ID
     * @param addr 16 bit core and register address (0xCCRR)
     * @param data Data to be written (8 bit)
     * @return frame object
     */
    public static Frame getFrameREGISTER_WR(int id, int addr, int data) {
        /* create raw frame data */
        Byte[] bytes = new Byte[LEN_REGISTER_WR];
        bytes[0] = OPC_REGISTER_WR;
        bytes[1] = (byte) id;
        bytes[2] = (byte) ((addr & 0xFF00) >>> 8); 	/* core address */
        bytes[3] = (byte) (addr & 0xFF);			/* register address */
        bytes[4] = (byte) (data & 0xFF);
        bytes[5] = xor_parity( new byte[] {bytes[0], bytes[1], bytes[2], bytes[3], bytes[4]});

        /* return frame */
        return new Frame(id, LEN_REGISTER_WR, bytes);
    }

    /**
     * Generate REGISTER_MWR frame
     *
     * @param id frame ID
     * @param addr 16 bit core and register address (0xCCRR)
     * @param data int[] containing data to be written. Sets the number of adjacent registers
     *         to be written
     * @return frame object
     */
    public static Frame getFrameREGISTER_MWR(int id, int addr, int[] data) {
        int dataLength = data.length;

        /* create raw frame data */
        Byte[] bytes = new Byte[LEN_REGISTER_MWR + dataLength];
        bytes[0] = OPC_REGISTER_MWR;
        bytes[1] = (byte) id;
        bytes[2] = (byte) ((addr & 0xFF00) >>> 8); 	/* core address */
        bytes[3] = (byte) (addr & 0xFF);			/* register address */
        bytes[4] = (byte) dataLength;

        for (int i = 0; i < dataLength; i++) {
            bytes[5+i] = (byte) (data[i] & 0xFF);
        }

        /* calculate parity */
        Byte[] parityInput = Arrays.copyOf(bytes, LEN_REGISTER_MWR + dataLength - 1);
        bytes[LEN_REGISTER_MWR + dataLength - 1] = xor_parity(parityInput);

        return new Frame(id, bytes.length, bytes);
    }

    /**
     * Generate REGISTER_AWR frame
     *
     * @param id frame ID
     * @param startAddress first address to write. 16 bit core and register address (0xCCRR)
     * @param data int[] containing data to be written. Sets the number of adjacent registers
     *         to be written
     * @return frame object
     */
    public static Frame getFrameREGISTER_AWR(int id, int startAddress, int[] data) {
        int dataLength = data.length;

        /* parameter check */
        if (startAddress > 0xFFFF) {
            throw new IllegalArgumentException("Illegal start address");
        }
        if (data.length == 0 || data.length > 0xFF) {
            throw new IllegalArgumentException("Illegal data length");
        }

        /* create raw frame data */
        Byte[] bytes = new Byte[LEN_REGISTER_AWR + dataLength];
        bytes[0] = OPC_REGISTER_AWR;
        bytes[1] = (byte) id;
        bytes[2] = (byte) ((startAddress & 0xFF00) >>> 8); 	/* core address */
        bytes[3] = (byte) (startAddress & 0xFF);			/* register address */
        bytes[4] = (byte) dataLength;

        for (int i = 0; i < dataLength; i++) {
            bytes[5+i] = (byte) (data[i] & 0xFF);
        }

        /* calculate parity */
        Byte[] parityInput = Arrays.copyOf(bytes, LEN_REGISTER_AWR + dataLength - 1);
        bytes[LEN_REGISTER_AWR + dataLength - 1] = xor_parity(parityInput);

        return new Frame(id, bytes.length, bytes);
    }

    /**
     * Generate REGISTER_RD frame
     *
     * @param id frame ID
     * @param addr 16 bit core and register address (0xCCRR)
     * @return frame object
     */
    public static Frame getFrameREGISTER_RD(int id, int addr) {
        Byte[] bytes = new Byte[LEN_REGISTER_RD];
        bytes[0] = OPC_REGISTER_RD;
        bytes[1] = (byte) id;
        bytes[2] = (byte) ((addr & 0xFF00) >>> 8); 	/* core address */
        bytes[3] = (byte) (addr & 0xFF);			/* register address */
        bytes[4] = xor_parity( new byte[] {bytes[0], bytes[1], bytes[2], bytes[3]});
        return new Frame(id, LEN_REGISTER_RD, bytes);
    }

    /**
     * Generate REGISTER_MRD frame
     *
     * @param id frame ID
     * @param addr 16 bit core and register address (0xCCRR)
     * @return frame object
     */
    public static Frame getFrameREGISTER_MRD(int id, int addr, int numberOfReads) {
        Byte[] bytes = new Byte[LEN_REGISTER_MRD];
        bytes[0] = OPC_REGISTER_MRD;
        bytes[1] = (byte) id;
        bytes[2] = (byte) ((addr & 0xFF00) >>> 8);  /* core address */
        bytes[3] = (byte) (addr & 0xFF);            /* register address */
        bytes[4] = (byte) (numberOfReads & 0xFF);
        bytes[5] = xor_parity( new byte[] {bytes[0], bytes[1], bytes[2], bytes[3], bytes[4]});
        return new Frame(id, LEN_REGISTER_MRD, bytes);
    }

    /**
     * Generate REGISTER_ARD frame
     *
     * @param id Package ID
     * @param startAddress first address to read from. 16 bit core and register address (0xCCRR)
     * @param length number of registers to read
     * @return frame object
     */
    public static Frame getFrameREGISTER_ARD(int id, int startAddress, int length) {

        /* parameter checks */
        if (startAddress < 0 || startAddress > 0xFFFF || length < 0 || length > 0xFF) {
            throw new IllegalArgumentException();
        }

        /* compose frame */
        Byte[] bytes = new Byte[LEN_REGISTER_ARD];
        bytes[0] = OPC_REGISTER_ARD;
        bytes[1] = (byte) id;
        bytes[2] = (byte) ((startAddress & 0xFF00) >>> 8); 	/* core address */
        bytes[3] = (byte) (startAddress & 0xFF);			/* register address */
        bytes[4] = (byte) (length & 0xFF);
        bytes[5] = xor_parity(new byte[] {bytes[0], bytes[1], bytes[2], bytes[3], bytes[4]});
        return new Frame(id, LEN_REGISTER_ARD, bytes);
    }

    /**
     * Generate SECTOR_WR frame for writing an application memory sector
     *
     * @param sector_id number of sector 0 .. 1023
     * @param sector_data byte array containing the sector data
     * @return frame object
     */
    public static Frame getFrameSECTOR_WR(int sector_id, byte[] sector_data) {

        byte[] sector_address;

        /* parameter checks */
        if (sector_id > 1023) throw new IllegalArgumentException("Illegal sector ID");
        if (sector_data.length != 4096) throw new IllegalArgumentException("Illegal sector size");

        /* determine sector_address */
        sector_address = new byte[2];
        sector_address[0] = (byte)(sector_id & 0xFF);			// lowbyte 0..0xff
        sector_address[1] = (byte)((sector_id & 0x300) >> 8);	// highbyte 0..0x3

        /* concatenate addresses and data */
        byte[] address_data = new byte[2+APPMEM_SECTOR_SIZE];
        System.arraycopy(sector_address, 0, address_data, 0 , 2);
        System.arraycopy(sector_data, 0,  address_data, 2, APPMEM_SECTOR_SIZE);

        /* calculate adler32 checksum */
        Adler32 adler = new Adler32();
        adler.reset();
        adler.update(address_data, 0, address_data.length);
        long tempChecksum = adler.getValue();

        /* package checksum */
        Byte[] checksum = new Byte[4];
        checksum[0] = (byte) (tempChecksum & 0x000000FF);
        checksum[1] = (byte) ((tempChecksum & 0x0000FF00) >>  8);
        checksum[2] = (byte) ((tempChecksum & 0x00FF0000) >> 16);
        checksum[3] = (byte) ((tempChecksum & 0xFF000000) >> 24);

        /* package frame */
        Byte[] rawBytes = new Byte[LEN_SECTOR_WR];
        rawBytes[0] = OPC_SECTOR_WR;
        for (int i = 0; i < APPMEM_SECTOR_SIZE + 2; i++) {
            rawBytes[i + 1] = address_data[i];
        }

        System.arraycopy(checksum, 0, rawBytes, 3 + APPMEM_SECTOR_SIZE, 4);
        return new Frame(-1, LEN_SECTOR_WR, rawBytes);
    }

    /**
     * Generate STATUS_WR frame for writing the MCU's soc_status structure
     *
     * @param conf FPGABinary object that contains the status information
     * @return byte[] that contains generated frame
     */
    public static Frame getFrameSTATUS_WR(FPGABinary conf) {
        Byte[] frame = new Byte[LEN_STATUS_WR];
        int start_address, size, hash;

        /* insert mandatory bytes */
        frame[0] = (byte) OPC_STATUS_WR;
        frame[1] = 0;

        /* insert flags */
        if (conf.isSocUploaded()) frame[1] = (byte) (frame[1] + 1);
        if (conf.isSocVerified()) frame[1] = (byte) (frame[1] + 2);

        /* insert start address */
        start_address = conf.getStartSectorID() << 12;
        frame[2] = (byte) ((start_address & 0x000FF000) >> 12);
        frame[3] = (byte) ((start_address & 0x00300000) >> 20);

        /* insert size */
        size = conf.getSize();
        frame[4] = (byte)  (size & 0x000000FF);
        frame[5] = (byte) ((size & 0x0000FF00) >>  8);
        frame[6] = (byte) ((size & 0x00FF0000) >> 16);
        frame[7] = (byte) ((size & 0xFF000000) >> 24);

        /* insert hashCode */
        hash = conf.getHashcode();
        frame[ 8] = (byte)  (hash & 0x000000FF);
        frame[ 9] = (byte) ((hash & 0x0000FF00) >>  8);
        frame[10] = (byte) ((hash & 0x00FF0000) >> 16);
        frame[11] = (byte) ((hash & 0xFF000000) >> 24);

        /* insert parity */
        Byte[] payload = new Byte[LEN_STATUS_WR - 2];
        payload = Arrays.copyOfRange(frame, 1, LEN_STATUS_WR - 1);
        frame[12] = xor_parity(payload);

        return new Frame(-1, LEN_STATUS_WR, frame);
    }

    /**
     * Generate STATUS_RD frame
     *
     * @return frame object
     */
    public static Frame getFrameSTATUS_RD() {
        return new Frame(-1, LEN_STATUS_RD, new Byte[] {OPC_STATUS_RD});
    }

    /**
     * Generate a SERIAL_RD frame
     *
     * @return frame object
     */
    public static Frame getFrameSERIAL_RD() {
        return new Frame(-1, LEN_SERIAL_RD, new Byte[] {OPC_SERIAL_RD});
    }

    /**
     * Generate a SERIAL_WR frame
     *
     * @param serial number to be written
     * @return frame object
     */
    public static Frame getFrameSERIAL_WR(int serial) {
        Byte[] raw = new Byte[LEN_SERIAL_WR];
        raw[0] = OPC_SERIAL_WR;
        raw[1] = (byte) (serial & 0xFF);
        raw[2] = (byte) ((serial & 0xFF00) >> 8);
        raw[3] = (byte) ((serial & 0xFF0000) >> 16);
        raw[4] = (byte) ((serial & 0xFF000000) >> 24);
        raw[5] = xor_parity(new byte[] {raw[0], raw[1], raw[2], raw[3], raw[4]});

        Frame frame = new Frame(-1, LEN_SERIAL_WR, raw);
        return frame;
    }

    /**
     * Calculate an XOR-Parity
     *
     * @param data bytes to be XOR'ed
     * @return Parity
     */
    public static byte xor_parity(byte[] data) {
        byte parity = 0x00;

        for (int index = 0; index < data.length; index++) {
            parity ^= data[index];
        }

        return parity;
    }

    /**
     * Calculate an XOR-Parity
     *
     * @param data Bytes to be XOR'ed
     * @return Parity
     */
    public static Byte xor_parity(Byte[] data) {
        byte parity = 0x00;

        for (int index = 0; index < data.length; index++) {
            parity ^= data[index];
        }

        return parity;
    }
}