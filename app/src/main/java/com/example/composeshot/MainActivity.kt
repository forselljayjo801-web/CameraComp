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
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var hintText: TextView
    private lateinit var scoreText: TextView
    private lateinit var autoZoom: Switch
    private lateinit var autoShot: Switch
    private lateinit var exposureBar: SeekBar
    private lateinit var timerButton: Button
    private lateinit var shutter: Button

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private lateinit var sensors: SensorManager
    private var countdown: CountDownTimer? = null
    private var timerSeconds = 0

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
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        sensors.registerListener(this, sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
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
        top.addView(hintText); top.addView(scoreText)
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 0, 24, 64)
        }
        exposureBar = SeekBar(this).apply {
            max = 100; progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) applyExposure(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val label = TextView(this).apply { text = "Exposure"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }

        val switches = LinearLayout(this).apply { gravity = Gravity.CENTER }
        autoZoom = Switch(this).apply { text = "Auto-zoom  "; setTextColor(Color.WHITE); isChecked = true }
        autoShot = Switch(this).apply { text = "  Auto-shoot"; setTextColor(Color.WHITE) }
        switche
