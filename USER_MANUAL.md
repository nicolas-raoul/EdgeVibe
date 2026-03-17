# EdgeVibe User Manual

## Goal
EdgeVibe is an on-device AI-powered tool that allows you to generate single-file HTML web applications directly on your Android phone using Gemini Nano. It converts natural language descriptions into functional web apps that can be previewed instantly, saved with custom names, and reopened later.

## Main Activities

### 1. Describing your Webapp
- When you launch the app, you are presented with a "Describe your webapp" screen.
- **Input:** Type a detailed or simple description of the webapp you want to create.
- **Example:** "A random addition quiz with a check button".

### 2. Generating the Webapp
- Press the **"Generate webapp"** button.
- The app uses local Gemini Nano to generate the HTML. This might take 10-20 seconds.
- A loading indicator will be shown during the process.

### 3. Previewing and Inspecting
Once generated, you can switch between four views using the tabs at the top:
- **App:** Interact with your generated webapp in the built-in viewer.
- **Prompt:** See the original description used for generation.
- **HTML:** View the raw HTML source code.
- **Errors:** See any JavaScript console errors that occurred while running the app. A badge will show the total number of errors detected.

### 4. Copying Content
In the **Prompt**, **HTML**, and **Errors** tabs, a **Copy** icon is available in the action bar to quickly copy the displayed text to your clipboard.

### 5. Saving your Work
- Press the **Save** icon in the action bar.
- Gemini Nano will suggest a name based on your prompt.
- You can edit this name in the dialog that appears.
- Both the HTML and the prompt are saved together.

### 6. Opening Previous Webapps
- On the home screen, press the **Folder** icon in the top bar.
- A list of your saved webapps will appear.
- Select any webapp to open it instantly.

### 7. Retrying
- Press the **Back** arrow in the action bar to return to the prompt screen and try a different description.

## Privacy
- All AI generation and file storage happens locally on your device. Your data remains private.
