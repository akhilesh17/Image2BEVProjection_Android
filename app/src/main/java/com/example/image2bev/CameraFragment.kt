package com.example.image2bev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.image2bev.databinding.FragmentCameraBinding

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var bevScale = 2.0
    private var inputScale = 1.0

    companion object {
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val PERMISSION_REQUEST = 101
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSliders()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(CAMERA_PERMISSION), PERMISSION_REQUEST)
        }
    }

    // -------------------------------
    // PERMISSIONS
    // -------------------------------
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    // -------------------------------
    // SLIDERS
    // -------------------------------
    private fun setupSliders() {

        // BEV Scale slider (1.0 to 5.0)
        binding.bevScaleSeek.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                bevScale = (progress + 100) / 100.0   // min 1.0
                BevProcessor.bevScale = bevScale
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Input scale slider (0.5 to 1.5)
        binding.inputScaleSeek.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                inputScale = 0.5 + (progress / 200.0)
                BevProcessor.inputScale = inputScale
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    // -------------------------------
    // CAMERA SETUP (Preview Only)
    // -------------------------------
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Clear previous use cases
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview
                )

            } catch (e: Exception) {
                Log.e("CameraFragment", "Camera start failed: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // -------------------------------
    // CLEANUP
    // -------------------------------
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
