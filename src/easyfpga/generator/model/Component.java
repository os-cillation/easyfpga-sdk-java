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

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A component represents every core in a FPGA and the FPGA itself
 */
public abstract class Component {

    protected int index;
    protected Set<Connection> connections;
    protected Map<String, Pin> pinMap;
    protected FPGA fpga;

    /** the frequency of the wishbone clock in Hz */
    protected final int WISHBONE_CLOCK_FREQUENCY = 80000000;

    public Component() {
        this.connections = new TreeSet<Connection>();
        this.pinMap = new TreeMap<String, Pin>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        });
    }

    /**
     * Set the components index. In Core instances (which inherit from Component) the index is
     * used to determine the core address.
     *
     * @param index to set
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * Set the FPGA instance that contains the component
     *
     * @param fpga instance
     */
    public void setFPGA(FPGA fpga) {
        this.fpga = fpga;
    }

    /**
     * @return the unique index of the component
     */
    public int getIndex() {
        return index;
    }

    /**
     * Create a pin with a given name and add it to the list of pins.
     *
     * @param name of the pin
     * @return pin object created
     */
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Pin pin = new Pin(this, name);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

    /**
     * Get all pins connected to a given pin
     *
     * @param pin
     * @return a collection containing all connected pins
     */
    public Collection<Pin> getTargets(Pin pin) {
        Set<Pin> result = new TreeSet<Pin>();
        for (Connection connection : connections) {
            if (connection.getSrc() == pin) {
                for (Pin sink : connection.getSinks()) {
                    result.add(sink);
                }
            }
        }
        return result;
    }

    /**
     * @return a string containing all HDL generic map entries, or null if not defined or required
     */
    public abstract String getGenericMap();


    /**
     * A component's HDL sources may be located in a given directory (i.e. the CAN core)
     *
     * @return A file that points to the sources directory or null if the sources are located
     * in the default path
     */
    public File getExternalSourcesDirectory() {
        return null;
    }

    @Override
    public String toString() {
        return String.format("Component #%d: %s", index, this.getClass().getSimpleName());
    }
}