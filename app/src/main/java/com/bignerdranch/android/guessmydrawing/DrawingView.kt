package com.bignerdranch.android.guessmydrawing

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.google.mlkit.vision.digitalink.*

class DrawingView(
    context: Context,
    attrs: AttributeSet? = null
): View(context, attrs) {
    private lateinit var myCanvas: Canvas
    private lateinit var myBitmap: Bitmap
    private val backgroundColor = ResourcesCompat.getColor(resources, R.color.colorBackground, null)
    private val inkColor = ResourcesCompat.getColor(resources, R.color.colorPaint, null)
    private val penSize = 12f
    private val paint = Paint().apply {
        color = inkColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = penSize
    }
    private var path = Path()
    private var currentPoint: PointF = PointF(0f, 0f)
    private var nextPoint: PointF = PointF(0f, 0f)
    private val pixelThreshold = ViewConfiguration.get(context).scaledTouchSlop
    private val recognitionModel: DigitalInkRecognitionModel = DigitalInkRecognitionModel
        .builder(DigitalInkRecognitionModelIdentifier.AUTODRAW)
        .build()
    private val recognizer: DigitalInkRecognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions
            .builder(this.recognitionModel)
            .build()
    )

    private var strokeBuilder: Ink.Stroke.Builder = Ink.Stroke.builder()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(::myBitmap.isInitialized){
            myBitmap.recycle()
        }
        myBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        myCanvas = Canvas(myBitmap)
        myCanvas.drawColor(backgroundColor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(myBitmap, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        nextPoint.x = event.x
        nextPoint.y = event.y
        var action = ""
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                action = "ACTION_DOWN"
                penDown()
            }
            MotionEvent.ACTION_MOVE -> {
                action = "ACTION_MOVE"
                penMove()
            }
            MotionEvent.ACTION_UP -> {
                action = "ACTION_UP"
                penUp()
            }
            MotionEvent.ACTION_CANCEL -> {
                action = "ACTION_CANCEL"
            }
        }

        return true
    }

    private fun penDown() {
        path.reset()
        path.moveTo(nextPoint.x, nextPoint.y)
        currentPoint.x = nextPoint.x
        currentPoint.y = nextPoint.y
        recordStroke()
    }

    /*
    https://proandroiddev.com/on-device-google-translate-with-jetpack-compose-mlkit-7a48f5b11948
    https://developers.google.com/android/reference/com/google/mlkit/vision/digitalink/DigitalInkRecognitionModelIdentifier
    https://developers.google.com/android/reference/com/google/mlkit/vision/digitalink/package-summary
    https://heartbeat.comet.ml/digital-drawing-recognition-using-googles-ml-kit-on-android-e0a2de26379
    https://developer.android.com/codelabs/advanced-android-kotlin-training-canvas#5
    https://proandroiddev.com/recognize-drawings-using-ml-kit-25e99a30a951
     */

    private fun penMove() {
        val deltaX = Math.abs(nextPoint.x - currentPoint.x)
        val deltaY = Math.abs(nextPoint.y - currentPoint.y)
        recordStroke()
        if(!(deltaX < pixelThreshold && deltaY < pixelThreshold)){
            val x1 = currentPoint.x
            val y1 = currentPoint.y
            val x2 = (nextPoint.x + currentPoint.x) / 2
            val y2 = (nextPoint.y + currentPoint.y) / 2
            path.quadTo(x1, y1, x2, y2)
            currentPoint.x = nextPoint.x
            currentPoint.y = nextPoint.y
            myCanvas.drawPath(path, paint)
        }
        invalidate()
    }

    private fun penUp() {
        path.reset()
        evaluateDrawing()
    }

    fun recordStroke() {
        val inkPoint = Ink.Point.create(currentPoint.x, currentPoint.y)
        strokeBuilder.addPoint(inkPoint)
    }

    fun evaluateDrawing(){
        val stroke: Ink.Stroke = strokeBuilder.build()
        val inkBuilder = Ink.builder()
        inkBuilder.addStroke(stroke)
        recognizer.recognize(inkBuilder.build())
            .addOnSuccessListener { result: RecognitionResult ->
                Toast.makeText(context, "This is ${result.candidates[0].text}", Toast.LENGTH_LONG)
                    .show()
            }
            .addOnFailureListener { e: Exception ->
                Toast.makeText(context, "Error", Toast.LENGTH_LONG)
                    .show()
            }
    }
}