package com.darwin.viola.gender

import android.content.Context
import android.graphics.Bitmap
import android.media.FaceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


/**
 * The class ViolaGenderClassifier
 *
 * @author Darwin Francis
 * @version 1.0
 * @since 28 2021
 */
class ViolaGenderClassifier(private val listener: GenderClassificationListener = object : GenderClassificationListener{
    override fun onGenderClassificationResult(result: List<GenderRecognition>) {
        TODO("Not yet implemented")
    }

    override fun onGenderClassificationError(error: String) {
        Util.printLog(error)
    }

}) {

    var isInitialized = false
        private set
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var results : List<GenderRecognition>


    fun initialize(context: Context) {
        if (!isInitialized) {
            Util.printLog("Initializing Viola gender classifier.")
            val model = Classifier.Model.QUANTIZED_MOBILE_NET
            val device = Classifier.Device.CPU
            try {
                classifier = Classifier.create(context, model, device, 1)
                isInitialized = true
            } catch (e: IOException) {
                val error =
                    "Failed to create gender classifier: ${e.javaClass.canonicalName}(${e.message})"
                Util.printLog(error)
                listener.onGenderClassificationError(error)
            }
        }
    }


    fun dispose() {
        Util.printLog("Disposing gender classifier and its resources.")
        isInitialized = false
        classifier?.close()
    }

    @JvmOverloads
    fun findGenderAsync(faceBitmap: Bitmap, options: GenderOptions = getDefaultGenderOptions()) {
        Util.debug = options.debug
        if (isInitialized) {
            Util.printLog("Processing face bitmap for gender classification.")
            coroutineScope.launch {
                val resizedBitmap = resize(faceBitmap)
                val fixedBitmap = Util.forceEvenBitmapSize(resizedBitmap)!!
                if (!options.preValidateFace || verifyFacePresence(fixedBitmap)) {
                    val results: List<GenderRecognition> =
                        classifier!!.recognizeImage(resizedBitmap, 0)
                    this@ViolaGenderClassifier.results = results
                    Util.printLog("Gender classification completed, sending back the result.")
                    withContext(Dispatchers.Main) { listener.onGenderClassificationResult(results) }
                } else {
                    withContext(Dispatchers.Main) { listener.onGenderClassificationError("There is no face portraits in the given image.") }
                }
            }
        } else {
            Util.printLog("Viola gender classifier is not initialized.")
            listener.onGenderClassificationError("Viola gender classifier is not initialized.")
        }
    }

    fun getResults() : List<GenderRecognition> = results


    @JvmOverloads
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun findGenderSynchronized(
        faceBitmap: Bitmap,
        options: GenderOptions = getDefaultGenderOptions()
    ): List<GenderRecognition> {
        Util.debug = options.debug
        if (isInitialized) {
            Util.printLog("Processing face bitmap in synchronized manner for gender classification.")
            val resizedBitmap = resize(faceBitmap)
            val fixedBitmap = Util.forceEvenBitmapSize(resizedBitmap)!!
            if (options.preValidateFace && !verifyFacePresence(fixedBitmap)) {
                throw IllegalArgumentException("There is no face portraits in the given image.")
            }
            return classifier!!.recognizeImage(resizedBitmap, 0)
        } else {
            Util.printLog("Viola gender classifier is not initialized. Throwing exception.")
            throw IllegalStateException("Viola gender classifier is not initialized.")
        }
    }

    private fun getDefaultGenderOptions(): GenderOptions {
        return GenderOptions.Builder()
            .build()
    }

    private fun verifyFacePresence(bitmap: Bitmap): Boolean {
        val pBitmap: Bitmap = bitmap.copy(Bitmap.Config.RGB_565, true)
        val faceDetector = FaceDetector(pBitmap.width, pBitmap.height, 1)
        val faceArray = arrayOfNulls<FaceDetector.Face>(1)
        val faceCount = faceDetector.findFaces(pBitmap, faceArray)
        return faceCount != 0
    }

    private fun resize(image: Bitmap): Bitmap {
        Util.printLog("Re-scaling input bitmap for fast image processing.")
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
     companion object{
         var classifier: Classifier? = null
    }
}