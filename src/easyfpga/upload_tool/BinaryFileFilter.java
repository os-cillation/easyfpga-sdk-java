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

package easyfpga.upload_tool;

import java.io.File;

import javax.swing.filechooser.FileFilter;

import easyfpga.Util;

public class BinaryFileFilter extends FileFilter {

    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        else {
            String extension = Util.getFilenameExtension(f);
            if (extension != null) {
                return extension.equals("bin");
            }
            else {
                return false;
            }
        }
    }

    @Override
    public String getDescription() {
        return "*.bin";
    }
}
