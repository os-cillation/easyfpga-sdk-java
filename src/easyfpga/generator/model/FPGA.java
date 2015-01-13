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

package easyfpga.generator.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jssc.SerialPortException;
import easyfpga.Util;
import easyfpga.communicator.Communicator;
import easyfpga.communicator.FPGABinary;
import easyfpga.communicator.InterruptListener;
import easyfpga.communicator.Protocol;
import easyfpga.communicator.VirtualComPort;
import easyfpga.exceptions.BuildException;
import easyfpga.exceptions.BusExceedsPhysicalSizeException;
import easyfpga.exceptions.BusSizeDoesNotMatchException;
import easyfpga.exceptions.CommunicationException;
import easyfpga.exceptions.InternalBidirectionalException;
import easyfpga.exceptions.PinNotAvailableException;
import easyfpga.exceptions.PinTypeFixedException;
import easyfpga.generator.FPGA2VHDLGenerator;
import easyfpga.generator.model.Pin.Type;

/**
 * The FPGA object is the main component of the device. It manages cores and
 * connections. A custom FPGA must implement the build() method to describe the
 * architecture.
 */
public abstract class FPGA extends Component {
    private static final int banks = 3;
    private static final int pins = 24;

    protected List<Component> components;
    private String name;
    private Communicator communicator;
    private int index;

    protected FPGA() {
        this.name = "easyFPGA_" + this.getClass().getSimpleName();
        this.index = 1;
        this.components = new ArrayList<Component>();
        this.pinMap = new LinkedHashMap<String, Pin>();

        /* fill pinMap */
        for (int i = 0; i < banks; i++) {
            for (int j = 0; j < pins; j++) {
                Pin pin = new GPIOPin(this, i, j);
                this.pinMap.put(pin.getName(), pin);
            }
        }
    }

    /**
     * Build the FPGA architecture with its cores and connections. Implement this method to call the
     * connect method and the core constructors.
     *
     * @throws BuildException
     */
    public abstract void build() throws BuildException;

    /**
     * Reset the FPGA model and call the FPGA2VHDLGenerator to synthesize the binary.
     * The Xilinx toolchain path is taken from the configuration file.
     *
     * @throws Exception
     */
    public void synthesizeBinary() throws Exception {
        Util.prepareLogging();

        reset();
        FPGA2VHDLGenerator generator = new FPGA2VHDLGenerator();
        generator.buildFPGA(this);
    }

    /**
     * Reset the model and call the build method.
     *
     * @throws Exception
     */
    private void reset() throws Exception {
        this.index = 1;
        this.components = new ArrayList<Component>();
        build();
    }

    /**
     * Initialize the model, try to find the binary and the board device. Upload
     * the binary to the FPGA and establish a connection.
     *
     * @throws Exception
     */
    public void init() throws Exception {
        reset();
        communicator = uploadAndConnect();
    }

    /**
     * Initialize the model, try to find the binary and the board device with the
     * given serial number. Upload the binary to the FPGA and establish a connection.
     *
     * @param serial number to be searched for
     * @throws Exception
     */
    public void init(int serial) throws Exception {
        reset();
        communicator = uploadAndConnect(serial);
    }

    /**
     * Upload the binary and establish a connection
     *
     * @return A communicator to manage the connection
     * @throws IOException
     * @throws SerialPortException
     * @throws CommunicationException
     */
    private Communicator uploadAndConnect() throws IOException, SerialPortException,
                                                     CommunicationException {
        return uploadAndConnect(-1);
    }

    /**
     * Upload the binary and establish a connection to a board with a given serial number.
     *
     * @param serial of the board to connect to
     * @return A communicator to manage the connection
     * @throws IOException
     * @throws SerialPortException
     * @throws CommunicationException
     */
    private Communicator uploadAndConnect(int serial) throws IOException, SerialPortException,
                                                               CommunicationException {
        Util.initLogging();

        /* load configuration file */
        FPGABinary conf = new FPGABinary(null);
        conf.loadFile();

        /* open virtual com port */
        VirtualComPort vcp;
        if (serial != -1) {
            vcp = new VirtualComPort(serial);
        }
        else {
            vcp = new VirtualComPort();
        }
        vcp.open();

        /* switch to MCU if FPGA is active */
        Communicator com = new Communicator(vcp, this);
        if (com.isFPGAActive()) {
            com.selectMCU();
        }

        /* upload configuration file */
        if (!conf.upload(vcp)) {
            throw new CommunicationException("Failed to upload FPGA binary");
        }

        /* send fpga_configuration command */
        if (!conf.configureFPGA(vcp))
            throw new CommunicationException("FPGA configuration failed");

        /* switch to SoC */
        com.selectSoC();

        return com;
    }

    /**
     * Close the connection.
     *
     * @throws CommunicationException
     */
    public void quit() throws CommunicationException {
        if (communicator == null) {
            System.out.println("FPGA.quit> Communicator is null");
        }
        else {
            communicator.closeConnection();
        }
    }

    /**
     * Establish a connection between two pins.
     *
     * @param srcPin the source of the connection. Has to be an output pin.
     * @param sinkPin the sink of the connection. Has to be an input pin.
     * @throws PinTypeFixedException
     * @throws InternalBidirectionalException
     */
    public void connect(Pin srcPin, Pin sinkPin) throws PinTypeFixedException,
                                                          InternalBidirectionalException {

        /* add components if necessary */
        if (!(srcPin.getComponent() instanceof FPGA) && !components.contains(srcPin.getComponent())) {
            addComponent(srcPin.getComponent());
        }
        if (!(sinkPin.getComponent() instanceof FPGA) && !components.contains(sinkPin.getComponent())) {
            addComponent(sinkPin.getComponent());
        }

        /* prevent inout-pins from getting connected internally */
        if (sinkPin.getType() == Type.INOUT || srcPin.getType() == Type.INOUT) {
            if (!  (srcPin.getComponent() instanceof FPGA ||
                    sinkPin.getComponent() instanceof FPGA)) {
                throw new InternalBidirectionalException();
            }
        }

        /* determine pin types if possible */
        if (!srcPin.isFixedTyped() && sinkPin.isFixedTyped()) {
            srcPin.setType(sinkPin.getType() == Type.IN ? Type.OUT : Type.IN);
        }
        else if (srcPin.isFixedTyped() && !sinkPin.isFixedTyped()) {
            sinkPin.setType(srcPin.getType() == Type.IN ? Type.OUT : Type.IN);
        }
        else if (srcPin.isFixedTyped() && sinkPin.isFixedTyped() &&
                    srcPin.getType() == sinkPin.getType()) {
            throw new PinTypeFixedException();
        }
        else if (!srcPin.isFixedTyped() && !sinkPin.isFixedTyped()) {
            srcPin.setType(Type.IN);
            sinkPin.setType(Type.OUT);
        }
        if (srcPin.getComponent() instanceof FPGA && sinkPin.isFixedTyped()) {
            srcPin.setType(sinkPin.getType());
        }
        if (sinkPin.getComponent() instanceof FPGA && srcPin.isFixedTyped()) {
            sinkPin.setType(srcPin.getType());
        }

        /* if connection with this source exist, only add the sink */
        boolean connectionSourceExists = false;
        for (Connection con : connections) {
            Pin source = con.getSrc();
            if (source == srcPin) {
                connectionSourceExists = true;
                con.addSink(sinkPin);
            }
        }

        /* add new connection */
        if (!connectionSourceExists) {
            Connection newConnection = new Connection(srcPin, sinkPin);
            connections.add(newConnection);
        }
    }

    /**
     * Connect two buses with each other by connecting all pins of the source bus to
     * the corresponding pins of the sink bus.
     *
     * @param srcBus source bus. Has to consist of output pins.
     * @param sinkBus sink bus. Has to consist of input pins.
     * @throws BusSizeDoesNotMatchException
     * @throws PinTypeFixedException
     * @throws InternalBidirectionalException
     */
    public void connect(Bus srcBus, Bus sinkBus) throws BusSizeDoesNotMatchException,
                                            PinTypeFixedException, InternalBidirectionalException {

        if (srcBus.size() != sinkBus.size()) {
            throw new BusSizeDoesNotMatchException();
        }
        for (int i = 0; i < srcBus.size(); i++) {
            Pin srcPin = srcBus.get(i);
            Pin sinkPin = sinkBus.get(i);
            connect(srcPin, sinkPin);
        }
    }

    /**
     * Add a component to the FPGA.
     *
     * @param component to be added
     */
    public void addComponent(Component component) {
        if (!components.contains(component)) {
            component.setIndex(index++);
            this.components.add(component);
            component.setFPGA(this);
        }
    }

    /**
     * @return a list of all components
     */
    public List<Component> getComponents() {
        return components;
    }

    /**
     * Get a core object by its core address.
     *
     * @param coreAddress two bytes of which the last one has to be zero 0xCC00
     * @return The Core or null if not found
     */
    public Core getCoreByAddress(int coreAddress) {
        /* parameter check */
        if (coreAddress < 0 || coreAddress > Protocol.CORE_ADDRESS_MAX || (coreAddress & 0xFF) !=0) {
            throw new IllegalArgumentException("Illegal core address");
        }

        /* first try determining the index */
        Component cmpCore = components.get((coreAddress >>> 8) - 1);
        if (cmpCore instanceof Core) {
            Core core = (Core) cmpCore;
            if (core.getCoreAddress() == coreAddress) return core;
        }

        /* in case this fails, look at all components */
        for (Component cmpCore2 : components) {
            if (cmpCore2 instanceof Core) {
                Core core = (Core) cmpCore2;
                if (core.getCoreAddress() == coreAddress) return core;
            }
        }
        return null;
    }

    /**
     * @return the Communicator instance associated with the FPGA.
     * @throws CommunicationException
     */
    public Communicator getCommunicator() throws CommunicationException {
        if (communicator == null) {
            throw new CommunicationException();
        }
        return communicator;
    }

    /**
     * Get an FPGA GPIO pin.
     *
     * @param bank number (0 .. 2)
     * @param pin number (0 .. 23)
     * @return the GPIO pin
     * @throws PinNotAvailableException
     */
    public Pin getPin(int bank, int pin) throws PinNotAvailableException {
        String pinName = String.format(GPIOPin.namePattern, bank, pin);
        Pin result = pinMap.get(pinName);
        if (result == null) {
            throw new PinNotAvailableException();
        }
        return result;
    }

    /**
     * Get sequential GPIO pins as a Bus.
     *
     * @param startPin first pin of the sequence
     * @param size of the sequence
     * @return a bus containing the pins
     * @throws BusExceedsPhysicalSizeException
     */
    protected Bus getBus(Pin startPin, int size) throws BusExceedsPhysicalSizeException {
        GPIOPin startGPIOPin = (GPIOPin) startPin;
        int bank = startGPIOPin.getBank();
        int pos = startGPIOPin.getPin();
        if (pos + size - 1 > pins) {
            throw new BusExceedsPhysicalSizeException();
        }
        Bus bus = new Bus();
        for (int i = 0; i < size; i++) {
            bus.add(this.getPin(String.format(GPIOPin.namePattern, bank, pos + i)));
        }
        return bus;
    }

    /**
     * @return the name of the FPGA that contains the name of class extending FPGA.
     */
    public String getName() {
        return name;
    }

    /**
     * Get all outgoing connections associated to a given component.
     *
     * @param component to be examined
     * @return a set of connections that have their source pins at the given component
     */
    public Set<Connection> getConnectionsOut(Component component) {
        Set<Connection> result = new TreeSet<Connection>();
        for (Connection connection : connections) {
            if (connection.getSrc().getComponent() == component) {
                result.add(connection);
            }
        }
        return result;
    }

    /**
     * Get all incoming connections associated to a given component.
     *
     * @param component to be examined
     * @return a set of connections that have at least one sink pins at the given component
     */
    public Set<Connection> getConnectionsIn(Component component) {
        Set<Connection> result = new TreeSet<Connection>();
        for (Connection connection : connections) {
            for (Pin sink : connection.getSinks()) {
                if (sink.getComponent() == component) {
                    result.add(connection);
                }
            }
        }
        return result;
    }

    /**
     * Enable global interrupt generation.
     */
    public void enableInterrupts() {
        communicator.enableInterrupts();
    }

    /**
     * Register an interrupt listener.
     *
     * @param listener to be added
     * @see easyfpga.communicator.InterruptListener
     */
    public void addInterruptListener(InterruptListener listener) {
        communicator.addInterruptListener(listener);
    }

    /**
     * Remove an interrupt listener.
     *
     * @param listener to be removed
     * @see easyfpga.communicator.InterruptListener
     */
    public void removeInterruptListener(InterruptListener listener) {
        communicator.removeInterruptListener(listener);
    }

    @Override
    public String toString() {
        return "FPGA(" + hashCode() + ")\n" +
                "components = " + components + "\n" +
                "connectionMap = " + connections;
    }

    /**
     * Helper method to get a String showing the FPGA's connections
     *
     * @return human readable multiline string
     */
    public String getConnectionsString() {
        StringBuilder sb = new StringBuilder();
        for (Connection con : connections) {

            String src = con.getSrc().getComponent().getClass().getSimpleName() +
                    con.getSrc().getComponent().getIndex() + "." +
                    con.getSrc().getName();

            StringBuilder sinks = new StringBuilder();
            for (Pin sink : con.getSinks()) {
                sinks.append(sink.getComponent().getClass().getSimpleName());
                sinks.append(sink.getComponent().getIndex());
                sinks.append(".");
                sinks.append(sink.getName());
                sinks.append(", ");
            }
            sinks.delete(sinks.length() - 2, sinks.length());

            sb.append(con.getSignalName() + ": ");
            sb.append(src + " -> " + sinks.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Contains name constants (that reflect the user constraint file) for all GPIO pins.
     */
    public final class GPIO {

        /* BANK 0 */
        public static final String gpio_b0_00 = "gpio_b0_00";
        public static final String gpio_b0_01 = "gpio_b0_01";
        public static final String gpio_b0_02 = "gpio_b0_02";
        public static final String gpio_b0_03 = "gpio_b0_03";
        public static final String gpio_b0_04 = "gpio_b0_04";
        public static final String gpio_b0_05 = "gpio_b0_05";
        public static final String gpio_b0_06 = "gpio_b0_06";
        public static final String gpio_b0_07 = "gpio_b0_07";
        public static final String gpio_b0_08 = "gpio_b0_08";
        public static final String gpio_b0_09 = "gpio_b0_09";
        public static final String gpio_b0_10 = "gpio_b0_10";
        public static final String gpio_b0_11 = "gpio_b0_11";
        public static final String gpio_b0_12 = "gpio_b0_12";
        public static final String gpio_b0_13 = "gpio_b0_13";
        public static final String gpio_b0_14 = "gpio_b0_14";
        public static final String gpio_b0_15 = "gpio_b0_15";
        public static final String gpio_b0_16 = "gpio_b0_16";
        public static final String gpio_b0_17 = "gpio_b0_17";
        public static final String gpio_b0_18 = "gpio_b0_18";
        public static final String gpio_b0_19 = "gpio_b0_19";
        public static final String gpio_b0_20 = "gpio_b0_20";
        public static final String gpio_b0_21 = "gpio_b0_21";
        public static final String gpio_b0_22 = "gpio_b0_22";
        public static final String gpio_b0_23 = "gpio_b0_23";

        /* BANK 1 */
        public static final String gpio_b1_00 = "gpio_b1_00";
        public static final String gpio_b1_01 = "gpio_b1_01";
        public static final String gpio_b1_02 = "gpio_b1_02";
        public static final String gpio_b1_03 = "gpio_b1_03";
        public static final String gpio_b1_04 = "gpio_b1_04";
        public static final String gpio_b1_05 = "gpio_b1_05";
        public static final String gpio_b1_06 = "gpio_b1_06";
        public static final String gpio_b1_07 = "gpio_b1_07";
        public static final String gpio_b1_08 = "gpio_b1_08";
        public static final String gpio_b1_09 = "gpio_b1_09";
        public static final String gpio_b1_10 = "gpio_b1_10";
        public static final String gpio_b1_11 = "gpio_b1_11";
        public static final String gpio_b1_12 = "gpio_b1_12";
        public static final String gpio_b1_13 = "gpio_b1_13";
        public static final String gpio_b1_14 = "gpio_b1_14";
        public static final String gpio_b1_15 = "gpio_b1_15";
        public static final String gpio_b1_16 = "gpio_b1_16";
        public static final String gpio_b1_17 = "gpio_b1_17";
        public static final String gpio_b1_18 = "gpio_b1_18";
        public static final String gpio_b1_19 = "gpio_b1_19";
        public static final String gpio_b1_20 = "gpio_b1_20";
        public static final String gpio_b1_21 = "gpio_b1_21";
        public static final String gpio_b1_22 = "gpio_b1_22";
        public static final String gpio_b1_23 = "gpio_b1_23";

        /* BANK 2 */
        public static final String gpio_b2_00 = "gpio_b2_00";
        public static final String gpio_b2_01 = "gpio_b2_01";
        public static final String gpio_b2_02 = "gpio_b2_02";
        public static final String gpio_b2_03 = "gpio_b2_03";
        public static final String gpio_b2_04 = "gpio_b2_04";
        public static final String gpio_b2_05 = "gpio_b2_05";
        public static final String gpio_b2_06 = "gpio_b2_06";
        public static final String gpio_b2_07 = "gpio_b2_07";
        public static final String gpio_b2_08 = "gpio_b2_08";
        public static final String gpio_b2_09 = "gpio_b2_09";
        public static final String gpio_b2_10 = "gpio_b2_10";
        public static final String gpio_b2_11 = "gpio_b2_11";
        public static final String gpio_b2_12 = "gpio_b2_12";
        public static final String gpio_b2_13 = "gpio_b2_13";
        public static final String gpio_b2_14 = "gpio_b2_14";
        public static final String gpio_b2_15 = "gpio_b2_15";
        public static final String gpio_b2_16 = "gpio_b2_16";
        public static final String gpio_b2_17 = "gpio_b2_17";
        public static final String gpio_b2_18 = "gpio_b2_18";
        public static final String gpio_b2_19 = "gpio_b2_19";
        public static final String gpio_b2_20 = "gpio_b2_20";
        public static final String gpio_b2_21 = "gpio_b2_21";
        public static final String gpio_b2_22 = "gpio_b2_22";
        public static final String gpio_b2_23 = "gpio_b2_23";
    }

    @Override
    public String getGenericMap() {
        return null;
    }
}
