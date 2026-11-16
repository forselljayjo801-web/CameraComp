package com.example.composeshot

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SensorEventListener {

    companion object {
        /** Third-stop apertures from f/1.4 to f/25. */
        val APERTURES = listOf(
            1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3,
            7.1, 8.0, 9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0
        )
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var hintText: TextView
    private lateinit var scoreText: TextView
    private lateinit var triangleText: TextView
    private lateinit var apertureLabel: TextView
    private lateinit var apertureBar: SeekBar
    private lateinit var manualCheck: CheckBox
    private lateinit var evInput: EditText
    private lateinit var autoZoom: Switch
    private lateinit var autoShot: Switch
    private lateinit var exposureBar: SeekBar
    private lateinit var timerButton: Button
    private lateinit var shutter: Button
    private lateinit var triangleButton: Button

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var sensors: SensorManager? = null
    private var countdown: CountDownTimer? = null
    private var timerSeconds = 0

    private val sceneMeter = SceneMeter()
    private var sceneEv: Double? = null
    private var triangleOn = false
    private var aperture = 1.8

    private var smoothedZoom: Float? = null
    private var stableFrames = 0
    private var lastShotAt = 0L

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else {
            showError("Camera permission is off. Turn it on in Settings, then Apps, then ComposeShot.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(buildUi())
            sensors = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) startCamera()
            else permission.launch(Manifest.permission.CAMERA)
        } catch (e: Throwable) {
            showError("Startup error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Shows the problem on screen instead of closing the app. */
    private fun showError(message: String) {
        val box = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(120, 0, 0))
            setPadding(32, 32, 32, 32)
            textSize = 16f
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(box, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        })
    }

    override fun onResume() {
        super.onResume()
        sensors?.let { s ->
            s.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                s.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rm = FloatArray(9)
        val orient = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rm, event.values)
        SensorManager.getOrientation(rm, orient)
        overlay.levelAngleDeg = -Math.toDegrees(orient[2].toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        overlay = OverlayView(this)
        root.addView(previewView, MATCH_PARENT, MATCH_PARENT)
        root.addView(overlay, MATCH_PARENT, MATCH_PARENT)

        previewView.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) focusAt(e.x, e.y)
            true
        }

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
        triangleText = TextView(this).apply {
            setTextColor(Color.rgb(255, 204, 0)); textSize = 14f
            gravity = Gravity.CENTER; setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        top.addView(hintText); top.addView(scoreText); top.addView(triangleText)
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
