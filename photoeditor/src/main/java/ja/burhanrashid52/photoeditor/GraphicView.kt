package ja.burhanrashid52.photoeditor

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

class GraphicView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private val borderView: BorderView by lazy { findViewById(R.id.frmBorder) }
    private val handleTopLeft: HandleView by lazy { findViewById(R.id.imgHandleTopLeft) }
    private val handleTopRight: HandleView by lazy { findViewById(R.id.imgHandleTopRight) }
    private val handleBottomLeft: HandleView by lazy { findViewById(R.id.imgHandleBottomLeft) }
    private val handleBottomRight: HandleView by lazy { findViewById(R.id.imgHandleBottomRight) }

    fun hideHandleViews() {
        listOf(handleBottomLeft, handleBottomRight, handleTopLeft, handleTopRight).forEach {
            it.visibility = View.GONE
        }
        borderView.setBackgroundColor(0)
    }

    fun showHandleViews() {
        listOf(handleBottomLeft, handleBottomRight, handleTopLeft, handleTopRight).forEach {
            it.visibility = View.VISIBLE
        }
        borderView.setBackgroundResource(R.drawable.rounded_border_tv)
        adjustHandleSizeAndBorder()
    }


    fun adjustHandleSizeAndBorder() {
        val scale = 1 / scaleX
        handleTopLeft.adjustSize(scale)
        handleTopRight.adjustSize(scale)
        handleBottomLeft.adjustSize(scale)
        handleBottomRight.adjustSize(scale)
        updateBorderScaleToFitMargin()
    }

    private fun updateBorderScaleToFitMargin() {
        // Change border margin to keep the handler center is in border line.
        val newMargin: Float =
            resources.getDimensionPixelSize(R.dimen.handle_size) / ZoomManager.scale
        val borderWidth: Float = scaleX * borderView.width
        val graphicViewWidth: Float = scaleX * width
        val expectingWidth = graphicViewWidth - newMargin
        val expectingScale = expectingWidth / borderWidth

        if (expectingScale > 0) {
            borderView.scaleX = expectingScale
            borderView.scaleY = expectingScale
        }
    }
}