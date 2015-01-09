# UART
A 16750 UART with automatic CTS/RTS hardware flow control and 64-byte FIFO buffers. Its internal behavior (i.e. register map) is comparable to the integrated circuit [TL16C750](http://www.ti.com/product/tl16c750).

## Pins

* **RXD** : Receive input
* **TXD** : Transmit output
* **CTSn** : Clear to send flow control input
* **RTSn** : Request to send flow control output
* **AUX1** : Auxiliary output 1
* **AUX2** : Auxiliary output 2

## Usage

### Initialization
The UART cores has to be initialized before beeing used. For this purpose, call the method

```java
void init(int baudrate, int wordLength, char parity, int stopBits)
```

with the following parameters:

* **baudrate** : An arbitrary baudrate up to 921600
* **wordLength** : Number of bits per word (5 .. 8)
* **parity** : Odd, even or no parity (takes the characters 'O', 'E' or 'N')
* **stopBits** : Single or double stop bit (1 or 2)

For example, a UART using 8N1 at 9600 bps is initialized as follows:

```java
myUART.init(9600, 8, 'N', 1);
```

### Flow Control
The UART core is capable of managing hardware CTS/RTS flow control internally. If your application incorporates hardware flow control, call the method

```java
void enableAutoHardwareFlowControl()
```

right after initialization. Note, that hardware flow control requires a (crossover) connection of the control lines CTSn and RTSn between both communication partners.

### Transmitting
The SDK offers two method to send data through the UART core:

```java
void transmit(int data)
void transmit(String str)
```

The methods are able to transmit a single integer (0 .. 255) or a string. Since they require more than 8 bits, Unicode characters will be truncated. When using a word length smaller than 8, the most significant bits will be truncated.

### Receiving
Reception of data that have been stored in the UARTs receive buffer involves the following methods:

```java
int receive()
String receiveString()
int[] receive(int length)
String receiveString(int length)
```

The `receive()` method gets a single character from the receive buffer and returns it as an integer. When handling string reception the no-parameter method `receiveString()` can be used for reception. It will return all characters received until a binary zero is detected or the receive buffer is empty.

Both methods can be called to receive a certain number of characters, returning an integer array or string. In case the given length is greater than the number of characters in the receive buffer, the result will contain trailing binary zeroes.

Note that all receive methods are non-blocking and will return binary zeroes or empty strings respectively.

### Interrupts
The following interrupts are currently supported:

* **RX_AVAILABLE** : Receive buffer has reached its trigger level. Cleared when buffer drops below the trigger level.
* **TX_EMPTY** : Transmit buffer empty interrupt. Cleared when transmitting or reading interrupt identification register.

Interrupts are managed using the following methods:

```java
void enableInterrupt(int interrupt)
void disableInterrupt(int interrupt)
int identifyInterrupt()
```

The return value of the `identifyInterrupt()` method can be compared to an interrupt type, i.e.

```java
if (myUart.identifyInterrupt() == UART.INT.RX_AVAILABLE) {
    System.out.println("RX_AVAILABLE interrupt pending");
}
```

This method will return -1 in case there is no interrupt pending. For general instructions on interrupts refer to the  [interrupt article](../interrupts.md).

#### Trigger Levels
The receive buffer trigger level configures how many received words are required to issue an `RX_AVAILABLE` interrupt. There are four levels allowed: 1, 16, 32 and 56. For convenience you can use constants defined in the `UART` class:

```java
myUart.setRxTriggerLevel(UART.RX_TRIGGER_LEVEL_16);
```

### Auxiliary Outputs
There are two auxiliary outputs that can be used independently of the actual UART. By means of the method

```java
void setAuxiliaryOutput(int output, boolean value)
```

both of them can be controlled. The first argument refers to the pin names (1 or 2).
