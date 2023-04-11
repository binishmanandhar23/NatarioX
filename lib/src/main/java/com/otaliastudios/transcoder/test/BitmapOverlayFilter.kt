package com.otaliastudios.transcoder.test

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.program.GlTextureProgram
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class BitmapOverlayFilter(): BaseOverlayFilter() {
    private val tag = BitmapOverlayFilter::class.java.simpleName

    private val TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    private val TEXTURE_UNIT = GLES20.GL_TEXTURE0

    var overlayTextureID = -12346

    private var context: Context? = null

    private lateinit var bitmap: Bitmap

    constructor(context: Context, bitmap: Bitmap) : this() {
        this.context = context
        this.bitmap = bitmap
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
        super.onCreate(programHandle)
        program = GlTextureProgram(
            programHandle,
            vertexPositionName,
            vertexModelViewProjectionMatrixName,
            vertexTextureCoordinateName,
            vertexTransformMatrixName
        )
        programDrawable = GlRect()

        overlayTextureID = createOverlayTexture(bitmap)
        setOverlayTextureId(overlayTextureId = overlayTextureID, bitmap = bitmap)

        /*if(overlayTextureID >= 0)
            program?.texture = GlTexture(TEXTURE_UNIT, TEXTURE_TARGET, overlayTextureID)*/
    }


    private fun decodeBitmap(imageUri: Uri): Bitmap? {
        val options = BitmapFactory.Options()
        //options.inScaled = false // No pre-scaling

        var bitmap: Bitmap? = null
        if (ContentResolver.SCHEME_FILE == imageUri.scheme && imageUri.path != null) {
            val file = File(imageUri.path)
            bitmap = BitmapFactory.decodeFile(file.path, options)
        } else if (ContentResolver.SCHEME_CONTENT == imageUri.scheme) {
            val inputStream: InputStream?
            try {
                inputStream = context!!.contentResolver.openInputStream(imageUri)
                if (inputStream != null) {
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options)
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