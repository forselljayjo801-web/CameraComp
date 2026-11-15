package com.example.composeshot

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var hintText: TextView
    private lateinit var scoreText: TextView
    private lateinit var autoZoom: Switch
    private lateinit var autoShot: Switch

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var smoothedZoom: Float? = null
    private var stableFrames = 0
    private var lastShotAt = 0L

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else {
            Toast.makeText(this, "Camera permission needed", Toast.LENGTH_LONG).show(); finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        overlay = OverlayView(this)
        root.addView(previewView, MATCH_PARENT, MATCH_PARENT)
        root.addView(overlay, MATCH_PARENT, MATCH_PARENT)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 96, 24, 0)
        }
        hintText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            text = "Point at a subject"; setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        scoreText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        top.addView(hintText); top.addView(scoreText)
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 0, 24, 64)
        }
        val switches = LinearLayout(this).apply { gravity = Gravity.CENTER }
        autoZoom = Switch(this).apply { text = "Auto-zoom  "; setTextColor(Color.WHITE); isChecked = true }
        autoShot = Switch(this).apply { text = "  Auto-shoot"; setTextColor(Color.WHITE) }
        switches.addView(autoZoom); switches.addView(autoShot)
        val shutter = Button(this).apply { text = "●  Capture"; setOnClickListener { takePhoto() } }
        bottom.addView(switches); bottom.addView(shutter)
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        return root
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val res = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder().setResolutionSelector(res).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setResolutionSelector(res)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val analysis = ImageAnalysis.Builder().setResolutionSelector(res)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                .also { it.setAnalyzer(analysisExecutor, SubjectAnalyzer(::onSubject)) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    /** Runs on an ML Kit callback thread; hop to main for UI + camera control. */
    private fun onSubject(subject: android.graphics.RectF?) {
        runOnUiThread {
            val cam = camera ?: return@runOnUiThread
            val z = cam.cameraInfo.zoomState.value ?: return@runOnUiThread
            val result = CompositionEngine.evaluate(subject, z.zoomRatio, z.minZoomRatio, z.maxZoomRatio)
            overlay.result = result
            hintText.text = result.hint
            scoreText.text = if (subject == null) "" else "Composition ${result.score}/100"

            if (autoZoom.isChecked) {
                val target = result.targetZoom
                if (target != null) {
                    val cur = smoothedZoom ?: z.zoomRatio
                    val next = cur + (target - cur) * 0.15f
                    smoothedZoom = next
                    cam.cameraControl.setZoomRatio(next)
                } else smoothedZoom = null
            }

            stableFrames = if (result.score >= 85) stableFrames + 1 else 0
            val now = System.currentTimeMillis()
            if (autoShot.isChecked && stableFrames >= 12 && now - lastShotAt > 4000) {
                lastShotAt = now; stableFrames = 0; takePhoto()
            }
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ComposeShot_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ComposeShot")
        }
        val out = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        capture.takePicture(out, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Saved to Pictures/ComposeShot", Toast.LENGTH_SHORT).show()
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Capture failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    override fun onDestroy() { super.onDestroy(); analysisExecutor.shutdown() }
}
