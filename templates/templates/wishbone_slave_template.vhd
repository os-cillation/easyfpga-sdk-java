-- This file is part of easyFPGA.
-- Copyright 2013,2014 os-cillation GmbH
--
-- easyFPGA is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- easyFPGA is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with easyFPGA.  If not, see <http://www.gnu.org/licenses/>.

--===========================================================================--
-- Type and component definition package
--===========================================================================--

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.constants.all;
use work.interfaces.all;

package %wbs_package_name is

   type %wbs_reg_type is record
      %wbs_register_typedef
   end record;

   component %wbs_component_name
      port (
         -- register outputs
         %register_output_definitions

         -- wishbone interface
         wbs_in   : in  wbs_in_type;
         wbs_out  : out wbs_out_type
      );
   end component;

end package;

--===========================================================================--
-- Entity
--===========================================================================--

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use work.interfaces.all;
use work.constants.all;
use work.%wbs_package_name.all;

-------------------------------------------------------------------------------
entity %wbs_component_name is
-------------------------------------------------------------------------------
   port (
         -- register outputs
         %register_output_definitions

         -- wishbone interface
         wbs_in   : in  wbs_in_type;
         wbs_out  : out wbs_out_type
   );
end %wbs_component_name;

-------------------------------------------------------------------------------
architecture behavioral of %wbs_component_name is
 
-------------------------------------------------------------------------------
   ----------------------------------------------
   -- register addresses
   ----------------------------------------------
   %register_address_constants

   ----------------------------------------------
   -- signals
   ----------------------------------------------
   signal reg_out_s, reg_in_s : %wbs_reg_type;
   %signal_definitions

begin

-------------------------------------------------------------------------------
-- Concurrent
-------------------------------------------------------------------------------

-- register address decoder/comparator
%address_comparators

-- register enable signals
%register_enables

-- acknowledge output
wbs_out.ack <= wbs_in.stb;

-- register inputs always get data from wbs_in
%register_inputs

-- register output -> wbs_out via demultiplexer
%register_out_demux

-- register outputs -> non-wishbone outputs
%register_outputs

-------------------------------------------------------------------------------
   REGISTERS : process(wbs_in.clk)
-------------------------------------------------------------------------------
   begin
      -- everything sync to clk
      if (rising_edge(wbs_in.clk)) then

         -- reset all registers
         if (wbs_in.rst = '1') then
            %register_reset_assignments

         %register_store_conditions

         -- hold
         else
            reg_out_s <= reg_out_s;
         end if;
      end if;
   end process REGISTERS;

end behavioral;
