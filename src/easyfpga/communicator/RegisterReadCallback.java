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
 * A callback that can be passed to a number of readRegisterAsync calls. The number of reads has
 * to be specified when calling the constructor. The data that has been read can be fetched using
 * the getData() method that blocks until the given number of reads has been performed. Before that,
 * data can be fetched partially.
 */
public class RegisterReadCallback implements Callback {

    /* received data and flags which portion has already been received */
    private int[] receivedData;
    private boolean[] receiveFlags;

    private int numberOfReads;
    private int readCount = 0;
    private boolean completed = false;

    /**
     * Construct a RegisterReadCallback that can be called multiple times
     *
     * @param numberOfReads how many times the callback will be called
     */
    public RegisterReadCallback(int numberOfReads) {
        /* parameter check */
        if (numberOfReads <= 0) {
            throw new IllegalArgumentException();
        }

        this.numberOfReads = numberOfReads;
        this.receivedData = new int[numberOfReads];
        this.receiveFlags = new boolean[numberOfReads];
    }

    @Override
    public void callback(Exchange exchange) {

        readCount++;
        if (readCount == numberOfReads) completed = true;

        /* fetch received data */
        int i = exchange.getCallbackSequenceID();
        receivedData[i] = exchange.getReply().getRawByte(2) & 0xFF;

        /* mark as received */
        receiveFlags[i] = true;
    }

    /**
     * Partially fetch received data. Blocks until data with the given sequence ID is received.
     *
     * @param sequenceID number of read operation to get data from (0 .. numberOfReads-1)
     * @return data that belongs to the given sequence ID
     */
    public int getData(int sequenceID) {
        /* parameter check */
        if (sequenceID < 0 || sequenceID >= numberOfReads) {
            throw new IllegalArgumentException();
        }

        /* block until the requested data is received and return */
        while (receiveFlags[sequenceID] == false) {
            Thread.yield();
        }
        return receivedData[sequenceID];
    }

    /**
     * Get received data. Blocks until the given number of reads is done (the callback method has
     * been called numberOfRead times)
     *
     * @return received data as an array of int in the order the readRegisterAsync method
     *          has been called
     */
    public int[] getData() {
        while (!completed) {
            Thread.yield();
        }
        return receivedData;
    }
}
