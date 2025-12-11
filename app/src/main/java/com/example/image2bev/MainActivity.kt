package com.example.image2bev

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.image2bev.databinding.ActivityMainBinding
import com.example.image2bev.BevProcessor
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.util.concurrent.Executors
import kotlin.math.tan

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // --- Sensor Variables ---
    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Live values
    private var currentPitch: Double = 0.0
    private var currentRoll: Double = 0.0

    // --- Configuration Variables ---
    private var cameraHeightMeters: Double = 1.5
    private var lookAheadDistanceMeters: Double = 10.0

    // --- Measurement Variables ---
    private var startPoint: android.graphics.Point? = null
    private var endPoint: android.graphics.Point? = null

    // --- UI State Variables ---
    private var isSwapped = false
    private var isInfoVisible = true // Track visibility state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Initialize OpenCV
        if (OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV loaded successfully")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV failed to load", Toast.LENGTH_LONG).show()
        }

        setupTouchListeners()
        setupSliders()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupSliders() {
        // 1. Height Slider Setup
        binding.heightSlider.valueFrom = 0.5f
        binding.heightSlider.valueTo = 20.0f
        binding.heightSlider.value = cameraHeightMeters.toFloat().coerceIn(0.5f, 20.0f)

        binding.heightSlider.addOnChangeListener { slider, value, fromUser ->
            cameraHeightMeters = value.toDouble()
        }

        // 2. Length Slider Setup
        binding.lengthSlider.valueFrom = 5.0f
        binding.lengthSlider.valueTo = 30.0f
        binding.lengthSlider.value = lookAheadDistanceMeters.toFloat().coerceIn(5.0f, 30.0f)

        binding.lengthSlider.addOnChangeListener { slider, value, fromUser ->
            lookAheadDistanceMeters = value.toDouble()
        }
    }

    private fun setupTouchListeners() {

        // --- 1. TOGGLE INFO BUTTON ---
        binding.btnToggleInfo.setOnClickListener {
            isInfoVisible = !isInfoVisible
            if (isInfoVisible) {
                binding.infoContainer.visibility = View.VISIBLE
                binding.btnToggleInfo.alpha = 1.0f // Fully opaque when active
            } else {
                binding.infoContainer.visibility = View.GONE
                binding.btnToggleInfo.alpha = 0.5f // Semi-transparent when hidden
            }
        }

        // --- 2. SWAP Logic ---
        val swapAction = View.OnClickListener {
            isSwapped = !isSwapped

            if (isSwapped) {
                Toast.makeText(this, "Swapped: Main View is now BEV", Toast.LENGTH_SHORT).show()
                binding.viewFinder.visibility = View.INVISIBLE
                binding.mainImageView.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "Standard: Main View is Camera", Toast.LENGTH_SHORT).show()
                binding.viewFinder.visibility = View.VISIBLE
                binding.mainImageView.visibility = View.INVISIBLE
            }
        }

        binding.viewFinder.setOnClickListener(swapAction)
        binding.mainImageView.setOnClickListener(swapAction)

        // --- 3. MEASUREMENT Logic ---
        binding.bevOverlay.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()

                if (startPoint == null) {
                    startPoint = android.graphics.Point(x, y)
                    Toast.makeText(this, "Start Point Set. Tap End Point.", Toast.LENGTH_SHORT).show()
                } else {
                    endPoint = android.graphics.Point(x, y)
                    val dist = calculateRealDistance(startPoint!!, endPoint!!)
                    val resultMsg = "Distance: %.2f m".format(dist)
                    Toast.makeText(this, resultMsg, Toast.LENGTH_LONG).show()
                    startPoint = null
                    endPoint = null
                }
                true
            } else {
                false
            }
        }
    }

    private fun calculateRealDistance(p1: android.graphics.Point, p2: android.graphics.Point): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val pixelDistance = Math.sqrt((dx * dx + dy * dy).toDouble())

        // Calculate visible width logic
        val fovYRad = Math.toRadians(60.0)
        val aspectRatio = 4.0 / 3.0
        val fovXRad = 2.0 * Math.atan(Math.tan(fovYRad / 2.0) * aspectRatio)
        val visibleWidthMeters = 2.0 * cameraHeightMeters * Math.tan(fovXRad / 2.0)

        val viewWidthPixels = binding.bevOverlay.width.toDouble()
        val metersPerPixel = visibleWidthMeters / viewWidthPixels

        return pixelDistance * metersPerPixel
    }

    override fun onResume() {
        super.onResume()
        rotationVector?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            currentPitch = Math.toDegrees(orientationAngles[1].toDouble())
            currentRoll = Math.toDegrees(orientationAngles[2].toDouble())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                        runOnUiThread {
                            val bitmap = binding.viewFinder.bitmap
                            if (bitmap != null) {
                                processImageAndDisplay(bitmap, currentPitch, currentRoll)
                            }
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageAndDisplay(srcBitmap: Bitmap, pitch: Double, roll: Double) {

        // --- 1. Update Text (Only if visible) ---
        if (isInfoVisible) {
            val fovYRad = Math.toRadians(60.0)
            val aspectRatio = srcBitmap.width.toDouble() / srcBitmap.height.toDouble()
            val fovXRad = 2.0 * Math.atan(Math.tan(fovYRad / 2.0) * aspectRatio)
            val visibleWidthMeters = 2.0 * cameraHeightMeters * Math.tan(fovXRad / 2.0)
            val visibleLengthMeters = lookAheadDistanceMeters

            val infoText = """
                Pitch: %.1f° | Roll: %.1f°
                Height: %.2fm
                BEV Area: %.1fm (W) x %.1fm (L)
            """.trimIndent().format(pitch, roll, cameraHeightMeters, visibleWidthMeters, visibleLengthMeters)

            binding.tvSensorInfo.text = infoText
        }

        // --- 2. OpenCV Processing ---
        val srcMat = Mat()
        Utils.bitmapToMat(srcBitmap, srcMat)

        val bevMat = BevProcessor.toBirdsEye(srcMat, pitch, roll, cameraHeightMeters, lookAheadDistanceMeters)

        val bevBitmap = Bitmap.createBitmap(bevMat.cols(), bevMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(bevMat, bevBitmap)

        if (isSwapped) {
            binding.mainImageView.setImageBitmap(bevBitmap)
            binding.bevOverlay.setImageBitmap(srcBitmap)
        } else {
            binding.bevOverlay.setImageBitmap(bevBitmap)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
