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

package easyfpga;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.LogManager;

import jssc.SerialPortException;
import easyfpga.communicator.Communicator;
import easyfpga.communicator.FPGABinary;
import easyfpga.communicator.VirtualComPort;
import easyfpga.exceptions.CommunicationException;

/**
 * The UploadTool can be used to upload an FPGA-binary to the board. The main class of the jar
 * archive created with ant points to this class.
 */
public class UploadTool {

    private VirtualComPort vcp;
    private Communicator com;
    private FPGABinary bin;

    public static void main(String[] args) {

        UploadTool uploadTool = new UploadTool();

        /* check argument count */
        int argc = args.length;
        if (argc != 1) {
            printUsage();
            System.exit(1);
        }

        /* check whether file exists */
        String fileName = args[0];
        File binaryFile = new File(fileName);
        if (!binaryFile.exists()) {
            System.out.println("File not found: " + fileName);
            System.exit(1);
        }

        /* upload binary, configure and select FPGA */
        int exitValue;
        if (uploadTool.upload(binaryFile) &&
            uploadTool.configure()) {

            System.out.println("SUCCESS");
            exitValue = 0;
        }
        else {
            System.out.println("FAILED!");
            exitValue = 1;
        }

        uploadTool.close();
        System.exit(exitValue);
    }

    public UploadTool() {
        disableLogging();

        /* setup vcp */
        vcp = new VirtualComPort();
        try {
            vcp.open();
        }
        catch (SerialPortException | CommunicationException e) {
            e.printStackTrace();
        }

        /* setup communicator and switch to MCU if necessary */
        com = new Communicator(vcp);
        if (com.isFPGAActive()) {
            com.selectMCU();
        }
    }

    private boolean upload(File binaryFile) {
        boolean retval = false;

        try {
            bin = new FPGABinary(binaryFile.getCanonicalPath());
            bin.loadFile();
            if (bin.upload(vcp)) retval = true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return retval;
    }

    private boolean configure() {
        if (bin.configureFPGA(vcp)) return true;
        else return false;
    }

    private void close() {
        vcp.close();
        System.gc();
    }

    private void disableLogging() {
        LogManager manager = LogManager.getLogManager();
        String config = ".level= OFF";
        try {
            manager.readConfiguration(new ByteArrayInputStream(config.getBytes()));
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("usage: java -jar easyFPGA.jar binary_file");
    }
}
