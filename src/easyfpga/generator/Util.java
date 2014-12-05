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

import java.io.File;

/**
 * Utility class with helper methods
 */
public final class Util {

    private static final String EASY_FPGA_FOLDER = ".easyFPGA";

    /**
     * Search for the user home directory and create a .easyfpga directory. If user home is not
     * available, a temp directory is used.
     *
     * @return
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

}
