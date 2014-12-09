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

import easyfpga.communicator.Util;

/**
 * Representation of MIDI messages
 *
 * @see <a href="http://www.midi.org/techspecs/midimessages.php">midi.org</a>
 */
public class MIDIMessage {

    private MessageType type;
    /** the logical MIDI channel (1..16) */
    private int channel;

    private int key;
    private int velocity;
    private int data;

    /**
     * Constructor for messages with two data values
     *
     * @param type NOTE_OFF, NOTE_ON, POLYPHONIC_KEY_PRESSURE or CONTROL_CHANGE
     * @param channel the MIDI channel (1 .. 16)
     * @param key pressed, released or "aftertouched" (0 .. 127). For CONTROL_CHANGE messages, this
     *         is the controller number (0 .. 119).
     * @param velocity (0 .. 127)
     */
    public MIDIMessage(MessageType type, int channel, int key, int velocity) {
        /* parameter checks */
        checkChannel(channel);
        checkValue(velocity);
        checkValue(key);
        if (type != MessageType.NOTE_OFF && type != MessageType.NOTE_ON &&
             type != MessageType.POLYPHONIC_KEY_PRESSURE && type != MessageType.CONTROL_CHANGE) {
            throw new IllegalArgumentException();
        }
        if (type == MessageType.CONTROL_CHANGE && key > 119) {
            throw new IllegalArgumentException();
        }

        /* set fields */
        this.type = type;
        this.channel = channel;
        this.key = key;
        this.velocity = velocity;
    }

    /**
     * Constructor for messages with one data value
     *
     * @param type PROGRAM_CHANGE, CHANNEL_PRESSURE or PITCH_BEND_CHANGE
     * @param channel the MIDI channel (1 .. 16)
     * @param data depending on message type.<br>
     *         For PROGRAM_CHANGE messages: Patch number (0 .. 127).<br>
     *         For CHANNEL_PRESSURE messages: The greatest pressure value (0 .. 127).<br>
     *         For PITCH_BEND_CHANGE messages: 14-bit change information (0 .. 0x4000), whereas
     *         0x2000 represents no change.
     */
    public MIDIMessage(MessageType type, int channel, int data ) {
        /* parameter checks */
        checkChannel(channel);
        if (type == MessageType.PROGRAM_CHANGE || type == MessageType.CHANNEL_PRESSURE) {
            checkValue(data);
        }
        else if(type == MessageType.PITCH_BEND_CHANGE) {
            if (data < 0 || data > 0x4000) throw new IllegalArgumentException();
        }
        else {
            throw new IllegalArgumentException();
        }

        /* set fields */
        this.type = type;
        this.channel = channel;
        this.data = data;
    }

    /**
     * Constructor for generating messages from raw bytes
     *
     * @param rawMessage int[] containing the raw message bytes
     */
    public MIDIMessage(int[] rawMessage) {
        /* parameter checks */
        int length = rawMessage.length;
        if (length == 0 || length > 3) {
            throw new IllegalArgumentException();
        }

        /* extract channel */
        channel = (rawMessage[0] & 0x0F) + 1;

        /* extract type and data values; check message length */
        switch (rawMessage[0] & 0xF0) {
            case 0x80:
                if (length != 3) throw new IllegalArgumentException();
                type = MessageType.NOTE_OFF;
                key = rawMessage[1] & 0x7F;
                velocity = rawMessage[2] & 0x7F;
                break;
            case 0x90:
                if (length != 3) throw new IllegalArgumentException();
                type = MessageType.NOTE_ON;
                key = rawMessage[1] & 0x7F;
                velocity = rawMessage[2] & 0x7F;
                break;
            case 0xA0:
                if (length != 3) throw new IllegalArgumentException();
                type = MessageType.POLYPHONIC_KEY_PRESSURE;
                key = rawMessage[1] & 0x7F;
                velocity = rawMessage[2] & 0x7F;
                break;
            case 0xB0:
                if (length != 3) throw new IllegalArgumentException();
                type = MessageType.CONTROL_CHANGE;
                key = rawMessage[1] & 0x7F;
                velocity = rawMessage[2] & 0x7F;
                break;
            case 0xC0:
                if (length != 2) throw new IllegalArgumentException();
                type = MessageType.PROGRAM_CHANGE;
                data = rawMessage[1] & 0x7F;
                break;
            case 0xD0:
                if (length != 2) throw new IllegalArgumentException();
                type = MessageType.CHANNEL_PRESSURE;
                data = rawMessage[1] & 0x7F;
                break;
            case 0xE0:
                if (length != 3) throw new IllegalArgumentException();
                type = MessageType.PITCH_BEND_CHANGE;
                data = (rawMessage[1] & 0x7F) + ((rawMessage[2] & 0x7F) << 7);
                break;
            default:
                throw new IllegalArgumentException("Invalid midi message: " +
                                                     Util.toHexString(rawMessage));
        }
    }

    public int[] getRawMessage() {
        int status;
        int data0;
        int data1 = -1;

        /* compose message */
        if (type == MessageType.NOTE_OFF) {
            status = 0x80;
            data0 = key & 0x7F;
            data1 = velocity & 0x7F;
        }
        else if (type == MessageType.NOTE_ON) {
            status = 0x90;
            data0 = key & 0x7F;
            data1 = velocity & 0x7F;
        }
        else if (type == MessageType.POLYPHONIC_KEY_PRESSURE) {
            status = 0xA0;
            data0 = key & 0x7F;
            data1 = velocity & 0x7F;
        }
        else if (type == MessageType.CONTROL_CHANGE) {
            status = 0xB0;
            data0 = key & 0x7F;
            data1 = velocity & 0x7F;
        }
        else if (type == MessageType.PROGRAM_CHANGE) {
            status = 0xC0;
            data0 = data & 0x7F;
        }
        else if (type == MessageType.CHANNEL_PRESSURE) {
            status = 0xD0;
            data0 = data & 0x7F;
        }
        else if (type == MessageType.PITCH_BEND_CHANGE) {
            status = 0xE0;
            data0 = data & 0x7F;
            data1 = (data & 0x3F80) >>> 7;
        }
        else {
            throw new IllegalStateException();
        }

        /* add channel number to status byte */
        status|= (channel - 1) & 0x0F;

        /* return two-element array if data1 has not been touched */
        if (data1 == -1) {
            return new int[] {status, data0};
        }
        else {
            return new int[] {status, data0, data1};
        }
    }

    /**
     * @return the message's type
     */
    public MessageType getType() {
        return type;
    }

    /**
     * @return the MIDI channel number
     */
    public int getChannel() {
        return channel;
    }

    /**
     * @return the key number, or controller number for CONTROL_CHANGE messages
     */
    public int getKey() {
        if (type != MessageType.NOTE_OFF && type != MessageType.NOTE_ON &&
                type != MessageType.POLYPHONIC_KEY_PRESSURE && type != MessageType.CONTROL_CHANGE) {
            throw new IllegalStateException();
        }
        return key;
    }

    /**
     * @return the velocity value
     */
    public int getVelocity() {
        if (type != MessageType.NOTE_OFF && type != MessageType.NOTE_ON &&
                type != MessageType.POLYPHONIC_KEY_PRESSURE && type != MessageType.CONTROL_CHANGE) {
            throw new IllegalStateException();
        }
        return velocity;
    }

    /**
     * @return the multi-purpose data field of PROGRAM_CHANGE, CHANNEL_PRESSURE and
     *          PITCH_BEND_CHANGE messages.
     */
    public int getData() {
        if (type != MessageType.PROGRAM_CHANGE && type != MessageType.CHANNEL_PRESSURE &&
                type != MessageType.PITCH_BEND_CHANGE) {
            throw new IllegalStateException();
        }
        return data;
    }

    @Override
    public String toString() {
        int[] raw = getRawMessage();

        StringBuilder sb = new StringBuilder();

        if (type.getLength() == 2) {
            sb.append(String.format("[0x%02X, 0x%02X]", raw[0], raw[1]));
        }
        else {
            sb.append(String.format("[0x%02X, 0x%02X, 0x%02X]", raw[0], raw[1], raw[2]));
        }

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + channel;
        result = prime * result + data;
        result = prime * result + key;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + velocity;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MIDIMessage other = (MIDIMessage) obj;
        if (channel != other.channel)
            return false;
        if (data != other.data)
            return false;
        if (key != other.key)
            return false;
        if (type != other.type)
            return false;
        if (velocity != other.velocity)
            return false;
        return true;
    }

    /** support MIDI message types */
    public enum MessageType {
        /** Note is released */
        NOTE_OFF(0x80),
        /** Note is pressed */
        NOTE_ON(0x90),
        /** Aftertouch (Polyphonic) */
        POLYPHONIC_KEY_PRESSURE(0xA0),
        /** Controller value changed */
        CONTROL_CHANGE(0xB0),
        /** Patch number changed */
        PROGRAM_CHANGE(0xC0),
        /** Aftertouch (single greatest pressure value) */
        CHANNEL_PRESSURE(0xD0),
        /** Pitch bender changed */
        PITCH_BEND_CHANGE(0xE0);

        private int status;
        private MessageType(final int status) {
            this.status = status;
        }

        /**
         * @return the length of a message of this type
         */
        public int getLength() {
            if (this == PROGRAM_CHANGE || this == CHANNEL_PRESSURE) return 2;
            else return 3;
        }

       /**
        * @return the status byte (0xS0)
        */
        public int getStatus() {
            return status;
        }

        /**
         * Get a MessageType from the first byte of a message
         *
         * @param status int with a messages first byte
         * @return MessageType or null if first byte is unknown
         */
        public static MessageType fromInteger(int status) {

            status &= 0xF0;
            for (MessageType msgType : MessageType.values()) {
                if (msgType.getStatus() == status) {
                    return msgType;
                }
            }
            return null;
        }
    }

    private void checkChannel(int channel) {
        if (channel < 1 || channel > 16) {
            throw new IllegalArgumentException();
        }
    }

    private void checkValue(int value) {
        if (value < 0 || value > 127) {
            throw new IllegalArgumentException();
        }
    }
}
