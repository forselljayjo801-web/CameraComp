# ComposeShot

A camera app for Android that helps you frame better photos and shows suggested DSLR-style exposure settings.

Made by Yves Sidney Valeriano.

## Features

- **Composition guidance:** a rule-of-thirds grid, subject detection (face first, then object), and live hints such as "Zoom in" or "Turn camera left".
- **Composition scores:** a 0–100 score for how well the subject is placed.
- **Level line:** a centre line that turns green when the phone is flat.
- **Tap to focus:** tap the preview to focus and set exposure at that point.
- **Exposure slider:** brighten or darken the image.
- **Timer:** Off, 3 seconds, or 10 seconds before a photo.
- **Auto-zoom and auto-shoot:** optional, based on the composition score.
- **Auto exposure triangle:** suggests aperture, shutter speed, and ISO for the scene, with an aperture slider from f/1.4 to f/25 and an option to type your own meter reading (EV).
- **Photo quality:** highest available resolution, maximum JPEG quality, and high-quality noise reduction and sharpening.

## Notes on the exposure triangle

- The suggested settings are a planning guide. Phone main cameras have a fixed aperture, and most phones don't let apps set shutter speed or ISO, so the phone's own auto exposure still takes the photo.
- Without a meter reading, brightness is estimated from the preview and is rough.

## Installing

1. Go to the **Actions** tab and open the newest green build.
2. Under **Artifacts**, download **ComposeShot-apk** and extract `app-debug.apk`.
3. Open the APK on your Android phone and allow installs from this source if asked.

Your phone will warn that the app is from an unknown developer, because it isn't on the Play Store. This is expected for a self-built app.

## Permissions

- **Camera:** to show the preview and take photos.
- **Motion sensors:** to read the phone's tilt for the level line. No other permissions are used.

## Building it yourself

The project builds with GitHub Actions. Each push runs the workflow in `.github/workflows/build.yml` and produces the APK.

To build locally, open the project in Android Studio and press **Run**.

## Project structure

- `MainActivity.kt`: camera screen, controls, and photo saving.
- `OverlayView.kt`: grid, subject box, level line, and focus ring.
- `SubjectAnalyzer.kt`: finds the main subject with ML Kit.
- `CompositionEngine.kt`: rule-of-thirds scoring and hints.
- `CompositionTechniques.kt`: centre, golden ratio, fill-the-frame, and negative-space scores.
- `HorizonTechnique.kt`: level scoring.
- `ExposureTriangle.kt`: aperture, shutter, and ISO calculation.
- `SceneMeter.kt`: rough brightness estimate from the preview.

## Limitations

- Phone cameras can't be set to a true manual aperture, shutter, or ISO through this app.
- Composition scores use the subject box only. Techniques such as leading lines and symmetry aren't detected.
- Built as a personal project, not tested on every Android model.

## License

All rights reserved. Please ask the author before reusing this code.
