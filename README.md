# BLE apps
## BLE Client
The mobile application is designed to send and receive data using BLE protocol. It interacts with a BLE device, sending and receiving data in blocks of 160 bytes with a 60 ms delay between blocks.

### Features

- **Bluetooth Activation:** Ensures that Bluetooth is enabled before attempting to connect to a BLE device.
- **Location Services Activation:** Ensures that location services are enabled.
- **Connecting to BLE Device:** Establishes a connection with the BLE device and discovers available services and characteristics.
- **Data Transmission:** Splits data into blocks of 160 bytes and transmits them to the server application with a 60 ms delay between blocks. The client is capable of transmitting multi-byte characters, allowing for the transfer of more complex data structures..
- **Data Reception:** Receives data from the server application, displays the received data, and processes it in the user interface.


## BLE Server
The server application acts as a BLE device, handling data reception and transmission using BLE protocol. It manages data transfer in blocks of 160 bytes with a delay of 60 ms.

### Features

- **Server Activation:** Initializes and starts the BLE server, enabling it to begin advertising and handling connections.
- **Server Deactivation:** Stops the BLE server, disables advertising, and cleans up resources.
- **Data Reception:** Receives data from the mobile application and processes it for further use.
- **Data Transmission:** Sends data to the mobile application in blocks of 160 bytes with a delay of 60 ms. The server is capable of transmitting multi-byte characters, allowing for the transfer of more complex data structures.
