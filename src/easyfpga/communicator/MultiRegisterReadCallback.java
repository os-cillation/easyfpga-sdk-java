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

/**
 * Implementation of the Callback interface for the usage with asynchronous multiple- and
 * auto-address-increment register read operations. The data that has been read can be fetched
 * using the getData() method that blocks until the reply is received. Contrary to the classes
 * name, the callback method is supposed to be called only once.
 */
public class MultiRegisterReadCallback implements Callback {

    private boolean received = false;
    private Exchange exchange;

    @Override
    public void callback(Exchange exchange) {
        this.exchange = exchange;
        received = true;
    }

    /**
     * Block until the callback has been called and return the payload of the associated exchange's
     * reply
     *
     * @return the payload of multi-read and auto-address-increment read exchanges
     */
    public int[] getData() {
        /* block until callback called */
        while (!received) {
            Thread.yield();
        }

        /* get length and data */
        int length = exchange.getRequest().getRawByte(4) & 0xFF;
        Byte[] data = new Byte[length];
        Frame reply = exchange.getReply();
        data = Arrays.copyOfRange(reply.getRawBytes(), 2, 2 + length);

        /* convert to int[] */
        int[] dataOut = new int[length];
        for (int i = 0; i < length; i++) {
            dataOut[i] = data[i] & 0xFF;
        }

        return dataOut;
    }
}
