# Image to Bird's-Eye-View (BEV) Generator

This is an Android application that generates a Bird's-Eye-View (BEV) perspective from the device's camera feed in real-time. The transformation is augmented by data from the device's orientation sensors to provide a more accurate representation.

## Features

*   **Real-time Camera Feed**: Utilizes `CameraX` to display a live preview from the device's camera.
*   **Bird's-Eye-View (BEV) Overlay**: Renders a BEV transformation of the camera image in a corner of the screen.
*   **Sensor-driven Transformation**: Uses device orientation sensors (likely accelerometer and magnetometer/gyroscope) to adjust the BEV projection.
*   **Interactive Controls**:
    *   Adjust the assumed **height** of the camera from the ground plane via a slider.
    *   Adjust the forward **distance** (length) of the BEV projection.
    *   Toggle the visibility of the sensor information and control panel.
*   **Dynamic UI**: The user interface is built with modern Android components like `ConstraintLayout`, `CardView`, and Material Design components for a clean and responsive experience.

## Project Structure

The project seems to follow a standard Android application structure. Key components identified from the layout file (`activity_main.xml`) include:

*   `androidx.camera.view.PreviewView`: Displays the live camera feed.
*   `ImageView` (`mainImageView`): A display area, possibly for showing a static BEV image when swapped with the live feed.
*   `ImageView` (`bevOverlay`): Shows the generated BEV image as an overlay.
*   `TextView` (`tvSensorInfo`): Displays real-time data from device sensors.
*   `com.google.android.material.slider.Slider`: Sliders for controlling the height and distance parameters for the BEV transformation.
*   `com.google.android.material.floatingactionbutton.FloatingActionButton`: Toggles the visibility of the information and controls panel.

## Dependencies

Based on the project files, this app likely uses the following major libraries:

*   **AndroidX**: Core Android libraries, including `ConstraintLayout`, `CardView`, and `AppCompat`.
*   **Google Material Components**: For modern UI elements like sliders and floating action buttons.
*   **CameraX**: For simplified camera operations.
*   **OpenCV**: For image processing and performing the perspective transformation to generate the BEV image.

## How to Build

1.  Clone the repository.
2.  Make sure you have a `local.properties` file with the correct `sdk.dir` path.
3.  If you are using Firebase or other Google services, place your `google-services.json` file in the `app/` directory.
4.  Open the project in Android Studio.
5.  Build and run the application on a physical Android device to test camera and sensor functionality.

## How It Works

The application captures frames from the camera. For each frame, it reads the latest sensor data to understand the phone's pitch, roll, and yaw. This orientation data, along with user-defined height and distance parameters, is used to calculate a perspective transformation matrix. OpenCV likely applies this matrix to the input image to warp it into a top-down, bird's-eye view, which is then displayed on the `bevOverlay`.
