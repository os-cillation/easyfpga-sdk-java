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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import easyfpga.Util;
import easyfpga.exceptions.CommunicationException;
import easyfpga.generator.model.FPGA;

/**
 * Central class to manage the communication with the easyFPGA board.
 */
public class Communicator {
    private IC activeIC;
    private VirtualComPort vcp;
    private IDGenerator idGenerator;
    private FrameSeparator frameSeparator;
    private Thread exchangeHandlingThread;
    private ExchangeHandleRunnable exchangeHandlingRunnable;

    private static final Logger LOGGER = Logger.getLogger(Communicator.class.getName());

    /** list of InterruptListeners */
    private ArrayList<InterruptListener> interruptListenerList;

    /** raw bytes received */
    private LinkedBlockingQueue<Byte> receiveBuffer;

    /** frame objects of received frames */
    private ConcurrentHashMap<Integer, Frame> receivedFrames;

    /** pending exchanges and their IDs */
    private ConcurrentHashMap<Integer, Exchange> pendingExchanges;

    /** number of reads performed with a certain callback */
    private Hashtable<Callback, Integer> callbackReadCount;

    /**
     * @param com VirtualComPort
     * @param fpga instance of the FPGA to communicate with
     */
    public Communicator(VirtualComPort com, FPGA fpga) {
        this.vcp = com;
        this.vcp.setCommunicator(this);
        this.idGenerator = new IDGenerator();
        this.interruptListenerList = new ArrayList<InterruptListener>();
        this.receiveBuffer = vcp.getReceiveBuffer();
        this.receivedFrames = new ConcurrentHashMap<Integer, Frame>();
        this.pendingExchanges = new ConcurrentHashMap<Integer, Exchange>();
        this.frameSeparator = new FrameSeparator(receiveBuffer, receivedFrames, this,
                                                    fpga, pendingExchanges);
        this.exchangeHandlingRunnable = new ExchangeHandleRunnable(this);
        this.exchangeHandlingThread = new Thread(exchangeHandlingRunnable);
        this.exchangeHandlingThread.setName("ReplyHandlingThread");
        this.callbackReadCount = new Hashtable<Callback, Integer>();
        this.activeIC = IC.MCU;
    }

    /**
     * Constructor that does not set the FPGA reference
     *
     * @param com virtual comport
     */
    public Communicator(VirtualComPort com) {
        this(com, null);
    }

    /**
     * Request the MCU to switch to the SoC
     *
     * @return True if successful
     */
    public boolean selectSoC() {
        LOGGER.entering(getClass().getName(), "selectSoC");
        /* send SOC_SELECT command to MCU */
        vcp.send(Protocol.getFrameSOC_SEL());

        /* start reply handling thread */
        exchangeHandlingThread.start();

        /* if successful, activate frame separator */
        if (mcuSuccess()) {
            frameSeparator.setActive(true);
            activeIC = IC.FPGA;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Switch to the MCU and close the connection
     */
    public void closeConnection() {
        LOGGER.entering(getClass().getName(), "closeConnection");

        /* wait for processing all pendingReplies */
        int elapsedMillis = 0;
        while (!pendingExchanges.isEmpty()) {
            try {
                Thread.sleep(1);
                elapsedMillis++;
                if (elapsedMillis >= Protocol.CLOSE_CONNECTION_TIMEOUT_MILLIS) {
                    break;
                }
            }
            catch (InterruptedException ignored) {}
        }

        /* close connection */
        exchangeHandlingThread.interrupt();
        if (activeIC == IC.FPGA) selectMCU();
        vcp.close();

        /* exception if there are still pendingReplies */
        if (!pendingExchanges.isEmpty()) {
            LOGGER.warning("Pending transmissions while closing connection:" + Util.LS +
                                       getStatusString());
        }

        /* run garbage collector to speed up application exit */
        System.gc();
        LOGGER.finer("Connection closed");
    }

    /**
     * Switch to the MCU
     */
    public void selectMCU() {
        LOGGER.entering(getClass().getName(), "selectMCU");

        /* check whether necessary */
        if (!isFPGAActive()) {
            activeIC = IC.MCU;
            return;
        }

        /* deactivate frame separator */
        this.frameSeparator.setActive(false);

        boolean success = false;
        while (success == false) {

            /* send request */
            int id = idGenerator.getFreeID();
            vcp.send(Protocol.getFrameMCU_SEL(id));

            /* assume ACK, receive 3 bytes. 1s timeout. */
            byte[] reply;
            try {
                reply = vcp.receive(Protocol.LEN_ACK, Protocol.SELECT_MCU_TIMEOUT_MILLIS);
            }
            catch (TimeoutException e) {
                success = false;
                break;
            }


            if (reply[0] == Protocol.OPC_ACK && reply[1] == (byte) (id & 0xFF)) {
                success = true;
            }

            /* if NACK received receive remaining byte and retry */
            else if (reply[0] == Protocol.OPC_NACK) {
                try {
                    vcp.receive(1, Protocol.SELECT_MCU_TIMEOUT_MILLIS);
                }
                catch (TimeoutException e) {
                    LOGGER.warning("Timeout during reception of remaining byte of NACK frame");
                }
                success = false;
            }
        }

        activeIC = IC.MCU;
    }

    /**
     * Set the boards serial number
     *
     * @param serial to be written
     */
    public void writeSerial(int serial) {
        LOGGER.entering(getClass().getName(), "writeSerial");
        boolean success = false;
        if (activeIC == IC.MCU) {
            do {
                vcp.send(Protocol.getFrameSERIAL_WR(serial));
                success = mcuSuccess();
            } while(!success);
        }
        else {
            throw new IllegalStateException();
        }
    }

    /**
     * Get the boards serial number. Switch to MCU if necessary
     *
     * @return serial number
     * @throws TimeoutException
     */
    public int readSerial() throws TimeoutException {

        LOGGER.entering(getClass().getName(), "readSerial");
        byte[] reply = new byte[Protocol.LEN_SERIAL_RDRE];
        byte[] replyTmp = new byte[Protocol.LEN_NACK];
        byte[] remaining = new byte[Protocol.LEN_SERIAL_RDRE - Protocol.LEN_NACK];
        int parityReceived;
        int parityCalculated;
        int serial;

        /* try until parity match */
        do {
            /* request/reply reading only length of NACK (which would be sent by SoC) */
            vcp.send(Protocol.getFrameSERIAL_RD());
            replyTmp = vcp.receive(Protocol.LEN_NACK, Protocol.SERIAL_READ_TIMEOUT_MILLIS);

            /* on reception of NACK switch to MCU and retry */
            if (replyTmp[0] == Protocol.OPC_NACK) {
                selectMCU();
                vcp.send(Protocol.getFrameSERIAL_RD());
                replyTmp = vcp.receive(Protocol.LEN_NACK, Protocol.SERIAL_READ_TIMEOUT_MILLIS);
            }

            /* mcu is active, receive remaining bytes */
            remaining = vcp.receive(Protocol.LEN_SERIAL_RDRE - Protocol.LEN_NACK,
                                        Protocol.SERIAL_READ_TIMEOUT_MILLIS);

            System.arraycopy(replyTmp, 0, reply, 0, Protocol.LEN_NACK);
            System.arraycopy(remaining, 0, reply, Protocol.LEN_NACK, remaining.length);

            parityReceived = reply[Protocol.LEN_SERIAL_RDRE - 1];

            /* calculate parity */
            byte[] payload = Arrays.copyOf(reply, reply.length - 1);
            parityCalculated = Protocol.xor_parity(payload);

            if (parityCalculated != parityReceived) {
                LOGGER.info("Parity error");
            }
        }
        while (parityReceived != parityCalculated);

        /* return serial number */
        serial = reply[1]        & 0xFF |
                (reply[2] <<  8) & 0xFF00 |
                (reply[3] << 16) & 0xFF0000 |
                (reply[4] << 24) & 0xFF000000;

        LOGGER.finer(String.format("Will return serial 0x%08X", serial));
        return serial;
    }

    /**
     * Write to a register in the SoC
     *
     * @param address
     *            8 bit core and 8 bit register address: 0xCCRR
     * @param data
     *            8 data bits
     */
    public void writeRegister(int address, int data) {
        checkAddress(address);

        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_WR(id, address, data);

        /* send request and add pending reply */
        generatePendingExchange(id, request);
        vcp.send(request);
    }

    /**
     * Write multiple times to a register in the SoC
     *
     * @param address
     *            8 bit core and 8 bit register address: 0xCCRR
     * @param data
     *            8 data bits
     */
    public void writeRegister(int address, int[] data) {
        checkAddress(address);

        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_MWR(id, address, data);

        /* send request and add pending reply */
        generatePendingExchange(id, request);
        vcp.send(request);
    }

    /**
     * Auto-Address-Increment write. Write multiple registers (of one core) with
     * consecutive addresses
     * @param startAddress the full address of the first register to write (0xCCRR)
     * @param data integer array to be written. The array's length determines how many
     *         consecutive registers will be written
     */
    public void writeRegisterAAI(int startAddress, int[] data) {
        checkAddress(startAddress);

        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_AWR(id, startAddress, data);

        /* send request and add pending reply */
        generatePendingExchange(id, request);
        vcp.send(request);
    }

    /**
     * Read from a register in the FPGA
     *
     * @param address 8 bit core and 8 bit register address: 0xCCRR
     * @return register value
     * @throws CommunicationException
     */
    public int readRegister(int address) throws CommunicationException {
        checkAddress(address);

        /* get id and generate request frame */
        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_RD(id, address);

        /* send request and add exchange */
        Exchange exchange = generatePendingExchange(id, request);
        vcp.send(request);

        /* check receive buffer until reply received */
        long startMillis = System.currentTimeMillis();
        while (exchange.getReply() == null) {
            receiveBufferChanged();

            /* retry after timeout */
            if (System.currentTimeMillis() - startMillis > Protocol.REGISTER_READ_TIMEOUT_MILLIS) {
                LOGGER.warning("Register read took longer than " +
                            Protocol.REGISTER_READ_TIMEOUT_MILLIS + " ms. Will now retry.");
                return readRegister(address);
            }

            if (exchange.isTimedOut()) {
                CommunicationException ex;
                ex = new CommunicationException("Timeout of register read packet with id " + id);
                LOGGER.log(Level.SEVERE, "Throwing exception", ex);
                throw ex;
            }
        }

        /* process reply */
        Frame reply = exchange.getReply();

        if(reply.checkParity() == false) {
            LOGGER.log(Level.INFO, "Parity check failed. Will now retry");
            return readRegister(address);
        }
        if (exchange.getReplyOpcode() == Protocol.OPC_REGISTER_RDRE) {
            return exchange.getReply().getRawByte(2) & 0xFF;
        }
        else {
            LOGGER.log(Level.INFO, "Retry register read after reception of NACK: " + exchange);
            return readRegister(address);
        }
    }

    /**
     * Read from a register in the FPGA multiple times. Useful for reading Wishbone-Attached FIFOs.
     *
     * @param address 8 bit core and 8 bit register address: 0xCCRR
     * @param numberOfReads how many read operations should be performed
     * @return multiple register value
     * @throws CommunicationException
     */
    public int[] readRegister(int address, int numberOfReads) throws CommunicationException {

        /* parameter checks */
        checkAddress(address);
        if (numberOfReads == 0) {
            throw new IllegalStateException();
        }

        /* use normal read if number is 1 */
        else if (numberOfReads == 1) {
            int[] returnArray = new int[1];
            returnArray[0] = readRegister(address);
            return returnArray;
        }

        /* use multi-read (MRD) command */

        /* get id and generate request frame */
        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_MRD(id, address, numberOfReads);

        /* send request and add pending exchange */
        Exchange exchange = generatePendingExchange(id, request);
        vcp.send(request);

        /* check receive buffer until reply received */
        while (exchange.getReply() == null) {
            receiveBufferChanged();
            if (exchange.isTimedOut()) {
                CommunicationException ex;
                ex = new CommunicationException("Timeout of register multi-read"
                                                + "packet with id " + id);
                LOGGER.log(Level.SEVERE, "Throwing exception", ex);
                throw ex;
            }
        }

        /* process reply */
        Frame reply = exchange.getReply();
        Byte[] rawReplyData;
        int[] replyData = new int[numberOfReads];

        if(reply.checkParity() == false) {
            throw new CommunicationException("Parity check (id = " + id + ") failed");
        }
        if (exchange.getReplyOpcode() == Protocol.OPC_REGISTER_MRDRE) {
            rawReplyData = exchange.getReply().getRawBytes();
        }
        else {
            throw new CommunicationException("Not acknowledged received");
        }

        /* cut relevant reply bytes and convert to int[] */
        for (int i = 0; i < numberOfReads; i++) {
            replyData[i] = rawReplyData[i+2] & 0xFF;
        }

        return replyData;
    }

    /**
     * Read a register in the FPGA asynchronously
     *
     * @param address 8 bit core and 8 bit register address: 0xCCRR
     * @param callback
     *            implementation of the BackCallable interface that defines what to do when the
     *            reply containing the value is received
     */
    public void readRegisterAsync(int address, RegisterReadCallback callback) {
        checkAddress(address);

        /* get id and generate request frame */
        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_RD(id, address);

        /* send request */
        vcp.send(request);

        /* check how many async reads have already been performed with this callback */
        Integer currentCount = callbackReadCount.get(callback);
        if (currentCount == null) currentCount = 0;
        else currentCount++;

        /* update callbackReadCount */
        callbackReadCount.put(callback, currentCount);

        /* generate exchange containing the callbackReadCount */
        Exchange pr = new Exchange(request, callback, currentCount);
        while(pendingExchanges.put(id, pr) != null);
    }

    /**
     * Asynchronously read from a register in the FPGA multiple times. Useful for reading
     * Wishbone-Attached FIFOs.
     *
     * @param address 8 bit core and 8 bit register address: 0xCCRR
     * @param numberOfReads how many bytes to read
     * @param callback of type MultiRegisterReadCallback
     */
    public void readRegisterAsync(int address, int numberOfReads, MultiRegisterReadCallback callback) {
        checkAddress(address);

        /* get id and generate request frame */
        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_MRD(id, address, numberOfReads);

        /* send request */
        vcp.send(request);

        /* generate exchange with callback count 1 */
        Exchange pr = new Exchange(request, callback, 1);
        while(pendingExchanges.put(id, pr) != null);
    }

    /**
     * Read multiple registers with ascending addresses. Block until a reply is received
     *
     * @param startAddress
     *            8 bit core and 8 bit register address: 0xCCRR
     * @param length number of registers to read
     * @return an int[] containing the read values beginning at the value of the register
     *          at the start address
     * @throws CommunicationException
     */
    public int[] readRegisterAAI(int startAddress, int length) throws CommunicationException {
        /* parameter checks */
        checkAddress(startAddress);
        if (length < 0 || length > 0xFF) throw new IllegalArgumentException();

        /* get id and generate request frame */
        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_ARD(id, startAddress, length);

        /* send request and add pending exchange */
        vcp.send(request);
        Exchange exchange = generatePendingExchange(id, request);

        /* check receive buffer until reply received */
        while (exchange.getReply() == null) {
            receiveBufferChanged();
            if (exchange.isTimedOut()) {
                throw new CommunicationException("Timeout of register read AAI packet with id " + id);
            }
        }

        /* process reply */
        Frame reply = exchange.getReply();
        Byte[] rawReplyData;
        int[] replyData = new int[length];

        if(reply.checkParity() == false) {
            throw new CommunicationException("Parity check (id = " + id + ") failed");
        }
        if (exchange.getReplyOpcode() == Protocol.OPC_REGISTER_ARDRE) {
            rawReplyData = exchange.getReply().getRawBytes();
        }
        else {
            throw new CommunicationException("Not acknowledged received");
        }

        /* convert to int[] */
        for (int i = 0; i < length; i++) {
            replyData[i] = rawReplyData[i+2] & 0xFF;
        }

        return replyData;
    }

    /**
     * Asynchronous auto-address-increment read
     *
     * @param startAddress
     *          8 bit core and 8 bit register address: 0xCCRR
     * @param length
     *          number of adjacent registers to read from
     * @param callback
     *            implementation of the BackCallable interface that defines what to do when the
     *            reply containing the value is received
     */
    public void readRegisterAAIAsync(int startAddress, int length, MultiRegisterReadCallback callback) {
        /* parameter checks */
        checkAddress(startAddress);
        if (length < 0 || length > 0xFF) throw new IllegalArgumentException();

        /* get id and generate request frame */
        final int id = getFreeID();
        final Frame request = Protocol.getFrameREGISTER_ARD(id, startAddress, length);

        /* send request */
        vcp.send(request);

        /* generate exchange with callback count 1 */
        Exchange pr = new Exchange(request, callback, 1);
        while(pendingExchanges.put(id, pr) != null);
    }

    private void checkAddress(int address) {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Invalid adddress (allowed range 0x0000 .. 0xFFFF)");
        }
    }

    /**
     * Get a free packet id. If necessary, try to free ids by looking for matching replies.
     * @return a free id
     */
    private int getFreeID() {
        int id = 0;
        while (id == 0 || id == -1) {
            id = idGenerator.getFreeID();
            if (id == -1) {
                checkReplyMatches();
            }
        }
        return id;
    }

    /**
     *  Generate a pending exchange with a certain request frame and add it to the list of pending
     *  exchanges. Wait if there is already a pending exchange with the same id id.
     *
     *  @param id associated with the new exchange
     *  @param request of the new exchange
     *  @return a new exchange object
     */
    private Exchange generatePendingExchange(int id, Frame request) {
        Exchange newExchange = new Exchange(request);
        while(pendingExchanges.put(id, newExchange) != null);
        return newExchange;
    }

    /**
     * Notification method called by VirtualComPort after receiving data
     */
    public void receiveBufferChanged() {

        /* separate all frames in receive buffer */
        synchronized (receiveBuffer) {
            while (receiveBuffer.size() >= Protocol.LEN_SHORTEST_SOC_REPLY) {
                frameSeparator.process();
            }
        }
        checkReplyMatches();
    }

    /**
     * Find and process received frames that match to a pending exchange.
     */
    private synchronized void checkReplyMatches() {
        /* get keys that are in both receivedFrames and pendingReplyThreads */
        Set<Integer> matchingKeys = new HashSet<Integer>(receivedFrames.keySet());
        matchingKeys.retainAll(pendingExchanges.keySet());

        /* check for received frames that match pending reply threads */
        for (Integer receivedFrameID : matchingKeys) {

            /* check if timed out */
            Exchange pendingExchange = pendingExchanges.get(receivedFrameID);
            if (pendingExchange.isTimedOut()) {
                LOGGER.info("Timeout of frame # " + receivedFrameID);
            }

            /* add the received frame to the pending reply */
            Frame reply = receivedFrames.get(receivedFrameID);
            pendingExchange.setReply(reply);

            /* add to exchangeHandlingRunnable */
            exchangeHandlingRunnable.addExchange(pendingExchange);

            /* free the id and remove pendingReply */
            idGenerator.releaseID(receivedFrameID);
            pendingExchanges.remove(receivedFrameID);
            receivedFrames.remove(receivedFrameID);
        }
    }

    /**
     * Send an interrupt enable frame to the SoC
     */
    public void enableInterrupts() {
        final int id = getFreeID();
        final Frame request = Protocol.getFrameSOC_INT_EN(id);
        generatePendingExchange(id, request);
        vcp.send(request);
    }

    /**
     * Register an interrupt listener
     *
     * @param listener
     */
    public synchronized void addInterruptListener(InterruptListener listener) {
        if (!interruptListenerList.contains(listener)) {
            interruptListenerList.add(listener);
        }
    }

    /**
     * Remove an interrupt listener
     *
     * @param listener
     */
    public synchronized void removeInterruptListener(InterruptListener listener) {
        if (interruptListenerList.contains(listener)) {
            interruptListenerList.remove(listener);
        }
    }

    /**
     * Informs all registered listeners about an event. Gets called by FrameSeparator when an
     * interrupt frame is detected.
     *
     * @param event
     */
    @SuppressWarnings("unchecked")
    public void propagateInterruptEvent(InterruptEvent event) {
        ArrayList<InterruptListener> tmpInterruptListenerList;

        /* clone list of listeners */
        synchronized (this) {
            if (interruptListenerList.size() == 0) {
                return;
            }
            tmpInterruptListenerList = (ArrayList<InterruptListener>) interruptListenerList.clone();
        }

        /* call interrupt event handler of all listeners */
        for (InterruptListener listener : tmpInterruptListenerList) {
            listener.interruptHandler(event);
        }
    }

    /**
     * @return a string containing communicator status information
     */
    public String getStatusString() {
        return "<---- COMMUNICATOR STATUS ---->" + Util.LS +
                "       Receive Buffer: " + getReceiveBufferString() + Util.LS +
                "      Received Frames: " + getFramesString() + Util.LS +
                "      Pending replies: " + getRepliesString() + Util.LS +
                "          idGenerator: " + idGenerator.toString();
    }

    /**
     * @return a String representation of all frames received
     */
    private String getFramesString() {
        return receivedFrames.toString();
    }

    private String getRepliesString() {
        return pendingExchanges.toString();
    }

    private String getReceiveBufferString() {
        StringBuilder sb = new StringBuilder();
        Object[] tmpArray = receiveBuffer.toArray();

        sb.append("[ ");
        for (Object byteObj : tmpArray) {
            sb.append(String.format("%02X ", (Byte) byteObj));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Receives a single byte from the MCU to check success of an operation
     *
     * @return True if an MCU ACK frame was received
     */
    private boolean mcuSuccess() {
        byte[] reply = vcp.receive(1);
        if (reply[0] == Protocol.OPC_ACK) return true;
        else return false;
    }

    /**
     * Test which IC is currently active and ready to communicate. During this, the frame separator
     * will be deactivated.
     *
     * @return true if FPGA is currently active
     * @throws CommunicationException
     */
    private boolean isFPGAActive() {
        LOGGER.entering(getClass().getName(), "isFPGAActive");

        /* deactivate frame separator and send status read request */
        frameSeparator.setActive(false);
        vcp.setCommunicator(null);
        LOGGER.finer("Will now send STATUS_RD to determine active IC ...");
        vcp.send(Protocol.getFrameSTATUS_RD());
        LOGGER.finer("done");

        /* use the reply to determine active IC */
        byte[] reply;
        try {
            reply = vcp.receive(Protocol.LEN_NACK, Protocol.DETECT_TIMEOUT_MILLIS);
        }
        catch (TimeoutException e) {
            LOGGER.info("Timeout receiving 4 bytes");
            vcp.reset();
            vcp.setCommunicator(this);
            return isFPGAActive();
        }

        if (reply[0] == Protocol.OPC_NACK && reply[2] == 0x11) {
            vcp.setCommunicator(this);
            LOGGER.fine("FPGA is active");
            return true;
        }
        else if (reply[0] == Protocol.OPC_STATUS_RDRE) {
            /* if mcu is active receive remaining bytes */
            try {
                vcp.receive(Protocol.LEN_STATUS_RDRE - Protocol.LEN_NACK,
                                Protocol.DETECT_TIMEOUT_MILLIS);
            }
            catch (TimeoutException e) {
                LOGGER.warning("Timeout during reception of remaining bytes from MCU");
            }
            vcp.setCommunicator(this);
            LOGGER.fine("MCU is active");
            return false;
        }
        else {
            LOGGER.info(String.format("Received unexpected reply (%s). Will retry.",
                    Util.toHexString(reply)));
            vcp.reset();
            vcp.setCommunicator(this);
            return isFPGAActive();
        }

    }

    /**
     * Retry an exchange that failed
     *
     * @param exchange to retry
     * @param error enum type
     */
    public void retry(Exchange exchange, Protocol.Error error) {
        LOGGER.info("Retry exchange: " + exchange.toString());
        if (error == Protocol.Error.PARITY) {
            Frame request = exchange.getRequest();

            switch (request.getRawByte(0)) {

                /* WR (write) */
                case Protocol.OPC_REGISTER_WR:
                    int address = ((request.getRawByte(2) << 8) & 0xFF00) +
                    (request.getRawByte(3) & 0xFF);
                    int data = request.getRawByte(4) & 0xFF;
                    writeRegister(address, data);
                    break;

                /* INT_EN (interrupt enable) */
                case Protocol.OPC_SOC_INT_EN:
                    enableInterrupts();
                    break;

                default:
                    LOGGER.severe("Retry not yet implemented. Exchange: " + exchange.toString());
                    break;
            }

            /* TODO: MWR (multi write) */
            /* TODO: AWR (auto address increment write) */
            /* TODO: RD (read) */
            /* TODO: MRD (multi read)*/
            /* TODO: ARD (auto address increment read)*/

        }
    }

    /**
     * Used to distinguish the currently communicating IC
     */
    private enum IC {
        FPGA, MCU
    }
}
