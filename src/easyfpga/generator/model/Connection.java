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

package easyfpga.generator.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a connection (or signal in the HDL) with a source pin and one or more sink pins
 */
public class Connection implements Comparable<Connection> {

    private static final String signalNamePattern = "c%d_%s_s";
    private String name;

    private Pin sourcePin;
    private Set<Pin> sinkPins;

    /**
     * Construct a connection with a source and a single sink
     *
     * @param src source of the connection
     * @param sink sink of the connection
     */
    public Connection(Pin src, Pin sink) {
        super();
        sourcePin = src;
        sinkPins = new HashSet<Pin>();
        addSink(sink);

        name = String.format(signalNamePattern,
                sourcePin.getComponent().getIndex(), sourcePin.getName());
    }

    /**
     * Add a further sink pin to the connection
     *
     * @param sink pin to be added
     */
    public void addSink(Pin sink) {
        sinkPins.add(sink);
    }

    /**
     * @return the Connection's source pin
     */
    public Pin getSrc() {
        return sourcePin;
    }

    /**
     * @return a set of all sink pins
     */
    public Set<Pin> getSinks() {
        return sinkPins;
    }

    /**
     * @return the signal name containing the index of the source pin component and source pin name
     */
    public String getSignalName() {
        return name;
    }

    @Override
    public int compareTo(Connection o) {
        return name.compareTo(o.getSignalName());
    }

    @Override
    public String toString() {
        return getSignalName();
    }

}
