# Improved Dictate Keyboard (Whisper AI transcription)

#### Dictate is an easy-to-use keyboard for transcribing and dictating. This is a rebranded and enhanced version of the original FOSS Dictate app, featuring numerous additions, improvements, and bug fixes. The app uses [OpenAI Whisper](https://openai.com/index/whisper/) in the background, which supports extremely accurate results for [many different languages](https://platform.openai.com/docs/guides/speech-to-text/supported-languages) with punctuation and custom AI rewording using GPT-4 Omni.

This fork is based on the original Dictate repository. This fork includes a lot of major improvements, new features, and bug fixes.
The following sections provide an overview of the key changes and additions made in this enhanced version.

The APK can be downloaded as Debug Build (unsigned) [from here](https://github.com/bjspi/AI-Voice-Keyboard/blob/main/app/build/outputs/apk/debug/app-debug.apk). 

## Key Improvements, Added Features and Bugfixes over original Dictate

- **Transcription capability for Audiofiles**: Added Keyboard into the "Share / SendTo" menu to transcribe audio files from any app (e.g., WhatsApp voice messages) using share menu
- **Automatic Use of Rewording-Prompts**: Added option to automatically use one of the defined custom prompts after each transcription automatically.
  - **Functionality to temporary set Automatic Prompt**: Added option to temporarily toggle one of the user-defined prompts for automatic use for the current Keyboard session (until Keyboard is closed)
- **Live Prompting based on Text**: Added functionality to use the "Live Prompt" (Instant Prompt) based on the current text in the input field. If you click the LivePrompt Button as normal, it will start a recording and use the transcript as input for the prompt. If you make a text-selection and click the button, it will use the selected text in the input field as input for the prompt without starting a recording.
- **Added support for Bluetooth Headsets** : Added support for Bluetooth headsets (e.g., AirPods) as input source (Thanks to [@cuylerstuwe](https://github.com/cuylerstuwe/Dictate/tree/for-pr))
- **Improved Workflow: Stop Recording and switch back**: Added a button to stop recording and return to previous Keyboard (e.g., Gboard) without needing to switch manually
- **Enhanced Prompt Buttons**: Prompt buttons are always visible and intelligently handle text selection - using either existing selection or automatically selecting all text
  - **Pressing prompt buttons during Active Recording**: Pressing prompt buttons during an active recording just toggles this prompt to use after Recording Stop instead of immediately applying it to existing text
- **Fixed Instant Recording**: Resolved issues with instant recording immediately ending in certain apps (e.g., Gemini)
- **GBoard-Style Backspace-Functionality**: Added swipe-capability to backspace button for deleting multiple words at once
- **Import/Export Prompts**: Added feature to import and export user-created prompts/presets
- **Double click on Prompt Button**: Added feature to double-click prompt buttons in the Keyboard UI to edit them directly
- **Improved Custom Characters with Emoji Support**: Smiley support added to "input custom characters" with improved limit and styling
- **Better Style for Recording**: Added a more modern and appealing style during Recording to notice instantly when recording is active
- **Smart Transcription Flow**: After finishing transcription, pressing send buttons (e.g., in WhatsApp) no longer triggers instant recording again
- **Better Logging**: Enhanced logging for more effective ADB debugging

## Showcase

Since a picture is worth a thousand words, here is a showcase video and some screenshots:

| <a href='https://youtube.com/watch?v=F6C1hRi1PSI'><img src='https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_player.png?raw=true'/></a> | ![dictate_keyboard_notes_recording.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_keyboard_notes_recording.png?raw=true) | ![dictate_settings.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_settings.png?raw=true) |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| ![dictate_settings_2.png](https://github.com/DevEmperor/Dictate/blob/58fd05bad9b33a91efb51a9506f6d9bf6310ad5b/img/dictate_settings_2.png?raw=true) | ![dictate_prompts_overview.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_prompts_overview.png?raw=true) | ![dictate_prompts_edit.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_prompts_edit.png?raw=true) |


## License

Original Dictate was released under the terms of the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0), following all clarifications stated in the [license file](https://raw.githubusercontent.com/DevEmperor/Dictate/master/LICENSE)