package com.example.test_opencv4

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private val REQUEST_CODE_FOR_PERMISSIONS = 1234
    private val REQUIRED_PERMISSIONS =
        //arrayOf("android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE")
        arrayOf("android.permission.CAMERA")

    /*** Views  */
    private var previewView: PreviewView? = null
    private var imageView: ImageView? = null

    /*** For CameraX  */
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById<PreviewView>(R.id.previewView)
        imageView = findViewById<ImageView>(R.id.imageView)
        if (checkPermissions()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_FOR_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val context: Context = this
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder().build()
                imageAnalysis = ImageAnalysis.Builder().build()
                imageAnalysis!!.setAnalyzer(cameraExecutor, MyImageAnalyzer())
                val cameraSelector =
                    CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    (context as LifecycleOwner),
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                preview!!.setSurfaceProvider(previewView!!.createSurfaceProvider(camera!!.cameraInfo))
            } catch (e: Exception) {
                Log.e(TAG, "[startCamera] Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class MyImageAnalyzer : ImageAnalysis.Analyzer {
        private var matPrevious: Mat? = null
        override fun analyze(image: ImageProxy) {
            /* Create cv::mat(RGB888) from image(NV21) */
            val matOrg = getMatFromImage(image)

            /* Fix image rotation (it looks image in PreviewView is automatically fixed by CameraX???) */
            val mat = fixMatRotation(matOrg)
            Log.i(
                TAG,
                "[analyze] width = " + image.width + ", height = " + image.height + "Rotation = " + previewView!!.display.rotation
            )
            Log.i(TAG, "[analyze] mat width = " + matOrg.cols() + ", mat height = " + matOrg.rows())

            /* Do some image processing */
            val matOutput = Mat(mat.rows(), mat.cols(), mat.type())
            if (matPrevious == null) matPrevious = mat
            Core.absdiff(mat, matPrevious, matOutput)
            matPrevious = mat

            /* Draw something for test */Imgproc.rectangle(
                matOutput,
                Rect(10, 10, 100, 100),
                Scalar(255.0, 0.0, 0.0)
            )
            Imgproc.putText(
                matOutput,
                "leftTop",
                Point(10.0, 10.0),
                1,
                1.0,
                Scalar(255.0, 0.0, 0.0)
            )

            /* Convert cv::mat to bitmap for drawing */
            val bitmap =
                Bitmap.createBitmap(matOutput.cols(), matOutput.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(matOutput, bitmap)

            /* Display the result onto ImageView */runOnUiThread { imageView!!.setImageBitmap(bitmap) }

            /* Close the image otherwise, this function is not called next time */image.close()
        }

        private fun getMatFromImage(image: ImageProxy): Mat {
            /* https://stackoverflow.com/questions/30510928/convert-android-camera2-api-yuv-420-888-to-rgb */
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer[nv21, 0, ySize]
            vBuffer[nv21, ySize, vSize]
            uBuffer[nv21, ySize + vSize, uSize]
            val yuv = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
            yuv.put(0, 0, nv21)
            val mat = Mat()
            Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2RGB_NV21, 3)
            return mat
        }

        private fun fixMatRotation(matOrg: Mat): Mat {
            val mat: Mat
            when (previewView!!.display.rotation) {
                Surface.ROTATION_0 -> {
                    mat = Mat(matOrg.cols(), matOrg.rows(), matOrg.type())
                    Core.transpose(matOrg, mat)
                    Core.flip(mat, mat, 1)
                }

                Surface.ROTATION_90 -> mat = matOrg
                Surface.ROTATION_270 -> {
                    mat = matOrg
                    Core.flip(mat, mat, -1)
                }

                else -> {
                    mat = Mat(matOrg.cols(), matOrg.rows(), matOrg.type())
                    Core.transpose(matOrg, mat)
                    Core.flip(mat, mat, 1)
                }
            }
            return mat
        }
    }

    private fun checkPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_FOR_PERMISSIONS) {
            if (checkPermissions()) {
                startCamera()
            } else {
                Log.i(TAG, "[onRequestPermissionsResult] Failed to get permissions")
                finish()
            }
        }
    }

    companion object {
        /*** Fixed values  */
        private const val TAG = "MyApp"

        init {
            System.loadLibrary("opencv_java4")
        }
    }
}