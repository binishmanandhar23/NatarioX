package com.otaliastudios.transcoder.test

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.otaliastudios.opengl.draw.GlDrawable
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.program.GlTextureProgram
import com.otaliastudios.opengl.texture.GlTexture
import com.otaliastudios.transcoder.test.LiTr.GlRenderUtils
import com.otaliastudios.transcoder.test.natario.Filter
import com.otaliastudios.transcoder.test.natario.OneParameterFilter
import com.otaliastudios.transcoder.test.natario.Size
import com.otaliastudios.transcoder.test.natario.TwoParameterFilter

abstract class BaseOverlayFilter : Filter {
    private val tag = BaseOverlayFilter::class.java.simpleName

    override fun getFragmentShader(): String {
        return FRAGMENT_OVERLAY_SHADER
    }

    override fun getVertexShader(): String {
        return VERTEX_SHADER
    }

    @VisibleForTesting
    var program: GlTextureProgram? = null
    var programDrawable: GlDrawable? = null

    private var glOverlayProgram = 0
    private var overlayMvpMatrixHandle = 0
    private var overlayUstMatrixHandle = 0
    private val stMatrix = FloatArray(16)

    @VisibleForTesting
    var size: Size? = null

    private val DEFAULT_VERTEX_POSITION_NAME = "aPosition"

    private val DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME = "aTextureCoord"

    private val DEFAULT_VERTEX_MVP_MATRIX_NAME = "uMVPMatrix"

    private val DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME = "uTexMatrix"
    private val DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME = "vTextureCoord"

    var vertexPositionName = DEFAULT_VERTEX_POSITION_NAME
    var vertexTextureCoordinateName = DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME
    var vertexModelViewProjectionMatrixName = DEFAULT_VERTEX_MVP_MATRIX_NAME
    var vertexTransformMatrixName = DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME
    private var fragmentTextureCoordinateName = DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME


    override fun onCreate(programHandle: Int) {
        Matrix.setIdentityM(stMatrix, 0)
        Matrix.scaleM(stMatrix, 0, 1f, -1f, 1f)

        glOverlayProgram = programHandle
        // Get the location of our uniforms
        overlayMvpMatrixHandle = GLES20.glGetUniformLocation(glOverlayProgram, "uMVPMatrix")
        GlRenderUtils.checkGlError("glGetUniformLocation uMVPMatrix")
        if (overlayMvpMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uMVPMatrix")
        }
        overlayUstMatrixHandle = GLES20.glGetUniformLocation(glOverlayProgram, "uSTMatrix")
        GlRenderUtils.checkGlError("glGetUniformLocation uSTMatrix")
        if (overlayUstMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uSTMatrix")
        }
    }

    /*fun setGlProgram(program: GlTextureProgram, programDrawable: GlDrawable){
        this.program = program
        this.programDrawable = programDrawable
    }*/

    override fun onDestroy() {
        // Since we used the handle constructor of GlTextureProgram, calling release here
        // will NOT destroy the GL program. This is important because Filters are not supposed
        // to have ownership of programs. Creation and deletion happen outside, and deleting twice
        // would cause an error.
        program!!.release()
        program = null
        programDrawable = null
    }

    override fun setSize(width: Int, height: Int) {
        size = Size(width, height)
    }

    private var textureId = 0
    fun setTextureId(textureId: Int){
        this.textureId = textureId
    }

    override fun draw(timestampUs: Long, transformMatrix: FloatArray) {
        if (program == null) {
            Log.w(
                tag,
                "Filter.draw() called after destroying the filter. " + "This can happen rarely because of threading."
            )
        } else {
            // Switch to overlay texture
            GLES20.glUseProgram(program?.handle?: glOverlayProgram)
            /*GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)*/

            /*program?.texture = GlTexture(
                unit = GLES20.GL_TEXTURE0,
                target = GLES20.GL_TEXTURE_2D,
                id = textureId
            )*/


            GLES20.glUniformMatrix4fv(
                overlayMvpMatrixHandle,
                1,
                false,
                programDrawable!!.modelMatrix,
                0
            )
            GLES20.glUniformMatrix4fv(overlayUstMatrixHandle, 1, false, stMatrix, 0)


            // Enable blending
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)


            // For controlling opacity
            val opacityParam = GLES20.glGetUniformLocation(program?.handle?: glOverlayProgram, "opacity")
            GLES20.glUniform1f(opacityParam, 0.8f)


            // Call OpenGL to draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlRenderUtils.checkGlError("glDrawArrays")
            GLES20.glDisable(GLES20.GL_BLEND)


            onPreDraw(timestampUs, transformMatrix)
            onDraw(timestampUs)
            onPostDraw(timestampUs)
        }
    }

    private fun onPreDraw(timestampUs: Long, transformMatrix: FloatArray) {
        program!!.textureTransform = transformMatrix
        program!!.onPreDraw(programDrawable!!, programDrawable!!.modelMatrix)
    }

    private fun onDraw(timestampUs: Long) {
        program!!.onDraw(programDrawable!!)
    }

    private fun onPostDraw(timestampUs: Long) {
        program!!.onPostDraw(programDrawable!!)
    }

    override fun copy(): BaseOverlayFilter {
        val copy = onCopy()
        if (size != null) {
            copy.setSize(size!!.width, size!!.height)
        }
        if (this is OneParameterFilter) {
            (copy as OneParameterFilter).parameter1 = (this as OneParameterFilter).parameter1
        }
        if (this is TwoParameterFilter) {
            (copy as TwoParameterFilter).parameter2 = (this as TwoParameterFilter).parameter2
        }
        return copy
    }

    fun onCopy(): BaseOverlayFilter {
        return try {
            this.javaClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        } catch (e: InstantiationException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        }
    }

    /**
     * Create a texture and load the overlay bitmap into this texture.
     */
    fun createOverlayTexture(overlayBitmap: Bitmap): Int {
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

    private fun createDefaultVertexShader(
        vertexPositionName: String,
        vertexTextureCoordinateName: String,
        vertexModelViewProjectionMatrixName: String,
        vertexTransformMatrixName: String,
        fragmentTextureCoordinateName: String
    ): String {
        return """uniform mat4 $vertexModelViewProjectionMatrixName;
        uniform mat4 $vertexTransformMatrixName;
    attribute vec4 $vertexPositionName;
attribute vec4 $vertexTextureCoordinateName;
varying vec2 $fragmentTextureCoordinateName;
void main() {
    gl_Position = $vertexModelViewProjectionMatrixName * $vertexPositionName;
    $fragmentTextureCoordinateName = ($vertexTransformMatrixName * $vertexTextureCoordinateName).xy;
}
"""
    }

    private fun createDefaultFragmentShader(
        fragmentTextureCoordinateName: String
    ): String {
        return """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 $fragmentTextureCoordinateName;
uniform samplerExternalOES sTexture;
void main() {
  gl_FragColor = texture2D(sTexture, $fragmentTextureCoordinateName);
}
"""
    }

    companion object {
        private const val VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" + "uniform mat4 uTexMatrix;\n" + "attribute vec4 aPosition;\n" + "attribute vec4 aTextureCoord;\n" + "varying vec2 vTextureCoord;\n" + "void main() {\n" + "  gl_Position = uMVPMatrix * aPosition;\n" + "  vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" + "}\n"
        private const val FRAGMENT_OVERLAY_SHADER =
            "precision mediump float;\n" + "uniform sampler2D uTexture;\n" + "uniform float opacity;\n" + "varying vec2 vTextureCoord;\n" + "void main() {\n" + "  gl_FragColor = texture2D(uTexture, vTextureCoord);\n" + "  gl_FragColor.a *= opacity;\n" + "}\n"
    }
}