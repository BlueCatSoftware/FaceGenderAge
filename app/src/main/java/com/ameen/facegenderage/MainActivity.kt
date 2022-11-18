package com.ameen.facegenderage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.ameen.facegenderage.databinding.ActivityMainBinding
import com.darwin.viola.still.FaceDetectionListener
import com.darwin.viola.still.Viola
import com.darwin.viola.still.model.CropAlgorithm
import com.darwin.viola.still.model.FaceDetectionError
import com.darwin.viola.still.model.FaceOptions
import com.darwin.viola.still.model.Result
import com.google.modernstorage.photopicker.PhotoPicker
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE: Int = 12
    private lateinit var binding: ActivityMainBinding
    private lateinit var path: String
    private lateinit var bitmap: Bitmap

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("UnsafeOptInUsageError")
    val photoPicker = registerForActivityResult(PhotoPicker()) { uri ->
        val pickedImage: Uri = uri[0]
        val imagePath = Util.getPath(this, pickedImage)
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        bitmap = BitmapFactory.decodeFile(imagePath, options)
        bitmap = Util.modifyOrientation(bitmap!!, imagePath!!)
        bitmap = Bitmap.createScaledBitmap(bitmap,480, 360, false)
        binding.imageView.setImageBitmap(bitmap)
    }


    private val listener: FaceDetectionListener = object : FaceDetectionListener {

        override fun onFaceDetected(result: Result) {
            binding.imageView.setImageBitmap(result.facePortraits[0].face)
            val string = StringBuilder()
            val map = HashMap<String, HashMap<String, String>>()

            result.facePortraits.forEachIndexed() { position, item ->
                string.append("The Age: ")
                string.append(item.ageRange)
                string.append("\n")
                string.append("The Gender: ")
                string.append(item.genderRange)
                string.append("\n")
                map["Face ${position+1}"] = HashMap<String, String>().apply {
                    put("Gender", item.genderRange!!)
                    put("Age", item.ageRange!!)
                }
                val file = File(
                    Environment.getExternalStorageDirectory()
                        .toString() + "/" + File.separator + "Face/data_face${Date().time}.json"
                )
                FileUtil.writeFile(file.path, map.toString())
            }
            map.clear()
            binding.textView.text = string
        }

        override fun onFaceDetectionFailed(error: FaceDetectionError, message: String) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }


    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("UnsafeOptInUsageError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.button.setOnClickListener {
            photoPicker.launch(PhotoPicker.Args(PhotoPicker.Type.IMAGES_ONLY, 1))
        }
        binding.button2.setOnClickListener {
            val viola = Viola(listener, context)
            viola.addAgeClassificationPlugin(this)
            val faceOption =
                FaceOptions.Builder()
                    .cropAlgorithm(CropAlgorithm.THREE_BY_FOUR)
                    .enableAgeClassification()
                    .setMinimumFaceSize(1)
                    .enableDebug()
                    .build()
            viola.detectFace(bitmap, faceOption)
        }

    }

    private fun cropFaceFromBitmap(bitmap: Bitmap): Bitmap? {
        val resizedBitmap = resize(bitmap)
        val fixedBitmap = forceEvenBitmapSize(resizedBitmap)
        val pBitmap: Bitmap = fixedBitmap.copy(Bitmap.Config.RGB_565, true)
        val faceDetector = FaceDetector(pBitmap.width, pBitmap.height, 1)
        val faceArray = arrayOfNulls<FaceDetector.Face>(1)
        val faceCount = faceDetector.findFaces(pBitmap, faceArray)

        if (faceCount != 0) {
            val face: FaceDetector.Face = faceArray[0]!!

            val faceMidpoint = PointF()

            face.getMidPoint(faceMidpoint);

            val eyesDistance = face.eyesDistance()
            val xPadding = (eyesDistance * 2)
            val yPadding = (eyesDistance * 2)

            var bStartX = faceMidpoint.x - xPadding
            bStartX = bStartX.coerceAtLeast(0.0f)
            var bStartY = faceMidpoint.y - yPadding
            bStartY = bStartY.coerceAtLeast(0.0f)
            var bWidth = (eyesDistance / 0.25).toFloat()

            var bHeight = (bWidth / 0.75).toFloat()

            bWidth =
                if (bStartX + bWidth > fixedBitmap.width) fixedBitmap.width.toFloat() else bWidth
            bHeight =
                if (bStartY + bHeight > fixedBitmap.height) fixedBitmap.height.toFloat() else bHeight

            if (bStartY + bHeight > fixedBitmap.height) {
                val excessHeight: Float = bStartY + bHeight - fixedBitmap.height
                bHeight -= excessHeight
            }

            if (bStartX + bWidth > fixedBitmap.width) {
                val excessWidth: Float = bStartX + bWidth - fixedBitmap.width
                bWidth -= excessWidth
            }

            return Bitmap.createBitmap(
                fixedBitmap,
                bStartX.toInt(),
                bStartY.toInt(),
                bWidth.toInt(),
                bHeight.toInt()
            )
        } else {
            return null
        }

    }
    private fun resize(image: Bitmap): Bitmap {
        val maxWidth = 300
        val maxHeight = 400
        val width = image.width
        val height = image.height
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()
        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        return Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
    }
    private fun forceEvenBitmapSize(original: Bitmap): Bitmap {
        var width = original.width
        var height = original.height
        if (width % 2 == 1) {
            width++
        }
        if (height % 2 == 1) {
            height++
        }
        var fixedBitmap = original
        if (width != original.width || height != original.height) {
            fixedBitmap = Bitmap.createScaledBitmap(original, width, height, false)
        }
        return fixedBitmap
    }

    companion object {
        lateinit var context: Context
    }
}