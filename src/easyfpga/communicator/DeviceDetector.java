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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jssc.SerialPortList;
import easyfpga.Util;
import easyfpga.exceptions.CommunicationException;
import easyfpga.exceptions.CurrentlyConfiguringException;

/**
 * Used by VirtualComPort to determine device files of easyFPGA boards prior to opening them.
 * Ensures that the returned device has MCU active and is currently not configuring anymore.
 */
public class DeviceDetector extends Observable {

    private VirtualComPort vcp;

    /** regular expression to choose devices to test */
    private final String DEVICE_PATH_REGEX = "(ttyUSB\\d+)|(COM\\d+)";

    /** message to print to system.err when no device gets detected */
    private final String NO_DEVICE_MSG =
            "ERROR: No device has been detected. Please make sure that an easyFPGA board is" + Util.LS +
            "attached and you have the permission to connect to it." + Util.LS;

    private final static Logger LOGGER = Logger.getLogger(DeviceDetector.class.getName());

    /**
     * Find the first device responding to detect frames.
     * If necessary, switch to MCU or wait until ongoing configuration is completed.
     *
     * @return the device name, i.e. /dev/ttyUSB1 or COM4 or null if no device found
     */
    public String findDevice() {

        Map<String, Integer> deviceMap = findCommunicatingBoards();

        /* return first entry of null if empty */
        if (deviceMap.isEmpty()) {
            System.err.println(NO_DEVICE_MSG);
            return null;
        }
        else {
            Object[] devicesArray = deviceMap.keySet().toArray();
            return (String) devicesArray[0];
        }
    }

    /**
     * Find a device with a given serial number.
     *
     * @param serial to be searched for
     * @return the device name, i.e. /dev/ttyUSB1 or COM4 or null if no device found
     */
    public String findDevice(int serial) {

        Map<String, Integer> deviceMap = findCommunicatingBoards();

        /* search map for given serial */
        Iterator<String> itr = deviceMap.keySet().iterator();
        while (itr.hasNext()) {
            String device = itr.next();
            if (deviceMap.get(device).equals(serial)) {
                return device;
            }
        }
        /* no match */
        System.err.println(NO_DEVICE_MSG);
        return null;
    }

    /**
     * Test all device paths for easyFPGA boards. All boards are switched to the MCU.
     * If necessary, this method waits until ongoing FPGA configuration is completed.
     *
     * @return a map containing device path keys vs. serial number values
     */
    private Map<String, Integer> findCommunicatingBoards() {

        final int POLL_COUNT_MAX =
            (int) (Protocol.CURRENTLY_CONFIGURING_TIMEOUT_MILLIS /
                    Protocol.CURRENTLY_CONFIGURING_SLEEP_MILLIS);

        /* device path vs. serial number */
        Map<String, Integer> deviceMap = new HashMap<String, Integer>();

        /* get feasible paths */
        String[] devicePaths = getDevicePaths();
        LOGGER.fine("Found " + devicePaths.length + " possible devices");

        /* test all devices on the list and return first communicating */
        for (String devicePath : devicePaths) {

            /* open port for this device */
            try {
                initCommunication(devicePath);
            }
            catch (CommunicationException initException) {
                LOGGER.log(Level.SEVERE, "Throwing exception", initException);
                closeCommunication();
                break;
            }

            /* probe devices, sleep when currently configuring */
            boolean configuring = false;
            int pollCount = 0;
            try {
                do {
                    try {
                        if (probeDevice()) {

                            /* read serial and add to map */
                            Integer sn = readSerial();
                            if (sn == null) {
                                LOGGER.warning("Read serial of device " + devicePath + " failed");
                            }
                            else {
                                deviceMap.put(devicePath, sn);
                            }
                            break;
                        }
                    }
                    /* sleep and retry when currently configuring */
                    catch (CurrentlyConfiguringException ex) {

                        /* notify observers about configuration in progress */
                        super.setChanged();
                        super.notifyObservers(devicePath);

                        configuring = true;
                        try {
                            Thread.sleep(Protocol.CURRENTLY_CONFIGURING_SLEEP_MILLIS);
                        }
                        catch (InterruptedException ignored) {}
                    }

                    /* break if configuration takes apparently too long */
                    if (pollCount > POLL_COUNT_MAX) break;
                    pollCount++;

                } while(configuring);
            }
            catch(TimeoutException ignored) {}
            catch (CommunicationException e) {
                e.printStackTrace();
            }

            /* close port for this device */
            closeCommunication();
        }

        LOGGER.fine("Detected " + deviceMap.size() + " responding devices");
        return deviceMap;
    }

    /**
         * Get device paths matching feasible pattern for linux and windows
         *
         * @return All device paths matching the device path regex
         */
        private String[] getDevicePaths() {
            String[] portList = SerialPortList.getPortNames(Pattern.compile(DEVICE_PATH_REGEX));
            return portList;
        }

    /**
     * Probe whether the device currently attached to VPC communicates. If so, establish
     * a well-defined state (MCU active)
     *
     * @return true when the devices responds
     * @throws CommunicationException when failing to init or board is currently configuring
     * @throws TimeoutException in case switching to MCU times out
     */
    private boolean probeDevice()
            throws CommunicationException, TimeoutException {

        LOGGER.entering(getClass().getName(), "probeDevice");

        byte[] detectReply = null;
        short detectRetry = 0;
        boolean parityFine = false;
        do {
            /* send detect via VCP */
            vcp.send(Protocol.OPC_DETECT, Protocol.SEND_DETECT_MESSAGE_TIMEOUT_MILLIS);

            /* receive with timeout */
            try {
                detectReply = vcp.receive(Protocol.LEN_DETECT_RE, Protocol.DETECT_TIMEOUT_MILLIS);
            }
            catch (TimeoutException e) {
                return false;
            }

            /* should not happen, but check anyway */
            if (detectReply == null) {
                LOGGER.warning("Detect reply is null without a timeout");
                return false;
            }

            /* reply opcode sanity check */
            if (detectReply[0] != Protocol.OPC_DETECT_RE) {
                LOGGER.fine(String.format("Device replied to detect with unexpected"
                                        + "opcode (0x%02X)", detectReply[0]));
                return false;
            }

            /* reply parity check */
            final byte[] payload = new byte[]{detectReply[0], detectReply[1]};
            byte parityIs = Protocol.xor_parity(payload);
            byte parityShould = detectReply[2];
            if (parityIs != parityShould) {
                LOGGER.fine("Parity error");
            }
            else {
                parityFine = true;
            }

            /* break if too many retries */
            if (detectRetry > Protocol.RETRIES && !parityFine) {
                LOGGER.warning("Parity errors for " + detectRetry + " retries");
                return false;
            }
            detectRetry++;

        } while(!parityFine);

        /* interpret IC identifier and establish well-defined board state */
        switch (detectReply[1]) {

            case Protocol.DETECT_RE_FPGA:
                /* switch to MCU */
                for (int switchRetry = 0; switchRetry < Protocol.RETRIES; switchRetry++) {
                    if(switchToMCU()) {
                        return true;
                    }
                }
                LOGGER.warning("Switch to MCU retries exceeded");
                return false;

            case Protocol.DETECT_RE_MCU:
                /* this is the well-defined board state */
                return true;

            case Protocol.DETECT_RE_MCU_CONF:
                LOGGER.fine("MCU currently configuring");
                throw new CurrentlyConfiguringException();

            default:
                LOGGER.warning("Received unexpected IC identifier");
                return false;
        }
    }

    /**
     * Open VCP and create Communicator
     *
     * @param devicePath path of the device file
     * @throws CommunicationException when VCP cannot be opened
     */
    private void initCommunication(String devicePath) throws CommunicationException {
        vcp = new VirtualComPort(devicePath);
        try {
            vcp.open();
        }
        catch (CommunicationException e) {
            LOGGER.log(Level.SEVERE, "Exception while opening port", e);
            throw e;
        }
    }

    private void closeCommunication() {
        vcp.close();
        vcp = null;
    }

    /**
     * Read the serial number of the device currently attached to VCP
     *
     * @param devicePath
     * @return The serial or null if reply is not as expected
     * @throws TimeoutException when reply takes to long
     */
    private Integer readSerial() throws TimeoutException {

        /* request */
        vcp.send(Protocol.OPC_SERIAL_RD);

        /* reply */
        byte[] reply = null;
        reply = vcp.receive(Protocol.LEN_SERIAL_RDRE, Protocol.SERIAL_READ_TIMEOUT_MILLIS);

        /* opcode check */
        if (reply[0] != Protocol.OPC_SERIAL_RDRE) return null;

        /* parity check */
        byte[] payload = Arrays.copyOf(reply, reply.length - 1);
        byte parityIs = Protocol.xor_parity(payload);
        byte parityShould = reply[reply.length - 1];
        if (parityIs != parityShould) return null;

        /* interpret reply */
        Integer sn = (int) ((reply[1] & 0xFF) |
                           ((reply[2] << 8 ) & 0xFF00) |
                           ((reply[3] << 16) & 0xFF0000) |
                           ((reply[4] << 24) & 0xFF000000));
        LOGGER.fine(String.format("Read serial: 0x%08x", sn));
        return sn;
    }

    /**
     * Switch to MCU
     * @return
     */
    private boolean switchToMCU() {

        /* request */
        final int ID = 123;
        vcp.send(Protocol.getFrameMCU_SEL(ID));

        /* reply */
        byte[] reply = null;
        try {
            reply = vcp.receive(Protocol.LEN_MCU_SEL, Protocol.SELECT_MCU_TIMEOUT_MILLIS);
        }
        catch (TimeoutException e) {
            LOGGER.warning("Timeout occured");
            return false;
        }

        /* check reply */
        if (reply[0] != Protocol.OPC_ACK) return false;
        if (reply[1] != ID) return false;
        byte[] payload = new byte[]{reply[0], reply[1]};
        byte parity = Protocol.xor_parity(payload);
        if (reply[2] != parity) return false;

        return true;
    }
}
