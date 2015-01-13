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
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import easyfpga.ConfigurationFile;
import easyfpga.Util;
import easyfpga.exceptions.BuildException;
import easyfpga.generator.model.FPGA;
import easyfpga.generator.wishbone.InterconBuilder;

/**
 * Top-level class that manages the conversion from an FPGA instance to VHDL code.
 */
public class FPGA2VHDLGenerator {

    private static final String BIN_FILENAME = "tle.bin";
    private String xilinxPath;
    private ConfigurationFile configFile;

    public FPGA2VHDLGenerator() {

        /* load or create default configuration  file */
        this.configFile = new ConfigurationFile();

        this.xilinxPath = configFile.getValue(ConfigurationFile.XILINX_DIR_KEY);
    }

    /**
     * Build an FPGA binary
     *
     * @param fpga instance to be converted
     * @throws BuildException
     */
    public void buildFPGA(FPGA fpga) throws BuildException {
        buildFPGA(fpga, false);
    }

    /**
     * Build an FPGA binary
     *
     * @param fpga instance to be converted
     * @param forceBuild controls whether to check if there is already a binary file
     * @throws BuildException
     */
    public void buildFPGA(FPGA fpga, boolean forceBuild) throws BuildException {
        try {
            File folder = Util.getEasyFPGAFolder();
            folder.mkdirs();
            copySocFiles(folder);

            /* generate TLE and intercon and check if there are changes */
            boolean tleUnchanged = generateTLE(folder, fpga);
            boolean interconUnchanged = generateIntercon(folder, fpga);

            /* don't build if TLE and intercon are unchanged and a binary exists */
            File binFile = new File(folder, BIN_FILENAME);
            if (!forceBuild && tleUnchanged && interconUnchanged && binFile.exists()) {
                System.out.println("Top-Level-Entity and Intercon are unchanged, "
                        + "skipping binary build.");
                return;
            }

            generateXST_Project(folder, fpga);
            generateXST_Script(folder, fpga);
            File buildFile = copyBuildScript(folder);
            runBuildProcess(buildFile);

            if (!binFile.exists()) {
                throw new BuildException();
            }
        }
        catch (Exception e) {
            throw new BuildException(e.getMessage());
        }
    }

    /**
     * Copy all files from the HDL sources folder to the target folder
     *
     * @param folder
     *            The target folder. Should be something like
     *            /home/userName/.easyFPGA/
     * @throws URISyntaxException
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void copySocFiles(File folder) throws URISyntaxException, FileNotFoundException,
                                                        IOException {

        URL resource = FPGA2VHDLGenerator.class.getResource("/soc");
        URLConnection connection = resource.openConnection();
        if (connection instanceof JarURLConnection) {
            JarURLConnection jarConnection = (JarURLConnection) connection;
            copyJarFolder(jarConnection, folder);
        } else {
            File soc = new File(resource.getPath());
            Util.copy(soc, folder);
        }
    }

    /**
     * Helper method to copy files from inside a jar archive
     *
     * @param jarConnection
     * @param folder
     * @throws IOException
     */
    private void copyJarFolder(JarURLConnection jarConnection, File folder) throws IOException {
        JarFile jarFile = jarConnection.getJarFile();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = (JarEntry) entries.nextElement();
            if (entry.getName().startsWith(jarConnection.getEntryName())) {
                if (entry.isDirectory()) {
                    File destFolder = new File(folder, entry.getName());
                    destFolder.mkdir();
                } else {
                    InputStream inputStream = jarFile.getInputStream(entry);
                    File destFile = new File(folder, entry.getName());
                    Util.copyFileUsingStream(inputStream, destFile);
                }
            }

        }
    }

    /**
     * Create the top-level-entity HDL code.
     *
     * @param folder
     * @param fpga
     * @return true if top-level-entity has not been changed since last build
     * @throws IOException
     */
    private boolean generateTLE(File folder, FPGA fpga) throws IOException {
        File file = new File(folder, "tle.vhd");
        System.out.println(file.getCanonicalPath());

        /* generate TLE string */
        TLEBuilder builder = new TLEBuilder(fpga);
        String newTLE = builder.buildTLE();

        /* read current TLE file */
        String currentTLE = Util.readFile(file);

        /* compare and abort returning true if equals */
        if (currentTLE.equals(newTLE)) {
            return true;
        }
        else {
            /* write */
            FileWriter writer = new FileWriter(file);
            writer.write(newTLE);
            writer.close();
            return false;
        }
    }

    /**
     * Generate the wishbone-intercon HDL code
     *
     * @param folder
     * @param fpga
     * @return true if intercon has not been changed since last build
     * @throws IOException
     */
    private boolean generateIntercon(File folder, FPGA fpga) throws IOException {
        File file = new File(folder, "intercon.vhd");
        System.out.println(file.getCanonicalPath());

        /* generate intercon string */
        InterconBuilder builder = new InterconBuilder(fpga);
        String newIntercon = builder.buildIntercon();

        /* read current intercon file */
        String currentIntercon = Util.readFile(file);

        /* compare and abort returning true if equals */
        if (currentIntercon.equals(newIntercon)) {
            return true;
        }
        else {
            /* write intercon */
            FileWriter writer = new FileWriter(file);
            writer.write(newIntercon);
            writer.close();
            return false;
        }
    }

    /**
     * Generate the xst-project file
     *
     * @param folder
     * @param fpga
     * @throws IOException
     * @throws BuildException
     */
    private void generateXST_Project(File folder, FPGA fpga) throws IOException, BuildException {
        File file = new File(folder, "xst-project");
        System.out.println(file.getCanonicalPath());
        FileWriter writer = new FileWriter(file);
        XSTProjectBuilder builder = new XSTProjectBuilder(fpga);
        String XSTProject = builder.buildProject();
        writer.write(XSTProject);
        writer.close();
    }

    private void generateXST_Script(File folder, FPGA fpga) throws IOException {
        File file = new File(folder, "xst-script");
        System.out.println(file.getCanonicalPath());
        FileWriter writer = new FileWriter(file);
        XSTScriptBuilder builder = new XSTScriptBuilder(fpga);
        String xstScript = builder.buildScript();
        writer.write(xstScript);
        writer.close();
    }

    /**
     * Copy build.sh script file to target directory
     *
     * @param targetDirectory
     * @return File object pointing to copy target
     * @throws IOException
     */
    private File copyBuildScript(File targetDirectory) throws IOException {
        File target = new File(targetDirectory, "build.sh");
        InputStream inputStream = FPGA2VHDLGenerator.class.getResourceAsStream("/templates/build.sh");
        Util.copyFileUsingStream(inputStream, target);
        System.out.println(target.getCanonicalPath());
        return target;
    }

    /**
     * Run the build script to create the FPGA binary
     *
     * @param buildFile
     * @throws IOException
     * @throws InterruptedException
     */
    private void runBuildProcess(File buildFile) throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder("bash", buildFile.getName());
        builder.directory(buildFile.getParentFile().getCanonicalFile());
        builder.redirectErrorStream(true);
        builder.environment().put("PATH", String.format("%s:%s", builder.environment().get("PATH"),
                                                                                    xilinxPath));

        long startMillis = System.currentTimeMillis();
        Process p = builder.start();

        /* print output m shell process */
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = inputReader.readLine()) != null) {
            System.out.println(line);
        }
        p.waitFor();
        long durationMillis = System.currentTimeMillis() - startMillis;

        /* print exit value and duration */
        System.out.println(String.format("Build exit value: %d", p.exitValue()));

        System.out.println(String.format("Build duration: %d:%02d min",
                TimeUnit.MILLISECONDS.toMinutes(durationMillis),
                TimeUnit.MILLISECONDS.toSeconds(durationMillis) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationMillis))));
    }
}
