package com.otaliastudios.transcoder.test.natario

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.otaliastudios.opengl.draw.GlDrawable
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.extensions.clear
import com.otaliastudios.opengl.extensions.scale
import com.otaliastudios.opengl.program.GlTextureProgram
import com.otaliastudios.transcoder.test.LiTr.GlRenderUtils

open class BaseOverlayFilter: Filter {
    private val TAG = BaseOverlayFilter::class.java.simpleName
    override fun getVertexShader(): String = VERTEX_SHADER

    override fun getFragmentShader(): String = FRAGMENT_OVERLAY_SHADER

    var program: GlTextureProgram? = null
    private var programDrawable: GlDrawable? = null

    @VisibleForTesting
    var size: Size? = null

    private var vertexPositionName = BaseFilter.DEFAULT_VERTEX_POSITION_NAME
    private var vertexTextureCoordinateName = BaseFilter.DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME
    private var vertexModelViewProjectionMatrixName = BaseFilter.DEFAULT_VERTEX_MVP_MATRIX_NAME
    private var vertexTransformMatrixName = BaseFilter.DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME
    private var fragmentTextureCoordinateName =
        BaseFilter.DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME

    override fun onCreate(programHandle: Int) {
        program = GlTextureProgram(
            programHandle,
            vertexPositionName,
            vertexModelViewProjectionMatrixName,
            vertexTextureCoordinateName,
            vertexTransformMatrixName
        )
        programDrawable = GlRect()
    }

    override fun onDestroy() {
        // Since we used the handle constructor of GlTextureProgram, calling release here
        // will NOT destroy the GL program. This is important because Filters are not supposed
        // to have ownership of programs. Creation and deletion happen outside, and deleting twice
        // would cause an error.

        // Since we used the handle constructor of GlTextureProgram, calling release here
        // will NOT destroy the GL program. This is important because Filters are not supposed
        // to have ownership of programs. Creation and deletion happen outside, and deleting twice
        // would cause an error.
        program!!.release()
        program = null
        programDrawable = null
    }

    override fun draw(timestampUs: Long, transformMatrix: FloatArray) {
        if (program == null) {
            Log.w(
                TAG, "Filter.draw() called after destroying the filter. " +
                        "This can happen rarely because of threading."
            )
        } else {
            onPreDraw(timestampUs, transformMatrix)
            onDraw(timestampUs)
            onPostDraw(timestampUs)
        }
    }
    private fun onPreDraw(timestampUs: Long, transformMatrix: FloatArray) {
        program!!.textureTransform = transformMatrix
        program!!.onPreDraw(programDrawable!!, program!!.textureTransform)
    }

    private fun onDraw(timestampUs: Long) {
        // Enable blending
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program!!.onDraw(programDrawable!!)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun onPostDraw(timestampUs: Long) {
        program!!.onPostDraw(programDrawable!!)
    }

    override fun setSize(width: Int, height: Int) {
        size = Size(width, height)
    }

    override fun copy(): BaseOverlayFilter {
        val copy: BaseOverlayFilter = onCopy()
        if (size != null) {
            copy.setSize(size!!.width, size!!.height)
        }
        return copy
    }

    private fun onCopy(): BaseOverlayFilter {
        return try {
            javaClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        } catch (e: InstantiationException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        }
    }

    /**
     * Create a texture and load the overlay bitmap into this texture.
     */
    open fun createOverlayTexture(overlayBitmap: Bitmap): Int {
        val overlayTextureID: Int

        // Generate one texture for overlay
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        overlayTextureID = textures[0]

        // Tell OpenGL to bind this texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureID)
        GlRenderUtils.checkGlError("glBindTexture overlayTextureID")

        // Set default texture filtering parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GlRenderUtils.checkGlError("glTexParameter")

        // Load the bitmap and copy it over into the texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)
        return overlayTextureID
    }

    companion object{
        const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_Position = uMVPMatrix * aPosition;\n" +
                "  vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                "}\n"

        const val FRAGMENT_OVERLAY_SHADER = "precision mediump float;\n" +
                "uniform sampler2D uTexture;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
                "}\n"
    }
}