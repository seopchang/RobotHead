package com.robothead.app

import android.content.Context
import android.util.Base64
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceTracker(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFaceMoved: (xOffset: Float, yOffset: Float) -> Unit,
    private val onFaceLost: () -> Unit
) {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    private var imageCapture: ImageCapture? = null

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                processFrame(imageProxy)
            }

            val capture = ImageCapture.Builder().build()
            imageCapture = capture

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis,
                capture
            )
        }, ContextCompat.getMainExecutor(context))
    }

    fun captureSnapshotBase64(onResult: (String?) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onResult(null)
            return
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    onResult(Base64.encodeToString(bytes, Base64.NO_WRAP))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(null)
                }
            }
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face != null) {
                    val box = face.boundingBox
                    val cx = box.centerX().toFloat() / image.width
                    val cy = box.centerY().toFloat() / image.height
                    val xOffset = ((cx - 0.5f) * 2f).coerceIn(-1f, 1f)
                    val yOffset = ((cy - 0.5f) * 2f).coerceIn(-1f, 1f)
                    onFaceMoved(xOffset, yOffset)
                } else {
                    onFaceLost()
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
