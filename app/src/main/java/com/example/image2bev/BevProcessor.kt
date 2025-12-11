package com.example.image2bev

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import kotlin.math.tan
import kotlin.math.atan

object BevProcessor {

    // Variables for external access if needed (kept from previous steps)
    var bevScale: Double = 2.0
    var inputScale: Double = 1.0

    /**
     * Transforms the source image to Bird's Eye View.
     * Restricts the view to a specific ground distance.
     *
     * @param src Source image Mat
     * @param pitchDeg Sensor pitch in degrees
     * @param rollDeg Sensor roll in degrees
     * @param cameraHeightMeters Physical height of the camera
     * @param lookAheadMeters Max distance to display (controlled by slider)
     * @return Transformed Mat
     */
    fun toBirdsEye(
        src: Mat,
        pitchDeg: Double,
        rollDeg: Double,
        cameraHeightMeters: Double,
        lookAheadMeters: Double // NEW Parameter
    ): Mat {
        val w = src.cols().toDouble()
        val h = src.rows().toDouble()

        // --- CONFIGURATION ---
        val cameraFovDeg = 60.0
        val pitchOffset = 90.0
        val camPitchRad = Math.toRadians(pitchDeg + pitchOffset)

        // Calculate Focal Length in pixels (Pinhole model)
        val fovRad = Math.toRadians(cameraFovDeg)
        val f = h / (2.0 * tan(fovRad / 2.0))

        // --- SOURCE POINTS CALCULATION ---

        // 1. Angle to the bottom of the visible ground
        val angleToBottomFrame = camPitchRad + (fovRad / 2.0)

        // 2. Angle to the look-ahead point (controlled by slider)
        // tan(angle_down) = height / distance
        val angleToTargetDist = atan(cameraHeightMeters / lookAheadMeters)

        // angle relative to the camera optical axis
        val angleInCamToTarget = camPitchRad - angleToTargetDist

        // y_offset = f * tan(angle)
        val yOffsetTarget = f * tan(angleInCamToTarget)

        // Convert to absolute image coordinates
        val yPixelTarget = (h / 2.0) - yOffsetTarget

        // Safety check: Clamp to image top
        val safeTopY = yPixelTarget.coerceIn(0.0, h - 1.0)

        // Define Trapezoid Source Points
        val srcPoints = ArrayList<Point>()
        srcPoints.add(Point(0.0, h))           // Bottom-Left
        srcPoints.add(Point(w, h))             // Bottom-Right
        srcPoints.add(Point(w, safeTopY))      // Top-Right (at target distance)
        srcPoints.add(Point(0.0, safeTopY))    // Top-Left (at target distance)

        // --- DESTINATION POINTS CALCULATION ---
        val dstPoints = ArrayList<Point>()

        // We map the trapezoid to a rectangle
        dstPoints.add(Point(0.0, h))      // Bottom-Left
        dstPoints.add(Point(w, h))        // Bottom-Right
        dstPoints.add(Point(w, 0.0))      // Top-Right
        dstPoints.add(Point(0.0, 0.0))    // Top-Left

        // --- WARP ---
        val srcMatOfPoint = Converters.vector_Point_to_Mat(srcPoints, CvType.CV_32F)
        val dstMatOfPoint = Converters.vector_Point_to_Mat(dstPoints, CvType.CV_32F)

        val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMatOfPoint, dstMatOfPoint)

        val dst = Mat()
        Imgproc.warpPerspective(src, dst, perspectiveMatrix, Size(w, h))

        return dst
    }
}
