# Updating the Microcontroller

This article describes procedures to use in order to update the firmware of the microcontroller (MCU). In order to ensure safe operation and avoid any damage of the hardware, we recommend to only use MCU firmware that has been officially released by os-cillation GmbH!

## Prerequisites
For programming the MCU (which is an Atmel atxmega128a4u) you will need a version of avrdude that supports this device (>= 6.0.1.). Most probably will will have to build avrdude, the sources can be downloaded from the avrdude [project page](http://savannah.nongnu.org/projects/avrdude).

## The recommended way
The easiest way of loading new firmware to the MCU uses the the installed application to enter the bootloader:

1. Make sure the FPGA is inactive (green LED is off). Maybe you have to unplug and reconnect the USB cable (without an external power supply attached).
2. Send the byte 0xEB to the board to enter the bootloader.
3. Use avrdude to upload a new firmware. Avrdude needs to be started '''immediately''' after sending the 0xEB byte.

It is most convenient to use the following script:

```
#!/bin/sh
# easyFPGA microcontroller update script

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <hexfile> <device>" >&2
    exit 1
fi

HEXFILE=$1
TTYUSB=$2

echo -en '\xEB' > $TTYUSB
avrdude -p atxmega128a4u -c avr109 -P $TTYUSB -e -U flash:w:$HEXFILE
```

## Fallback1: Enter the bootloader manually
This is for the case you have modified and updated the firmware in a way that it does not recognize the enter bootloader command (0xEB) anymore. You can force the board to enter the bootloader during startup by connecting GND to a solder joint of the power LED.

1. Disconnect any power supply
2. Use a wire or probe to temporarily connect GND to the solder joint marked with "boot" (see the article about the [board](board.md))
3. Connect USB to power up the board. You can now remove the ground wire from the LED. After this, both LEDs should be off.
4. Use avrdude to upload the new firmware as shown above

## Fallback2: Use PDI
In the rare case that nothing else works anymore, the MCU can still be programmed using the PDI interface. For this, a compatible programmer like AVRISP mkII or JTAGICE mkII is required.
