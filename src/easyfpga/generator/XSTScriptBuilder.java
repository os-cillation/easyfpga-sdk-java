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

package easyfpga.generator;

import java.util.Scanner;

import easyfpga.generator.model.FPGA;

/**
 * Builder that generates an XST script that will be passed to the HDL synthesis tool (XST)
 */
public class XSTScriptBuilder {

    private String tle_name;

    public XSTScriptBuilder(FPGA fpga) {
        tle_name = fpga.getName();
    }

    /**
     * Read the XST script template and replace magic words. Currently, only the top level entity
     * name (which is fpga.getName) is filled in.
     *
     * @return a string representation of the XST script file
     */
    public String buildScript() {
        StringBuilder builder = new StringBuilder();
        Scanner scanner = new Scanner(XSTScriptBuilder.class.getResourceAsStream("/templates/xst-script_template.txt"));

        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            line = line.replaceAll("%tle_name", tle_name);
            builder.append(String.format("%s\n", line));
        }

        scanner.close();
        return builder.toString();
    }
}