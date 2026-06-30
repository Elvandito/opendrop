# OpenDrop

OpenDrop is an open-source, local network file and message sharing application for Android devices, designed as a secure alternative to AirDrop. It allows direct device-to-device communication over Wi-Fi without relying on an internet connection or external servers.

## Features
- **Local Network Discovery**: Automatically discover nearby devices using Network Service Discovery (NSD).
- **Direct File Transfer**: Send and receive files directly over local Wi-Fi.
- **Messaging & Clipboard Sync**: Send text messages and sync clipboard data between devices.
- **Voice Calling**: Initiate secure local network voice calls between devices.
- **Background Support**: Transfers and calls run reliably in the background.
- **No Internet Required**: All communication happens strictly over the local network.

## Requirements
- Android 6.0 (API level 23) or higher
- Both devices must be connected to the same Wi-Fi network

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Elvandito/OpenDrop.git
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync and download dependencies.
4. Run the app on a physical device or emulator.

## Usage
1. Open the app on two Android devices connected to the same Wi-Fi network.
2. Grant the required permissions (Storage, Notifications, Microphone).
3. Select the target device from the list of discovered peers.
4. Choose to send a message, transfer a file, or initiate a call.

