# Improved Dictate Keyboard (Whisper AI transcription)

#### Dictate is an easy-to-use keyboard for transcribing and dictating. This is a rebranded and enhanced version of the original FOSS Dictate app, featuring numerous additions, improvements, and bug fixes. The app uses [OpenAI Whisper](https://openai.com/index/whisper/) in the background, which supports extremely accurate results for [many different languages](https://platform.openai.com/docs/guides/speech-to-text/supported-languages) with punctuation and custom AI rewording using GPT-4 Omni.

This fork is based on the original Dictate repository. This fork includes a lot of major improvements, new features, and bug fixes.
The following sections provide an overview of the key changes and additions made in this enhanced version.

The APK can be downloaded as Debug Build (unsigned) [from here](https://github.com/bjspi/AI-Voice-Keyboard/blob/main/app/build/outputs/apk/debug/app-debug.apk). 

## Key Improvements, Added Features and Bugfixes over original Dictate

- **Transcription via SendTo / Share**: Added Keyboard into the "Share / SendTo" menu to transcribe audio files from any app (e.g., WhatsApp voice messages) using share menu
- **Better API Prompt control**: The original APP doesn't make use of System Prompts AND adds a custom prompt to each request, which can lead to unexpected results. See the original code for the constant PROMPT_REWORDING_BE_PRECISE. This can get confusing for the API because it could be written in a different language than your prompt. This version allows to set your defined Prompts as System Prompt and the input text (i.e. your transcription) is sent as user message. This way, the API has a much better understanding of what you want to achieve. Removed the used of PROMPT_REWORDING_BE_PRECISE, because all instructions shall be part of your own definitions.
- **Automation of Rewording-Prompts**: Added option to automatically use one of the defined custom prompts after each transcription automatically.
- **Quickselection of Temporary Rewording Prompt**: Added option to temporarily toggle one of the user-defined prompts for immediate use for the current Keyboard session (until Keyboard is closed)
- **Live/Instant Prompting based on Textselection**: Added functionality to use the "Live Prompt" (Instant Prompt) based on the current text in the input field. If you click the LivePrompt Button as normal, it will start a recording and use the transcript as input for the prompt. If you make a text-selection and click the button, it will use the selected text in the input field as input for the prompt without starting a recording.
- **Added support for Bluetooth Headsets** : Added support for Bluetooth headsets (e.g., AirPods) as input source (Thanks to [@cuylerstuwe](https://github.com/cuylerstuwe/Dictate/tree/for-pr))
- **Improved Workflow: Stop Recording & switch back**: Added a button to stop recording and return to previous Keyboard (e.g., Gboard) without needing to switch manually
- **Enhanced Prompt-Buttons UI**: Prompt buttons are always visible and intelligently handle text selection - using either existing selection or automatically selecting all text
  - **Pressing prompt buttons during Active Recording**: Pressing prompt buttons during an active recording just toggles this prompt to use after Recording Stop instead of immediately applying it to existing text
  - **Longpressing prompt buttons**: Longpressing prompt button without active recording selects this prompt for immediate use and starts a recording
  - **Double click on Prompt Button**: Double-click prompt buttons opens the edit-dialogue directly (quick access to edit prompts)
- **Fixed Instant Recording**: Resolved issues with instant recording immediately ending in certain apps (e.g., Gemini) by adding a minimal initialization delay (to avoid RACE conditions)
- **GBoard-like functionality:**
  - **Backspace-Key**: Added swipe-capability to delete multiple words in one go
  - **Shift-Key**: Pressing the button will toggle between lower, camel and upper case for the selected text or word at cursor position
- **Import/Export Prompts**: Added feature to import and export user-created prompts/presets
- **Custom Characters with Emoji Support**: Smiley support added to "input custom characters" with improved limit and styling
- **Better UI while Recording**: Added a more modern and appealing style during Recording to notice instantly when recording is active
- **Function to Play last Recording**: Added button in Preferences to play the previously recorded audio (mostly for debugging purposes in case of transcription issues)
- **Smart Transcription Flow**: After finishing transcription, pressing send buttons (e.g., in WhatsApp) no longer triggers instant recording again
- **Improved Logging**: Enhanced logging for more effective ADB debugging of prompts and API calls

## Showcase

Since a picture is worth a thousand words, here is a showcase video and some screenshots:

| <a href='https://youtube.com/watch?v=F6C1hRi1PSI'><img src='https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_player.png?raw=true'/></a> | ![dictate_keyboard_notes_recording.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_keyboard_notes_recording.png?raw=true) | ![dictate_settings.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_settings.png?raw=true) |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| ![dictate_settings_2.png](https://github.com/DevEmperor/Dictate/blob/58fd05bad9b33a91efb51a9506f6d9bf6310ad5b/img/dictate_settings_2.png?raw=true) | ![dictate_prompts_overview.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_prompts_overview.png?raw=true) | ![dictate_prompts_edit.png](https://github.com/DevEmperor/Dictate/blob/624fde1cbc8e29fdb77f334f3edfa6231d27df82/img/dictate_prompts_edit.png?raw=true) |


## License

Original Dictate was released under the terms of the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0), following all clarifications stated in the [license file](https://raw.githubusercontent.com/DevEmperor/Dictate/master/LICENSE)