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

/**
 * A Frame that is used for communication
 */
class Frame {
    private int id;
    private int length;
    private Byte[] data;

    /**
     * Construct a frame
     *
     * @param id of the frame
     * @param length of the entire frame including opcode and parity
     * @param data an array of Byte containing the entire frame
     */
    public Frame(int id, int length, Byte[] data) {
        this.id = id;
        this.length = length;
        this.data = data;
    }

    /**
     * @return the raw bytes as they are sent or received
     */
    public Byte[] getRawBytes() {
        return data;
    }

    /**
     * Get a byte at a certain position
     *
     * @param position of the byte to fetch (0 .. length-1)
     * @return a single Byte
     */
    public Byte getRawByte(int position) {
        return data[position];
    }

    /**
     * @return the id associated with the frame object
     */
    public int getID() {
        return id;
    }

    /**
     * Check the frame's parity assuming that the last byte contains the parity which is determined
     * by bitwise calculating the exclusive-or of all preceding bytes
     *
     * @return the if parity check passes
     */
    public boolean checkParity() {
        final int lengthWithoutParity = data.length - 1;
        byte[] frameWithoutParity = new byte[lengthWithoutParity];

        for (int i = 0; i < lengthWithoutParity; i++) {
            frameWithoutParity[i] = data[i];
        }

        return (Protocol.xor_parity(frameWithoutParity) == data[lengthWithoutParity]);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < length; i++) {
            sb.append(String.format("0x%02X", data[i]));
            if (i < length - 1) {
                sb.append(" ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}