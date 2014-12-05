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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import easyfpga.generator.model.Core;
import easyfpga.generator.model.FPGA;

/**
 * Manages the detection and separation of frames in the receive buffer. All
 * detected frames are removed from the receive buffer, converted into frame
 * objects and inserted into the receive frame buffer.
 */
class FrameSeparator {

    private LinkedBlockingQueue<Byte> receiveBuffer;
    private ConcurrentHashMap<Integer, Frame> receiveFrameBuffer;
    private boolean isActive = false;
    private Communicator com;
    private FPGA fpga;

    /** contains all pending exchanges and their IDs */
    private ConcurrentHashMap<Integer, Exchange> pendingExchanges;

    private final static Logger LOGGER = Logger.getLogger(FrameSeparator.class.getName());

    /**
     * Constructor called by the Communicator class
     *
     * @param receiveBuffer reference to the receive buffer queue containing raw bytes
     * @param receivedFrames reference to the map assigning received frames to their ids
     * @param com reference to the Communicator itself
     * @param fpga the FPGA managed by the Communicator
     * @param pendingExchanges reference to the Communicator's pending exchanges
     */
    public FrameSeparator(	LinkedBlockingQueue<Byte> receiveBuffer,
                            ConcurrentHashMap<Integer, Frame> receivedFrames,
                            Communicator com,
                            FPGA fpga,
                            ConcurrentHashMap<Integer, Exchange> pendingExchanges) {
        this.receiveBuffer = receiveBuffer;
        this.receiveFrameBuffer = receivedFrames;
        this.com = com;
        this.fpga = fpga;
        this.pendingExchanges = pendingExchanges;
    }

    /**
     * Convert a received sequence of bytes into a frame object. Called by the communicator after
     * the receive buffer changes
     */
    public void process() {
        /* return if inactive */
        if (!isActive) {
            return;
        }

        Byte opcode = receiveBuffer.peek();
        if (opcode == null) {
            return;
        }

        switch (opcode) {
        case Protocol.OPC_ACK:
            processACK();
            break;
        case Protocol.OPC_NACK:
            processNACK();
            break;
        case Protocol.OPC_REGISTER_RDRE:
            processRDRE();
            break;
        case Protocol.OPC_REGISTER_MRDRE:
            processMultiRDRE();
            break;
        case Protocol.OPC_REGISTER_ARDRE:
            processMultiRDRE();
            break;
        case Protocol.OPC_SOC_INT:
            processINT();
            break;

        /* if the first byte is unexpected remove it */
        default:
            Byte[] unknown = take(1);
            LOGGER.warning(String.format("Removed unexpected byte: 0x%02X", unknown[0]));
            break;
        }
    }

    /**
     * Activate or deactivate processing. While communicating the MCU the frame separator is
     * deactivated.
     *
     * @param isActive
     */
    public void setActive(boolean isActive) {
        LOGGER.entering(getClass().getName(), "setActive( " + isActive + " )");
        this.isActive = isActive;
    }

    private void processACK() {
        Byte[] rawBytes = take(Protocol.LEN_ACK);
        Frame frame;
        frame = new Frame(rawBytes[1].intValue() & 0xFF, Protocol.LEN_ACK, rawBytes);
        receiveFrameBuffer.put(frame.getID(), frame);
    }

    private void processNACK() {
        Byte[] rawBytes = take(Protocol.LEN_NACK);
        Frame frame;
        frame = new Frame(rawBytes[1].intValue() & 0xFF, Protocol.LEN_NACK, rawBytes);
        receiveFrameBuffer.put(frame.getID(), frame);
    }

    private void processRDRE() {
        Byte[] rawBytes = take(Protocol.LEN_REGISTER_RDRE);
        Frame frame;
        frame = new Frame(rawBytes[1].intValue() & 0xFF, Protocol.LEN_REGISTER_RDRE, rawBytes);
        receiveFrameBuffer.put(frame.getID(), frame);
    }

    /**
     * Process both MRDRE and ARDRE frames
     */
    private void processMultiRDRE() {
        /* take the first two bytes (OPC and ID) */
        Byte[] opcID = take(2);
        int id = opcID[1] & 0xFF;

        /* get the length byte of the request */
        while (!pendingExchanges.containsKey(id));
        Frame request = pendingExchanges.get(id).getRequest();
        int length = request.getRawByte(4) & 0xFF;

        /* take data and parity */
        Byte[] dataParity = take(length + 1);

        /* merge to two arrays */
        Byte[] wholeFrame = new Byte[opcID.length + dataParity.length];
        System.arraycopy(opcID, 0, wholeFrame, 0, opcID.length);
        System.arraycopy(dataParity, 0, wholeFrame, opcID.length, dataParity.length);

        /* add to received frames buffer */
        Frame frame = new Frame(id, length, wholeFrame);
        receiveFrameBuffer.put(id, frame);

    }

    private void processINT() {
        Byte[] rawBytes = take(Protocol.LEN_SOC_INT);

        /* get a reference to the core requesting the interrupt */
        final Core irqCore = fpga.getCoreByAddress((rawBytes[1] << 8) & 0xFF00);

        /* start new thread to propagate interrupt event */
        new Thread(new Runnable() {
            public void run() {
                com.propagateInterruptEvent(new InterruptEvent(irqCore));
            }
        }).start();
    }

    private Byte[] take(int bytecount) {
        Byte[] bytes = new Byte[bytecount];
        for (int i = 0; i < bytecount; i++) {
            try {
                bytes[i] = receiveBuffer.take();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return bytes;
    }
}