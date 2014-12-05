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

package easyfpga.generator.wishbone;

import java.util.Scanner;

/**
 * Class for generating wishbone slaves
 *
 * TODO: Pin directions / Use register as an input
 * TODO: Interrupt ability
 */
public class WishboneSlaveBuilder {
    /* number of registers */
    private int registerCount;

    /* arbitrary unique identifiers */
    private String packageName;
    private String componentName;
    private String registerTypeName;

    /* used instead of currentTimeMillis if set */
    private String name = null;

    /* register names: reg0, reg1 ... */
    private String[] registerNames;

    /**
     * Constructor to set number of registers
     * @param registerCount
     */
    public WishboneSlaveBuilder(int registerCount) {
        /* check register count */
        if (registerCount > 0 && registerCount <= 256) {
            this.registerCount = registerCount;
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Set a custom name that will be used instead of currentTimeMillis
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Read the wishbone slave template, replace magic words and extend it to the number
     * of registers given in the constructor
     *
     * @return a string representation of the wishbone slave
     */
    public String buildWisboneSlave() {

        /* init private fields */
        setUniqueIdentifiers();
        setRegisterNames();

        StringBuffer buffer = new StringBuffer();
        Scanner scanner = new Scanner(WishboneSlaveBuilder.class.
                getResourceAsStream("/templates/wishbone_slave_template.vhd"));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            /* replace arbitrary unique identifiers */
            if (line.contains("%wbs_package_name")) {
                buffer.append(line.replaceAll("%wbs_package_name", packageName) + "\n");
                continue;
            }
            if (line.contains("%wbs_component_name")) {
                buffer.append(line.replaceAll("%wbs_component_name", componentName) + "\n");
                continue;
            }
            if (line.contains("%wbs_reg_type")) {
                buffer.append(line.replaceAll("%wbs_reg_type", registerTypeName) + "\n");
                continue;
            }

            /* build register typedef */
            if (line.contains("%wbs_register_typedef")) {
                buildRegisterTypeDefinitions(buffer);
                continue;
            }

            /* build register output definitions */
            if (line.contains("%register_output_definitions")) {
                buildRegisterOutputDefinitions(buffer);
                continue;
            }

            /* build register address constants */
            if (line.contains("%register_address_constants")) {
                buildRegisterAddressConstants(buffer);
                continue;
            }

            /* build signal definitions */
            if (line.contains("%signal_definitions")) {
                buildSignalDefinitions(buffer);
                continue;
            }

            /* build address comparators */
            if (line.contains("%address_comparators")) {
                buildAddressComparators(buffer);
                continue;
            }

            /* build register enable signals */
            if (line.contains("%register_enables")) {
                buildRegisterEnables(buffer);
                continue;
            }

            /* build register inputs */
            if (line.contains("%register_inputs")) {
                buildRegisterInputs(buffer);
                continue;
            }

            /* build register output demultiplexer */
            if (line.contains("%register_out_demux")) {
                buildRegisterOutDemux(buffer);
                continue;
            }

            /* build register output connections */
            if (line.contains("%register_outputs")) {
                buildRegisterOutputConnections(buffer);
                continue;
            }

            /* build register reset assignments */
            if (line.contains("%register_reset_assignments")) {
                buildRegisterResetAssignments(buffer);
                continue;
            }

            /* build register store conditions */
            if (line.contains("%register_store_conditions")) {
                buildRegisterStoreConditions(buffer);
                continue;
            }

            /* no matches, just append the line */
            buffer.append(String.format("%s\n", line));
        }

        scanner.close();
        return buffer.toString();
    }

    private void setUniqueIdentifiers() {
        if (name == null) name = String.format("%d", System.currentTimeMillis());

        packageName = "package_" + name;
        componentName = "component_" + name;
        registerTypeName = "reg_" + name + "_t";
    }

    private void setRegisterNames() {
        registerNames = new String[registerCount];
        for (int i = 0; i < registerCount; i++) {
            registerNames[i] = "reg" + i;
        }
    }

    private void buildRegisterTypeDefinitions(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append("      " + regName + " : std_logic_vector(WB_DW-1 downto 0);\n");
        }
    }

    private void buildRegisterOutputDefinitions(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append("         " + regName + "_out : out std_logic_vector(WB_DW-1 downto 0);\n");
        }
    }

    private void buildRegisterAddressConstants(StringBuffer buffer) {
        /* the address corresponds to the position in the names array */
        for (int address = 0; address < registerNames.length; address++) {
            buffer.append("   constant " + registerNames[address].toUpperCase());
            buffer.append("_ADR : std_logic_vector(WB_REG_AW-1 downto 0) := x\"");
            buffer.append(String.format("%02X", address));
            buffer.append("\";\n");
        }
    }

    private void buildSignalDefinitions(StringBuffer buffer) {
        /* address match signals */
        buffer.append("   signal ");
        for (int i = 0; i < registerCount; i++) {
            buffer.append(registerNames[i] + "_adr_match_s");
            if (i < (registerCount - 1)) {
                buffer.append(",");
            }
            buffer.append(" ");
        }
        buffer.append(": std_logic;\n");

        /* register enable signals */
        buffer.append("   signal ");
        for (int i = 0; i < registerCount; i++) {
            buffer.append(registerNames[i] + "_re_s");
            if (i < (registerCount - 1)) {
                buffer.append(",");
            }
            buffer.append(" ");
        }
        buffer.append(": std_logic;\n");
    }

    private void buildAddressComparators(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append(regName + "_adr_match_s <= '1' when wbs_in.adr = ");
            buffer.append(regName.toUpperCase() + "_ADR else '0';\n");
        }
    }

    private void buildRegisterEnables(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append(regName + "_re_s <= wbs_in.stb AND wbs_in.we AND ");
            buffer.append(regName + "_adr_match_s;\n");
        }
    }

    private void buildRegisterInputs(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append("reg_in_s." + regName + " <= wbs_in.dat;\n");
        }
    }

    private void buildRegisterOutDemux(StringBuffer buffer) {
        buffer.append("with wbs_in.adr select\n");
        buffer.append("   wbs_out.dat <= ");
        for (String regName : registerNames) {
            buffer.append("reg_out_s." + regName + "   when ");
            buffer.append(regName.toUpperCase() + "_ADR,\n                  ");
        }
        buffer.append("(others => '-')  when others;\n");
    }

    private void buildRegisterOutputConnections(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append(regName + "_out <= reg_out_s." + regName + ";\n");
        }
    }

    private void buildRegisterResetAssignments(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append("            reg_out_s." + regName + " <= (others => '0');\n");
        }
    }

    private void buildRegisterStoreConditions(StringBuffer buffer) {
        for (String regName : registerNames) {
            buffer.append("         -- store " + regName + "\n");
            buffer.append("         elsif(" + regName + "_re_s = '1') then\n");
            buffer.append("            reg_out_s." + regName + " <= reg_in_s." + regName + ";\n\n");
        }
    }

    /**
     * Test method
     */
    public static void main(String[] args) {
        WishboneSlaveBuilder wsb = new WishboneSlaveBuilder(256);
        wsb.setName("wbs256");
        System.out.println(wsb.buildWisboneSlave());
    }
}
