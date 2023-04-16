package com.otaliastudios.transcoder.test.natario

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.util.Log
import com.otaliastudios.opengl.texture.GlTexture
import com.otaliastudios.transcoder.test.LiTr.Transform
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class BitmapOverlayFilter : BaseOverlayFilter {
    private var context: Context? = null
    private var bitmapUri: Uri? = null
    private var bitmap: Bitmap? = null
    private var overlayTextureID = -12346


    /**
     * Create filter with bitmap URI, then scale, then position and then rotate the bitmap around its center as specified.
     * @param context context for accessing bitmap
     * @param bitmapUri bitmap [Uri]
     * @param transform [Transform] that defines bitmap positioning within target video frame
     */
    /*constructor(context: Context, bitmapUri: Uri, transform: Transform) : super(transform) {
        this.context = context
        this.bitmapUri = bitmapUri
    }*/

    /**
     * Create filter with client managed [Bitmap], then scale, then position and then rotate the bitmap around its center as specified.
     * @param bitmap client managed bitmap
     * @param transform [Transform] that defines bitmap positioning within target video frame
     */
    constructor(context: Context, bitmap: Bitmap) : super() {
        this.bitmap = bitmap
    }

    override fun onCreate(programHandle: Int) {
        super.onCreate(programHandle)
        if (bitmap != null) {
            overlayTextureID = createOverlayTexture(bitmap!!)
        } else {
            val bitmap = decodeBitmap(bitmapUri!!)
            if (bitmap != null) {
                overlayTextureID = createOverlayTexture(bitmap)
                bitmap.recycle()
            }
        }
    }


    override fun draw(timestampUs: Long, transformMatrix: FloatArray) {
        if(overlayTextureID >= 0) {
            program?.texture = GlTexture(
                unit = GLES20.GL_TEXTURE0,
                target = GLES20.GL_TEXTURE_2D, id = overlayTextureID
            )
            super.draw(timestampUs, transformMatrix)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GLES20.glDeleteTextures(1, intArrayOf(overlayTextureID), 0)
        overlayTextureID = 0
    }

    private fun decodeBitmap(imageUri: Uri): Bitmap? {
        var bitmap: Bitmap? = null
        if (ContentResolver.SCHEME_FILE == imageUri.scheme && imageUri.path != null) {
            val file = File(imageUri.path)
            bitmap = BitmapFactory.decodeFile(file.path)
        } else if (ContentResolver.SCHEME_CONTENT == imageUri.scheme) {
            val inputStream: InputStream?
            try {
                inputStream = context!!.contentResolver.openInputStream(imageUri)
                if (inputStream != null) {
                    bitmap = BitmapFactory.decodeStream(inputStream, null, null)
                }
            } catch (e: FileNotFoundException) {
                Log.e(
                    TAG,
                    "Unable to open overlay image Uri $imageUri", e
                )
            }
        } else {
            Log.e(TAG, "Uri scheme is not supported: " + imageUri.scheme)
        }
        return bitmap
    }

    companion object {
        private val TAG = BitmapOverlayFilter::class.java.simpleName
    }
}