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

package easyfpga.generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import easyfpga.generator.model.Component;
import easyfpga.generator.model.Connection;
import easyfpga.generator.model.FPGA;
import easyfpga.generator.model.Pin;

/**
 * VHDL top level entity (TLE) generator
 */
public class TLEBuilder {

    private FPGA fpga;
    private Map<Pin, String> signals;

    /** set to true to see details during the build process */
    private final boolean DBG_OUTPUT = false;

    /**
     * @param fpga instance to be processed
     */
    public TLEBuilder(FPGA fpga) {
        this.fpga = fpga;
        this.signals = new HashMap<Pin, String>();
    }

    /**
     * Read the TLE template and replace magic words with information from the FPGA instance
     *
     * @return a string representation of the TLE
     */
    public String buildTLE() {

        if (DBG_OUTPUT) {
            System.out.println("<<----- TLEBuilder.buildTLE() ----->>");
            System.out.println("FPGA Connections:");
            System.out.println(fpga.getConnectionsString());
        }

        StringBuilder builder = new StringBuilder();
        Scanner scanner = new Scanner(TLEBuilder.class.getResourceAsStream("/templates/tle_template.vhd"));

        /* search for magic words and call according methods */
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().equals("%user_gpios")) {
                buildUserGPIOs(builder);
                continue;
            }
            if (line.trim().equals("%wbslaves")) {
                buildWBSlaves(builder);
                continue;
            }
            if (line.trim().equals("%customsignals")) {
                buildCustomSignals(builder);
                continue;
            }
            if (line.trim().equals("%wbslavesintercon")) {
                buildWBSlavesIntercon(builder);
                continue;
            }
            if (line.trim().equals("%cores")) {
                buildCores(builder);
                continue;
            }
            line = line.replaceAll("%name", fpga.getName());
            builder.append(String.format("%s\n", line));
        }
        scanner.close();
        return builder.toString();
    }

    /**
     * Add user defined GPIO port definitions that connect components to the board's pin headers.
     *
     * @param builder
     */
    private void buildUserGPIOs(StringBuilder builder) {

        if (DBG_OUTPUT) {
            System.out.println("<<----- TLEBuilder.buildUserGPIOs() ----->>");
            System.out.println("Connections out: " + fpga.getConnectionsOut(fpga));
            System.out.println("Connections in:  " + fpga.getConnectionsIn(fpga));
        }

        /* inputs (outputs of the FPGA component) */
        for (Iterator<Connection> iterator = fpga.getConnectionsOut(fpga).iterator();
                iterator.hasNext();) {

            Connection connection = iterator.next();
            Pin srcPin = connection.getSrc();

            if (srcPin.getComponent() instanceof FPGA) {
                builder.append(String.format("      %s : %s std_logic;\n",
                                                srcPin.getName(), srcPin.getType()));
            }
        }

        /* outputs (inputs of the FPGA component) */
        for (Iterator<Connection> iterator = fpga.getConnectionsIn(fpga).iterator();
                iterator.hasNext();) {

            Connection connection = iterator.next();

            for (Pin pin : connection.getSinks()) {

                if (pin.getComponent() instanceof FPGA) {
                    builder.append(String.format("      %s : %s std_logic;\n",
                                                pin.getName(), pin.getType()));
                }
            }
        }
        builder.append("\n");
    }

    /**
     * Add wishbone slave signals
     *
     * @param builder top-level-entity StringBuilder
     */
    private void buildWBSlaves(StringBuilder builder) {

        if (DBG_OUTPUT) {
            System.out.println("<<----- TLEBuilder.buildWBSlaves() ----->");
        }

        /* inputs: wbs_<n>_in_s */
        builder.append("   signal ");
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            builder.append(String.format("wbs%d_in_s", component.getIndex()));
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(" : wbs_in_type;\n");

        /* outputs: wbs_<n>_out_s */
        builder.append("   signal ");
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            builder.append(String.format("wbs%d_out_s", component.getIndex()));
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(" : wbs_out_type;\n");
    }

    /**
     * Append signal definitions for all connections
     *
     * @param builder top-level-entity StringBuilder
     */
    private void buildCustomSignals(StringBuilder builder) {

        /* connections to and from the FPGA */
        for (Connection connection : fpga.getConnectionsIn(fpga)) {
            createSignal(builder, connection);
        }
        for (Connection connection : fpga.getConnectionsOut(fpga)) {
            createSignal(builder, connection);
        }

        /* internal connections between components */
        for (Component component : fpga.getComponents()) {
            for (Connection connection : fpga.getConnectionsOut(component)) {
                createSignal(builder, connection);
            }
        }
    }

    /**
     * Append a signal line and add it to the signals map field
     *
     * @param builder top-level-entity StringBuilder
     * @param connection that requires a signal
     */
    private void createSignal(StringBuilder builder, Connection connection) {

        Pin src = connection.getSrc();
        String signalName = signals.get(src);

        /* add new signal if necessary */
        if (signalName == null) {
            signalName = connection.getSignalName();
            signals.put(src, signalName);

            /* add all sinks to signals */
            for (Pin sink : connection.getSinks()) {
                signals.put(sink, signalName);
            }

            builder.append(String.format("   signal %s : std_logic;\n", signalName));
        }
    }

    /**
     * Generate intercon port map for the wishbone slaves
     *
     * @param builder top-level-entity StringBuilder
     */
    private void buildWBSlavesIntercon(StringBuilder builder) {

        if (DBG_OUTPUT) {
            System.out.println("<<----- TLEBuilder.buildWBSlavesIntercon() ----->>");
        }

        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            builder.append(String.format("      wbs%1$d_out => wbs%1$d_out_s,\n", component.getIndex()));
            builder.append(String.format("      wbs%1$d_in => wbs%1$d_in_s", component.getIndex()));
            if (iterator.hasNext()) {
                builder.append(",\n");
            }
        }
    }

    /**
     * Instantiate all components
     *
     * @param builder top-level-entity StringBuilder
     */
    private void buildCores(StringBuilder builder) {

        if (DBG_OUTPUT) {
            System.out.println("<<----- TLEBuilder.buildCores() ----->>");
        }

        for (Component component : fpga.getComponents()) {
            buildCore(builder, component);
        }
    }

    /**
     * Append (VHDL-93) direct instantiations for a component
     *
     * @param builder top-level-entity StringBuilder
     * @param component to be instantiated
     */
    private void buildCore(StringBuilder builder, Component component) {

        if (DBG_OUTPUT) {
            System.out.println("Building core: " + component.toString());
        }

        builder.append("\n");
        /* add direct FPGA GPIO connections of portmap signals */
        addCoreGPIOs(builder, component);

        /* prepare template scanner */
        String templateName = component.getClass().getSimpleName().toLowerCase();
        String templatePath = "/templates/cores/" + templateName + "_template.txt";
        Scanner scanner = new Scanner(TLEBuilder.class.getResourceAsStream(templatePath));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            /* replace %d by core index */
            if (line.contains("%d")) {
                line = String.format(line, component.getIndex());
            }

            /* replace %generic_map if component defines one */
            if (line.contains("%generic_map")) {
                String genericMap = component.getGenericMap();
                if (genericMap != null) {
                    builder.append("   generic map (\n");
                    for (Scanner sc = new Scanner(genericMap); sc.hasNextLine();) {
                        builder.append(String.format("      %s\n", sc.nextLine().trim()));
                    }
                    builder.append("   )\n");
                }
                continue;
            }

            /* generate port mapping (replace %connections) */
            if (line.contains("%connections")) {

                /* inputs */
                for (Iterator<Connection> iterator = fpga.getConnectionsIn(component).iterator();
                        iterator.hasNext();) {

                    Connection connection = iterator.next();
                    String srcName = connection.getSignalName();

                    /* connect all sinks */
                    for (Pin snkPin : connection.getSinks()) {
                        if (snkPin.getComponent() == component) {
                            builder.append(String.format("      %s => %s,\n",
                                                            snkPin.getName(), srcName));
                        }
                    }
                }

                /* outputs */
                for (Iterator<Connection> iterator = fpga.getConnectionsOut(component).iterator();
                        iterator.hasNext();) {

                    Connection connection = iterator.next();
                    Pin srcPin = connection.getSrc();
                    String sinkName = connection.getSignalName();

                    if (signals.get(srcPin) != null) {
                        sinkName = signals.get(srcPin);
                    }

                    builder.append(String.format("      %s => %s,\n", srcPin.getName(), sinkName));
                }

                /* remove last comma */
                builder.deleteCharAt(builder.length() - 2);

                continue;
            }
            builder.append(String.format("%s\n", line));
        }
        scanner.close();
    }

    /**
     * Add direct connection of portmap signals to the FPGA GPIOs
     *
     * @param builder top-level-entity StringBuilder
     * @param component before instantiation
     */
    private void addCoreGPIOs(StringBuilder builder, Component component) {

        String coreName = component.getClass().getSimpleName() + component.getIndex();

        /* inputs */
        boolean inputComment = false;
        for (Iterator<Connection> iterator = fpga.getConnectionsIn(component).iterator();
                iterator.hasNext();) {

            Connection connection = iterator.next();

            if (connection.getSrc().getComponent() instanceof FPGA) {

                /* add comment before the first line */
                if (!inputComment) {
                    builder.append(String.format("-- GPIO inputs of %s signals\n", coreName));
                    inputComment = true;
                }

                String srcGPIO = connection.getSrc().getName();
                String sinkSignalName = connection.getSignalName();
                builder.append(String.format("%s <= %s;\n", sinkSignalName, srcGPIO));
            }
        }

        /* outputs */
        boolean outputComment = false;
        for (Iterator<Connection> iterator = fpga.getConnectionsOut(component).iterator();
                iterator.hasNext();) {

            Connection connection = iterator.next();

            for (Pin sink : connection.getSinks()) {
                if (sink.getComponent() instanceof FPGA) {
                    /* add comment before the first line */
                    if (!outputComment) {
                        builder.append(String.format("-- GPIO outputs of %s signals\n", coreName));
                        outputComment = true;
                    }

                    String sinkGPIO = sink.getName();
                    String srcSignalName = connection.getSignalName();
                    builder.append(String.format("%s <= %s;\n", sinkGPIO, srcSignalName));
                }
            }
        }
    }
}
