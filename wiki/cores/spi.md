# SPI Master
An SPI master core with variable clock frequency up to 40 MHz.

## Pins

* **SCK** : Serial clock output
* **MOSI** : Master out, slave in (serial data output)
* **MISO** : Master in, slave out (serial data input)

## Usage

### Initialization
Before the core can be used, the

```java
void init(int mode, int clockDiv)
```

method has to be called to specify the mode and clock frequency divider.

An SPI interface can be operated in four modes that specify the clock polarity (CPOL) and clock phase (CPHA). For the sake of readability the SPI core defines constants:

* **MODE_0** : CPOL = 0, CPHA = 0
* **MODE_1** : CPOL = 0, CPHA = 1
* **MODE_2** : CPOL = 1, CPHA = 0
* **MODE_3** : CPOL = 1, CPHA = 1

For the clock divider, the SPI core also defines constants named after the resulting clock frequency:

* **SCK_40_MHZ**
* **SCK_20_MHZ**
* **SCK_10_MHZ**
* **SCK_5_MHZ**
* **SCK_2500_KHZ**
* **SCK_1250_KHZ**
* **SCK_625_KHZ**

For example, initialization in mode 2 with 5 MHz clock frequency reads as follows:

```java
mySPI.init(SPI.MODE_2, SPI.SCK_5_MHZ);
```

### Communication
There are three methods for communicating with an SPI slave:

```java
void transmit(int data)
int receive()
int transceive(int data)
```

SPI generally operates in full-duplex mode: With each clock cycle one bit is transfered from master to slave over the MOSI line and one from slave to master on the MISO line. In case only one direction is of interrest, the method `transmit()` or `receive()` should be used. In order to transmit and receive at the same time, use the `transceive()` method. Since SPI communication usually works byte-wise, all method take and return 8-bit integers (0 .. 255).
