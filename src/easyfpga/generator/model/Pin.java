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

import easyfpga.exceptions.PinTypeFixedException;

/**
 * A pin is one end of a connection and must be a part of a component. It has
 * got a name and in some cases a fixed type. The type can be a IN, OUT or INOUT
 */
public class Pin {

    private Component component;
    private String name;
    private Type type;
    private boolean isFixedTyped = false;

    /**
     * Construct a pin with fixed type.
     *
     * @param component associated with the pin
     * @param name of the pin
     * @param type of the pin that cannot be changed anymore
     */
    public Pin(Component component, String name, Type type) {
        this.component = component;
        this.name = name;
        this.type = type;
        this.isFixedTyped = true;
    }

    /**
     * Construct a pin. Type will be Type.OUT by default, but can be changed afterwards.
     *
     * @param component associated with the pin
     * @param name of the pin
     */
    public Pin(Component component, String name) {
        this.component = component;
        this.name = name;
        this.type = Type.OUT;
    }

    /**
     * @return the pin name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the pin's type.
     *
     * @param type to set
     * @throws PinTypeFixedException in case the type is already fixed
     */
    public void setType(Type type) throws PinTypeFixedException {
        if (isFixedTyped && type != this.type) {
            throw new PinTypeFixedException();
        } else {
            this.type = type;
        }
    }

    /**
     * @return the pin's type
     */
    public Type getType() {
        return type;
    }

    /**
     * @return the component associated with the pin
     */
    public Component getComponent() {
        return component;
    }

    /**
     * @return true if the pin type cannot be changed anymore
     */
    public boolean isFixedTyped() {
        return isFixedTyped;
    }

    /**
     * The types a pin can have: Input, Output or In/Output. The latter can only be assigned
     * to FPGA GPIOs.
     */
    public enum Type {
        IN, OUT, INOUT;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    @Override
    public String toString() {
        return getComponent().getClass().getSimpleName() + getComponent().getIndex() + "." + name;
    }

}
