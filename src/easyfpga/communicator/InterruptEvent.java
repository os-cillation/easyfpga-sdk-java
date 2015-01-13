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

import easyfpga.generator.model.Core;

/**
 * Represents an event that is generated when an easyCore requested an interrupt. Contains
 * information on the requesting core.
 *
 * @see InterruptListener
 */
public class InterruptEvent {

    private Core irqCore;

    public InterruptEvent(Core irqCore) {
        if (irqCore != null) {
            this.irqCore = irqCore;
        }
        else {
            throw new IllegalStateException("InterruptEvent constructor called with nullpointer");
        }
    }

    /**
     * @return a core object of the core causing the interrupt
     */
    public Core getCore() {
        return irqCore;
    }

    /**
     * @return the core address of the core causing the interrupt (0xCC00)
     */
    public int getCoreAddress() {
        return irqCore.getCoreAddress();
    }
}