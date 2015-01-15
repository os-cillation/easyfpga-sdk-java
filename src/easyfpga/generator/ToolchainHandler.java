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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import easyfpga.ConfigurationFile;
import easyfpga.Util;
import easyfpga.exceptions.BuildException;

/**
 * Triggers the Xilinx toolchain to build an FPGA binary
 */
public class ToolchainHandler {

    private ConfigurationFile config;
    private boolean verbose;

    private final String DESIGN_NAME = "tle";
    private File BUILD_DIR;

    public ToolchainHandler() {
        config = new ConfigurationFile();
        BUILD_DIR = Util.getEasyFPGAFolder();

        /* get verbose flag from config */
        String verboseConfigValue = config.getValue(ConfigurationFile.BUILD_VERBOSE_KEY);
        if (verboseConfigValue != null && verboseConfigValue.toLowerCase().equals("true")) {
            verbose = true;
        }
        else {
            verbose = false;
        }
    }

    /**
     * Run the toolchain to generate FPGA binary from HDL sources located in ~/.easyFPGA
     *
     * @throws BuildException
     */
    public void runToolchain() throws BuildException {

        print("Build started, this will take some time ...");
        long startMillis = System.currentTimeMillis();

        try {
            /* remove binary to force re-build in case the build process gets interrupted */
            removeCurrentBinary();

            /* run the toolchain */
            synthesize();
            translate();
            map();
            placeAndRoute();
            generateBitfile();
        }
        catch (BuildException ex) {
            throw ex;
        }
        finally {
            removeIntermediateFiles();
        }

        float durationSeconds = (float) ((System.currentTimeMillis() - startMillis) / 1000.0);
        print(String.format("Build sucessfully finished in %.2f s", durationSeconds));
    }

    private void removeCurrentBinary() throws BuildException {
        String currentBinPath = Util.getEasyFPGAFolder() + File.separator + DESIGN_NAME + ".bin";
        File currentBin = new File(currentBinPath);

        if (currentBin.exists()) {
            if (!currentBin.delete()) {
                throw new BuildException("Unable to remove current binary");
            }
        }
    }

    private void synthesize() throws BuildException {
        List<String> cmdList = new ArrayList<String>();
        cmdList.add("xst");

        /* run commands from xst-script */
        cmdList.add("-ifn");
        cmdList.add("xst-script");

        runProcess(cmdList);
    }

    private void translate() throws BuildException {
        List<String> cmdList = new ArrayList<String>();
        cmdList.add("ngdbuild");

        /* user constraint file */
        String ucfPath = "soc" + File.separator + "easyFPGA.ucf";
        cmdList.add("-uc");
        cmdList.add(ucfPath);

        /* allow unmatched LOC constraints */
        cmdList.add("-aul");

        /* input file */
        cmdList.add(DESIGN_NAME + ".ngc");

        /* output file */
        cmdList.add(DESIGN_NAME + ".ngd");

        runProcess(cmdList);
    }

    private void map() throws BuildException {
        List<String> cmdList = new ArrayList<String>();
        cmdList.add("map");

        /* device name */
        cmdList.add("-p");
        cmdList.add("xc6slx9-tqg144-2");

        /* overwrite existing file */
        cmdList.add("-w");

        /* output file */
        cmdList.add("-o");
        cmdList.add(DESIGN_NAME + "-before-par.ncd");

        /* input file */
        cmdList.add(DESIGN_NAME + ".ngd");

        runProcess(cmdList);
    }

    private void placeAndRoute() throws BuildException {
        List<String> cmdList = new ArrayList<String>();
        cmdList.add("par");

        /* overwrite existing file */
        cmdList.add("-w");

        /* output file */
        cmdList.add(DESIGN_NAME + "-before-par.ncd");

        /* input file */
        cmdList.add(DESIGN_NAME + ".ncd");

        runProcess(cmdList);
    }

    private void generateBitfile() throws BuildException {
        List<String> cmdList = new ArrayList<String>();
        cmdList.add("bitgen");

        /* overwrite existing file */
        cmdList.add("-w");

        /* generate .bin file containing configuration data only */
        cmdList.add("-g");
        cmdList.add("binary:yes");

        /* compress output file */
        cmdList.add("-g");
        cmdList.add("compress");

        /* input file */
        cmdList.add(DESIGN_NAME + ".ncd");

        runProcess(cmdList);
    }

    private void removeIntermediateFiles() {

        /* remove files by extension */
        ArrayList<String> extensions = new ArrayList<>(Arrays.asList(
                "bgn", "bld", "drc", "ncd", "ngd", "pad", "par",
                "pcf", "ptwx", "unroutes", "xpi", "map", "html",
                "xrpt", "xwbt", "txt", "csv", "ngm", "xml",
                "mrp", "ngc", "bit", "lst", "srp", "lso"
        ));

        for (String ext : extensions) {
            Util.removeFilesByExtension(BUILD_DIR, ext);
        }

        /* remove directories with fixed names */
        ArrayList<String> dirNames = new ArrayList<>(Arrays.asList(
                "xst",
                "webtalk.log",
                "_xmsgs"
        ));

        for (String dirName : dirNames) {
            try {
                String dirPath = BUILD_DIR.getCanonicalPath() + File.separator + dirName;
                File dir = new File(dirPath);
                if (dir.exists()) {
                    Util.removeRecursively(dir);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* remove directories with names starting with xlnx */
        for (File dir : BUILD_DIR.listFiles()) {
            if (dir.isDirectory()) {
                String dirName = dir.getName();
                if (dirName.startsWith("xlnx")) {
                    try {
                        Util.removeRecursively(dir);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void runProcess(List<String> command) throws BuildException {

        /* say hello */
        String processName = command.get(0).toUpperCase();
        print(String.format("Starting process %s ...", processName));

        /* add toolchain path to first element of command list */
        String toolchainPath = config.getValue(ConfigurationFile.XILINX_DIR_KEY);
        String cmd = toolchainPath + File.separator + command.get(0);
        command.set(0, cmd);

        /* setup process builder */
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(BUILD_DIR);
        builder.redirectErrorStream(true);

        /* run process */
        boolean success = false;
        String cliOutput = null;
        float durationSeconds = 0;
        try {
            /* start*/
            long startMillis = System.currentTimeMillis();
            Process process = builder.start();

            /* store output, display and store if verbose */
            BufferedReader inputReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while (((line = inputReader.readLine()) != null)) {
                sb.append(String.format("%s%n", line));
                if (verbose) System.out.println(line);
            }

            /* finish */
            int exitValue = process.waitFor();
            success = exitValue == 0 ? true : false;
            cliOutput = sb.toString();
            durationSeconds = (float) ((System.currentTimeMillis() - startMillis) / 1000.0);
        }
        catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        /* say goodbye */
        if (success) {
            print(String.format("%s successfully returned after %.2f s",
                    processName, durationSeconds));
        }
        else {
            System.out.println(cliOutput);
            String error = String.format("Errors occured during %s, aborting build process",
                                          processName);
            print(error);
            throw new BuildException(error);
        }
    }

    private void print(String message) {
        String ls = System.getProperty("line.separator");
        String msg;
        if (verbose) {
            msg = "*************************************************************************" + ls +
                  " TOOLCHAIN HANDLER: " + message + ls +
                  "*************************************************************************";
        }
        else {
            msg = "*** TOOLCHAIN HANDLER: " + message ;
        }
        System.out.println(msg);
    }
}