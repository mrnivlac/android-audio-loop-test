# Android Audio Loop Test
This app is designed for the purpose of testing if a device is suitable to be used in telecon/telemedicine applications.
It uses the speaker and mic of the device and relies on the user's hearing.

## How it works
The app will generate a random sequence of numbers and speak them out while using the mic to record. 
After this, the user needs to listen to the playback of the recording. 
The user will then type in the numbers he/she is able to comprehend. 
The app will then check if the numbers inputted match the sequence it generated in the beginning.

## User Guide
1. Tap in Record. A countdown will be shown. At the end of the countdown, the app will begin speaking. The time duration in seconds will be shown.
2. Once done, the Play button will be enabled.
3. Tap Play.
4. Listen to the numbers.
5. Type the numbers into the text field labelled "sequence". You can listen again by tapping Play for a maximum of 3 times.
6. Tap Sequence.
7. If the inputted sequence matches the internally generated sequence, "PASSED" is displayed. Otherwise, "FAILED". The chack can only be done once.

At any point the user can tap Reset. A new sequence will be generated.

## Logfile and Recording
User actions (such as tapping the record button) are logged along with the time of the action.
The recording of the latest test is also kept.

The logfile can be opened by tapping Open Logfile. This launches a view intent with a content URI.

The logfile and recording can be extracted by tapping SHare Log & Test Files. This launches a share intent with content URIs for both files

## Audio Samples
This app uses samples from [Aspect](https://evolution.voxeo.com/library/audio/prompts/) licensed under LGPL

## License
todo