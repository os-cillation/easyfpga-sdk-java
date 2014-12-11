# PWM16
A pulse width modulation core with 16-bit resolution. 

## Pins

* **OUT** : Pulse width modulated output
* **CLK** : Optional clock input

By default the clock input is connected to an 80 MHz clock resulting in a constant output frequency of 1.22 kHz (80 Mhz / 2^16). For decreasing the input frequency, a [frequency divider](fdiv.md) core can be connected to the clock input.

## Usage
The duty cycle can be set using the `setDutyCycle(int dutyCycle)` method. The duty cycle has to be in the range of 0 to 65535. For checking the current duty cycle the method `getDutyCycle()` can be used.
