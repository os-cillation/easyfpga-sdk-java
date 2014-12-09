# I2C Master
An I2C master core.

## Pins

* **SDA** : Data I/O
* **SCL** : Clock I/O

## Usage
Note that both pins are bidirectional and have to be connected directly to FPGA GPIO pins in the FPGA definition.

### Initialization
The core can either be initialized in standard or in fast mode:

```java
myI2CMaster.init(I2CMaster.MODE_STANDARD);
myI2CMaster.init(I2CMaster.MODE_FAST);
```

In standard mode the clock frequency will be approx. 90 kHz, in fast mode 350 kHz. Calling the `init()` method without parameters defaults to the normal mode.

### Transfer Data
For typical read and write operations, you can use the following methods:

```java
void writeByte(int deviceAddress, int registerAddress, int data)
int readByte(int deviceAddress, int registerAddress)
```

The `writeByte(...)` method initiates three transfers:

1. Start condition; Write device address
2. Write register address
3. Write data; Stop condition

The `readByte(...)` method performs a typical I2C four-transfer-read:

1. Start condition; Write device address
2. Write register address
3. Repeated start condition; Write device address
4. Read register content; Stop condition; Send nack

In case the slave device does not acknowledge any of the transfers, an `I2CException` will be thrown. Both methods use the generic transfer method that will now be introduced.

#### Generic Transfer Method
In case these shorthand methods can not be applied, there is also a generic transfer method giving full control of the core:

```java
int transfer(int data, boolean transmit, Boolean start, boolean nack)
```

The method requires the following parameters:

* ** data ** : 8 bits of data when transmitting data or 7 address bits plus the R/W bit (which is the LSB)
* ** write ** : True, when transmitting data or address. False when receiving, the data parameter will then be ignored.
* ** start ** : True, if the start condition should be asserted. False, if the stop condition should be asserted at the end of the transfer. If neither start nor stop condition should be asserted this parameter can be set to `null`.
* ** nack ** : True, if a nack should be sent to indicate the end of a read transfer.

Note that the `nack` parameter can only be true for read transfers (`write` is false). For read transfers the method will return the value that has been received from a slave device. When writing (`write` is true), the method will return `1` if the transfer has been acknowledged by a slave and `0` if this is not the case.
