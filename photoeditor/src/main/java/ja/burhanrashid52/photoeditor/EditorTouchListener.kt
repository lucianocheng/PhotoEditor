package ja.burhanrashid52.photoeditor

import android.graphics.Matrix
import android.util.Log
import ja.burhanrashid52.photoeditor.MultiTouchListener.Companion.adjustTranslation
import ja.burhanrashid52.photoeditor.Vector2D.Companion.getAngle
import ja.burhanrashid52.photoeditor.MultiTouchListener.Companion.move
import android.widget.RelativeLayout
import android.view.View.OnTouchListener
import android.view.GestureDetector
import ja.burhanrashid52.photoeditor.MultiTouchListener.TransformInfo
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View

/**
 * Touch listener for main editor. Used for resizing the main image, background,
 * and currently selected image.
 *
 * TODO(cheng): Collapse this logic with MultiTouchListener. Use callbacks or a static method call.
 *
 * @author Leylow
 */
internal class EditorTouchListener(
    photoEditorView: PhotoEditorView,
    canvasView: RelativeLayout,
    viewState: PhotoEditorViewState
) : OnTouchListener {
    private val mGestureListener: GestureDetector
    private val isRotateEnabled = true
    private val isTranslateEnabled = true

    // NOTE(cheng): Appears inert, can be removed
    private val isScaleEnabled = true
    private var mActivePointerId = INVALID_POINTER_ID
    private var mPrevX = 0f
    private var mPrevY = 0f
    private val mScaleGestureDetector: ScaleGestureDetector
    private val photoEditorView: PhotoEditorView
    private val canvasView: RelativeLayout
    private val boxHelper: BoxHelper
    private var mOnPhotoEditorListener: OnPhotoEditorListener? = null
    private var isTouchMovable = false
    private val viewState: PhotoEditorViewState
    private var currentSelectedX = 0f
    private var currentSelectedY = 0f
    private var scalingInProgress = false
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        mScaleGestureDetector.onTouchEvent(view, event)
        mGestureListener.onTouchEvent(event)
        if (!isTranslateEnabled) {
            return true
        }
        val action = event.action
        when (action and event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "onTouch: $view")
                if (viewState.currentSelectedView == null) {
                    return false
                }
                isTouchMovable = true
                mPrevX = event.x
                mPrevY = event.y
                currentSelectedX = viewState.currentSelectedView!!.x
                currentSelectedY = viewState.currentSelectedView!!.y
                mActivePointerId = event.getPointerId(0)
                if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onStartViewChangeListener(
                    viewState.currentSelectedView!!.tag as ViewType
                )
            }
            MotionEvent.ACTION_MOVE ->                 // Initiates a move on the currently selected sticker.
                // NOTE(cheng): This is *not* for moving the background.
                // NOTE(cheng): Only enable dragging on focused stickers.
                if (isTouchMovable && viewState.currentSelectedView != null) {
                    // TODO(cheng): There's a weird bug happening here where a user will
                    //              pinch, but the sticker will translate instead of resize.
                    //              It's likely (1) state between this and MultiTouchListener,
                    //              (2) incorrectly dealing with the event pointers, or (3)
                    //              incorrectly tracking scalingInProgress state.
                    val pointerIndexMove = event.findPointerIndex(mActivePointerId)
                    if (pointerIndexMove != -1) {
                        val currX = event.getX(pointerIndexMove)
                        val currY = event.getY(pointerIndexMove)
                        if (!mScaleGestureDetector.isInProgress && !scalingInProgress) {

                            // Translate the delta vector (movement) by the current orientation
                            // of the canvas view
                            val deltaVectorTranslationMatrix = Matrix()
                            // https://stackoverflow.com/a/25381660
                            deltaVectorTranslationMatrix.set(canvasView.matrix)
                            // Negate and reverse compensate any rotation the canvas view has so that
                            // the focused view follows user's finger.
                            deltaVectorTranslationMatrix.postRotate(-(canvasView.rotation * 2))
                            adjustTranslation(
                                viewState.currentSelectedView!!,
                                deltaVectorTranslationMatrix,
                                currX - mPrevX,
                                currY - mPrevY,
                                currentSelectedX,
                                currentSelectedY
                            )
                        }
                    }
                }
            MotionEvent.ACTION_CANCEL -> mActivePointerId = INVALID_POINTER_ID
            MotionEvent.ACTION_UP -> {
                // https://stackoverflow.com/a/25277069
                view.performClick()
                mActivePointerId = INVALID_POINTER_ID
                isTouchMovable = false

                // Unlock the view from translating when all fingers are lifted.
                scalingInProgress = false
                if (mOnPhotoEditorListener != null) {
                    mOnPhotoEditorListener!!.onStopViewChangeListener(ViewType.IMAGE)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndexPointerUp =
                    action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = event.getPointerId(pointerIndexPointerUp)
                if (pointerId == mActivePointerId) {
                    val newPointerIndex = if (pointerIndexPointerUp == 0) 1 else 0
                    mPrevX = event.getX(newPointerIndex)
                    mPrevY = event.getY(newPointerIndex)
                    mActivePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    // NOTE(cheng): Scaling logic used to scale *currently selected graphic*,
    //              *not* the background. Background scaling is handled by ZoomLayout.
    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var mPivotX = 0f
        private var mPivotY = 0f
        private var initialScale = 0f
        private var initialRotation = 0f
        private val mPrevSpanVector = Vector2D()
        override fun onScaleBegin(view: View, detector: ScaleGestureDetector): Boolean {
            // Lock the view from translating.
            scalingInProgress = true
            mPivotX = detector.getFocusX()
            mPivotY = detector.getFocusY()
            if (viewState.currentSelectedView != null) {
                initialScale = viewState.currentSelectedView!!.scaleX
                initialRotation = viewState.currentSelectedView!!.rotation
                viewState.currentSelectedView?.let {
                    mOnPhotoEditorListener?.onGraphicActionDown(it)
                }
            }
            mPrevSpanVector.set(detector.getCurrentSpanVector())

            // Mark event as "handled / absorbed" to begin scaling action.
            return true
        }

        override fun onScale(view: View, detector: ScaleGestureDetector): Boolean {
            val info = TransformInfo()
            info.deltaScale = if (isScaleEnabled) detector.getScaleFactor() else 1.0f
            info.deltaAngle = if (isRotateEnabled) getAngle(
                mPrevSpanVector,
                detector.getCurrentSpanVector()
            ) else 0.0f
            info.deltaX = if (isTranslateEnabled) detector.getFocusX() - mPivotX else 0.0f
            info.deltaY = if (isTranslateEnabled) detector.getFocusY() - mPivotY else 0.0f
            info.pivotX = mPivotX
            info.pivotY = mPivotY
            viewState.currentSelectedView?.let {
                mOnPhotoEditorListener?.onGraphicMove(
                    it,
                    info,
                    photoEditorView.parentLayout.scaleX,
                    initialScale,
                    initialRotation
                )
            }

            // Mark event as "unhandled" so scale continues to accumulate.
            // See OnScaleGestureListener.onScale()
            return false
        }
    }

    fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener?) {
        mOnPhotoEditorListener = onPhotoEditorListener
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            boxHelper.clearHelperBox()
            if (mOnPhotoEditorListener != null) {
                mOnPhotoEditorListener!!.onInFocusViewChangeListener(null)
            }
            return true
        }
    }

    companion object {
        private const val TAG = "EditorTouchListener"
        private const val INVALID_POINTER_ID = -1
    }

    init {
        mScaleGestureDetector = ScaleGestureDetector(ScaleGestureListener())
        mGestureListener = GestureDetector(GestureListener())
        this.photoEditorView = photoEditorView
        this.canvasView = canvasView
        this.viewState = viewState
        boxHelper = BoxHelper(canvasView, viewState)
    }
}