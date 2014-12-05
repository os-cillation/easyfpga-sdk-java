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

--------------------------------------------------------------------------------
-- easyFPGA T O P    L E V E L    E N T I T Y
-- (tle.vhd)
--
-- Structural
--
-- Integrates SoC Bridge, Intercon, Syscon and needed cores
--------------------------------------------------------------------------------

library IEEE;
use IEEE.std_logic_1164.all;

library UNISIM;
use UNISIM.vcomponents.all;

use work.interfaces.all;

--------------------------------------------------------------------------------
ENTITY %name is
--------------------------------------------------------------------------------
   port (
      -- user gpios
      %user_gpios

      -- MCU interface
      clk_i          : in  std_logic;
      fpga_active_i  : in  std_logic;
      mcu_active_o   : out std_logic;

      -- FIFO
      fifo_data_io   : inout std_logic_vector(7 downto 0);
      fifo_rxf_n_i   : in    std_logic;
      fifo_txe_n_i   : in    std_logic;
      fifo_rd_n_o    : out   std_logic;
      fifo_wr_o      : out   std_logic
   );
end %name;

--------------------------------------------------------------------------------
ARCHITECTURE structural of %name is
--------------------------------------------------------------------------------
   ----------------------------------------------
   -- signals
   ----------------------------------------------
   -- clock from syscon
   signal gclk_s : std_logic;
   signal grst_s : std_logic;

   -- wishbone master
   signal wbm_i_s : wbm_in_type;
   signal wbm_o_s : wbm_out_type;

   -- wishbone slaves
   %wbslaves

   --custom signals
   %customsignals

-------------------------------------------------------------------------------
begin -- architecture structural
-------------------------------------------------------------------------------

-------------------------------------------------------------------------------
SOC_BRIDGE : entity work.soc_bridge
-------------------------------------------------------------------------------
   port map (
      -- mcu
      fpga_active_i  => fpga_active_i,
      mcu_active_o   => mcu_active_o,

      -- fifo interface
      fifo_data_io   => fifo_data_io,
      fifo_rxf_n_i   => fifo_rxf_n_i,
      fifo_txe_n_i   => fifo_txe_n_i,
      fifo_rd_n_o    => fifo_rd_n_o,
      fifo_wr_o      => fifo_wr_o,

      -- wishbone master
      wbm_i          => wbm_i_s,
      wbm_o          => wbm_o_s
   );

--------------------------------------------------------------------------------
INTERCON : entity work.intercon
--------------------------------------------------------------------------------
   port map (
      clk_in      => gclk_s,
      rst_in      => grst_s,

      -- wisbone master
      wbm_out     => wbm_o_s,
      wbm_in      => wbm_i_s,

      -- wishbone slaves
      %wbslavesintercon

   );

-------------------------------------------------------------------------------
SYSCON : entity work.syscon
-------------------------------------------------------------------------------
   port map (
      clk_in   => clk_i,
      clk_out  => gclk_s,
      rst_out  => grst_s
   );

-------------------------------------------------------------------------------
-- Cores
-------------------------------------------------------------------------------
%cores

end structural;
