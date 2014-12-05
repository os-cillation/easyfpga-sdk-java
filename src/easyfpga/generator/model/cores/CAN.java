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

package easyfpga.generator.model.cores;

import java.io.File;

import easyfpga.ConfigurationFile;
import easyfpga.communicator.MultiRegisterReadCallback;
import easyfpga.communicator.RegisterReadCallback;
import easyfpga.exceptions.CANException;
import easyfpga.exceptions.CommunicationException;
import easyfpga.exceptions.ConfigurationFileException;
import easyfpga.generator.model.Core;
import easyfpga.generator.model.Pin;
import easyfpga.generator.model.Pin.Type;

/**
 * A wrapper for including a CAN-Bus controller with SJA1000 compatible interface.
 *
 * Due to the patents owned by Bosch, the sources of this core are not deployed with the easyFPGA
 * SDK. The sources can be downloaded from opencores.org. Note that for commercial use, the
 * purchase of  a CAN protocol license is mandatory.<br><br>
 *
 * <b>Known issues:</b><br>
 * - When in basic CAN mode, the reception of an extended message generates - if enabled - a
 *   receive interrupt. This behavior differs from the SJA1000 specification. Therefore it is
 *   recommended to operate the core in extended (PeliCAN) mode.
 *
 * @see <a href="http://opencores.org/project,can">http://opencores.org/project,can</a>
 *
 */
public class CAN extends Core {

    /** buffered register for control register or mode register (extended mode) */
    private BufferedRegister regCtrlMode = new BufferedRegister(REG.CONTROL);
    private BufferedRegister regClockDivider = new BufferedRegister(REG.CLOCK_DIVIDER);
    /** interrupt enable (only in extended mode) */
    private BufferedRegister regInterruptEnable;

    private boolean isReset;
    private boolean extendedMode;

    private ConfigurationFile configFile = new ConfigurationFile();

    private final long TRANSMIT_TIMEOUT_MILLIS = 3000;
    private final long RESET_TIMEOUT_MILLIS = 3000;

    public final class PIN {
        /** receive data input*/
        public static final String RX = "can_rx_in";
        /** transmit data output */
        public static final String TX = "can_tx_out";
    }

    /**
     * Register addresses for operation in basic mode
     */
    public final class REG {

        /*
         * Segment: Control
         */

        /** Control register. R/W */
        public static final int CONTROL = 0; /* called "mode" in can_registers.v */

        /** Command register. Write only, always reads as 0xFF */
        public static final int COMMAND = 1;

        /** Status register. Read only. */
        public static final int STATUS = 2;

        /** Interrupt register. Read only. */
        public static final int INTERRUPT = 3;

        /** Acceptance code register. R/W only in reset mode. */
        public static final int ACCEPTANCE_CODE = 4;

        /** Acceptance mask register. R/W only in reset mode. */
        public static final int ACCEPTANCE_MASK = 5;

        /** Bus timing register 0. R/W only in reset mode. */
        public static final int BUS_TIMING_0 = 6;

        /** Bus timing register 1. R/W only in reset mode. */
        public static final int BUS_TIMING_1 = 7;

        /* Output control register. R/W only in reset mode.
         * public static final int OUTPUT_CONTROL = 8;
         *
         * not available!
         */


        /*
         * Segment: Transmit buffer
         */

        /** TX identifier 1 [10..3]. R/W in operating mode. */
        /* called "tx_data_0" in can_registers.v */
        public static final int TX_IDENTIFIER_1 = 10;

        /** TX identifier 1 [2..0] (RTR and DLC). R/W in operating mode. */
        /* called "tx_data_1" in can_registers.v */
        public static final int TX_IDENTIFIER_2= 11;

        /** TX data byte 1. R/W in operating mode. */
        /* called "tx_data_2" in can_registers.v */
        public static final int TX_DATA_1 = 12;

        /** TX data byte 2. R/W in operating mode. */
        /* called "tx_data_3" in can_registers.v */
        public static final int TX_DATA_2 = 13;

        /** TX data byte 3. R/W in operating mode. */
        /* called "tx_data_4" in can_registers.v */
        public static final int TX_DATA_3 = 14;

        /** TX data byte 4. R/W in operating mode. */
        /* called "tx_data_5" in can_registers.v */
        public static final int TX_DATA_4 = 15;

        /** TX data byte 5. R/W in operating mode. */
        /* called "tx_data_6" in can_registers.v */
        public static final int TX_DATA_5 = 16;

        /** TX data byte 6. R/W in operating mode. */
        /* called "tx_data_7" in can_registers.v */
        public static final int TX_DATA_6 = 17;

        /** TX data byte 7. R/W in operating mode. */
        /* called "tx_data_8" in can_registers.v */
        public static final int TX_DATA_7 = 18;

        /** TX data byte 8. R/W in operating mode. */
        /* called "tx_data_9" in can_registers.v */
        public static final int TX_DATA_8 = 19;


        /*
         * Segment: Receive buffer
         */

        /** RX identifier 1 [10..3]. R/W */
        public static final int RX_IDENTIFIER_1 = 20;

        /** RX identifier 2 [2..0] (RTR and DLC). R/W */
        public static final int RX_IDENTIFIER_2 = 21;

        /** RX data byte 1. R/W */
        public static final int RX_DATA_1 = 22;

        /** RX data byte 2. R/W */
        public static final int RX_DATA_2 = 23;

        /** RX data byte 3. R/W */
        public static final int RX_DATA_3 = 24;

        /** RX data byte 4. R/W */
        public static final int RX_DATA_4 = 25;

        /** RX data byte 5. R/W */
        public static final int RX_DATA_5 = 26;

        /** RX data byte 6. R/W */
        public static final int RX_DATA_6 = 27;

        /** RX data byte 7. R/W */
        public static final int RX_DATA_7 = 28;

        /** RX data byte 8. R/W */
        public static final int RX_DATA_8 = 29;


       /**
        * Clock divider register. R/W, except for CAN mode and CBP which are only
        * writable in reset mode
        */
        public static final int CLOCK_DIVIDER = 31;

    }

    /**
     * Register addresses for operation in extended mode (PeliCAN)
     */
    public final class REG_EXT {
        /** Mode register. (R/W) */
        public static final int MODE = 0;

        /** Command register. Write only, will read as 0x00 */
        public static final int COMMAND = 1;

        /** Status register. Read only */
        public static final int STATUS = 2;

        /** Interrupt flag register. Read only */
        public static final int INTERRUPT = 3;

        /** Interrupt enable register. (R/W) */
        public static final int INTERRUPT_ENABLE = 4;

        /** Bus timing register 0. Writable in reset mode */
        public static final int BUS_TIMING_0 = 6;

        /** Bus timing register 1. Writable in reset mode */
        public static final int BUS_TIMING_1 = 7;

        /** Arbitration lost capture register. Read only */
        public static final int ARBITRATION_LOST_CAPTURE = 11;

        /** Error code capture register. Read only */
        public static final int ERROR_CODE_CAPTURE = 12;

        /** Error warning limit register. Writable in reset mode */
        public static final int ERROR_WARNING_LIMIT = 13;

        /** RX error counter register. Writable in reset mode */
        public static final int RX_ERROR_COUNTER = 14;

        /** TX error counter register. Writable in reset mode */
        public static final int TX_ERROR_COUNTER = 15;

        /**
         * Read: RX frame information; Write: TX frame information<br>
         * In reset: Acceptance code 0 (R/W)
         */
        public static final int FRAME_INFORMATION = 16;

        /**
         * Read: RX identifier 1; Write: TX identifier 1<br>
         * In reset: Acceptance code 1 (R/W)
         */
        public static final int IDENTIFIER_1 = 17;

        /**
         * Read: RX identifier 2; Write: TX identifier 2<br>
         * In reset: Acceptance code 2 (R/W)
         */
        public static final int IDENTIFIER_2 = 18;

        /**
         * SFF: Read: RX data 1; Write: TX data 1<br>
         * EFF: Read: RX identifier 3; Write: TX identifier 3<br>
         * In reset: Acceptance code 3 (R/W)
         */
        public static final int DATA_1 = 19;

        /**
         * SFF: Read: RX data 2; Write: TX data 2<br>
         * EFF: Read: RX identifier 4; Write: TX identifier 4<br>
         * In reset: Acceptance mask 0 (R/W)
         */
        public static final int DATA_2 = 20;

        /**
         * SFF: Read: RX data 3; Write: TX data 3<br>
         * EFF: Read: RX data 1; Write: TX data 1<br>
         * In reset: Acceptance mask 1 (R/W)
         */
        public static final int DATA_3 = 21;

        /**
         * SFF: Read: RX data 4; Write: TX data 4<br>
         * EFF: Read: RX data 2; Write: TX data 2<br>
         * In reset: Acceptance mask 2 (R/W)
         */
        public static final int DATA_4 = 22;

        /**
         * SFF: Read: RX data 5; Write: TX data 5<br>
         * EFF: Read: RX data 3; Write: TX data 3<br>
         * In reset: Acceptance mask 3 (R/W)
         */
        public static final int DATA_5 = 23;

        /**
         * SFF: Read: RX data 6; Write: TX data 6<br>
         * EFF: Read: RX data 4; Write: TX data 4
         */
        public static final int DATA_6 = 24;

        /**
         * SFF: Read: RX data 7; Write: TX data 7<br>
         * EFF: Read: RX data 5; Write: TX data 5
         */
        public static final int DATA_7 = 25;

        /**
         * SFF: Read: RX data 8; Write: TX data 8<br>
         * EFF: Read: RX data 6; Write: TX data 6
         */
        public static final int DATA_8 = 26;

        /** EFF: Read: RX data 7; Write: TX data 7 */
        public static final int DATA_9 = 27;

        /** EFF: Read: RX data 8; Write: TX data 8 */
        public static final int DATA_10 = 28;

        /** RX message counter. Read only. */
        public static final int RX_MESSAGE_COUNTER = 29;

        /** RX buffer start address. Writable in reset mode */
        public static final int RX_BUFFER_START_ADDRESS = 30;

        /** Clock divider. Some bits writable only in reset mode */
        public static final int CLOCK_DIVIDER = 31;
    }

    /**
     * CAN interrupt identification constants. All interrupts are cleared by calling the
     * identifyInterrupt method.
     *
     * @see easyfpga.generator.model.cores.CAN#identifyInterrupt() CAN.identifyInterrupt()
     */
    public final class INT {

        /**
         * Bus error interrupt. Extended mode only!
         */
        public static final int BUS_ERROR = 0x80;

        /**
         * Arbitration lost interrupt. Extended mode only!
         */
        public static final int ARBITRATION_LOST = 0x40;

        /**
         * If the error status changes from error active to error passive or vice-versa an
         * error passive interrupt is generated. Extended mode only!
         */
        public static final int ERROR_PASSIVE = 0x20;

        /**
         * Wake-up interrupt. The sleep mode has been left.
         */
        public static final int WAKE_UP = 0x10;

        /**
         * Data overrun interrupt. An incoming message has been lost due receive buffer overrun.
         */
        public static final int DATA_OVERRUN = 0x08;

        /**
         * Error interrupt. Either the error- or bus status bit has changed.
         */
        public static final int ERROR = 0x04;

        /**
         * Transmit interrupt. The transmit buffer is not locked anymore.
         */
        public static final int TRANSMIT = 0x02;

        /**
         * Receive interrupt. There is data in the receive buffer.
         */
        public static final int RECEIVE = 0x01;
    }

    /* bitrate constants, the values correspond to prescaler settings (at 80 MHz) */
    /** 1 Mbit/s */
    public static final int BITRATE_1M = 4;
    /** 250 kBit/s */
    public static final int BITRATE_250k = 16;
    /** 125 kBit/s */
    public static final int BITRATE_125k = 32;

    /**
     * Initialize the core in CAN-basic mode
     * @param bitrate use one of the bitrate constants, i.e. CAN.BITRATE_250k
     * @throws CommunicationException
     */
    public void init(int bitrate) throws CommunicationException {
        init(bitrate,false);
    }

    /**
     * Initialize the core with mode selection
     * @param bitrate use one of the bitrate constants, i.e. CAN.BITRATE_250k
     * @param extendedMode if true, the extended mode (PeliCAN) is used
     * @throws CommunicationException
     */
    public void init(int bitrate, boolean extendedMode) throws CommunicationException {
        /* parameter check */
        if (bitrate != BITRATE_125k && bitrate != BITRATE_250k && bitrate != BITRATE_1M) {
            throw new IllegalArgumentException("Invalid bitrate. Please use bitrate constants!");
        }

        enterCanMode(extendedMode);

        /* setup buffered registers for extended mode */
        if (extendedMode) {
            regInterruptEnable = new BufferedRegister(REG_EXT.INTERRUPT_ENABLE);
        }

        /* possible default timing */
        setBusTiming(bitrate, 2, false, 5, 2);

        /* set single-filter message filtering in extended mode*/
        if (extendedMode) {
            regCtrlMode.changeBit(3, true);
        }

        /* enable core */
        reset(false);
    }

    /**
     * @return True if the core is in extended mode
     */
    public boolean isExtendedMode() {
        return this.extendedMode;
    }

    private void enterCanMode(boolean extendedMode) throws CommunicationException {
        reset(true);
        regClockDivider.changeBit(7, extendedMode);
        this.extendedMode = extendedMode;
        reset(false);
    }

    /**
     * Specify the bus timing behavior.
     *
     * @param prescaler
     *          bitrate prescaler (1 .. 64)
     * @param syncronizationJumpWidth
     *          maximum number of clock cycles a bit period may be shortened
     *          or lengthened by one re-synchronization (0 .. 15)
     * @param sampling
     *          true: triple sampling; false: single sampling
     * @param timeSegment1 see datasheet (0 .. 15)
     * @param timeSegment2 see datasheet (0 .. 7)
     * @throws CommunicationException
     * @see  SJA1000 datasheet
     */
    /* works in both modes */
    private void setBusTiming(int prescaler, int syncronizationJumpWidth, boolean sampling,
                                int timeSegment1, int timeSegment2) throws CommunicationException {

        /* parameter check */
        if (prescaler < 1 || prescaler > 64) {
            throw new IllegalArgumentException("Invalid prescaler");
        }
        if (syncronizationJumpWidth < 0 || syncronizationJumpWidth > 3) {
            throw new IllegalArgumentException("Invalid synchronization jump width");
        }
        if (timeSegment1 < 0 || timeSegment1 > 15) {
            throw new IllegalArgumentException("Invalid time segment 2");
        }
        if (timeSegment2 < 0 || timeSegment2 > 7) {
            throw new IllegalArgumentException("Invalid time segment 2");
        }

        /* determine bus timing register values */
        int btr0 = ((syncronizationJumpWidth & 0x03) << 6) | ((prescaler - 1) & 0x3F);
        int btr1 = ((timeSegment2 & 0x07) << 4) | (timeSegment1 & 0x0F);
        if (sampling) btr1 |= (1 << 7);

        /* enter reset mode if necessary */
        boolean isResetTemp = isReset;
        if (!isReset) reset(true);

        /* write bus timing registers */
        writeRegister(REG.BUS_TIMING_0, btr0);
        writeRegister(REG.BUS_TIMING_1, btr1);

        /* restore the previously selected mode */
        if (!isResetTemp) reset(false);
    }

    /**
     * Transmit a CAN message
     * @param message message object that has to be constructed before
     * @throws CommunicationException
     * @throws CANException on timeout
     */
    public void transmit(CANMessage message) throws CommunicationException, CANException {
        long startMillis = System.currentTimeMillis();
        while (transmitBufferLocked()) {
            if ((System.currentTimeMillis() - startMillis) >= TRANSMIT_TIMEOUT_MILLIS) {
                throw new CANException("Timeout: Transmit buffer locked for over " +
                                         TRANSMIT_TIMEOUT_MILLIS + " ms");
            }
        }
        writeTransmitBuffer(message);
        requestTransmission();
        //while (transmissionInProgress());
    }

    private boolean transmitBufferLocked() throws CommunicationException {
        int status = readRegister(REG.STATUS);
        if ((status & 0x04) != 0) return false;
        else return true;
    }

    private void writeTransmitBuffer(CANMessage message) throws CommunicationException {
        int[] desc = message.getDescriptor();
        int[] data = message.getData();
        int length = message.getDataLength();

        /* extended message, core in basic mode */
        if (message.isExtended() && !this.extendedMode) {
            throw new IllegalStateException("Core is in basic CAN mode and cannot transmit "
                        + "extended messages.");
        }

        /* extended message, core in extended mode */
        else if (message.isExtended() && this.extendedMode) {
            /* prepare array for AAI write */
            int[] writeData = new int[length + 5];
            writeData[0] = message.getFrameInformation();
            writeData[1] = desc[0];
            writeData[2] = desc[1];
            writeData[3] = desc[2];
            writeData[4] = desc[3];
            for (int i=0; i < length; i++) {
                writeData[i + 5] = data[i];
            }

            /* write with AAI write */
            wrRegisterAAI(REG_EXT.FRAME_INFORMATION, writeData);
        }

        /* basic message, core in extended mode */
        else if (!message.isExtended() && this.extendedMode) {
            /* prepare array for AAI write */
            int[] writeData = new int[length + 3];
            writeData[0] = message.getFrameInformation();
            writeData[1] = desc[0];
            writeData[2] = desc[1];
            for (int i=0; i < length; i++) {
                writeData[i + 3] = data[i];
            }

            /* write with AAI write */
            wrRegisterAAI(REG_EXT.FRAME_INFORMATION, writeData);
        }

        /* basic message, core in basic mode */
        else {
            /* prepare array for AAI write */
            int[] writeData = new int[length + 2];
            writeData[0] = desc[0];
            writeData[1] = desc[1];
            for (int i=0; i < length; i++) {
                writeData[i + 2] = data[i];
            }

            /* write with AAI write */
            wrRegisterAAI(REG.TX_IDENTIFIER_1, writeData);
        }
    }

    /**
     * Get a message that has been received
     * @return The message in the receive buffer or null if empty
     * @throws CommunicationException
     */
    public CANMessage getReceivedMessage() throws CommunicationException {
        int identifier;
        int[] data;
        int[] regData = new int[15];
        CANMessage received;

        /* read status with single read */
        RegisterReadCallback statusCallback = new RegisterReadCallback(1);
        rdRegisterAsync(REG.STATUS, statusCallback);

        /* read 0x10 .. 0x1D with AAI read */
        MultiRegisterReadCallback aaiCallback = new MultiRegisterReadCallback();
        rdRegisterAAIAsync(REG_EXT.FRAME_INFORMATION, 14, aaiCallback);

        /* wait for both callbacks and fetch data */
        int[] statusData = statusCallback.getData();
        int[] aaiData = aaiCallback.getData();

        /* concatenate */
        System.arraycopy(statusData, 0, regData, 0, 1);
        System.arraycopy(aaiData, 0, regData, 1, aaiData.length);

        /* return null if receive buffer empty */
        if ((regData[0] & 0x01) == 0) {
            return null;
        }

        /* core is in basic mode */
        if (!extendedMode) {
            int identifier1 = regData[5];
            int identifier2 = regData[6];
            identifier = (identifier1 << 3) | ((identifier2 & 0xE0) >>> 5);

            /* if remote transmission request */
            if ((identifier2 & 0x10) != 0) {
                received = new CANMessage(identifier);
            }
            else {
                /* determine data length and read data */
                int dataLength = identifier2 & 0x0F;
                if (dataLength > 8) {
                    throw new RuntimeException("Illegal data length code detected");
                }
                data = new int[dataLength];
                for (int i = 0; i < dataLength; i++) {
                    data[i] = regData[7+i];
                }
                received = new CANMessage(identifier, data);
            }
        }
        /* core is in extended mode */
        else {
            int frameInformation = regData[1];
            int dataLength = frameInformation & 0x0F;
            int identifier1 = regData[2];
            int identifier2 = regData[3];

            /* received message is EFF */
            if ((frameInformation & 0x80) != 0) {
                int identifier3 = regData[4];
                int identifier4 = regData[5];
                identifier = identifier1 << 21 | identifier2 << 13 |
                             identifier3 << 5 | (identifier4 & 0xF8) >>> 3;

                /* if RTR */
                if ((frameInformation & 0x40) != 0) {
                    received = new CANMessage(identifier, true);
                }
                /* if data transmission */
                else {
                    data = new int[dataLength];
                    for (int i = 0; i < dataLength; i++) {
                        data[i] = regData[6+i];
                    }
                    received = new CANMessage(identifier, data, true);
                }
            }
            /* received message is SFF */
            else {
                identifier = identifier1 << 3 | (identifier2 & 0xE0) >>> 5;

                /* if RTR */
                if ((frameInformation & 0x40) != 0) {
                    received = new CANMessage(identifier);
                }
                /* if data transmission */
                else {
                    data = new int[dataLength];
                    for (int i = 0; i < dataLength; i++) {
                        data[i] = regData[4+i];
                    }
                    received = new CANMessage(identifier, data);
                }
            }
        }

        releaseReceiveBuffer();
        return received;
    }

    /* Control-Register related methods */

    /* works in both modes */
    private void reset(boolean reset) throws CommunicationException {

        /* check if necessary */
        if (isReset == reset) return;

        /* change reset request bit */
        regCtrlMode.changeBit(0, reset);

        /* verify and modify current mode */
        boolean resetRequestBit;
        long startTimeMillis = System.currentTimeMillis();
        while (true) {
            resetRequestBit = (readRegister(REG.CONTROL) & 0x01) != 0;
            if (resetRequestBit == reset) {
                break;
            }
            else if (System.currentTimeMillis() - startTimeMillis > RESET_TIMEOUT_MILLIS) {
                throw new CommunicationException("Timeout during reset");
            }
        }
        isReset = reset;
    }

    /* END Control-Register related methods */

    /* Command-Register related methods */

    /* works in both modes */
    private void requestTransmission() throws CommunicationException {
        writeRegister(REG.COMMAND, 0x01);
    }

    /* works in both modes */
    private void releaseReceiveBuffer() throws CommunicationException {
        writeRegister(REG.COMMAND, 0x04);
    }

    /**
     * Enter sleep mode if no interrupt is pending and there is no bus activity
     * @throws CommunicationException
     */
    public void goToSleep() throws CommunicationException {
        if (extendedMode) {
            regCtrlMode.changeBit(4, true);
        }
        else {
            writeRegister(REG.COMMAND, 0x10);
        }
    }

    /**
     * Wake up: Exit sleep mode and continue operation
     * @throws CommunicationException
     */
    public void wakeUp() throws CommunicationException {
        if (extendedMode) {
            regCtrlMode.changeBit(4, false);
        }
        else {
            writeRegister(REG.COMMAND, 0x00);
        }
    }

    /* END Command-Register related methods */

    /* Message filtering related methods */
    // TODO: Dual filter mode (extended)
    // TODO: Add RTR bit to acceptance code

    /**
     * Configure the core's acceptance code that - in conjunction with the acceptance mask -
     * filters messages before being stored in the receive buffer. In basic mode, the eight most
     * significant bits of the message's identifier must equal the acceptance code for acceptance.
     *
     * @param acceptanceCode in CAN basic mode, the eight most significant bits (0 .. 0xFF), in
     *                        extended mode the entire 29 bit identifier (0 .. 0x1FFFFFFF)
     * @throws CommunicationException
     */
    public void setAcceptanceCode(int acceptanceCode) throws CommunicationException {

        /* store current mode and switch mode if required */
        boolean isResetTemp = isReset;
        if (!isReset) {
            reset(true);
        }

        /* if the core is in basic mode */
        if (!extendedMode) {
            /* parameter check */
            if (acceptanceCode < 0 || acceptanceCode > 0xFF) {
                throw new IllegalArgumentException("Illegal acceptance code");
            }

            writeRegister(REG.ACCEPTANCE_CODE, acceptanceCode);
        }
        /* if the core is in extended mode */
        /* this setup if for receiving extended messages with a single filter configuration*/
        else {
            /* parameter check */
            if (acceptanceCode < 0 || acceptanceCode > 0x1FFFFFFF) {
                throw new IllegalArgumentException("Illegal acceptance code");
            }

            /* write with AAI */
            int[] writeData = new int[4];
            writeData[0] = (acceptanceCode & (0xFF << 21)) >>> 21;  /* ACR0 */
            writeData[1] = (acceptanceCode & (0xFF << 13)) >>> 13;  /* ACR1 */
            writeData[2] = (acceptanceCode & (0xFF << 5)) >>> 5;    /* ACR2 */
            writeData[3] = (acceptanceCode & 0x1F) << 3;            /* ACR3 */
            wrRegisterAAI(REG_EXT.FRAME_INFORMATION, writeData);
        }

        /* return to operating mode if previously selected */
        if (!isResetTemp) {
            reset(false);
        }
    }

    /**
     * Set a mask that qualifies which of the corresponding bits of the acceptance code are
     * relevant. Bits that are set ('1') in the mask are not used for filtering.
     *
     * @param acceptanceMask 8 bits in CAN basic mode (0 .. 0xFF), 29 bits in
     *                        extended mode (0 .. 0x1FFFFFFF)
     * @throws CommunicationException
     */
    public void setAcceptanceMask(int acceptanceMask) throws CommunicationException {

        /* store current mode and switch mode if required */
        boolean isResetTemp = isReset;
        if (!isReset) {
            reset(true);
        }

        /* core in basic mode */
        if (!extendedMode) {
            /* parameter check */
            if (acceptanceMask < 0 || acceptanceMask > 0xFF) {
                throw new IllegalArgumentException("Illegal acceptance mask");
            }

            writeRegister(REG.ACCEPTANCE_MASK, acceptanceMask);
        }
        /* core in extended mode */
        else {
            /* parameter check */
            if (acceptanceMask < 0 || acceptanceMask > 0x1FFFFFFF) {
                throw new IllegalArgumentException("Illegal acceptance mask");
            }

            /* write with AAI (AMR3.0 .. AMR3.2 are set to 1, so the RTR bit doesn't matter) */
            int[] writeData = new int[4];
            writeData[0] = (acceptanceMask & (0xFF << 21)) >>> 21;  /* AMR0 */
            writeData[1] = (acceptanceMask & (0xFF << 13)) >>> 13;  /* AMR1 */
            writeData[2] = (acceptanceMask & (0xFF << 5)) >>> 5;    /* AMR2 */
            writeData[3] = ((acceptanceMask & 0x1F) << 3) | 0x7;    /* AMR3 */
            wrRegisterAAI(REG_EXT.DATA_2, writeData);
        }

        /* return to operating mode if previously selected */
        if (!isResetTemp) {
            reset(false);
        }
    }

    /* END message filtering related methods */

    /* Interrupt related methods */

    /**
     * Identify pending interrupt(s)
     *
     * @return an interrupt identification integer which i.e. equals CAN.INT.RECEIVE when only
     *          this interrupt is pending or the sum of many interrupt identification constants
     *          when more than one interrupt is pending.
     * @throws CommunicationException
     * @see easyfpga.generator.model.cores.CAN.INT CAN.INT
     */
    public int identifyInterrupt() throws CommunicationException {
        int interrupts = readRegister(REG.INTERRUPT);

        /* truncate register content in basic mode */
        if (extendedMode) return interrupts;
        else return interrupts & 0x1F;
    }

    /**
     * Enable or disable a certain type of interrupt
     * @param interruptType an interrupt identification integer (i.e. CAN.INT.ERROR)
     * @param enable True: enable interrupt, False: disable interrupt
     * @throws CommunicationException
     * @see easyfpga.generator.model.cores.CAN.INT CAN.INT
     */
    public void enableInterrupt(int interruptType, boolean enable)
            throws CommunicationException {

        /* determine bit position in control or interrupt enable register */
        int bitPosition;
        switch (interruptType) {
            case INT.BUS_ERROR:
                bitPosition = 7;
                break;
            case INT.ARBITRATION_LOST:
                bitPosition = 6;
                break;
            case INT.ERROR_PASSIVE:
                bitPosition = 5;
                break;
            case INT.WAKE_UP:
                bitPosition = 4;
                break;
            case INT.DATA_OVERRUN:
                bitPosition = 3;
                break;
            case INT.ERROR:
                bitPosition = 2;
                break;
            case INT.TRANSMIT:
                bitPosition = 1;
                break;
            case INT.RECEIVE:
                bitPosition = 0;
                break;
            default:
                throw new IllegalArgumentException("Illegal interrupt type");
        }

        /* basic mode */
        if (!extendedMode) {
            if (bitPosition > 3) {
                throw new IllegalArgumentException("This interrupt type is usable in extended "
                                                  + "mode only.");
            }

            /* write to control register */
            regCtrlMode.changeBit(bitPosition + 1, enable);
        }
        /* extended mode */
        else {
            regInterruptEnable.changeBit(bitPosition, enable);
        }
    }


    /* END Interrupt related methods */


    /**
     * @return A String containing current status information
     */
    @Override
    public String toString() {

        /* read status register */
        int statusRegister = 0;
        try {
            statusRegister = readRegister(CAN.REG.STATUS);
        }
        catch (CommunicationException e) {
            e.printStackTrace();
        }

        /* extract bits */
        boolean bs = (statusRegister & 0x80) != 0;
        boolean es = (statusRegister & 0x40) != 0;
        boolean ts = (statusRegister & 0x20) != 0;
        boolean rs = (statusRegister & 0x10) != 0;
        boolean tcs = (statusRegister & 0x08) != 0;
        boolean tbs = (statusRegister & 0x04) != 0;
        boolean dos = (statusRegister & 0x02) != 0;
        boolean rbs = (statusRegister & 0x01) != 0;

        StringBuilder sb = new StringBuilder();

        sb.append(super.toString());
        sb.append("\nExtended mode:                " + this.extendedMode + "\n");

        sb.append("Bus status:                   ");
        if (bs) sb.append("bus off, no activities\n");
        else    sb.append("bus on\n");

        sb.append("Error status:                 ");
        if (es) sb.append("error; at least one of the error counters has reached the limit\n");
        else    sb.append("ok\n");

        sb.append("Transmit status:              ");
        if (ts) sb.append("transmission in progress\n");
        else    sb.append("idle\n");

        sb.append("Receive status:               ");
        if (rs) sb.append("reception in progress\n");
        else    sb.append("idle\n");

        sb.append("Transmission complete status: ");
        if (tcs)    sb.append("last requested transmission has been successfully completed\n");
        else        sb.append("previously requested transmission is not yet completed\n");

        sb.append("Transmit buffer status:       ");
        if (tbs)    sb.append("released\n");
        else        sb.append("locked\n");

        sb.append("Data overrun status:          ");
        if (dos)    sb.append("overrun\n");
        else        sb.append("ok, no overrun\n");

        sb.append("Receive buffer status:        ");
        if (rbs)    sb.append("full\n");
        else        sb.append("empty\n");

        return sb.toString();
    }

    public void writeRegister(int offset, int data) throws CommunicationException {
        if (offset == regCtrlMode.getOffset()) {
            regCtrlMode.write(data);
        }
        else if (offset == regClockDivider.getOffset()) {
            regClockDivider.write(data);
        }
        else {
            wrRegister(offset, data);
        }
    }

    public int readRegister(int offset) throws CommunicationException {
        if (offset == regCtrlMode.getOffset()) {
            return regCtrlMode.read();
        }
        else if (offset == regClockDivider.getOffset()) {
            return regClockDivider.read();
        }
        else {
            return rdRegister(offset);
        }
    }


    /* SDK related methods */

    @Override
    public File getExternalSourcesDirectory() {
        String canSourcesPath = configFile.getValue(ConfigurationFile.CAN_SOURCES_KEY);
        if (canSourcesPath == null) {
            throw new ConfigurationFileException("CAN_SOURCES is not configured. Please check"
                    + " your configuration file.");
        }
        return new File(canSourcesPath);
    }

    @Override
    public Pin getPin(String name) {
        if (!pinMap.containsKey(name)) {
            Type type = name.equals(PIN.RX) ? Type.IN : Type.OUT;
            Pin pin = new Pin(this, name, type);
            pinMap.put(name, pin);
        }
        return pinMap.get(name);
    }

    @Override
    public String getGenericMap() {
        return null;
    }

    /* END SDK related methods */

}
