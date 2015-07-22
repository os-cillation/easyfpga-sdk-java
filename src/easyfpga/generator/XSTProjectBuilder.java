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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import easyfpga.Util;
import easyfpga.exceptions.BuildException;
import easyfpga.generator.model.Component;
import easyfpga.generator.model.FPGA;

/**
 * The xst project builder. Uses templates to build the xst-project file that contains the HDL
 * entities to be compiled for a certain project.
 */
public class XSTProjectBuilder {

    private FPGA fpga;
    private static final String HDL_LIBRARY_NAME = "work";

    public XSTProjectBuilder(FPGA fpga) {
        this.fpga = fpga;
    }

    /**
     * Read the xst-project template and replace magic words with information about the FPGA.
     *
     * @return a string representation of the xst-project file
     * @throws BuildException
     */
    public String buildProject() throws BuildException {
        StringBuilder builder = new StringBuilder();
        Scanner scanner = new Scanner(XSTProjectBuilder.class.getResourceAsStream("/templates/xst-project_template.txt"));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().equals("%cores")) {
                buildCores(builder);
                continue;
            }
            builder.append(String.format("%s%n", line));
        }
        scanner.close();
        return builder.toString();
    }

    private void buildCores(StringBuilder builder) throws BuildException {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        for (Component component : fpga.getComponents()) {
            if (!classes.contains(component.getClass())) {
                classes.add(component.getClass());
                addCore(builder, component);
                builder.append(Util.LS);
            }
        }
    }

    private void addCore(StringBuilder builder, Component component) throws BuildException {

        /* add external sources if available */
        if (component.getExternalSourcesDirectory() != null) {
            File[] sourceFiles = component.getExternalSourcesDirectory().listFiles();
            String fileNameExtension;
            String language;
            String filePath;

            for (File sourceFile : sourceFiles) {
                /* determine filetype / language */
                fileNameExtension = Util.getFilenameExtension(sourceFile);
                if (fileNameExtension.equals("v")) language = "verilog";
                else if (fileNameExtension.equals("vhd")) language = "vhdl";
                else continue;

                /* add xst-project line */
                try {
                    filePath = sourceFile.getCanonicalPath();
                }
                catch (IOException e) {
                    throw new BuildException("Failed to determine path of external source file. "
                            + "Please check permissions.");
                }
                builder.append(String.format("%s %s \"%s\"%n",
                                            language, HDL_LIBRARY_NAME, filePath));
            }
        }

        /* always add template */
        String componentProject = "/templates/cores/" +
                            component.getClass().getSimpleName().toLowerCase() + "_project";
        Scanner scanner = new Scanner(XSTProjectBuilder.class.getResourceAsStream(componentProject));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            builder.append(String.format("%s%n", line));
        }
        scanner.close();
    }
}
