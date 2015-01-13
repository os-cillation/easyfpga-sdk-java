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

/**
 * Represents an ongoing request-reply exchange that is associated to a certain frame id
 */
class Exchange {

    private Frame request = null;
    private Frame reply = null;

    private Callback callback = null;
    private int callbackSequenceID;

    private final long TIMEOUT_MILLIS = 1000;
    private final long constructMillis;
    private long setReplyMillis;

    /**
     * Default constructor to set request frame
     * @param request
     */
    public Exchange(Frame request) {
        this.constructMillis = System.currentTimeMillis();
        this.request = request;
    }

    /**
     * Constructor to set request frame and add a callback for asynchronous read cycles
     * @param request frame
     * @param callback Implementation of the CallBack interface to define the method called on
     *          reply reception
     * @param callbackSequenceID an integer that marks the chronological order the data was
     *          requested
     */
    public Exchange(Frame request, Callback callback, int callbackSequenceID) {
        this(request);
        this.callback = callback;
        this.callbackSequenceID = callbackSequenceID;
    }

    /**
     * Timeout checker method
     * @return True, if setReply method is invoked later than a certain duration
     * after constructor call
     */
    public boolean isTimedOut() {
        return (setReplyMillis - constructMillis > TIMEOUT_MILLIS);
    }

    /**
     * Assign a reply of type Frame
     * @param reply
     * @throws UnsupportedOperationException in case the reply is already set
     */
    public void setReply(Frame reply) throws UnsupportedOperationException {
        if (this.reply == null) {
            this.reply = reply;
            this.setReplyMillis = System.currentTimeMillis();
        }
        else {
            throw new UnsupportedOperationException("Reply field can only be set once!");
        }
    }

    /**
     * Reply getter method
     * @return The reply frame or null
     */
    public Frame getReply() {
        return this.reply;
    }

    /**
     * @return The reply's first byte or null if request is not set
     */
    public Byte getReplyOpcode() {
        if (reply == null) {
            return null;
        }
        else {
            return reply.getRawByte(0);
        }
    }

    /**
     * Test if a transmission ended in the reception of a NACK frame
     *
     * @return True if a NACK frame has been received
     */
    public boolean isNotAcknowledged() {
        if (reply != null && reply.getRawByte(0) == Protocol.OPC_NACK) {
            return true;
        }
        return false;
    }

    /**
     * @return The callback object, or null if not available
     */
    public Callback getCallback() {
        return callback;
    }

    /**
     * @return An integer that reflects the chronological order in which a callback should sort the
     *          received data
     */
    public int getCallbackSequenceID() {
        return callbackSequenceID;
    }

    /**
     * Request frame getter method
     *
     * @return The request frame or null
     */
    public Frame getRequest() {
        return this.request;
    }

    /**
     * @return The request's first byte or null if request is not set
     */
    public Byte getRequestOpcode() {
        if (request == null) {
            return null;
        }
        else {
            return request.getRawByte(0);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        /* id and request */
        sb.append(String.format("[ID: 0x%02X | ", request.getID()));
        sb.append("request: " + request.toString() + " | ");

        /* reply */
        if (reply != null) {
            sb.append("reply: " + reply.toString() + "]");
        }
        else {
            sb.append("reply: null]");
        }
        return sb.toString();
    }

}