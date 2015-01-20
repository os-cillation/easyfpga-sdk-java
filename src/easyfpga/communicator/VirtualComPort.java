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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;
import easyfpga.ConfigurationFile;
import easyfpga.exceptions.CommunicationException;

/**
 * The Communicator's backend that manages the communication with the virtual com port (ttyUSB)
 * using the Java Simple Serial Connector (jSSC) library. This class implements the
 * SerialPortListener that consumes events generated by the jSSC library.
 */
public class VirtualComPort implements SerialPortEventListener {

    private SerialPort port;
    private String deviceName;
    private int serialNumber;
    private boolean searchBySerialNumber;

    private volatile LinkedBlockingQueue<Byte> receiveBuffer;
    private Communicator com;
    private ConfigurationFile configFile;

    private static final int BAUDRATE = 3000000;

    /** Timeout to avoid deadlock in serial event when port has been closed */
    private static final int SERIAL_EVENT_READ_TIMEOUT_MILLIS = 500;

    private static final Logger LOGGER = Logger.getLogger(VirtualComPort.class.getName());

    /**
     * Default constructor
     */
    public VirtualComPort() {
        this.receiveBuffer = new LinkedBlockingQueue<Byte>();
        this.configFile = new ConfigurationFile();
        this.deviceName = configFile.getValue(ConfigurationFile.USB_DEVICE_KEY);
        this.searchBySerialNumber = false;
    }

    /**
     * Construct VirtualComPort. Search for the board with the given serial number
     *
     * @param serial number to be looked for
     */
    public VirtualComPort(int serial) {
        this();
        this.deviceName = null;
        this.serialNumber = serial;
        this.searchBySerialNumber = true;
    }

    /**
     * Construct with a given device name. Will ignore device name given in configuration file.
     *
     * @param deviceName
     */
    public VirtualComPort(String deviceName) {
        this();
        this.deviceName = deviceName;
    }

    /**
     * Set the communicator reference which is required for calling the Communicator's
     * processInterruptEvent method.
     *
     * @param com reference to the associated Communicator
     */
    public void setCommunicator(Communicator com) {
        this.com = com;
    }

    /**
     * Open the port and set up the listener. If no serial port device name is set the device
     * will be searched.
     *
     * @throws SerialPortException
     * @throws CommunicationException in case a serial is given but not found
     */
    public void open() throws SerialPortException, CommunicationException {
        LOGGER.entering(getClass().getName(), "open");

        if (deviceName == null) {
            LOGGER.severe("Device name not set");
        }
        else {
            setupPort();
        }
    }

    /**
     * Open port and add receive listener
     *
     * @throws SerialPortException
     */
    private void setupPort() throws SerialPortException {

        /* open and init port */
        port = new SerialPort(deviceName);
        port.openPort();
        port.setParams(BAUDRATE, 8, 1, 0);
        port.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);

        /* add listener for reception */
        int eventsMask = SerialPort.MASK_RXCHAR;
        port.setEventsMask(eventsMask);
        port.addEventListener(this);
    }

    /**
     * Close the port
     */
    public void close() {
        LOGGER.entering(getClass().getName(), "close");
        try {
            /* close port if opened */
            if (isOpened()) {
                if (port.closePort()) {
                    LOGGER.fine("Port successfully closed");
                }
                else {
                    LOGGER.warning("Failed to close port");
                }
            }
        }
        catch (SerialPortException e) {
            e.printStackTrace();
        }
        System.gc();
    }

    /**
     * Check whether the port is opened
     *
     * @return true if port is opened
     */
    public boolean isOpened() {
        if (port != null) return port.isOpened();
        else return false;
    }

    /**
     * Send an array of bytes to the VCP
     *
     * @param byteStream array of bytes to send
     */
    public void send(byte[] byteStream) {
        try {
            /* send byte stream */
            port.writeBytes(byteStream);
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send a single byte to the VCP
     *
     * @param b byte to send
     */
    public void send(byte b) {
        try {
            port.writeByte(b);
        }
        catch (SerialPortException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send a frame instance over the VCP
     *
     * @param frame to send
     */
    public void send(Frame frame) {
        /* unbox Frame to byte[] */
        Byte[] byteObj = frame.getRawBytes();
        byte[] bytePrim = new byte[byteObj.length];

        for (int i = 0; i < byteObj.length; i++) {
            bytePrim[i] = byteObj[i];
        }

        /* send */
        try {
            port.writeBytes(bytePrim);
        }
        catch (SerialPortException e) {
            e.printStackTrace();
        }
    }

    /**
     * Block until a certain number of bytes is received. Remove the bytes from the receive buffer
     * before returning.
     *
     * @param byteCount Number of bytes to be received
     * @return Array of received bytes
     */
    public byte[] receive(int byteCount) {

        /* retrieve bytes from receiveBuffer (block until available) */
        byte[] received = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            try {
                received[i] = receiveBuffer.take();
            }
            catch (InterruptedException e) {
                return null;
            }
        }

        /* notify communicator (if present) */
        if (com != null) {
            com.receiveBufferChanged();
        }

        return received;
    }

    /**
     * Block until a certain number of bytes is received or a given timeout elapsed. Remove the
     * bytes from the receive buffer before returning.
     *
     * @param byteCount Number of bytes to be received
     * @param timeoutMillis timeout duration in milliseconds
     * @return Array of received bytes
     * @throws TimeoutException
     */
    public byte[] receive(int byteCount, long timeoutMillis) throws TimeoutException {

        /* prepare executor service, callable and future */
        ExecutorService executor = Executors.newSingleThreadExecutor();

        class ReceiveCallable implements Callable<byte[]> {
            int byteCount;
            public ReceiveCallable(int byteCount) {
                this.byteCount = byteCount;
            }

            @Override
            public byte[] call() throws Exception {
                return receive(byteCount);
            }
        };

        ReceiveCallable receiveCallable = new ReceiveCallable(byteCount);
        Future<byte[]> future = executor.submit(receiveCallable);

        /* receive */
        try {
            return (byte[]) future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            LOGGER.fine("Timeout occured during reception of " + byteCount + " bytes");
            throw e;
        }
        catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        finally {
            future.cancel(true);
        }
        return null;
    }

    /**
     * Reset the port: Purge receive buffers, close and re-open the port
     */
    public void reset() {
        LOGGER.entering(getClass().getName(), "purge");

        /* remove listener and receive bytes in buffers */
        int purgedBytes = 0;
        try {
            if (!port.removeEventListener()) {
                LOGGER.warning("Failed to remove jSSC serial event listener");
            }

            /* read from port until empty */
            while (true) {
                port.readBytes(1, 100);
                purgedBytes++;
            }
        }
        catch (SerialPortTimeoutException e) {
            LOGGER.finer(String.format("Purged %d bytes", purgedBytes));
        }
        catch (SerialPortException e) {
            LOGGER.log(Level.SEVERE, "Throwing exception", e);
        }
        finally {
            /* clear local receive buffer */
            receiveBuffer.clear();
        }

        /* close and open port */
        close();
        try {
            open();
        }
        catch (SerialPortException e) {
            e.printStackTrace();
        }
        catch (CommunicationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Invoked by jSSC library on incoming data. Reads and enqueues all data given by getEventValue,
     * then notify the communicator about the change in the receive buffer.
     */
    public void serialEvent(SerialPortEvent event) {
        if (event.isRXCHAR()) {

            try {
                /* read number of bytes given by event */
                for (int i = 0; i < event.getEventValue(); i++) {
                    /* read and enqueue */
                    receiveBuffer.put(port.readBytes(1, SERIAL_EVENT_READ_TIMEOUT_MILLIS)[0]);
                }

                /* notify communicator (if present) */
                if (com != null) {

                    /* call receiveBufferChanged notification method in a new thread */
                    Thread thr = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            com.receiveBufferChanged();
                        }
                    });
                    thr.start();
                }

            }
            catch (SerialPortTimeoutException ignore) {}
            catch (SerialPortException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        /* !event.isRXCHAR() */
        else {
            LOGGER.warning("Unknown serial event");
        }

    }

    /**
     * Give a string representation of the receive buffer
     */
    public String getReceiveBufferString() {
        String outString = new String();
        int bufferSize = receiveBuffer.size();

        /* convert receiveBuffer to Byte[] */
        Byte[] byteBuffer = new Byte[bufferSize];
        byteBuffer = receiveBuffer.toArray(new Byte[bufferSize]);

        outString = "[";

        /* iterate over byteBuffer */
        for (int i=0; i < bufferSize; i++) {

            /* append data in hex, if data is null append ## */
            if (byteBuffer[i] != null) {
                outString += String.format("%02X", byteBuffer[i].byteValue());
            } else {
                outString += "##";
            }

            /* separate bytes using spaces */
            if (i < (bufferSize - 1)) {
                outString += " ";
            }
        }

        outString += "]";

        return outString;
    }

    /**
     * Get a reference to the receive buffer
     *
     * @return receive buffer
     */
    public LinkedBlockingQueue<Byte> getReceiveBuffer() {
        return receiveBuffer;
    }
}
