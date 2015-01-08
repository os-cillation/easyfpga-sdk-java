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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Utility class with helper methods
 */
public final class Util {

    private static final String EASY_FPGA_FOLDER = ".easyFPGA";

    /**
     * Search for the user home directory and create a .easyfpga directory. If user home is not
     * available, a temp directory is used.
     *
     * @return File object pointing to easyFPGA folder
     */
    public static File getEasyFPGAFolder() {
        String userHome = System.getProperty("user.home");
        String fileSeparator = System.getProperty("file.separator");
        String tmpFolder = System.getProperty("java.io.tmpdir");

        String rootFolderName = new File(userHome).canWrite() ? userHome : tmpFolder;
        File rootFolder = new File(rootFolderName);
        File folder = new File(rootFolder, EASY_FPGA_FOLDER + fileSeparator);
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
            sb.append("\n");
            line = reader.readLine();
        }
        String fileString = sb.toString();
        reader.close();
        return fileString;
    }

}
