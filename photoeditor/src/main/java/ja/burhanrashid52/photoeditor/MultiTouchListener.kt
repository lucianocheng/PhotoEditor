package ja.burhanrashid52.photoeditor

import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import android.widget.RelativeLayout
import android.view.View.OnTouchListener
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Touch listener for stickers, emoji, text, etc.
 *
 * Created on 18/01/2017.
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 *
 *
 */
class MultiTouchListener(
    deleteView: View?,
    photoEditorView: PhotoEditorView,
    canvasView: RelativeLayout,
    photoEditImageView: ImageView,
    private val mIsPinchScalable: Boolean,
    onPhotoEditorListener: OnPhotoEditorListener?,
    viewState: PhotoEditorViewState
) : OnTouchListener {
    private val mGestureListener: GestureDetector

    // NOTE(cheng): Appears inert, can be removed
    private val isRotateEnabled = true
    private val isTranslateEnabled = true

    // NOTE(cheng): Appears inert, can be removed
    private val isScaleEnabled = true
    private val minimumScale = 0.5f
    private val maximumScale = 10.0f
    private var mActivePointerId = INVALID_POINTER_ID
    private var mPrevX = 0f
    private var mPrevY = 0f
    private var mPrevRawX = 0f
    private var mPrevRawY = 0f
    private val mScaleGestureDetector: ScaleGestureDetector
    private val location = IntArray(2)
    private var outRect: Rect? = null
    private val deleteView: View?
    private val photoEditImageView: ImageView?
    val photoEditorView: PhotoEditorView
    var canvasView: RelativeLayout
    private var onMultiTouchListener: OnMultiTouchListener? = null
    private var mOnGestureControl: OnGestureControl? = null
    private val mOnPhotoEditorListener: OnPhotoEditorListener?
    private val viewState: PhotoEditorViewState
    var itemRootFrameView: View? = null
    var isCornerMovable = false
    var isTouchMovable = false

    // Selection state
    var lastImageTouchMouseDownEventWasOpaque = false
    @JvmField
    var scaledImageIntersectionPixelMap: IntArray? = null
    var scaledImageBitmapWidth = 0
    var scaledImageBitmapHeight = 0
    var scaledImageIntersectionPixelMapTimestamp: Date? = null
    var centerX = 0f
    var centerY = 0f
    var startR = 0f
    var startScale = 0f
    var startX = 0f
    var startY = 0f
    var startRotation = 0f
    var startA = 0f
    var coordinateX = 0f
    var coordinateY = 0f
    var adjustedScaledWidth = 0f
    var adjustedScaledHeight = 0f
    var editorScale = 0f
    private var scalingInProgress = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {

        // NOTE(cheng): This view is the root view.  E.g., the imageRootView
        if (view === viewState.currentSelectedView) {
            mScaleGestureDetector.onTouchEvent(view, event)
        }
        mGestureListener.onTouchEvent(event)
        if (!isTranslateEnabled) {
            return true
        }
        val action = event.action
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        val frmBorderHitRectangle = Rect()
        view.findViewById<View>(R.id.frmBorder).getHitRect(frmBorderHitRectangle)
        when (action and event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (// NOTE(cheng): Disable lastImageTouchMouseDownEventWasOpaque below for testing.
                frmBorderHitRectangle.contains(event.x.toInt(), event.y.toInt())
                && lastImageTouchMouseDownEventWasOpaque
            ) {
                // NOTE(cheng): Use this flag to handle the case where the sticker is
                // inside the image border frame, and also the event was prior handled by
                // the listener attached to the image (in PhotoEditor.java)
                // We have to do it this way so the sticker can handle it's own "pointer intersection"
                // math to determine if the touched pixel was opaque.
                lastImageTouchMouseDownEventWasOpaque = false

                // NOTE(cheng): Always handle this case (returning "true") for non-stickers (text),
                // NOTE(cheng): It is also important to return "true" here so the GestureListener
                //              gets triggered, which allows for selection to take place
                //              in the case of a "fling".
                isTouchMovable = true
                isCornerMovable = false
                mPrevX = event.x
                mPrevY = event.y
                mActivePointerId = event.getPointerId(0)
                if (deleteView != null) {
                    deleteView.visibility = View.VISIBLE
                }

                // NOTE(cheng): Disabling this since this is a button now
                // view.bringToFront();
                firePhotoEditorSDKListener(
                    view,
                    PhotoEditorSDKListenerMode.START_VIEW_CHANGE
                )
            } else {
                // If event is not on a handleView, and not within the rect of the imageView,
                // it is within the space between the handleViews on the borders and should be ignored.
                // In this case, do not absorb the event.
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTouchMovable) {
                    true
                }
                // Only enable dragging on focused stickers.
                if (view === viewState.currentSelectedView) {
                    val pointerIndexMove = event.findPointerIndex(mActivePointerId)
                    if (pointerIndexMove != -1) {
                        val currX = event.getX(pointerIndexMove)
                        val currY = event.getY(pointerIndexMove)
                        if (!mScaleGestureDetector.isInProgress && !scalingInProgress) {
                            adjustTranslation(
                                view,
                                view.matrix,
                                currX - mPrevX,
                                currY - mPrevY,
                                view.x,
                                view.y
                            )
                        }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> mActivePointerId = INVALID_POINTER_ID
            MotionEvent.ACTION_UP -> {
                mActivePointerId = INVALID_POINTER_ID
                if (deleteView != null && isViewInBounds(deleteView, x, y)) {
                    onMultiTouchListener?.onRemoveViewListener(view)
                } else if (!isViewInBounds(photoEditImageView, x, y)) {
                    // NOTE(kleyow): Disabling this sticker out-of-bounds logic until it works with photo rotation.
                    // view.animate().translationY(0f).translationY(0f);
                }
                if (!isViewInBounds(photoEditImageView, x, y)) {
                    // NOTE(kleyow): Disabling this sticker out-of-bounds logic until it works with photo rotation.
                    // view.animate().translationY(0f).translationY(0f);
                }
                if (deleteView != null) {
                    deleteView.visibility = View.GONE
                }
                isCornerMovable = false

                // Unlock the view from translating when all fingers are lifted.
                scalingInProgress = false
                firePhotoEditorSDKListener(
                    view,
                    PhotoEditorSDKListenerMode.STOP_VIEW_CHANGE
                )
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

    private enum class PhotoEditorSDKListenerMode {
        START_VIEW_CHANGE, MOVE_VIEW_CHANGE, STOP_VIEW_CHANGE
    }

    // TODO(cheng): add 'onMoveViewChangeListener(...)' and make it fire whenever a move / resize happens
    //              hook this up to a callback in EditImageActivity that adjusts the handles.
    private fun firePhotoEditorSDKListener(view: View, listenerMode: PhotoEditorSDKListenerMode) {
        val viewTag = view.tag
        if (mOnPhotoEditorListener != null && viewTag != null && viewTag is ViewType) {
            if (listenerMode == PhotoEditorSDKListenerMode.START_VIEW_CHANGE)
                mOnPhotoEditorListener.onStartViewChangeListener(view.tag as ViewType)
            else if (listenerMode == PhotoEditorSDKListenerMode.STOP_VIEW_CHANGE)
                mOnPhotoEditorListener.onStopViewChangeListener(view.tag as ViewType)
            else if (listenerMode == PhotoEditorSDKListenerMode.MOVE_VIEW_CHANGE)
                mOnPhotoEditorListener.onMoveViewChangeListener(view.tag as ViewType)
        }
    }

    private fun isViewInBounds(view: View?, x: Int, y: Int): Boolean {
        return view?.run {
            getDrawingRect(outRect)
            getLocationOnScreen(location)
            outRect?.offset(location[0], location[1])
            outRect?.contains(x, y)
        } ?: false
    }

    fun setOnMultiTouchListener(onMultiTouchListener: OnMultiTouchListener?) {
        this.onMultiTouchListener = onMultiTouchListener
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var mPivotX = 0f
        private var mPivotY = 0f
        private val mPrevSpanVector = Vector2D()

        override fun onScaleBegin(view: View, detector: ScaleGestureDetector): Boolean {
            // Lock the view from translating.
            scalingInProgress = true
            mPivotX = detector.getFocusX()
            mPivotY = detector.getFocusY()
            mPrevSpanVector.set(detector.getCurrentSpanVector())
            return mIsPinchScalable
        }

        override fun onScale(view: View, detector: ScaleGestureDetector): Boolean {
            val info = TransformInfo()
            info.deltaScale = if (isScaleEnabled) detector.getScaleFactor() else 1.0f
            info.deltaAngle = if (isRotateEnabled) Vector2D.getAngle(
                mPrevSpanVector,
                detector.getCurrentSpanVector()
            ) else 0.0f
            info.deltaX = if (isTranslateEnabled) detector.getFocusX() - mPivotX else 0.0f
            info.deltaY = if (isTranslateEnabled) detector.getFocusY() - mPivotY else 0.0f
            info.pivotX = mPivotX
            info.pivotY = mPivotY

            // TODO(cheng): Determines why these are disabled.
            // info.minimumScale = minimumScale
            //info.maximumScale = maximumScale
            move(
                view,
                info,
                photoEditorView.scaleX,
                view.scaleX,
                view.rotation
            )
            return !mIsPinchScalable
        }
    }

    class TransformInfo {
        @JvmField
        var deltaX = 0f
        @JvmField
        var deltaY = 0f
        @JvmField
        var deltaScale = 0f
        @JvmField
        var deltaAngle = 0f
        @JvmField
        var pivotX = 0f
        @JvmField
        var pivotY = 0f
        @JvmField
        var minimumScale = 0f
        @JvmField
        var maximumScale = 0f
    }

    interface OnMultiTouchListener {
        fun onEditTextClickListener(text: String?, colorCode: Int)
        fun onRemoveViewListener(removedView: View?)
    }

    // NOTE(cheng): Making this public temporarily to get past a kotlin compile issue.
    interface OnGestureControl {
        fun onClick()
        fun onLongClick()
        fun onDown()
        fun onFling()
    }

    fun setOnGestureControl(onGestureControl: OnGestureControl?) {
        mOnGestureControl = onGestureControl
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            mOnGestureControl?.onClick()

            return true
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)
            mOnGestureControl?.onLongClick()
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocity1: Float,
            velocity2: Float
        ): Boolean {
            super.onFling(e1, e2, velocity1, velocity2)
            mOnGestureControl?.onFling()
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            if (mOnGestureControl != null) {
                mOnGestureControl!!.onDown()
            }
            return true
        }
    }

    companion object {
        private const val TAG = "MultiTouchListener"
        private const val ABSOLUTE_MINIMUM_SCALE = 0.2f
        private const val EDITOR_RELATIVE_MINIMUM_SCALE = 0.4f
        private const val EDITOR_RELATIVE_MAXIMUM_SCALE = 3.0f
        private const val INVALID_POINTER_ID = -1
        private fun adjustAngle(degrees: Float): Float {
            return when {
                degrees > 180.0f -> {
                    degrees - 360.0f
                }
                degrees < -180.0f -> {
                    degrees + 360.0f
                }
                else -> degrees
            }
        }

        @JvmStatic
        fun move(
            view: View,
            info: TransformInfo,
            editorScaleX: Float,
            initialStickerScale: Float,
            initialStickerRotation: Float
        ) {
            // NOTE(kleyow): No idea what this is for but it's messing with the pivot points
            //               will screw up one touch.
            //               I saw no observable difference as to what this was accomplishing.
            // TODO(cheng): I believe this is for moving a sticker with two fingers.
            //              When pivotX and pivotY exist, they represent the focal point between
            //              multiple touches in a pinch (aka the point directly in the middle).
            //              Need to confirm this, but best way to fix this would be to skip this
            //              step if pivotX / pivotY is null due to not existing when a user is using
            //              single-touch.
            //computeRenderOffset(view, info.pivotX, info.pivotY);

            // NOTE(kleyow): No idea what this is for since translation is handled in
            //               MotionEvent.ACTION_MOVE.
            //               I saw no observable difference as to what this was accomplishing.
            // TODO(cheng): This is for placing items programatically, such as right after
            //              adding them.  It doesn't matter for scale, but it can matter for
            //              others.  Turn this back on in the future after testing.
            //adjustTranslation(view, info.deltaX, info.deltaY);

            // NOTE(cheng): Since `ZoomLayout.getMaxZoom()` is a constant value of 4,
            //              this being NaN should be impossible.  However, this appears to be
            //              the likely candidate causing a NaN issue.
            val baseZoomAmount = ZoomLayout.getMaxZoom() - 1
            if (java.lang.Float.isNaN(baseZoomAmount)) {
                Log.e(TAG, "NaN scale value in MultiTouchListener:151")
                return
            }

            // Lower the floor by the same percentage of scale on the editor until a minimum.
            val adaptiveMinimumScale = Math.max(
                ABSOLUTE_MINIMUM_SCALE,
                EDITOR_RELATIVE_MINIMUM_SCALE - EDITOR_RELATIVE_MINIMUM_SCALE * (editorScaleX - 1) / baseZoomAmount
            )
            val scale = Math.max(
                adaptiveMinimumScale,
                Math.min(EDITOR_RELATIVE_MAXIMUM_SCALE, initialStickerScale * info.deltaScale)
            )

            // NOTE(cheng): Since `ZoomLayout.getMaxZoom()` is a constant value of 4,
            //              this being NaN should be impossible.  However, this appears to be
            //              the likely candidate causing a NaN issue.
            if (java.lang.Float.isNaN(scale)) {
                Log.e(TAG, "NaN scale value in MultiTouchListener:166")
                return
            }

            // NOTE(cheng): When the image border is scaling, the margins
            //              do not scale with it.  This means when you are
            //              zoomed in on the main image, the margins can appear large.
            // NOTE(cheng): We only scale the inner image when it is getting scaled down (to help
            //              users make images smaller).  When it's getting scaled up (scale > 1.0),
            //              users don't need help, so we keep the scale of the inner image constant
            //              and scale only the outer border view.
            val imageBorderView = view.findViewById<FrameLayout>(R.id.frmBorder)
            imageBorderView.scaleX = Math.min(scale.toDouble(), 1.0).toFloat()
            imageBorderView.scaleY = Math.min(scale.toDouble(), 1.0).toFloat()

            // Scale the actual outer view
            // TODO(cheng): Change 'view' to 'graphicView'
            view.scaleX = scale
            view.scaleY = scale

            // Rotate the actual outer view.
            val rotation = adjustAngle(initialStickerRotation + info.deltaAngle)
            view.rotation = rotation
        }

        @JvmStatic
        fun adjustTranslation(
            graphicView: View,
            deltaVectorTranslationMatrix: Matrix,
            deltaX: Float,
            deltaY: Float,
            currentSelectedX: Float,
            currentSelectedY: Float
        ) {
            val deltaVector = floatArrayOf(deltaX, deltaY)
            deltaVectorTranslationMatrix.mapVectors(deltaVector)
            graphicView.translationX = currentSelectedX + deltaVector[0]
            graphicView.translationY = currentSelectedY + deltaVector[1]
        }

        private fun computeRenderOffset(view: View, pivotX: Float, pivotY: Float) {
            if (view.pivotX == pivotX && view.pivotY == pivotY) {
                return
            }
            val prevPoint = floatArrayOf(0.0f, 0.0f)
            view.matrix.mapPoints(prevPoint)
            view.pivotX = pivotX
            view.pivotY = pivotY
            val currPoint = floatArrayOf(0.0f, 0.0f)
            view.matrix.mapPoints(currPoint)
            val offsetX = currPoint[0] - prevPoint[0]
            val offsetY = currPoint[1] - prevPoint[1]
            view.translationX = view.translationX - offsetX
            view.translationY = view.translationY - offsetY
        }
    }

    // itemRootFrameView = The actual item (image, text, emoji)
    init {
        mScaleGestureDetector = ScaleGestureDetector(ScaleGestureListener())
        mGestureListener = GestureDetector(GestureListener())
        this.deleteView = deleteView
        this.photoEditorView = photoEditorView
        this.canvasView = canvasView
        this.photoEditImageView = photoEditImageView
        mOnPhotoEditorListener = onPhotoEditorListener
        outRect = if (deleteView != null) {
            Rect(
                deleteView.left, deleteView.top,
                deleteView.right, deleteView.bottom
            )
        } else {
            Rect(0, 0, 0, 0)
        }
        this.viewState = viewState
    }
}