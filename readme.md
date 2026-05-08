<p align="left">
  <img src="ic_launcher.png" alt="Project Icon" width="10%">
</p>

## Serif - Custom launcher for my eink tablet running android

<div align="center">
<div style="display:flex; justify-content:center; gap:10px;">
  <img src="media/screenshot_1.png" width="45%" style="margin-right: 10px;">
  <img src="media/screenshot_2.png" width="45%">
</div>
</div>

Minimalist Android launcher designed for e-ink tablets. Focused on reading, scheduling, and a clean aesthetic that minimizes screen refreshes.

### Core Features

- Dashboard with time, date, and weather updates.
- Google Calendar sync with daily event lists and day-by-day filtering.
- PDF and Book library for quick access to reading material.
- Integrated drawing layer on the calendar for handwritten notes.
- Customizable app dock supporting up to 10 shortcuts.
- Automated theme switching between light and dark modes based on time.

### Configuration

Settings are managed through the gear icon in the app dock.

#### Google Calendar Integration

To sync your calendar, you must use your own Google OAuth2 Client ID.

1. Create a project in the Google Cloud Console.
2. Set up an OAuth 2.0 Client ID for Android. This requires the SHA-1 fingerprint of your signing certificate.
3. Sign the application with your own keystore and recompile it using Android Studio.
4. Enter the Client ID in the System tab of the Serif settings.
5. Tap the calendar widget on the home screen to initiate the login.

#### Library and Reading Material

The launcher automatically indexes PDF and EPUB files stored on the device.

- The Recent PDFs section provides a grid of your latest documents.
- The Library section displays books in a horizontal list from a directory it creates called `snes_library` (because this is a personal project).
- Custom covers can be applied to books by long-pressing them in the library.

#### Customization Options

- Wallpaper: Upload a header image via Appearance settings; images are automatically greyscaled.
- Photo Widget: Select a personal image for display on the dashboard.
- App Dock: Modify the list of docked apps in the Dock & Widget settings. Includes options for compact layout and icon labels.
- Greeting: Update the name used in the dashboard greeting within System settings.

### E-ink Optimization

- Ink-saving mode: Reduces border weight to minimize ghosting and refresh requirements.
- Greyscale UI: All icons and images are processed for high contrast and greyscale to suit electronic paper displays.
- Zero-animation interface: Transitions are kept static to prevent unnecessary screen updates.

### Notes:

- Requires "All Files Access" permission to manage and open library documents.
- The drawable calendar widget is still buggy
- This is a clean start for the project. I had to scrap the old repo after accidentally committing API keys and environment variables.
