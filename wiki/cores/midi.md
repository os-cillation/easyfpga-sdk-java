# MIDI
The MIDI core is a wrapper around the [UART](uart.md) core specialized for the MIDI protocol which is used for the communication between music-related devices. MIDI uses a constant baudrate of 31250 bps and does not incorporate any flow control.

## Pins
* **MIDI_IN** : MIDI input
* **MIDI_OUT** : MIDI output

## Usage
The interaction with the MIDI core uses a class named `MIDIMessage`.

### MIDI Messages
A MIDI message has one of the following types:

* **NOTE_OFF** : Note is released
* **NOTE_ON** : Note is pressed
* **POLYPHONIC_KEY_PRESSURE** : Aftertouch (Polyphonic) 
* **CONTROL_CHANGE** : Controller value changed
* **PROGRAM_CHANGE** : Patch number changed
* **CHANNEL_PRESSURE** : Aftertouch (single greatest pressure value)
* **PITCH_BEND_CHANGE** : Pitch bender changed

The `MIDIMessage` class has an internal enum named `MessageType` that contains the possible types. For more information on purpose and composition of MIDI messages, refer to the [message specification](http://www.midi.org/techspecs/midimessages.php) by the MIDI Manufacturers Association.

There are three constructors for creating instances of MIDIMessage, two of them incorporate a type and one creates a MIDI message from a raw integer array:

```java
MIDIMessage(MessageType type, int channel, int key, int velocity) 
MIDIMessage(MessageType type, int channel, int data)
MIDIMessage(int[] rawMessage)
```

The first constructor is for creating messages of the following types: NOTE_OFF, NOTE_ON, POLYPHONIC_KEY_PRESSURE and CONTROL_CHANGE. The key parameter is the key pressed (0 .. 127) except for CONTROL_CHANGE messages: Here the key parameter refers to the controller number (0 .. 119) and the velocity parameter is the controller value.

The three-parameter constructor is meant for creating message of the types PROGRAM_CHANGE, CHANNEL_PRESSURE and PITCH_BEND_CHANGE. For the first two types, the data parameter contains the patch number or the greatest pressure value (0 .. 127). For PITCH_BEND_CHANGE messages, the data parameter contains 14 bits (0 .. 0x4000) giving the value of a pitch bender that usually works in two directions. Thus the value 0x2000 refers to the neutral position.

Both constructors have a parameter to select the channel of the message (1 .. 16).

### Communication
Once a message has been created, the methods of the MIDI core can be used for communication:

```java
void transmit(MIDIMessage message)
MIDIMessage receive()
```

Use the getter methods of the `MIDIMessage` class on the instance returned by the `receive()` method to get and process the content of a received message.

### Interrupts
The interrupt capability of this core is limited to a single interrupt generated on the reception of a message. It can be activated using the

```java
void enableInterrupt()
```

method.
