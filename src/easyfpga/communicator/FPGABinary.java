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

package easyfpga.communicator;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Adler32;

import easyfpga.Util;

/**
 * Represents the binary that configures the FPGA, manages its upload and triggers the FPGA
 * configuration.
 */
public class FPGABinary {

    private String filename;

    private byte[] confData;

    /* attributes in the MCU's soc_status structure */
    private boolean socUploaded = false;
    private boolean socVerified = false;
    private int size;
    private int startSectorID;
    private int hashcode;

    private final static Logger LOGGER = Logger.getLogger(FPGABinary.class.getName());

    /**
     * Constructor calls loadFile() and setStartSectorID(0)
     * @param filename
     */
    public FPGABinary(String filename) {
        this.filename = filename;

        setStartSectorID(0);
    }

    /**
     * @return the hashcode
     */
    public int getHashcode() {
        return hashcode;
    }

    /**
     * @return the startSectorID
     */
    public int getStartSectorID() {
        return startSectorID;
    }

    /**
     * @param startSectorID the startSectorID to set
     */
    private void setStartSectorID(int startSectorID) {
        this.startSectorID = startSectorID;
    }

    /**
     * @return the socIsUploaded
     */
    public boolean isSocUploaded() {
        return socUploaded;
    }

    /**
     * @return the socIsVerified
     */
    public boolean isSocVerified() {
        return socVerified;
    }

    /**
     * @return The size in bytes
     */
    public int getSize() {
        return size;
    }

    /**
     * Loads binary file content and sets the hash using adler32
     * @throws IOException
     */
    public void loadFile() throws IOException {
        LOGGER.entering(getClass().getName(), "loadFile");
        InputStream inputStream = null;

        if (this.filename != null) {
            LOGGER.fine(String.format("Uploading given binary file: %s", filename));

            /* create file handler */
            File file = new File(filename);

            /* get filesize */
            inputStream = new FileInputStream(file);
        }
        else {
            /* Try to load binary from jar */
            inputStream = FPGABinary.class.getResourceAsStream("/tle.bin");

            /* if binary is not available, load from home */
            if (inputStream == null || inputStream.available() == 0) {
                if (inputStream != null) {
                    inputStream.close();
                }
                File file = new File(Util.getEasyFPGAFolder(), "tle.bin");
                this.filename = file.getCanonicalPath();

                LOGGER.log(Level.INFO, (String.format("Uploading binary file from home dir: %s",
                        filename)));

                inputStream = new FileInputStream(file);
                if (inputStream.available() == 0) {
                    inputStream.close();
                    IOException ex = new IOException("Cannot find binary file!");
                    LOGGER.log(Level.WARNING, "Throwing exception", ex);
                    throw ex;
                }
            }
            else {
                LOGGER.log(Level.INFO, "Uploading binary file from jar");
            }
        }

        /* load data */
        DataInputStream dis = new DataInputStream(inputStream);
        this.size = dis.available();
        this.confData = new byte[this.size];
        dis.readFully(confData);
        dis.close();

        /* calculate hash using adler32 checksum */
        Adler32 adler = new Adler32();
        adler.reset();
        adler.update(this.confData, 0, this.confData.length);
        long tempChecksum = adler.getValue();
        adler.getValue();
        this.hashcode = (int) (tempChecksum);
    }

    /**
     * Uploads ConfigurationFile to application memory using sector write
     * commands. The last (possibly not filled) sector is filled with zeroes.
     * Prior to the upload, the status is downloaded and the hashcode stored
     * at the mcu side is compared to the confData hashcode.
     * @param vcp A VirtualComPort object. Needs to be opened.
     * @return True when successful
     */
    public boolean upload(VirtualComPort vcp) {
        LOGGER.entering(getClass().getName(), "upload");

        int requiredSectors, remoteHash;
        byte[] reply = new byte[1];
        ConfigurationStatus remoteStatus = new ConfigurationStatus();

        /* check whether vcp is opened */
        if (vcp.isOpened() == false) {
            return false;
        }

        /* get the hash of the application in memory */
        remoteStatus.download(vcp);
        remoteHash = remoteStatus.getHash();

        /* compare hashcode and abort upload on match */
        if (this.hashcode == remoteHash) {
            LOGGER.fine("Hashcode match - aborting upload");
            return true;
        }

        /* determine required sectors */
        requiredSectors =  this.size / Protocol.APPMEM_SECTOR_SIZE;
        if (this.size % Protocol.APPMEM_SECTOR_SIZE != 0 ) requiredSectors++;
        byte[][] sectors = new byte[requiredSectors][Protocol.APPMEM_SECTOR_SIZE];

        /* split into sectors */
        for (int sectorID = 0; sectorID < requiredSectors; sectorID++) {
            int offset = sectorID * Protocol.APPMEM_SECTOR_SIZE;
            sectors[sectorID] = Arrays.copyOfRange(confData, offset, offset+Protocol.APPMEM_SECTOR_SIZE);
        }

        /* for each sector call sector write command */
        System.out.println("Uploading FPGA binary ...");
        for (int sectorID = startSectorID; sectorID < requiredSectors+startSectorID; sectorID++) {
            vcp.send(Protocol.getFrameSECTOR_WR(sectorID, sectors[sectorID-startSectorID]));
            printProgressBar(sectorID - startSectorID + 1, requiredSectors);

            /* receive reply */
            reply = vcp.receive(1);

            if (reply[0] != Protocol.OPC_ACK) {
                return false;
            }
        }
        System.out.println("");

        /* tag as uploaded to flash */
        this.socUploaded = true;

        /* upload status */
        if (this.uploadStatus(vcp) == false) {
            LOGGER.severe("Failed to upload status");
            return false;
        }

        /* download status (mcu may override certain flags) */
        this.downloadStatus(vcp);

        return true;
    }

    public boolean configureFPGA(VirtualComPort vcp) {
        LOGGER.entering(getClass().getName(), "configureFPGA");
        ConfigurationStatus remoteStatus = new ConfigurationStatus();

        /* download status */
        remoteStatus.download(vcp);

        /* skip configuration depending on isConfigured flag and the hash */
        if (remoteStatus.isConfigured()) {
            if (this.hashcode == remoteStatus.getHash()) {
                return true;
            }
        }

        /* if not already done, configure fpga */
        vcp.send(Protocol.getFrameCONF_FPGA());

        /* receive and analyze single byte */
        byte reply = vcp.receive(1)[0];
        if (reply == Protocol.OPC_ACK) return true;
        else return false;
    }

    private boolean uploadStatus(VirtualComPort vcp) {
    LOGGER.entering(getClass().getName(), "uploadStatus");
        /* send status of this instance */
        vcp.send(Protocol.getFrameSTATUS_WR(this));

        /* receive and analyze single byte */
        byte reply;
        try {
            reply = vcp.receive(1, 500)[0];
            return (reply == Protocol.OPC_ACK);
        }
        catch (TimeoutException e) {
            LOGGER.info("Timeout during reception of ACK frame. Will now retry.");
            vcp.purge();
            return upload(vcp);
        }
    }

    private void downloadStatus(VirtualComPort vcp) {
    LOGGER.entering(getClass().getName(), "downloadStatus");
        byte[] reply = new byte[(Protocol.LEN_STATUS_RDRE)];
        byte[] payload = new byte[(Protocol.LEN_STATUS_RDRE - 1)];
        byte parity_received, parity_calculated;

        /* request receive and status until parity match */
        do {
            /* send request*/
            vcp.send(Protocol.getFrameSTATUS_RD());

            /* receive STATUS_RDRE */
            try {
                reply = vcp.receive(Protocol.LEN_STATUS_RDRE, 500);
            }
            catch (TimeoutException e) {
                LOGGER.info("Timeout during reception of MCU status. Will now retry.");
                vcp.purge();
                break;
            }

            /* extract payload (without parity) */
            payload = Arrays.copyOfRange(reply, 1, Protocol.LEN_STATUS_RDRE - 1);

            /* extract parity */
            parity_received = reply[Protocol.LEN_STATUS_RDRE - 1];

            /* calculate parity */
            parity_calculated = Protocol.xor_parity(payload);

        } while (parity_calculated != parity_received);

        /* set attributes: flags */
        this.socUploaded = ((payload[0] & 0x01) != 0);
        this.socVerified = ((payload[0] & 0x02) != 0);

        /* set attributes: startSectorID*/
        //this.startSectorID = payload[1] + ((payload[2] & 0x03 ) << 8);

        /* set attributes: configuration size */
        //this.size = payload[3] + (payload[4] << 8) +
        //		(payload[5] << 16) + (payload[6] << 24);

        /* set attributes: hashcode */
        this.hashcode = payload[7] 			& 0x000000FF |
                        (payload[8]  << 8) 	& 0x0000FF00 |
                        (payload[9]  << 16)	& 0x00FF0000 |
                        (payload[10] << 24) & 0xFF000000;
    }

    /**
     * Print a progress bar
     *
     * @param progress
     * @param max
     */
    private void printProgressBar(int progress, int max) {
        int donePercent;
        int todoPercent;
        String bar;

        /* calculate percentages */
        donePercent = 100 * progress / max;
        todoPercent = 100 - donePercent;

        /* compose string */
        bar = "\r[";
        donePercent--;
        for(int i = 0; i < donePercent; i++) {
            bar += "-";
        }
        bar += ">";
        for(int i = 0; i < todoPercent; i++) {
            bar += " ";
        }
        bar += "]";

        System.out.print(bar);
    }

    /**
     * @return filename, size and content as hexadecimal strings
     */
    public String toString() {
        String output = "<==   C O N F I G U R A T I O N   F I L E   ==>\n";
        output += "Filename:             " + this.filename + "\n";
        output += "Start Sector ID:      " + this.startSectorID + "\n";
        output += "Size:                 " + this.size + " bytes\n";
        output += "Hashcode:             " +
                String.format("0x%8s", Integer.toHexString(this.hashcode)).replace(' ', '0')
                + "\n";

        output += "socUploaded to Flash: " + this.socUploaded + "\n";
        output += "socVerified:          " + this.socVerified + "\n\n";

        return output;
    }
}
