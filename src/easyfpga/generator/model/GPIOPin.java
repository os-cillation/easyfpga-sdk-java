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

package easyfpga.generator.model;

/**
 * Represents a GPIO pin that is accessible via the pin headers.
 */
public class GPIOPin extends Pin {

    public static final String namePattern = "gpio_b%d_%02d";

    private int bank;
    private int pin;

    public GPIOPin(Component component, int bank, int pin) {
        super(component, String.format(namePattern, bank, pin));
        this.bank = bank;
        this.pin = pin;
    }

    /**
     * @return the bank (0 .. 2)
     */
    public int getBank() {
        return bank;
    }

    /**
     * @return the pin number (0 .. 23)
     */
    public int getPin() {
        return pin;
    }

}
