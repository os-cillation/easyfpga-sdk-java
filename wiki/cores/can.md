# CAN Wrapper
A wrapper for including a CAN-Bus controller. Due to the patents owned by Bosch, the sources of this core are not included in the easyFPGA SDK. The sources have to be downloaded at [opencores.org](http://opencores.org). Note that for commercial use, the purchase of a CAN protocol license is required.

## Setup

1. Create an account and login at [opencores.org](http://opencores.org)
2. Navigate to the can controllers project page (projects -> communication controller -> can protocol controller)
3. Download the cores RTL written in Verilog
4. Extract the archive to an arbitrary directory
5. Set the `CAN_SOURCES` entry in your [configuration file](../configuration.md) to the directory containing the Verilog sources

You should now be able to use the can controller.

## Pins
The pins of the CAN controller are directional and have to be connected to a CAN transceiver that implements the physical layer which is a differential-pair shared medium.

* **RX** : Receive input
* **TX** : Transmit output

## Usage
### CAN Frames
The frames are represented by instances of the `CANFrame` class. The CAN core supports both the basic- and the extended frame format. Furthermore a frame can either carry data or request a transmission from another node on the bus.

#### Construction
##### Basic Frame Format
The construction of frames containing data in the basic frame format is done by means of the following constructor:

```java
CANFrame(int identifier, int[] data)
```

The identifier parameter is 11 bits long (0 .. 0x7FF), the array containing the data to send has a maximum size of 8 elements, each of them in the range of 0 to 255.

Remote transfer request frames are created in a similar manner, but without the data parameter:

```java
CANFrame(int identifier)
```

##### Extended Frame Format
The extended frame format uses identifiers that are 29 bits long (0 .. 0x1FFFFFFF). To construct frames in this format, add the `isExtended` parameter and pass `true`:

```java
CANFrame(int identifier, int[] data, boolean isExtended)
CANFrame(int identifier, boolean isExtended)
```

When setting the `isExtended` parameter to `false` these constructors can also be used to create frames in the basic frame format.

#### Interpretation
Once a frame is received it can be interpreted using the following methods of the `CANFrame` class:

```java
boolean isExtended()
boolean isRTR()
int getIdentifier()
int[] getData()
```

The `isRTR()` method returns true if a `CANFrame` is a remote transfer request carrying no data. The frame format can be determined using the `isExtended()` method. The other methods return the actual frame content.

### Initialization
In order to initialize the core, call the

```java
void init(int bitrate, boolean extendedMode)
```

method setting the mode of the core and passing one of the bitrate constants:

* **CAN.BITRATE_1M** : 1 Mbit/s
* **CAN.BITRATE_250K** : 250 kBit/s
* **CAN.BITRATE_125K** : 125 kBit/s

When the core should be able to transmit frames in the extended format, it has to be initialized in extended mode by passing `true` to the second parameter.

### Frame Filtering
The CAN controller is capable of filtering frames by means of their identifiers. Frames that are filtered will not be stored in the receive buffer and will not cause any interrupts. The filter is configured using acceptance code and acceptance mask which can be set using the following methods:

```java
void setAcceptanceCode(int acceptanceCode)
void setAcceptanceMask(int acceptanceMask)
```

The acceptance mask defines which bits of the acceptance code (or identifier) should be compared to the identifier of an incoming frame. The bits that are set (`1`) in the mask are not used for filtering.

In basic mode, only the eight most significant bits of the 11-bit identifier are compared for filtering. In extended mode, the entire 29-bit identifier is taken into account.

### Communication
After constructing a frame, it can be transmitted using the

```java
void transmit(CANFrame frame)
```

method. If the core is not able to transmit the frame for a certain duration (3 seconds), a `CANException` will be thrown to indicate the timeout.

Once the core has received a frame it can be fetched by calling the method

```java
CANFrame getReceivedFrame()
```

which will return a `CANFrame` instance or null in case there is no frame in the receive buffer.

### Interrupts
These are the most important types of interrupts supported by the core:

* **RECEIVE** : A frame has been received
* **TRANSMIT** : A transmission has been completed
* **DATA_OVERRUN** : An incoming frame has been lost due to a receive buffer overrun

In order to enable or disable a certain interrupt type, call the method

```java
void enableInterrupt(int interruptType, boolean enable)
```

giving one of the interrupt type constants. After the core has issued an interrupt, the method

```
int identifyInterrupt()
```

is used to determine the type of pending interrupt. Note that in case of multiple pending interrupts this method will return the sum of multiple interrupt constants. Thus, the interpretation of the returned values looks as follows:

```java
int interrupts = myCANCore.identifyInterrupt();
if (interrupts & CAN.INT.DATA_OVERRUN) {
    // Handle data overrun interrupt
}
if (interrupts & CAN.INT.RECEIVE) {
    // Handle receive interrupt
}
```
