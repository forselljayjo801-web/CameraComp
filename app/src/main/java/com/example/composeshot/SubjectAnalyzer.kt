package com.example.composeshot

import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/** Finds the main subject: largest face (expanded to head+shoulders), else largest object. */
class SubjectAnalyzer(private val onSubject: (RectF?) -> Unit) : ImageAnalysis.Analyzer {

    private val faces = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    private val objects = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val rot = proxy.imageInfo.rotationDegrees
        val w = (if (rot % 180 == 0) proxy.width else proxy.height).toFloat()
        val h = (if (rot % 180 == 0) proxy.height else proxy.width).toFloat()
        val input = InputImage.fromMediaImage(media, rot)

        fun norm(r: Rect) = RectF(
            (r.left / w).coerceIn(0f, 1f), (r.top / h).coerceIn(0f, 1f),
            (r.right / w).coerceIn(0f, 1f), (r.bottom / h).coerceIn(0f, 1f)
        )

        faces.process(input)
            .addOnSuccessListener { found ->
                val face = found.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face != null) {
                    val b = face.boundingBox
                    val grown = Rect(
                        (b.left - b.width() * 0.6f).toInt(),
                        (b.top - b.height() * 0.3f).toInt(),
                        (b.right + b.width() * 0.6f).toInt(),
                        (b.bottom + b.height() * 1.4f).toInt()
                    )
                    onSubject(norm(grown))
                    proxy.close()
                } else {
                    objects.process(input)
                        .addOnSuccessListener { objs ->
                            val best = objs.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            onSubject(best?.let { norm(it.boundingBox) })
                        }
                        .addOnFailureListener { onSubject(null) }
                        .addOnCompleteListener { proxy.close() }
                }
            }
            .addOnFailureListener { onSubject(null); proxy.close() }
    }
}
