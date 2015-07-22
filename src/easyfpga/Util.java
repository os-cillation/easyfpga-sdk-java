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

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.LogManager;

/**
 * Utility class with helper methods
 */
public final class Util {

    /** system-dependent file separator */
    public static final String FS = System.getProperty("file.separator");

    /** system-dependent line separator */
    public static final String LS = System.getProperty("line.separator");

    private static final String EASY_FPGA_FOLDER = ".easyFPGA";
    private static final String LOGGING_FOLDER = "log";

    /**
     * Search for the user home directory and create a .easyfpga directory. If user home is not
     * available, a temp directory is used.
     *
     * @return File object pointing to easyFPGA folder
     */
    public static File getEasyFPGAFolder() {
        String userHome = System.getProperty("user.home");
        String tmpFolder = System.getProperty("java.io.tmpdir");

        String rootFolderName = new File(userHome).canWrite() ? userHome : tmpFolder;
        File rootFolder = new File(rootFolderName);
        File folder = new File(rootFolder, EASY_FPGA_FOLDER + FS);
        return folder;
    }

    /**
     * Get a file's extension (characters after the last dot character)
     *
     * @param file to be checked
     * @return String after the last dot character or null if there is no dot
     */
    public static String getFilenameExtension(File file) {
        String fileName = file.getName();
        int lastIndexOfDot = fileName.lastIndexOf(".");

        if (lastIndexOfDot != -1 && lastIndexOfDot != 0) {
            return fileName.substring(lastIndexOfDot + 1);
        }
        else {
            return null;
        }
    }

    /**
     * Copy files or directories recursively
     *
     * @param source source file or directory
     * @param dest destination file or directory
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void copy(File source, File dest)
            throws FileNotFoundException, IOException {

        if (source.isDirectory()) {
            for (File file : source.listFiles()) {
                File target = new File(dest, source.getName());
                target.mkdirs();
                copy(file, target);
            }
        }
        else {
            File target = new File(dest, source.getName());
            Util.copyFileUsingStream(new FileInputStream(source), target);
        }
    }

    /**
     * Copy file from a given InputStream to a destination file
     *
     * @param sourceStream source file as an InputStream
     * @param dest destination given as a File
     * @throws IOException
     */
    public static void copyFileUsingStream(InputStream sourceStream, File dest)
            throws IOException {

        if (sourceStream == null) {
            throw new IllegalArgumentException("Source stream is null");
        }

        OutputStream destStream = null;
        try {
            destStream = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = sourceStream.read(buffer)) > 0) {
                destStream.write(buffer, 0, length);
            }
        }
        finally {
            sourceStream.close();
            destStream.close();
        }
    }

    /**
     * Remove a directory and its contents recursively
     *
     * @param directory to be removed
     * @throws IOException
     */
    public static void removeRecursively(File directory) throws IOException {
        Path path = Paths.get(directory.getCanonicalPath());

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {

                Files.delete(file);
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                    IOException exc) throws IOException {

                if (exc == null) {
                    Files.delete(dir);
                    return CONTINUE;
                }
                else {
                    throw exc;
                }
            }
        });
    }

    /**
     * Remove all files with a certain filename extension from a given directory
     *
     * @param directory containing the files to delete
     * @param extension files with this filename extension will be removed
     * @throws IOException
     */
    public static void removeFilesByExtension(File directory, String extension) throws IOException {

        for (File f : directory.listFiles()) {

            /* skip directories */
            String currentFileExt = getFilenameExtension(f);
            if (currentFileExt == null) continue;

            /* remove matching files */
            if (currentFileExt.equals(extension)) {
                if (!f.delete()) {
                    throw new IOException("Failed to delete file " + f.getCanonicalPath());
                }
            }
        }
    }

    /**
     * Read string from a file
     *
     * @param file to be read
     * @return string containing file content or empty string on file not found
     * @throws IOException
     */
    public static String readFile(File file) throws IOException {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(file));
        }
        catch (FileNotFoundException e) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String line = reader.readLine();

        while (line != null) {
            sb.append(line);
            sb.append(LS);
            line = reader.readLine();
        }
        String fileString = sb.toString();
        reader.close();
        return fileString;
    }

    /**
     * Setup logging by using the properties file in the easyFPGA directory.
     * Called at runtime to setup logging.
     */
    public static void initLogging() {
        LogManager manager = LogManager.getLogManager();

        /* open logging.properties file in easyFPGA directory */
        InputStream propertiesInputStream = null;
        try {
            propertiesInputStream = new FileInputStream(getLoggingPropertiesPath());
        }
        catch (FileNotFoundException e) {
            System.err.println("WARNING: No logging properties found at "
                                + getLoggingPropertiesPath() + LS +
                               "         Will use default logging.properties");
            return;
        }

        /* hand the properties file to the log manager */
        try {
            manager.readConfiguration(propertiesInputStream);
            propertiesInputStream.close();
        }
        catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ensure that logging.properties file and logging directory exist. Called during build process.
     */
    public static void prepareLogging() {
        /* create log directory if necessary */
        String logDirPath = System.getProperty("user.home") + FS + EASY_FPGA_FOLDER + FS
                          + LOGGING_FOLDER;
        File logDir = new File(logDirPath);
        if (!logDir.exists()) {
            if (logDir.mkdirs()) {
                System.out.println("Created logging directory: " + logDirPath);
            }
            else {
                System.err.println("Failed to create logging directory: " + logDirPath);
            }
        }

        /* copy properties file if necessary */
        File propertiesFile = new File(getLoggingPropertiesPath());
        if (!propertiesFile.exists()) {
            /* copy properties file from jar to .easyFPGA */
            InputStream propertiesStream = Util.class.getResourceAsStream("/logging.properties");
            try {
                copyFileUsingStream(propertiesStream, propertiesFile);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getLoggingPropertiesPath() {
        String propertiesPath = System.getProperty("user.home") + FS + EASY_FPGA_FOLDER + FS
                              + "logging.properties";
        return propertiesPath;
    }
}
