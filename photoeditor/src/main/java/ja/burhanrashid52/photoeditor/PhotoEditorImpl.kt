package ja.burhanrashid52.photoeditor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextUtils
import android.view.GestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IntRange
import androidx.annotation.RequiresPermission
import ja.burhanrashid52.photoeditor.PhotoEditorImageViewListener.OnSingleTapUpCallback
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.RelativeLayout
import ja.burhanrashid52.photoeditor.PhotoEditor.OnSaveListener

/**
 *
 *
 * This class in initialize by [PhotoEditor.Builder] using a builder pattern with multiple
 * editing attributes
 *
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.1
 * @since 18/01/2017
 */
internal class PhotoEditorImpl @SuppressLint("ClickableViewAccessibility") constructor(
    builder: PhotoEditor.Builder
) : PhotoEditor {
    private val photoEditorView: PhotoEditorView = builder.photoEditorView
    override val viewState: PhotoEditorViewState = PhotoEditorViewState()
    private val mainImageView: ImageView? = builder.imageView
    private val deleteView: View? = builder.deleteView
    private val drawingView: DrawingView? = builder.drawingView
    private val mBrushDrawingStateListener: BrushDrawingStateListener =
        BrushDrawingStateListener(builder.photoEditorView, viewState)
    private val mBoxHelper: BoxHelper = BoxHelper(builder.canvasView, viewState)
    private var mOnPhotoEditorListener: OnPhotoEditorListener? = null
    private val isTextPinchScalable: Boolean = builder.isTextPinchScalable
    private val mDefaultTextTypeface: Typeface? = builder.textTypeface
    private val mDefaultEmojiTypeface: Typeface? = builder.emojiTypeface
    private val mGraphicManager: GraphicManager = GraphicManager(builder.canvasView, viewState)

    // NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
    private val mEditorTouchListener: EditorTouchListener
    private val canvasView: RelativeLayout = builder.canvasView

    // NOTE(cheng): The two values below are not hooked up
    private val overlayView: ImageView = builder.overlayView
    private val backgroundView: ImageView = builder.backgroundView

    override fun addImage(desiredImage: Bitmap?): View? {
        drawingView!!.enableDrawing(false)
        val multiTouchListener = getMultiTouchListener(true)
        val sticker = Sticker(
            canvasView,
            photoEditorView,
            multiTouchListener,
            viewState,
            mOnPhotoEditorListener!!,
            mGraphicManager
        )
        sticker.buildView(desiredImage)
        multiTouchListener.itemRootFrameView = sticker.rootView
        sticker.rootView.setOnTouchListener(multiTouchListener)
        addToEditor(sticker)
        val multiTouchListenerByView = viewState.multiTouchListenerByView
        multiTouchListenerByView.put(sticker.rootView, multiTouchListener)
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onInFocusViewChangeListener(
            sticker.rootView
        )
        return sticker.rootView
    }

    /**
     * This will rotate the main image and all sub views on [PhotoEditorView]
     * NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
     */
    override fun rotateImage(rotation: Float) {
        // Rotates the background image, main image, brush view and all currently placed stickers.
        canvasView.rotation = rotation
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onRotateViewListener()
    }

    override fun addText(text: String?, colorCodeTextView: Int): View? {
        return addText(null, text, colorCodeTextView)
    }

    override fun addText(textTypeface: Typeface?, text: String?, colorCodeTextView: Int): View? {
        val styleBuilder = TextStyleBuilder()
        styleBuilder.withTextColor(colorCodeTextView)
        if (textTypeface != null) {
            styleBuilder.withTextFont(textTypeface)
        }
        return addText(text, styleBuilder)
    }

    override fun addText(text: String?, styleBuilder: TextStyleBuilder?): View? {
        drawingView?.enableDrawing(false)
        val multiTouchListener = getMultiTouchListener(isTextPinchScalable)
        val textGraphic = Text(
            canvasView,
            photoEditorView,
            multiTouchListener,
            viewState,
            mOnPhotoEditorListener!!,
            mDefaultTextTypeface,
            mGraphicManager
        )

        textGraphic.buildView(text, styleBuilder)
        addToEditor(textGraphic)

        multiTouchListener.itemRootFrameView = textGraphic.rootView
        textGraphic.rootView.setOnTouchListener(multiTouchListener)
        viewState.multiTouchListenerByView.put(
            textGraphic.rootView, multiTouchListener
        )
        mOnPhotoEditorListener?.let {
            it.onInFocusViewChangeListener(textGraphic.rootView)
        }
        return textGraphic.rootView
    }

    override fun editText(view: View, inputText: String?, colorCode: Int) {
        editText(view, null, inputText, colorCode)
    }

    override fun editText(view: View, textTypeface: Typeface?, inputText: String?, colorCode: Int) {
        val styleBuilder = TextStyleBuilder()
        styleBuilder.withTextColor(colorCode)
        if (textTypeface != null) {
            styleBuilder.withTextFont(textTypeface)
        }
        editText(view, inputText, styleBuilder)
    }

    override fun editText(view: View, inputText: String?, styleBuilder: TextStyleBuilder?) {
        val inputTextView = view.findViewById<TextView>(R.id.tvPhotoEditorText)
        if (inputTextView != null && viewState.containsAddedView(view) && !TextUtils.isEmpty(
                inputText
            )
        ) {
            inputTextView.text = inputText
            styleBuilder?.applyStyle(inputTextView)
            mGraphicManager.updateView(view)
        }
    }

    override fun addEmoji(emojiName: String?): View? {
        return addEmoji(null, emojiName)
    }

    override fun addEmoji(emojiTypeface: Typeface?, emojiName: String?): View? {
        drawingView?.enableDrawing(false)
        // NOTE(kleyow): Emoji disappear when they are too big for some reason.
        //               I believe screen density plays into it, investigate a suitable font size
        //               again.
        val multiTouchListener = getMultiTouchListener(true)
        val emoji = Emoji(
            photoEditorView,
            canvasView,
            multiTouchListener,
            viewState,
            mOnPhotoEditorListener!!,
            mGraphicManager,
            mDefaultEmojiTypeface
        )
        emoji.buildView(emojiTypeface, emojiName)
        addToEditor(emoji)

        multiTouchListener.itemRootFrameView = emoji.rootView
        val emojiTextView = emoji.rootView.findViewById<TextView>(R.id.tvPhotoEditorText)
        emojiTextView.textSize = 70f
        emojiTextView.text = emojiName
        emoji.rootView.setOnTouchListener(multiTouchListener)
        val multiTouchListenerByView = viewState.multiTouchListenerByView
        multiTouchListenerByView.put(emoji.rootView, multiTouchListener)
        if (mOnPhotoEditorListener != null) {
            mOnPhotoEditorListener!!.onInFocusViewChangeListener(
                emoji.rootView
            )
        }
        return emoji.rootView
    }

    private fun addToEditor(graphic: Graphic) {
        clearHelperBox()
        mGraphicManager.addView(graphic)
        // Change the in-focus view
        viewState.currentSelectedView = graphic.rootView
    }

    /**
     * Create a new instance and scalable touchview
     *
     * @param isPinchScalable true if make pinch-scalable, false otherwise.
     * @return scalable multitouch listener
     */
    private fun getMultiTouchListener(isPinchScalable: Boolean): MultiTouchListener {
        return MultiTouchListener(
            deleteView,
            photoEditorView,
            canvasView,
            mainImageView!!,
            isPinchScalable,
            mOnPhotoEditorListener,
            viewState
        )
    }

    override fun setBrushDrawingMode(brushDrawingMode: Boolean) {
        drawingView?.enableDrawing(brushDrawingMode)
    }

    override val brushDrawableMode: Boolean
        get() = drawingView != null && drawingView.isDrawingEnabled

    override fun setOpacity(@IntRange(from = 0, to = 100) opacity: Int) {
        var opacityValue = opacity
        opacityValue = (opacityValue / 100.0 * 255.0).toInt()
        drawingView?.currentShapeBuilder?.withShapeOpacity(opacityValue)
    }

    override var brushSize: Float
        get() = drawingView?.currentShapeBuilder?.shapeSize ?: 0f
        set(size) {
            drawingView?.currentShapeBuilder?.withShapeSize(size)
        }
    override var brushColor: Int
        get() = drawingView?.currentShapeBuilder?.shapeColor ?: 0
        set(color) {
            drawingView?.currentShapeBuilder?.withShapeColor(color)
        }

    override fun setBrushEraserSize(brushEraserSize: Float) {
        drawingView?.eraserSize = brushEraserSize
    }

    override val eraserSize: Float
        get() = drawingView?.eraserSize ?: 0f

    override fun brushEraser() {
        drawingView?.brushEraser()
    }

    override fun undo(): Boolean {
        return mGraphicManager.undoView()
    }

    override fun redo(): Boolean {
        return mGraphicManager.redoView()
    }

    override fun removeInFocusView() {
        val inFocusView = viewState.currentSelectedView
        if (inFocusView == null || viewState.addedViewsCount == 0) {
            return
        }

        // Remove the view from ViewState and the UI tree.
        val multiTouchListenerByView = viewState.multiTouchListenerByView
        multiTouchListenerByView.remove(inFocusView)
        viewState.removeAddedView(inFocusView)
        canvasView.removeView(inFocusView)

        // Fire the callback if the listener exists.
        if (mOnPhotoEditorListener != null) {
            val viewTag = inFocusView.tag
            if (viewTag != null && viewTag is ViewType) {
                mOnPhotoEditorListener!!.onRemoveViewListener(
                    viewTag,
                    viewState.addedViewsCount
                )
            }
            mOnPhotoEditorListener!!.onInFocusViewChangeListener(null)
        }
    }

    override fun mirrorInFocusView() {
        val inFocusView = viewState.currentSelectedView ?: return

        // Determine if image (sticker/emoji) or text, and rotate appropriately.
        if (inFocusView.findViewById<View>(R.id.imgPhotoEditorImage) is ImageView) {
            if (inFocusView.findViewById<View>(R.id.imgPhotoEditorImage).rotationY == 180f) {
                inFocusView.findViewById<View>(R.id.imgPhotoEditorImage).rotationY = 0f
            } else {
                inFocusView.findViewById<View>(R.id.imgPhotoEditorImage).rotationY = 180f
            }
        } else if (inFocusView.findViewById<View>(R.id.tvPhotoEditorText) is TextView) {
            if (inFocusView.findViewById<View>(R.id.tvPhotoEditorText).rotationY == 180f) {
                inFocusView.findViewById<View>(R.id.tvPhotoEditorText).rotationY = 0f
            } else {
                inFocusView.findViewById<View>(R.id.tvPhotoEditorText).rotationY = 180f
            }
        }

        // Clear the intersection pixel map to force a recalculation of the transparent click-through.
        val multiTouchListener = viewState.multiTouchListenerByView[inFocusView]
        if (multiTouchListener != null) {
            multiTouchListener.scaledImageIntersectionPixelMap = null
        }
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onMirrorViewListener()
    }

    override fun bringToFrontInFocusView() {
        val inFocusView = viewState.currentSelectedView ?: return
        inFocusView.bringToFront()
    }

    override fun unfocusView() {
        clearHelperBox()
    }

    override fun clearAllViews() {
        mBoxHelper.clearAllViews(drawingView)
    }

    override fun clearHelperBox() {
        mBoxHelper.clearHelperBox()
        if (mOnPhotoEditorListener != null) {
            mOnPhotoEditorListener!!.onInFocusViewChangeListener(
                null
            )
        }
    }

    override fun setFilterEffect(customEffect: CustomEffect) {
        photoEditorView.setFilterEffect(customEffect)
    }

    override fun setFilterEffect(filterType: PhotoFilter) {
        photoEditorView.setFilterEffect(filterType)
    }

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    override suspend fun saveAsFile(
        imagePath: String,
        saveSettings: SaveSettings
    ): SaveFileResult = withContext(Dispatchers.Main) {
        photoEditorView.saveFilter()
        val photoSaverTask = PhotoSaverTask(photoEditorView, mBoxHelper, saveSettings)
        return@withContext photoSaverTask.saveImageAsFile(imagePath)
    }

    override suspend fun saveAsBitmap(
        saveSettings: SaveSettings
    ): Bitmap? = withContext(Dispatchers.Main) {
        photoEditorView.saveFilter()
        val photoSaverTask = PhotoSaverTask(photoEditorView, mBoxHelper, saveSettings)
        return@withContext photoSaverTask.saveImageAsBitmap()
    }

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    override fun saveAsFile(
        imagePath: String,
        saveSettings: SaveSettings,
        onSaveListener: PhotoEditor.OnSaveListener
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            when (val result = saveAsFile(imagePath, saveSettings)) {
                is SaveFileResult.Success -> onSaveListener.onSuccess(imagePath)
                is SaveFileResult.Failure -> onSaveListener.onFailure(result.exception)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    override fun saveAsFile(imagePath: String, onSaveListener: PhotoEditor.OnSaveListener) {
        saveAsFile(imagePath, SaveSettings.Builder().build(), onSaveListener)
    }

    override fun saveAsBitmap(saveSettings: SaveSettings, onSaveBitmap: OnSaveBitmap) {
        GlobalScope.launch(Dispatchers.Main) {
            val bitmap = saveAsBitmap(saveSettings)
            onSaveBitmap.onBitmapReady(bitmap)
        }
    }

    override fun saveAsBitmap(onSaveBitmap: OnSaveBitmap) {
        saveAsBitmap(SaveSettings.Builder().build(), onSaveBitmap)
    }

//    @SuppressLint("StaticFieldLeak")
//    override fun saveAsBitmap(
//        saveSettings: SaveSettings,
//        onSaveBitmap: OnSaveBitmap
//    ) {
//        photoEditorView.saveFilter(object : OnSaveBitmap {
//            override fun onBitmapReady(saveBitmap: Bitmap?) {
//                val photoSaverTask = PhotoSaverTask(photoEditorView, mBoxHelper)
//                photoSaverTask.setOnSaveBitmap(onSaveBitmap)
//                photoSaverTask.setSaveSettings(saveSettings)
//                photoSaverTask.saveBitmap()
//            }
//
//            override fun onFailure(e: Exception?) {
//                onSaveBitmap.onFailure(e)
//            }
//        })
//    }

    override fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener) {
        mOnPhotoEditorListener = onPhotoEditorListener
        mGraphicManager.onPhotoEditorListener = mOnPhotoEditorListener
        mBrushDrawingStateListener.setOnPhotoEditorListener(mOnPhotoEditorListener)
        mEditorTouchListener.setOnPhotoEditorListener(mOnPhotoEditorListener)
    }

    override val isCacheEmpty: Boolean
        get() = viewState.addedViewsCount == 0 && viewState.redoViewsCount == 0

    // region Shape
    override fun setShape(shapeBuilder: ShapeBuilder) {
        drawingView?.currentShapeBuilder = shapeBuilder
    } // endregion

    override fun lockMainImage() {
        photoEditorView.lockedZoom = true
    }

    override fun unlockMainImage() {
        photoEditorView.lockedZoom = false
    }

    override fun getMainImageLockValue(): Boolean {
        return photoEditorView.lockedZoom
    }

    companion object {
        private const val TAG = "PhotoEditor"
    }

    init {
        drawingView?.setBrushViewChangeListener(mBrushDrawingStateListener)

        // NOTE(cheng): Port this logic to PhotoEditorImageViewListener.kt
        // Create scaling logic for background image.
        mEditorTouchListener = EditorTouchListener(
            photoEditorView,
            canvasView,
            viewState
        )
        photoEditorView.parentLayout?.setOnTouchListener(mEditorTouchListener)
    }
}