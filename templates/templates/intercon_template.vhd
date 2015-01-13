-- This file is part of easyFPGA.
-- Copyright 2013-2015 os-cillation GmbH
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

--------------------------------------------------------------------------------
-- I N T E R C O N
-- (intercon.vhd)
--
-- Dynamic generated interconnection for multiple cores
--
-- Structural
--------------------------------------------------------------------------------

library IEEE;
use IEEE.std_logic_1164.all;

use work.interfaces.all;
use work.constants.all;

--------------------------------------------------------------------------------
ENTITY intercon is
--------------------------------------------------------------------------------
   port (
      clk_in      : in std_logic;
      rst_in      : in std_logic;

      -- wisbone master
      wbm_out     : in  wbm_out_type;
      wbm_in      : out wbm_in_type;

      -- wishbone slaves
      %wbslaves

      );

end intercon;

--------------------------------------------------------------------------------
ARCHITECTURE structural of intercon is
--------------------------------------------------------------------------------
   ----------------------------------------------
   -- constants
   ----------------------------------------------
   %constants

   ----------------------------------------------
   -- signals
   ----------------------------------------------
   signal core_adr_s  : std_logic_vector(WB_CORE_AW-1 downto 0);
   signal reg_adr_s   : std_logic_vector(WB_REG_AW-1 downto 0);

   %signals

--------------------------------------------------------------------------------
begin -- architecture structural
--------------------------------------------------------------------------------
   ----------------------------------------------------------------------------
   -- Split address
   ----------------------------------------------------------------------------
   reg_adr_s   <= wbm_out.adr(WB_REG_AW-1 downto 0);
   core_adr_s  <= wbm_out.adr(WB_AW-1 downto WB_REG_AW);

   ----------------------------------------------------------------------------
   -- Connect common signals
   ----------------------------------------------------------------------------
   %csignals

   ----------------------------------------------------------------------------
   -- DRD (Slave data out) Multiplexer
   ----------------------------------------------------------------------------
   %drdmultiplexer

   ----------------------------------------------------------------------------
   -- Address comparator
   ----------------------------------------------------------------------------
   %addresscomparator

   ----------------------------------------------------------------------------
   -- ACK OR Gate
   ----------------------------------------------------------------------------
   %ackorgate

   ----------------------------------------------------------------------------
   -- STB AND gates
   ----------------------------------------------------------------------------
   %stbandgates

   ----------------------------------------------------------------------------
   -- IRQ priority decoder
   ----------------------------------------------------------------------------
   %irqprioritydecoder

   ----------------------------------------------------------------------------
   -- IRQ OR gate
   ----------------------------------------------------------------------------
   %irqorgate

end structural;
