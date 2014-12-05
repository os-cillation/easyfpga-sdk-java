#!/bin/bash

# This file is part of easyFPGA.
# Copyright 2013,2014 os-cillation GmbH
#
# easyFPGA is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# easyFPGA is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with easyFPGA.  If not, see <http://www.gnu.org/licenses/>.


UCF_FILENAME="./soc/easyFPGA.ucf"
DESIGN_NAME="tle"
LOG_FILE="build.log"

# check if last command piped into tee was successful
check_return_status() {
    STATUS=${PIPESTATUS[0]}
    if [ $STATUS -ne 0 ] ; then
        echo "ERROR: $1 failed";
        exit $STATUS;
    fi
}

rm $LOG_FILE

# Synthesize
xst -ifn xst-script | tee $LOG_FILE
check_return_status "HDL synthesis (xst)"

# Translate
# -aul  allow unmatched loc constraints
ngdbuild -uc $UCF_FILENAME -aul $DESIGN_NAME.ngc $DESIGN_NAME.ngd \
    | tee -a $LOG_FILE
check_return_status "Translate (ngdbuild)"

# Map
# -w    overwrite existing ncd file
map -p xc6slx9-tqg144-2 -w -o $DESIGN_NAME-pre-par.ncd $DESIGN_NAME.ngd \
    | tee -a $LOG_FILE
check_return_status "Map"

# Place and Route
par -w $DESIGN_NAME-pre-par.ncd $DESIGN_NAME.ncd | tee -a $LOG_FILE
check_return_status "Place and route"

# Generate Bitfile
# -w    overwrite existing files
bitgen -w -g binary:yes -g compress $DESIGN_NAME.ncd | tee -a $LOG_FILE
check_return_status "Bitfile generation (bitgen)"

# Clean directory
rm -r xst *.bgn *.bld *.drc *.ncd *.ngd
rm -r *.pad *.par *.pcf *.ptwx *.unroutes
rm -r *.xpi *.map *.html *.xrpt *.xwbt *.txt *.csv *.ngm *.xml *.mrp *.ngc
rm -r *.bit *.lst *.srp
rm -r webtalk.log _xmsgs xlnx* *.lso
