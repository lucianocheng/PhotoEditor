package ja.burhanrashid52.photoeditor

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.IntRange
import androidx.annotation.RequiresPermission
import ja.burhanrashid52.photoeditor.PhotoEditor.OnSaveListener
import ja.burhanrashid52.photoeditor.ZoomLayout.lockedZoom
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder

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
class PhotoEditorImpl @SuppressLint("ClickableViewAccessibility") constructor(builder: PhotoEditor.Builder) :
    PhotoEditor {
    private val editorView: PhotoEditorView?
    override val viewState: PhotoEditorViewState
    private val mainImageView: ImageView?
    private val deleteView: View?
    private val drawingView: DrawingView?
    private val mBrushDrawingStateListener: BrushDrawingStateListener
    private val mBoxHelper: BoxHelper
    private var mOnPhotoEditorListener: OnPhotoEditorListener? = null
    private val isTextPinchScalable: Boolean
    private val mDefaultTextTypeface: Typeface?
    private val mDefaultEmojiTypeface: Typeface?
    private val mGraphicManager: GraphicManager

    // NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
    private val mEditorTouchListener: EditorTouchListener
    private val canvasView: RelativeLayout?
    private val overlayView: ImageView?
    private val backgroundView: ImageView?

    init {
        val context = builder.context
        editorView = builder.editorView
        canvasView = builder.canvasView
        mainImageView = builder.imageView
        deleteView = builder.deleteView
        drawingView = builder.drawingView
        overlayView = builder.overlayView
        backgroundView = builder.backgroundView
        isTextPinchScalable = builder.isTextPinchScalable
        mDefaultTextTypeface = builder.textTypeface
        mDefaultEmojiTypeface = builder.emojiTypeface
        viewState = PhotoEditorViewState()
        mGraphicManager = GraphicManager(builder.canvasView!!, viewState)
        mBoxHelper = BoxHelper(builder.canvasView!!, viewState)
        mBrushDrawingStateListener = BrushDrawingStateListener(builder.editorView, viewState)
        drawingView!!.setBrushViewChangeListener(mBrushDrawingStateListener)

        // Create scaling logic for background image.
        mEditorTouchListener = EditorTouchListener(
            editorView,
            canvasView!!,
            viewState
        )
        editorView.parentLayout.setOnTouchListener(mEditorTouchListener)
    }

    /**
     * This will rotate the main image and all sub views on [PhotoEditorView]
     * NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
     */
    override fun rotateImage(rotation: Float) {
        // Rotates the background image, main image, brush view and all currently placed stickers.
        canvasView!!.rotation = rotation
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onRotateViewListener()
    }

    override fun addImage(desiredImage: Bitmap?): View? {
        drawingView!!.enableDrawing(false)
        val multiTouchListener = getMultiTouchListener(true)
        val sticker = Sticker(
            canvasView!!,
            editorView!!,
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

    override fun addText(text: String?, colorCodeTextView: Int): View {
        return addText(null, text, colorCodeTextView)
    }

    override fun addText(textTypeface: Typeface?, text: String?, colorCodeTextView: Int): View {
        val styleBuilder = TextStyleBuilder()
        styleBuilder.withTextColor(colorCodeTextView)
        if (textTypeface != null) {
            styleBuilder.withTextFont(textTypeface)
        }
        return addText(text, styleBuilder)
    }

    override fun addText(text: String?, styleBuilder: TextStyleBuilder?): View {
        drawingView!!.enableDrawing(false)
        val multiTouchListener = getMultiTouchListener(isTextPinchScalable)
        val textGraphic = Text(
            canvasView!!,
            editorView!!,
            multiTouchListener,
            viewState,
            mOnPhotoEditorListener!!,
            mDefaultTextTypeface,
            mGraphicManager
        )
        textGraphic.buildView(text, styleBuilder)
        multiTouchListener.itemRootFrameView = textGraphic.rootView
        textGraphic.rootView.setOnTouchListener(multiTouchListener)
        addToEditor(textGraphic)
        val multiTouchListenerByView = viewState.multiTouchListenerByView
        multiTouchListenerByView.put(textGraphic.rootView, multiTouchListener)
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onInFocusViewChangeListener(
            textGraphic.rootView
        )
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

    override fun addEmoji(emojiName: String?): View {
        return addEmoji(null, emojiName)
    }

    override fun addEmoji(emojiTypeface: Typeface?, emojiName: String?): View {
        drawingView!!.enableDrawing(false)
        // NOTE(kleyow): Emoji disappear when they are too big for some reason.
        //               I believe screen density plays into it, investigate a suitable font size
        //               again.
        val multiTouchListener = getMultiTouchListener(true)
        val emoji = Emoji(
            editorView!!,
            canvasView!!,
            multiTouchListener,
            viewState,
            mOnPhotoEditorListener!!,
            mGraphicManager,
            mDefaultEmojiTypeface
        )
        emoji.buildView(emojiTypeface, emojiName)
        multiTouchListener.itemRootFrameView = emoji.rootView
        val emojiTextView = emoji.rootView.findViewById<TextView>(R.id.tvPhotoEditorText)
        emojiTextView.textSize = 70f
        emojiTextView.text = emojiName
        emoji.rootView.setOnTouchListener(multiTouchListener)
        addToEditor(emoji)
        val multiTouchListenerByView = viewState.multiTouchListenerByView
        multiTouchListenerByView.put(emoji.rootView, multiTouchListener)
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onInFocusViewChangeListener(
            emoji.rootView
        )
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
            editorView!!,
            canvasView!!,
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
        var opacity = opacity
        if (drawingView != null && drawingView.currentShapeBuilder != null) {
            opacity = (opacity / 100.0 * 255.0).toInt()
            drawingView.currentShapeBuilder!!.withShapeOpacity(opacity)
        }
    }

    override var brushSize: Float
        get() = if (drawingView != null && drawingView.currentShapeBuilder != null) {
            drawingView.currentShapeBuilder!!.shapeSize
        } else 0
        set(size) {
            if (drawingView != null && drawingView.currentShapeBuilder != null) {
                drawingView.currentShapeBuilder!!.withShapeSize(size)
            }
        }
    override var brushColor: Int
        get() = if (drawingView != null && drawingView.currentShapeBuilder != null) {
            drawingView.currentShapeBuilder!!.shapeColor
        } else 0
        set(color) {
            if (drawingView != null && drawingView.currentShapeBuilder != null) {
                drawingView.currentShapeBuilder!!.withShapeColor(color)
            }
        }

    override fun setBrushEraserSize(brushEraserSize: Float) {
        if (drawingView != null) {
            drawingView.eraserSize = brushEraserSize
        }
    }

    override val eraserSize: Float
        get() = drawingView?.eraserSize ?: 0

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
        canvasView!!.removeView(inFocusView)

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
        if (mOnPhotoEditorListener != null) mOnPhotoEditorListener!!.onInFocusViewChangeListener(
            null
        )
    }

    override fun setFilterEffect(customEffect: CustomEffect?) {
        editorView!!.setFilterEffect(customEffect)
    }

    override fun setFilterEffect(filterType: PhotoFilter?) {
        editorView!!.setFilterEffect(filterType)
    }

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    override fun saveAsFile(imagePath: String, onSaveListener: OnSaveListener) {
        saveAsFile(imagePath, SaveSettings.Builder().build(), onSaveListener)
    }

    @SuppressLint("StaticFieldLeak")
    override fun saveAsFile(
        imagePath: String,
        saveSettings: SaveSettings,
        onSaveListener: OnSaveListener
    ) {
        Log.d(TAG, "Image Path: $imagePath")
        editorView!!.saveFilter(object : OnSaveBitmap {
            override fun onBitmapReady(saveBitmap: Bitmap?) {
                val photoSaverTask = PhotoSaverTask(editorView, mBoxHelper)
                photoSaverTask.setOnSaveListener(onSaveListener)
                photoSaverTask.setSaveSettings(saveSettings)
                photoSaverTask.execute(imagePath)
            }

            override fun onFailure(e: Exception?) {
                onSaveListener.onFailure(e!!)
            }
        })
    }

    override fun saveAsBitmap(onSaveBitmap: OnSaveBitmap) {
        saveAsBitmap(SaveSettings.Builder().build(), onSaveBitmap)
    }

    @SuppressLint("StaticFieldLeak")
    override fun saveAsBitmap(
        saveSettings: SaveSettings,
        onSaveBitmap: OnSaveBitmap
    ) {
        editorView!!.saveFilter(object : OnSaveBitmap {
            override fun onBitmapReady(saveBitmap: Bitmap?) {
                val photoSaverTask = PhotoSaverTask(editorView, mBoxHelper)
                photoSaverTask.setOnSaveBitmap(onSaveBitmap)
                photoSaverTask.setSaveSettings(saveSettings)
                photoSaverTask.saveBitmap()
            }

            override fun onFailure(e: Exception?) {
                onSaveBitmap.onFailure(e)
            }
        })
    }

    override fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener) {
        mOnPhotoEditorListener = onPhotoEditorListener
        mGraphicManager.onPhotoEditorListener = mOnPhotoEditorListener
        mBrushDrawingStateListener.setOnPhotoEditorListener(mOnPhotoEditorListener)
        mEditorTouchListener.setOnPhotoEditorListener(mOnPhotoEditorListener)
    }

    override val isCacheEmpty: Boolean
        get() = viewState.addedViewsCount == 0 && viewState.redoViewsCount == 0

    // region Shape
    override fun setShape(shapeBuilder: ShapeBuilder?) {
        drawingView!!.currentShapeBuilder = shapeBuilder
    }

    // endregion
    override fun lockMainImage() {
        editorView.lockedZoom = true
    }

    override fun unlockMainImage() {
        editorView.lockedZoom = false
    }

    companion object {
        private const val TAG = "PhotoEditor"
        private fun convertEmoji(emoji: String): String {
            val returnedEmoji: String
            returnedEmoji = try {
                val convertEmojiToInt = emoji.substring(2).toInt(16)
                String(Character.toChars(convertEmojiToInt))
            } catch (e: NumberFormatException) {
                ""
            }
            return returnedEmoji
        }
    }
}