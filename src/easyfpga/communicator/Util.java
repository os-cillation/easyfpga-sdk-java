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

package easyfpga.communicator;


/**
 * Static communicator utility methods
 */
public class Util {

    public static String toHexString(int n) {
        return String.format("0x%8s", Integer.toHexString(n)).replace(' ', '0').toUpperCase();
    }

    public static String toHexString(byte[] byteStream) {
        String output = "";
        for (byte b : byteStream) {
            output += String.format("%02X ", b);
        }
        return output;
    }

    public static String toHexString(int[] byteStream) {
        String output = "";
        for (int i : byteStream) {
            output += String.format("%02X ", i & 0xFF);
        }
        return output;
    }
}
