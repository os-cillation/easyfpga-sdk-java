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

package easyfpga.communicator;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Represents the current configuration status stored in the application flash memory. Used to
 * get the hash of the FPGA binary and check whether the FPGA is already configured.
 */
class ConfigurationStatus {

    private boolean fpgaConfigured;
    private int hash;

    private static final Logger LOGGER = Logger.getLogger(ConfigurationStatus.class.getName());

    /**
     * Download the current status from the MCU
     *
     * @param vcp virtual com port to used
     */
    public void download(VirtualComPort vcp) {
        LOGGER.entering(getClass().getName(), "download");
        byte[] payload = new byte[(Protocol.LEN_STATUS_RDRE - 1)];
        byte[] reply = new byte[(Protocol.LEN_STATUS_RDRE)];
        byte parity_received, parity_calculated;

        /* request receive and status until parity match */
        do {
            /* send request*/
            vcp.send(Protocol.getFrameSTATUS_RD());

            /* wait for reply: STATUS_RDRE */
            try {
                reply = vcp.receive(Protocol.LEN_STATUS_RDRE, Protocol.STATUS_READ_TIMEOUT_MILLIS);
                LOGGER.finer("Status read reply: " + Util.toHexString(reply));
            }
            catch (TimeoutException e) {
                LOGGER.info("Timeout receiving status");
                vcp.reset();
                download(vcp);
                return;
            }

            /* extract payload (without parity) */
            payload = Arrays.copyOfRange(reply, 1, Protocol.LEN_STATUS_RDRE - 1);

            /* extract parity */
            parity_received = reply[Protocol.LEN_STATUS_RDRE - 1];

            /* calculate parity */
            parity_calculated = Protocol.xor_parity(payload);

            if (parity_calculated != parity_received) {
                LOGGER.info("Parity missmatch");
                vcp.reset();
            }
        } while (parity_calculated != parity_received);

        /* set attributes: fpga configured flag */
        this.fpgaConfigured = ((payload[0] & 0x04) != 0);

        /* set attributes: hash */
        this.hash =	(payload[7] 			& 0x000000FF |
                        (payload[8]  << 8) 	& 0x0000FF00 |
                        (payload[9]  << 16)	& 0x00FF0000 |
                        (payload[10] << 24) & 0xFF000000);
        LOGGER.exiting(getClass().getName(), "download");
    }

    /**
     * Get the hash of the FPGA binary currently stored in the application flash memory
     *
     * @return the hash
     */
    public int getHash() {
        return hash;
    }

    /**
     * fpgaConfigured getter function
     *
     * @return true if the MCU has already configured the FPGA
     */
    public boolean isConfigured() {
        return fpgaConfigured;
    }

}
