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
            setPadding(24, 0, 24, 64)
        }

        apertureLabel = TextView(this).apply {
            text = "Aperture f/1.8"; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        apertureBar = SeekBar(this).apply {
            max = APERTURES.size - 1
            progress = APERTURES.indexOf(1.8)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    aperture = APERTURES[p]
                    apertureLabel.text = "Aperture f/${APERTURES[p]}"
                    updateTriangle()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        manualCheck = CheckBox(this).apply {
            text = "Use my meter reading"; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, _ -> updateTriangle() }
        }
        evInput = EditText(this).apply {
            hint = "EV at ISO 100 (e.g. 12)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            gravity = Gravity.CENTER
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { updateTriangle() }
            })
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
        triangleButton = Button(this).apply {
            text = "Auto: Off"
            ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_auto_triangle)
                ?.let { setCompoundDrawablesWithIntrinsicBounds(it, null, null, null) }
            setOnClickListener {
                triangleOn = !triangleOn
                text = if (triangleOn) "Auto: On" else "Auto: Off"
                updateTriangle()
            }
        }
        switches.addView(autoZoom); switches.addView(autoShot)

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        timerButton = Button(this).apply { text = "Timer: Off"; setOnClickListener { cycleTimer() } }
        shutter = Button(this).apply { text = "●  Capture"; setOnClickListener { startCapture() } }
        controls.addView(triangleButton); controls.addView(timerButton); controls.addView(shutter)

        val credit = TextView(this).apply {
            text = "Made by Yves Sidney Valeriano"
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }

        bottom.addView(apertureLabel); bottom.addView(apertureBar)
        bottom.addView(manualCheck); bottom.addView(evInput)
        bottom.addView(label); bottom.addView(exposureBar)
        bottom.addView(switches); bottom.addView(controls)
        bottom.addView(credit)
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        return root
    }

    private fun startCamera() {
        try {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    val provider = future.get()

                    val res = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    val preview = Preview.Builder().setResolutionSelector(res).build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    imageCapture = ImageCapture.Builder().setResolutionSelector(res)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()

                    val subject = SubjectAnalyzer(::onSubject)
                    val analysis = ImageAnalysis.Builder().setResolutionSelector(res)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        .also {
                            it.setAnalyzer(analysisExecutor) { proxy ->
                                try {
                                    sceneMeter.measure(proxy)?.let { ev ->
                                        runOnUiThread { sceneEv = ev; updateTriangle() }
                                    }
                                    subject.analyze(proxy)
                                } catch (e: Throwable) {
                                    proxy.close()
                                }
                            }
                        }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
                    )
                } catch (e: Throwable) {
                    showError("Camera error: ${e.javaClass.simpleName}: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Throwable) {
            showError("Camera start error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Uses the typed meter reading if the box is ticked, otherwise the phone's estimate. */
    private fun currentEv(): Double? {
        if (manualCheck.isChecked) {
            return evInput.text.toString().toDoubleOrNull()
        }
        return sceneEv
    }

    /** Shows the suggested shutter and ISO for the chosen aperture and the light reading. */
    private fun updateTriangle() {
        if (!::triangleText.isInitialized) return
        if (!triangleOn) { triangleText.text = ""; return }
        val ev = currentEv() ?: run {
            triangleText.text = if (manualCheck.isChecked) "Type an EV reading" else "Reading scene…"
            return
        }
        val s = ExposureTriangle.solve(ev, ExposureMode.AUTO_MIX, aperture)
        val source = if (manualCheck.isChecked) "meter" else "scene"
        triangleText.text = "Suggested ($source): ${s.summary()}\n${s.note}"
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        cam.cameraControl.startFocusAndMetering(action)

        val r = overlay.contentRect()
        if (r.width() > 0 && x in r.left..r.right && y in r.top..r.bottom) {
            overlay.focusPoint = Pair((x - r.left) / r.width(), (y - r.top) / r.height())
        }
    }

    private fun applyExposure(progress: Int) {
        val cam = camera ?: return
        if (!cam.cameraInfo.exposureState.isExposureCompensationSupported) return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        val idx = range.lower + ((range.upper - range.lower) * progress / 100)
        cam.cameraControl.setExposureCompensationIndex(idx)
    }

    private fun cycleTimer() {
        timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        timerButton.text = if (timerSeconds == 0) "Timer: Off" else "Timer: ${timerSeconds}s"
    }

    /** Capture button: optional countdown, then take the photo. */
    private fun startCapture() {
        if (timerSeconds == 0) { takePhoto(); return }
        shutter.isEnabled = false
        countdown?.cancel()
        countdown = object : CountDownTimer(timerSeconds * 1000L, 1000L) {
            override fun onTick(ms: Long) { hintText.text = "${ms / 1000 + 1}" }
            override fun onFinish() { shutter.isEnabled = true; hintText.text = "📸"; takePhoto() }
        }.start()
    }

    /** Runs on an ML Kit callback thread; hop to main for UI + camera control. */
    private fun onSubject(subject: android.graphics.RectF?) {
        runOnUiThread {
            val cam = camera ?: return@runOnUiThread
            val z = cam.cameraInfo.zoomState.value ?: return@runOnUiThread
            val result = CompositionEngine.evaluate(subject, z.zoomRatio, z.minZoomRatio, z.maxZoomRatio)
            overlay.result = result
            if (shutter.isEnabled) hintText.text = result.hint
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
