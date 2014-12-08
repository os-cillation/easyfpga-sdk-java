# Frequency Divider
A configurable frequency divider core capable of dividing a clock signal by an 8-bit integer or deactivating the output. 

## Pins

* **OUT** : Divided clock output
* **IN** : Clock input

## Usage
Leaving the input pin unconnected results in a connection to the global 80 MHz clock.

The divisor can be set in range of 0 to 255 using the `setDivisor(int divisor)` method. There are also two convinience method named `bypass()` and `stopOutputClock()` that set the divisor to 1 or 0, respectively.
