package ja.burhanrashid52.photoeditor

//import android.annotation.SuppressLint
//import android.graphics.Bitmap
//import android.graphics.Canvas
//import android.os.AsyncTask
//import android.text.TextUtils
//import android.util.Log
//import android.view.View
//import ja.burhanrashid52.photoeditor.BitmapUtil.removeTransparency
//import ja.burhanrashid52.photoeditor.PhotoEditor.OnSaveListener
//import ja.burhanrashid52.photoeditor.PhotoSaverTask.SaveResult
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import ja.burhanrashid52.photoeditor.BitmapUtil.removeTransparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by Burhanuddin Rashid on 18/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal class PhotoSaverTask(
    private val photoEditorView: PhotoEditorView,
    private val boxHelper: BoxHelper,
    private var saveSettings: SaveSettings
) {

    private val drawingView: DrawingView = photoEditorView.drawingView

    private fun onBeforeSaveImage() {
        boxHelper.clearHelperBox()
        drawingView.destroyDrawingCache()
    }

    fun saveImageAsBitmap(): Bitmap? {
        onBeforeSaveImage()
        val bitmap = buildBitmap()
        if (saveSettings.isClearViewsEnabled) {
            boxHelper.clearAllViews(drawingView)
        }
        return bitmap
    }

    suspend fun saveImageAsFile(imagePath: String): SaveFileResult {
        onBeforeSaveImage()
        val capturedBitmap = buildBitmap()
            ?: return SaveFileResult.Failure(IOException("Failed to load the bitmap"))

        val result = withContext(Dispatchers.IO) {
            val file = File(imagePath)
            try {
                FileOutputStream(file, false).use { outputStream ->
                    capturedBitmap.compress(
                        saveSettings.compressFormat,
                        saveSettings.compressQuality,
                        outputStream
                    )
                    outputStream.flush()
                    outputStream.close()
                }
                // Log.d(TAG, "Filed Saved Successfully")
                SaveFileResult.Success
            } catch (e: IOException) {
                SaveFileResult.Failure(e)
            }
        }

        if (result is SaveFileResult.Success) {
            // Clear all views if it's enabled in save settings
            if (saveSettings.isClearViewsEnabled) {
                boxHelper.clearAllViews(drawingView)
            }
        }

        return result
    }

    private fun buildBitmap(): Bitmap? {
        return if (saveSettings.isTransparencyEnabled) {
            removeTransparency(captureView(photoEditorView))
        } else {
            captureView(photoEditorView)
        }
    }

//    private fun saveImageInFile(mImagePath: String): SaveResult {
//        val file = File(mImagePath)
//        return try {
//            val out = FileOutputStream(file, false)
//            if (mPhotoEditorView != null) {
//                val capturedBitmap = buildBitmap()
//                capturedBitmap?.compress(
//                    mSaveSettings.compressFormat,
//                    mSaveSettings.compressQuality,
//                    out
//                )
//            }
//            out.flush()
//            out.close()
//            Log.d(TAG, "Filed Saved Successfully")
//            SaveResult(null, mImagePath, null)
//        } catch (e: IOException) {
//            e.printStackTrace()
//            Log.e(TAG, "Failed to save File")
//            SaveResult(e, mImagePath, null)
//        }
//    }

//    override fun onPostExecute(saveResult: SaveResult) {
//        super.onPostExecute(saveResult)
//        if (TextUtils.isEmpty(saveResult.mImagePath)) {
//            handleBitmapCallback(saveResult)
//        } else {
//            handleFileCallback(saveResult)
//        }
//    }

//    private fun handleFileCallback(saveResult: SaveResult) {
//        val exception = saveResult.mException
//        val imagePath = saveResult.mImagePath
//        if (exception == null) {
//            //Clear all views if its enabled in save settings
//            if (mSaveSettings.isClearViewsEnabled) {
//                mBoxHelper.clearAllViews(mDrawingView)
//            }
//            assert(imagePath != null)
//            mOnSaveListener?.onSuccess(imagePath!!)
//        } else {
//            mOnSaveListener?.onFailure(exception)
//        }
//    }

//    private fun handleBitmapCallback(saveResult: SaveResult) {
//        val bitmap = saveResult.mBitmap
//        if (bitmap != null) {
//            if (mSaveSettings.isClearViewsEnabled) {
//                mBoxHelper.clearAllViews(mDrawingView)
//            }
//            mOnSaveBitmap?.onBitmapReady(bitmap)
//
//        } else {
//            mOnSaveBitmap?.onFailure(Exception("Failed to load the bitmap"))
//        }
//    }

    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

//    private fun captureView(view: View?): Bitmap? {
//        if (view == null) {
//            return null
//        }
//        val bitmap = Bitmap.createBitmap(
//            view.width,
//            view.height,
//            Bitmap.Config.ARGB_8888
//        )
//        val canvas = Canvas(bitmap)
//        view.draw(canvas)
//        return bitmap
//    }

//    fun saveBitmap() {
//        execute()
//    }
//
//    internal class SaveResult(
//        val mException: Exception?,
//        val mImagePath: String?,
//        val mBitmap: Bitmap?
//    )

    companion object {
        const val TAG = "PhotoSaverTask"
    }

//    init {
//        mPhotoEditorView = photoEditorView
//        mDrawingView = photoEditorView.drawingView
//        mBoxHelper = boxHelper
//        mSaveSettings = SaveSettings.Builder().build()
//    }
}