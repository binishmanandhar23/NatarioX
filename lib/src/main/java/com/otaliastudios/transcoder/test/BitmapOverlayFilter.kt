package com.otaliastudios.transcoder.test

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.program.GlTextureProgram
import com.otaliastudios.opengl.texture.GlTexture
import com.otaliastudios.transcoder.test.natario.Size
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class BitmapOverlayFilter(): BaseOverlayFilter() {
    private val tag = BitmapOverlayFilter::class.java.simpleName

    private val TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    private val TEXTURE_UNIT = GLES20.GL_TEXTURE0

    var overlayTextureID = -12346

    private var context: Context? = null

    constructor(context: Context, bitmap: Bitmap) : this() {
        this.context = context
        overlayTextureID = createOverlayTexture(bitmap)
    }

    constructor(context: Context, bitmapUri: Uri): this(){
        this.context = context
        val bitmap = decodeBitmap(bitmapUri)
        if (bitmap != null) {
            overlayTextureID = createOverlayTexture(bitmap)
            bitmap.recycle()
        }
    }

    @SuppressLint("VisibleForTests")
    override fun onCreate(programHandle: Int) {
        program = GlTextureProgram(
            programHandle,
            vertexPositionName,
            vertexModelViewProjectionMatrixName,
            vertexTextureCoordinateName,
            vertexTransformMatrixName
        )
        programDrawable = GlRect()
        /*if(overlayTextureID >= 0)
            program?.texture = GlTexture(TEXTURE_UNIT, TEXTURE_TARGET, overlayTextureID)*/
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
                Log.e(tag, "Unable to open overlay image Uri $imageUri", e)
            }
        } else {
            Log.e(tag, "Uri scheme is not supported: " + imageUri.scheme)
        }
        return bitmap
    }
}