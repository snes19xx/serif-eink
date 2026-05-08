package com.snes19xx.einklauncher

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

// Custom view for freehand drawing and annotation
class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var extraCanvas: Canvas? = null
    private var extraBitmap: Bitmap? = null
    private val drawPath = Path()
    private val drawPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = false
        strokeWidth = (1.6f * resources.displayMetrics.density)
    }

    private var mX = 0f
    private var mY = 0f
    private val touchTolerance = 2f

    private var onDrawingChanged: ((Boolean) -> Unit)? = null
    private var hasDrawing = false
    var isDrawingEnabled = false

    fun setOnDrawingChangedListener(listener: (Boolean) -> Unit) {
        onDrawingChanged = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)

        extraBitmap?.let {
            newCanvas.drawBitmap(it, 0f, 0f, null)
            it.recycle()
        }

        extraBitmap = newBitmap
        extraCanvas = newCanvas

        if (oldw == 0 && oldh == 0) {
            loadDrawing()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        extraBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
        canvas.drawPath(drawPath, drawPaint)
    }

    private fun touchStart(x: Float, y: Float) {
        drawPath.moveTo(x, y)
        mX = x
        mY = y
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = abs(x - mX)
        val dy = abs(y - mY)
        if (dx >= touchTolerance || dy >= touchTolerance) {
            drawPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            mX = x
            mY = y
        }
    }

    private fun touchUp() {
        drawPath.lineTo(mX, mY)
        extraCanvas?.drawPath(drawPath, drawPaint)
        drawPath.reset()
        if (!hasDrawing) {
            hasDrawing = true
            onDrawingChanged?.invoke(true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingEnabled) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touchStart(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                touchUp()
                invalidate()
            }
        }
        return true
    }

    fun clearCanvas() {
        extraCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        hasDrawing = false
        onDrawingChanged?.invoke(false)
        val file = File(context.filesDir, "calendar_drawing.png")
        if (file.exists()) file.delete()
        invalidate()
    }

    fun saveDrawing() {
        val file = File(context.filesDir, "calendar_drawing.png")
        try {
            FileOutputStream(file).use { out ->
                extraBitmap?.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDrawing() {
        val file = File(context.filesDir, "calendar_drawing.png")
        if (file.exists()) {
            val loadedBitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (loadedBitmap != null && extraCanvas != null) {
                extraCanvas?.drawBitmap(loadedBitmap, 0f, 0f, null)
                loadedBitmap.recycle()
                hasDrawing = true
                onDrawingChanged?.invoke(true)
                invalidate()
            }
        }
    }
}