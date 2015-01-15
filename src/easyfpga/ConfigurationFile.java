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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * Global SDK configuration file located in /home/userName/.config
 */
public class ConfigurationFile {

    private final String CONFIG_NAME = "easyfpga.conf";
    private final String homeDir;
    private final String configPath;

    private Properties configuration;
    InputStream inStream;
    OutputStream outStream;

    /* keys names */

    /** Directory containing the xilinx toolchain binaries (xst, map, etc.) */
    public final static String XILINX_DIR_KEY = "XILINX_DIR";

    /** Path to the ttyUSB device. Leave empty for automatic determination */
    public final static String USB_DEVICE_KEY = "USB_DEVICE";

    /** Path to the HDL source of the CAN bus core */
    public final static String CAN_SOURCES_KEY = "CAN_SOURCES";

    /** Boolean to specify whether FPGA toolchain output should be shown during build */
    public final static String BUILD_VERBOSE_KEY = "FPGA_BUILD_VERBOSE";

    public ConfigurationFile() {

        configuration = new Properties();

        /* get configuration path */
        homeDir = System.getProperty("user.home");
        configPath = homeDir + File.separator + ".config" + File.separator + CONFIG_NAME;

        /* load file or create default configuration */
        load();
    }

    /**
     * Load configuration file. If nonexistent, create a new one containing default values.
     */
    public void load() {
        try {
            inStream = new FileInputStream(configPath);
            configuration.load(inStream);
        }
        catch (IOException e) {
            if (e instanceof FileNotFoundException) {
                System.out.println("Default configuration created at " + configPath);
                createDefault();
            }
            else {
                e.printStackTrace();
            }
        }
        finally {
            if (inStream != null) {
                try {
                    inStream.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Store the current configuration file
     */
    public void store() {
        try {
            outStream = new FileOutputStream(configPath);
            configuration.store(outStream, "easyFPGA configuration file");
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            if (outStream != null) {
                try {
                    outStream.close();
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Override the current configuration with default values and write it to a new
     * default configuration file
     */
    private void createDefault() {

        /* build configuration file string */
        String ls = System.getProperty("line.separator");
        StringBuilder sb = new StringBuilder();
        sb.append("###############################" + ls);
        sb.append("# easyFPGA configuration file #" + ls);
        sb.append("###############################" + ls + ls);

        sb.append("# Location of Xilinx toolchain binaries" + ls);
        sb.append(XILINX_DIR_KEY + " = /opt/Xilinx/14.7/ISE_DS/ISE/bin/lin64" + ls + ls);

        sb.append("# Default USB device the board is connected to. If commented out, easyFPGA" + ls);
        sb.append("# will use /dev/ttyUSBn with the lowest n found." + ls);
        sb.append("#" + USB_DEVICE_KEY + " = /dev/ttyUSB0" + ls + ls);

        sb.append("# For using the CAN bus controller core, you have to download the sources" + ls);
        sb.append("# from opencores.com and copy them to a location of your choice:" + ls);
        sb.append("#" + CAN_SOURCES_KEY + " = /absolute/path/to/sources" + ls + ls);

        sb.append("# Uncomment to show entire output of FPGA toolchain during build" + ls);
        sb.append("#" + BUILD_VERBOSE_KEY + " = TRUE");

        /* create .config directory if necessary */
        File configDir = new File(homeDir + File.separator + ".config");
        if (configDir.mkdir()) {
            System.out.println("Created directory: " + configDir.getPath());
        }

        /* write to new configuration file */
        FileOutputStream fos = null;
        Writer out = null;
        try {
            fos = new FileOutputStream(configPath);
            out = new BufferedWriter(new OutputStreamWriter(fos, "8859_1"));
            out.write(sb.toString());
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        /* close */
        finally {
            try {
                out.close();
                fos.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* parse configuration file */
        load();
    }

    /**
     * Get a configuration value by its key
     *
     * @param key to be fetched
     * @return The configuration value as a String or null if not found
     */
    public String getValue(String key) {
        return configuration.getProperty(key);
    }
}
