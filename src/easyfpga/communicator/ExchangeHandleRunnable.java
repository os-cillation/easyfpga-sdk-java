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

import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * Processes completed exchanges, checks for the reception of NACK frames,
 * initiates error corrective actions and calls callback methods.
 */
class ExchangeHandleRunnable implements Runnable {

    /* the list of finished cycles to be processed */
    private volatile LinkedList<Exchange> finishedExchanges;

    private Communicator communicator;

    private final static Logger LOGGER = Logger.getLogger(ExchangeHandleRunnable.class.getName());

    /**
     * Construct with a reference to the Communicator
     *
     * @param com communicator
     */
    public ExchangeHandleRunnable(Communicator com) {
        this.communicator = com;
        this.finishedExchanges = new LinkedList<Exchange>();
    }

    /**
     * Add finished Exchange. Both the request and reply fields have to be set.
     */
    public synchronized void addExchange(Exchange exchange) {
        if (exchange.getReply() == null) {
            throw new IllegalArgumentException("Cannot add RequestReply: Reply == null");
        }
        else if(exchange.getRequest() == null) {
            if (exchange.getReplyOpcode() == Protocol.OPC_REGISTER_RDRE) {
                return;
            }
            else {
                throw new IllegalArgumentException("Cannot add RequestReply: Request == null");
            }
        }
        else {
            finishedExchanges.add(exchange);
        }
    }

    @Override
    public void run() {

        Exchange currentExchange;

        while (!Thread.currentThread().isInterrupted()) {

            /* wait until there are exchanges to process */
            while(finishedExchanges.isEmpty()) {
                try {
                    Thread.sleep(0, 500);
                }
                catch (InterruptedException e) {
                    return;
                }
            }

            /* process until exchange list is empty */
            while (!finishedExchanges.isEmpty()) {
                synchronized (this) {
                    /* get next reply */
                    currentExchange = finishedExchanges.poll();
                    if (currentExchange == null) break;

                    /* check the reply's parity */
                    if (!currentExchange.getReply().checkParity()) {
                        parityCheckFailed(currentExchange);
                    }

                    switch (currentExchange.getReplyOpcode()) {

                        /* discard ACK replies */
                        case Protocol.OPC_ACK: {
                            continue;
                        }
                        case Protocol.OPC_REGISTER_RDRE: {
                            /* if defined, run callback method */
                            if (currentExchange.getCallback() != null) {
                                currentExchange.getCallback().callback(currentExchange);
                            }
                            continue;
                        }
                        case Protocol.OPC_REGISTER_MRDRE: {
                            /* if defined, run callback method */
                            if (currentExchange.getCallback() != null) {
                                currentExchange.getCallback().callback(currentExchange);
                            }
                            continue;
                        }
                        case Protocol.OPC_REGISTER_ARDRE: {
                            /* if defined, run callback method */
                            if (currentExchange.getCallback() != null) {
                                currentExchange.getCallback().callback(currentExchange);
                            }
                            continue;
                        }
                        /* display debugging info on NACK replies */
                        case Protocol.OPC_NACK: {
                            processNACK(currentExchange);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void processNACK(Exchange nackExchange) {
        LOGGER.entering(getClass().getName(), "processNACK");
        /* retry on parity errors */
        if (Protocol.Error.fromByte(nackExchange.getReply().getRawByte(2)) == Protocol.Error.PARITY) {
            communicator.retry(nackExchange, Protocol.Error.PARITY);
        }

        /* TODO: retry on opcode unknown */

        /* only display unknown and timeout errors */
        else {
            displayNACK(nackExchange);
        }
    }

    private void parityCheckFailed(Exchange failed) {
        //TODO
        System.err.println("Reply parity check failed: " + failed);
        throw new RuntimeException("parity fail");
    }

    private void displayNACK(Exchange notAcknowledged) {
        LOGGER.entering(getClass().getName(), "displayNACK");
        LOGGER.severe("Exchange: " + notAcknowledged.toString());

        Protocol.Error err = Protocol.Error.fromByte(notAcknowledged.getReply().getRawByte(2));
        switch (err) {
            case OPCODE_UNKNOWN:
                LOGGER.severe("Request opcode unknown to the fpga");
                break;
            case PARITY:
                LOGGER.severe("Request parity error");
                break;
            case UNKNOWN:
                LOGGER.severe("Unknown error cause");
                break;
            case WISHBONE_TIMEOUT:
                LOGGER.severe("Wishbone timeout. Core or register not present?");
                break;
            default:
                LOGGER.severe("Error code undefined");
                break;
        }
    }
}
