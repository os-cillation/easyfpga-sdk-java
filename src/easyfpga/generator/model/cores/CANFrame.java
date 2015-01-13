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

import java.util.Arrays;

/**
 * A CAN bus frame or message
 */
public class CANFrame {

    private boolean isExtended;
    private boolean isRemoteTransferRequest;
    private int[] data;
    private int identifier;
    private int[] descriptor;
    private int dataLength;

    /**
     * Construct basic frame carrying data. The data length code is determined by the length of
     * given data.
     *
     * @param identifier a unique 11-bit identifier (0 .. 0x7FF) which indicates the priority
     *         (lower -> higher priority)
     * @param data (up to 8 8-bit integers)
     */
    public CANFrame(int identifier, int[] data) {
        this(identifier, data, false);
    }

    /**
     * Construct a basic or extended frame carrying data. The data length code is determined by
     * the length of given data.
     *
     * @param identifier a unique identifier. 11 bits (0 .. 0x7FF) for basic-, and 29 bits
     *         (0 .. 0x1FFFFFFF) for extended frames) which indicates the priority
     *         (lower -> higher priority)
     * @param data (up to 8 8-bit integers)
     * @param isExtended true, if an extended frame should be constructed
     */
    public CANFrame(int identifier, int[] data, boolean isExtended) {

        /* parameter check */
        if (isExtended == false && (identifier < 0 || identifier > 0x7FF)) {
            throw new IllegalArgumentException("Illegal identifier for basic CAN frame");
        }
        if (isExtended == true && (identifier < 0 || identifier > 0x1FFFFFFF)) {
            throw new IllegalArgumentException("Illegal identifier for extended CAN frame");
        }
        if (data.length < 1 || data.length > 8) {
            throw new IllegalArgumentException("Illegal data length: " + data.length);
        }
        for (int dataByte : data) {
            if (dataByte < 0 || dataByte > 0xFF) {
                throw new IllegalArgumentException("Illegal data byte found");
            }
        }

        /* assign fields */
        this.isExtended = isExtended;
        this.isRemoteTransferRequest = false;
        this.dataLength = data.length;
        this.data = new int[this.dataLength];
        this.data = Arrays.copyOf(data, this.dataLength);
        this.identifier = identifier;

        /* build descriptor */
        buildDescriptor();
    }

    /**
     * Construct remote transmission request frame in basic format
     *
     * @param identifier a unique identifier (0 .. 0x7FF) which indicates the priority
     *         (lower -> higher priority)
     */
    public CANFrame(int identifier) {
        this(identifier, false);
    }

    /**
     * Construct remote transmission request frame, either basic or extended format
     *
     * @param identifier a unique identifier. 11 bits (0 .. 0x7FF) for basic-, and 29 bits
     *         (0 .. 0x1FFFFFFF) for extended frames) which indicates the priority
     *         (lower -> higher priority)
     * @param isExtended true, if an extended frame should be constructed
     */
    public CANFrame(int identifier, boolean isExtended) {

        /* parameter check */
        if (!isExtended && (identifier < 0 || identifier > 0x7FF)) {
            throw new IllegalArgumentException("Illegal identifier");
        }
        else if (isExtended && (identifier < 0 || identifier > 0x1FFFFFFF)) {
            throw new IllegalArgumentException("Illegal identifier");
        }

        /* create empty data */
        this.data = new int[8];
        this.dataLength = 0;

        /* assign fields and build descriptor */
        this.isExtended = isExtended;
        this.isRemoteTransferRequest = true;
        this.identifier = identifier;
        buildDescriptor();
    }

    private void buildDescriptor() {
        /* build descriptor */
        if (isExtended == false) {
            descriptor = new int[2];
            descriptor[0] = (identifier & 0x07F8) >>> 3;
            descriptor[1] = (identifier & 0x0007) << 5;
            if (isRemoteTransferRequest) {
                descriptor[1] |= (1 << 4);
            }
            else {
                descriptor[1] |= (data.length & 0x0F);
            }
        }
        /* extended mode: four bytes (for the identifier registers 0..3) */
        else {
            descriptor = new int[4];
            descriptor[0] = (identifier & (0xFF << 21)) >>> 21; /* ID28 .. ID21 */
            descriptor[1] = (identifier & (0xFF << 13)) >>> 13; /* ID20 .. ID13 */
            descriptor[2] = (identifier & (0xFF <<  5)) >>>  5; /* ID12 .. ID5  */
            descriptor[3] = ((identifier & 0x1F) << 3);         /* ID4 .. ID0 & 3xDon't care */
        }
    }

    /**
     * Get the data bytes
     *
     * @return up to 8 8-bit integers containing the data
     */
    public int[] getData() {
        return data;
    }

    /**
     * Get the frame identifier
     *
     * @return Frame identifier. 29 bits for extended- and 11 bits for basic frames.
     */
    public int getIdentifier() {
        return identifier;
    }

    /**
     * Get the descriptor bytes containing the identifier, data length code and remote transfer
     * request bit.
     *
     * @return two (or four for extended frames) 8-bit integers containing the descriptor
     */
    public int[] getDescriptor() {
        return descriptor;
    }

    /**
     * @return True if frame is in extended frame format
     */
    public boolean isExtended() {
        return isExtended;
    }

    /**
     * @return True if frame is remote transfer request
     */
    public boolean isRTR() {
        return isRemoteTransferRequest;
    }

    /**
     * Get the frame information byte containing data length code, RTR bit and frame
     * format information
     *
     * @return 8-bit frame information integer
     */
    public int getFrameInformation() {
        int frameInfo = dataLength & 0xFF;
        if (isRemoteTransferRequest) frameInfo |= (1 << 6);
        if (isExtended) frameInfo |= (1 << 7);
        return frameInfo;
    }

    /**
     * Get the data length or zero for remote transmission request frames
     *
     * @return data length
     */
    public int getDataLength() {
        return dataLength;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        /* header */
        if (isExtended) {
            sb.append("<-- EXTENDED CAN MESSAGE (id = ");
            sb.append(String.format("0x%08X", identifier));
        }
        else {
            sb.append("<-- BASIC CAN MESSAGE (id = ");
            sb.append(String.format("0x%03X", identifier));
        }
        sb.append(") -->\n");

        /* descriptors */
        String descr0 = Integer.toBinaryString(descriptor[0] | 0x100).substring(1);
        String descr1 = Integer.toBinaryString(descriptor[1] | 0x100).substring(1);
        String descr2 = null;
        String descr3 = null;
        if (isExtended) {
            descr2 = Integer.toBinaryString(descriptor[2] | 0x100).substring(1);
            descr3 = Integer.toBinaryString(descriptor[3] | 0x100).substring(1);
        }
        sb.append("Descriptor 1: 0b" + descr0 + "\n");
        sb.append("Descriptor 2: 0b" + descr1 + "\n");
        if (isExtended) {
            sb.append("Descriptor 3: 0b" + descr2 + "\n");
            sb.append("Descriptor 4: 0b" + descr3 + "\n");
        }

        /* data */
        if (dataLength == 0) sb.append("RTR frame - no data");
        for (int i = 0; i < dataLength; i++) {
            sb.append("      Data " + (i+1) + ": 0x" + String.format("%02X", data[i]) + "\n");
        }

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(data);
        result = prime * result + dataLength;
        result = prime * result + Arrays.hashCode(descriptor);
        result = prime * result + identifier;
        result = prime * result + (isExtended ? 1231 : 1237);
        result = prime * result + (isRemoteTransferRequest ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CANFrame other = (CANFrame) obj;
        if (!Arrays.equals(data, other.data))
            return false;
        if (dataLength != other.dataLength)
            return false;
        if (!Arrays.equals(descriptor, other.descriptor))
            return false;
        if (identifier != other.identifier)
            return false;
        if (isExtended != other.isExtended)
            return false;
        if (isRemoteTransferRequest != other.isRemoteTransferRequest)
            return false;
        return true;
    }
}