# GPIO8
A versatile GPIO core with 8 pins that can either be used as in- or outputs. Inputs are capable of generating interrupts.

## Pins

* **GPIO0** : General purpose I/O 0
* **GPIO1** : General purpose I/O 1
* **GPIO2** : General purpose I/O 2
* **GPIO3** : General purpose I/O 3
* **GPIO4** : General purpose I/O 4
* **GPIO5** : General purpose I/O 5
* **GPIO6** : General purpose I/O 6
* **GPIO7** : General purpose I/O 7

When defining an FPGA using the GPIO8 core, note that its pins can only be connected to FPGA GPIOs. This is due to the fact that the pins are capable of changing from sink (input) to source (output) and vice-versa which is not allowed for FPGA-internal connections.

## Usage
### Inputs
These methods are involved when using pins as inputs:

```java
void makeInput(int pinNumber)
boolean getInput(int pinNumber)
int getAllPins()
```

The `getInput` method can be used to read the current logic level at a certain pin. You can also get the levels of all pins (including output pins) by means of the `getAllPins` method. After a reset, all pins will act as inputs. Pins that were meanwhile configured as outputs can be turned back into inputs using the `makeInput` method.

### Outputs
For using certain pins as outputs, the following methods can be used:

```java
void makeOutput(int pinNumber)
void setOutput(int pinNumber, boolean logicLevel)
void setAllPins(int value)
```

Initially, a certain pin (0 .. 7) has to be configured to act as an output using the `makeOutput` method. Then its output level can be set with the `setOutput` method. By means of the `setAllPins` method, you can control the output levels of all pins pins configured as outputs. Pin configured as inputs will not be affected by this method.

### Interrupts
All pins can be configured to trigger an interrupt either on the rising or the falling edge. The are the methods involved:

```java
void enableInterrupt(int pinNumber)
void enableInterrupt(int pinNumber, boolean risingEdge)
int getInterruptStatus()
void clearInterrupts()
```

Using the single-argument version of the `enableInterrupt` method, a rising-edge triggered interrupt can be enabled on a certain pin. Falling-edge interrupts can be configured by means of the second argument of the two-argument version.

Once an interrupt is generated, the `getInterruptStatus` method returns a bitmask of the pins that caused the interrupt. Finally, all pending interrupts can be cleared with the `clearInterrupts` method. For general instructions on interrupts refer to the  [interrupt article](../interrupts.md).
