package ja.burhanrashid52.photoeditor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import ja.burhanrashid52.photoeditor.FilterImageView.OnImageChangedListener

/**
 *
 *
 * This ViewGroup will have the [DrawingView] to draw paint on it with [ImageView]
 * which our source image
 *
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.1
 * @since 1/18/2018
 */
class PhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ZoomLayout(context, attrs, defStyle) {
    private var mImgSource: FilterImageView = FilterImageView(context)
    var drawingView: DrawingView
        private set
    private var mImageFilterView: ImageFilterView
    private var clipSourceImage = false

    /**
     * Overlay view which you want to edit
     * NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
     *
     * @return source ImageView
     */
    var imageOverlayView: ImageView? = null
        private set

    /**
     * Background view which you want to edit
     * NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
     *
     * @return source ImageView
     */
    var backgroundView: ImageView? = null
        private set

    /**
     * Parent layout which holds all sub views
     * NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
     *
     * @return source RelativeLayout
     */
    var parentLayout: RelativeLayout? = null
        private set
    var canvasLayout: RelativeLayout? = null
        private set

    init {
        //Setup image attributes
        val sourceParam = setupImageSource(attrs)
        //Setup GLSurface attributes
        mImageFilterView = ImageFilterView(context)
        val filterParam = setupFilterView()

        mImgSource.setOnImageChangedListener(object : OnImageChangedListener {
            override fun onBitmapLoaded(sourceBitmap: Bitmap?) {
                mImageFilterView.setFilterEffect(PhotoFilter.NONE)
                mImageFilterView.setSourceBitmap(sourceBitmap)
                Log.d(TAG, "onBitmapLoaded() called with: sourceBitmap = [$sourceBitmap]")
            }
        })


        //Setup drawing view
        drawingView = DrawingView(context)
        val brushParam = setupDrawingView()

        // NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
        imageOverlayView = ImageView(context)
        imageOverlayView!!.id = imgOverlayId
        val imgOverlayParam = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        // NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
        // TODO(kleyow): Need to add logic to handle when the image is square.
        backgroundView = ImageView(context)
        backgroundView!!.scaleType = ImageView.ScaleType.FIT_XY
        backgroundView!!.id = imgBackgroundId
        val imgBackgroundParam = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        // NOTE(kleyow): Order of addition of views is important.
        // Add background view
        canvasLayout!!.addView(backgroundView, imgBackgroundParam)

        //Add image source
        canvasLayout!!.addView(mImgSource, sourceParam)

        //Add Gl FilterView
        canvasLayout!!.addView(mImageFilterView, filterParam)

        //Add brush view
        canvasLayout!!.addView(drawingView, brushParam)
        parentLayout!!.addView(canvasLayout)

        // Add overlay view
        addView(imageOverlayView, imgOverlayParam)
    }

    @SuppressLint("Recycle")
    private fun setupImageSource(attrs: AttributeSet?): RelativeLayout.LayoutParams {
        mImgSource.id = imgSrcId
        mImgSource.adjustViewBounds = true
        mImgSource.scaleType = ImageView.ScaleType.FIT_CENTER

        val imgSrcParam = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        imgSrcParam.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)

        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.PhotoEditorView)
            val imgSrcDrawable = a.getDrawable(R.styleable.PhotoEditorView_photo_src)
            if (imgSrcDrawable != null) {
                mImgSource.setImageDrawable(imgSrcDrawable)
            }
        }

        var widthParam = ViewGroup.LayoutParams.MATCH_PARENT
        if (clipSourceImage) {
            widthParam = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val params = RelativeLayout.LayoutParams(
            widthParam, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        return params
    }

    private fun setupDrawingView(): RelativeLayout.LayoutParams {
        drawingView.visibility = GONE
        drawingView.id = shapeSrcId

        // Align drawing view to the size of image view
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        params.addRule(RelativeLayout.ALIGN_TOP, imgSrcId)
        params.addRule(RelativeLayout.ALIGN_BOTTOM, imgSrcId)
        params.addRule(RelativeLayout.ALIGN_LEFT, imgSrcId)
        params.addRule(RelativeLayout.ALIGN_RIGHT, imgSrcId)
        return params
    }

    private fun setupFilterView(): RelativeLayout.LayoutParams {
        mImageFilterView.visibility = GONE
        mImageFilterView.id = glFilterId

        //Align brush to the size of image view
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        params.addRule(RelativeLayout.ALIGN_TOP, imgSrcId)
        params.addRule(RelativeLayout.ALIGN_BOTTOM, imgSrcId)

        // NOTE(kleyow): This is custom added code diverging from https://github.com/burhanrashid52/PhotoEditor
        // NOTE(lucianocheng): This should be renamed from 'parent'
        parentLayout = RelativeLayout(context)
        parentLayout!!.id = parentLayoutId
        val parentLayoutParam = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        // `ZoomLayout` must have only one child, so this will be the container for all sub-views.
        // NOTE(cheng): This should be moved out of this method
        addView(parentLayout, parentLayoutParam)

        // NOTE(kleyow): Seperate the view into layers so functionality is not fighting over a
        //               view's pivot. Better seperation of layouts here could be an improvement.
        // NOTE(cheng): This should be moved out of this method
        canvasLayout = RelativeLayout(context)
        canvasLayout!!.id = parentLayoutId
        val rotateLayoutParam = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        return params
    }

    /**
     * Source image which you want to edit
     *
     * @return source ImageView
     */
    val source: ImageView?
        get() = mImgSource

    fun resetSourceImageSettings() {
        // NOTE(kleyow): Need to reset image after changing the main image because Zooming changes
        //               the settings.
        mImgSource!!.adjustViewBounds = true
        mImgSource!!.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun saveFilter(onSaveBitmap: OnSaveBitmap) {
        if (mImageFilterView!!.visibility == VISIBLE) {
            mImageFilterView!!.saveBitmap(object : OnSaveBitmap {
                override fun onBitmapReady(saveBitmap: Bitmap?) {
                    Log.e(TAG, "saveFilter: $saveBitmap")
                    mImgSource!!.setImageBitmap(saveBitmap!!)
                    mImageFilterView!!.visibility = GONE
                    onSaveBitmap.onBitmapReady(saveBitmap)
                }

                override fun onFailure(e: Exception?) {
                    onSaveBitmap.onFailure(e)
                }
            })
        } else {
            onSaveBitmap.onBitmapReady(mImgSource!!.bitmap)
        }
    }

    fun setFilterEffect(filterType: PhotoFilter?) {
        mImageFilterView.visibility = VISIBLE
        mImageFilterView.setSourceBitmap(mImgSource.bitmap)
        mImageFilterView.setFilterEffect(filterType)
    }

    fun setFilterEffect(customEffect: CustomEffect?) {
        mImageFilterView.visibility = VISIBLE
        mImageFilterView.setSourceBitmap(mImgSource.bitmap)
        mImageFilterView.setFilterEffect(customEffect)
    }

    fun setClipSourceImage(clip: Boolean) {
        clipSourceImage = clip
        val param = setupImageSource(null)
        mImgSource.layoutParams = param
    } // endregion

    companion object {
        private const val TAG = "PhotoEditorView"
        private const val imgSrcId = 1
        private const val shapeSrcId = 2
        private const val glFilterId = 3
        private const val imgOverlayId = 4
        private const val imgBackgroundId = 5
        private const val parentLayoutId = 6
    }
}