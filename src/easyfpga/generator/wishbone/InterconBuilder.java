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

package easyfpga.generator.wishbone;

import java.util.Iterator;
import java.util.Scanner;

import easyfpga.Util;
import easyfpga.generator.model.Component;
import easyfpga.generator.model.FPGA;

/**
 * Generator for the Wishbone-Interconnection (intercon) that connects all the cores to the
 * top-level-entity.
 */
public class InterconBuilder {

    private FPGA fpga;

    public InterconBuilder(FPGA fpga) {
        this.fpga = fpga;
    }

    /**
     * Read the intercon template and replace magic words with information about the FPGA
     *
     * @return a string representation of the intercon file
     */
    public String buildIntercon() {

        StringBuffer buffer = new StringBuffer();
        Scanner scanner = new Scanner(
                InterconBuilder.class.getResourceAsStream("/templates/intercon_template.vhd"));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().equals("%wbslaves")) {
                buildWishboneSlaves(buffer);
                continue;
            }
            if (line.trim().equals("%constants")) {
                buildWishboneConstants(buffer);
                continue;
            }
            if (line.trim().equals("%signals")) {
                buildSignals(buffer);
                continue;
            }
            if (line.trim().equals("%csignals")) {
                buildCommonSignals(buffer);
                continue;
            }
            if (line.trim().equals("%drdmultiplexer")) {
                buildDRDMultiplexer(buffer);
                continue;
            }
            if (line.trim().equals("%addresscomparator")) {
                buildAdressComparator(buffer);
                continue;
            }
            if (line.trim().equals("%ackorgate")) {
                buildAckOrGates(buffer);
                continue;
            }
            if (line.trim().equals("%stbandgates")) {
                buildSTBAndGates(buffer);
                continue;
            }
            if (line.trim().equals("%irqprioritydecoder")) {
                buildIRQPriorityDecoder(buffer);
                continue;
            }
            if (line.trim().equals("%irqorgate")) {
                buildIRQGates(buffer);
                continue;
            }
            buffer.append(String.format("%s%n", line));
        }
        scanner.close();
        return buffer.toString();
    }

    private void buildWishboneSlaves(StringBuffer buffer) {
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            buffer.append(String.format("      wbs%d_out\t: in wbs_out_type;%n", component.getIndex()));
            buffer.append(String.format("      wbs%d_in\t: out wbs_in_type", component.getIndex()));
            if (iterator.hasNext()) {
                buffer.append(";" + Util.LS);
            }
        }
    }

    private void buildWishboneConstants(StringBuffer buffer) {
        for (Component component : fpga.getComponents()) {
            String hexString = String.format("%02X", component.getIndex());
            buffer.append(String.format("   constant WBS%d_ADR : std_logic_vector"
                    + "(WB_CORE_AW-1 downto 0) := x\"%s\";%n", component.getIndex(), hexString));
        }
    }

    private void buildSignals(StringBuffer buffer) {
        for (Component component : fpga.getComponents()) {
            buffer.append(String.format("   signal adr_match_%d_s : std_logic;%n", component.getIndex()));
        }
    }

    private void buildCommonSignals(StringBuffer buffer) {
        StringBuffer dat = new StringBuffer("   -- dat" + Util.LS);
        StringBuffer adr = new StringBuffer(Util.LS + "   -- adr" + Util.LS);
        StringBuffer we = new StringBuffer(Util.LS + "   -- we" + Util.LS);
        StringBuffer cyc = new StringBuffer(Util.LS + "   -- cyc" + Util.LS);
        StringBuffer clk = new StringBuffer(Util.LS + "   -- clk (wbm as well)" + Util.LS);
        StringBuffer rst = new StringBuffer(Util.LS + "   -- rst" + Util.LS);

        for (Component component : fpga.getComponents()) {
            int index = component.getIndex();
            dat.append(String.format("   wbs%d_in.dat <= wbm_out.dat;%n", index)); // dat
            adr.append(String.format("   wbs%d_in.adr <= reg_adr_s;%n", index)); // adr
            we.append(String.format("   wbs%d_in.we  <= wbm_out.we;%n", index)); // we
            cyc.append(String.format("   wbs%d_in.cyc <= wbm_out.cyc;%n", index)); // cyc
            clk.append(String.format("   wbs%d_in.clk <= clk_in;%n", index)); // clk
            rst.append(String.format("   wbs%d_in.rst <= rst_in;%n", index)); // rst
        }
        clk.append("   wbm_in.clk <= clk_in;" + Util.LS);

        buffer.append(dat.toString() + adr + we + cyc + clk + rst + Util.LS);
    }

    private void buildDRDMultiplexer(StringBuffer buffer) {
        buffer.append("   with core_adr_s select wbm_in.dat  <= " + Util.LS);
        for (Component component : fpga.getComponents()) {
            buffer.append(String.format("      wbs%1$d_out.dat when WBS%1$d_ADR,%n",
                    component.getIndex()));
        }
        buffer.append("      (others => '-') when others;" + Util.LS);
    }

    private void buildAdressComparator(StringBuffer buffer) {
        for (Component component : fpga.getComponents()) {
            buffer.append(String.format("   adr_match_%1$d_s <= '1' when core_adr_s = WBS%1$d_ADR else '0';%n",
                    component.getIndex()));
        }
    }

    private void buildAckOrGates(StringBuffer buffer) {
        buffer.append("   wbm_in.ack <=  ");
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            buffer.append(String.format("wbs%d_out.ack", component.getIndex()));
            if (iterator.hasNext()) {
                buffer.append(" OR" + Util.LS + "      ");
            } else {
                buffer.append(";" + Util.LS);
            }
        }
    }

    private void buildSTBAndGates(StringBuffer buffer) {
        for (Component component : fpga.getComponents()) {
            buffer.append(String.format("   wbs%1$d_in.stb <= wbm_out.cyc AND wbm_out.stb AND adr_match_%1$d_s;%n", component.getIndex()));
        }
    }

    private void buildIRQPriorityDecoder(StringBuffer buffer) {
        /* process line with sensitivity list */
        buffer.append("   IRQ_DEC : process (");
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            buffer.append(String.format("wbs%d_out.irq", component.getIndex()));
            if (iterator.hasNext()) {
                buffer.append(", ");
            } else {
                buffer.append(")" + Util.LS + Util.LS);
            }
        }

        buffer.append("\tbegin " + Util.LS + "\t\tif ");
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            buffer.append(String.format("wbs%1$d_out.irq = '1' then wbm_in.int_adr <= WBS%1$d_ADR;%n\t",
                    component.getIndex()));

            if (iterator.hasNext()) {
                buffer.append("\telsif ");
            } else {
                buffer.append("\telse wbm_in.int_adr <= (others => '-'); " + Util.LS +
                              "\t\tend if;" + Util.LS);
            }
        }
        buffer.append("\tend process IRQ_DEC;" + Util.LS);
    }

    private void buildIRQGates(StringBuffer buffer) {
        buffer.append("   wbm_in.girq <= ");
        for (Iterator<Component> iterator = fpga.getComponents().iterator(); iterator.hasNext();) {
            Component component = iterator.next();
            buffer.append(String.format("wbs%d_out.irq", component.getIndex()));
            if (iterator.hasNext()) {
                buffer.append(" OR" + Util.LS + "      ");
            } else {
                buffer.append(";" + Util.LS);
            }
        }
    }

}
