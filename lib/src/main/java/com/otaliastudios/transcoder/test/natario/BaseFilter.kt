package com.otaliastudios.transcoder.test.natario

import android.graphics.PointF
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.otaliastudios.opengl.draw.GlDrawable
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.program.GlTextureProgram
import com.otaliastudios.opengl.types.ByteBuffer
import com.otaliastudios.opengl.types.FloatBuffer
import com.otaliastudios.transcoder.test.BaseOverlayFilter
import com.otaliastudios.transcoder.test.LiTr.GlFilterUtil
import com.otaliastudios.transcoder.test.LiTr.GlRenderUtils
import com.otaliastudios.transcoder.test.LiTr.ShaderParameter
import com.otaliastudios.transcoder.test.LiTr.Transform
import java.nio.ByteOrder

/**
 * A base implementation of [Filter] that just leaves the fragment shader to subclasses.
 * See [*NoFilter*] for a non-abstract implementation.
 *
 * This class offers a default vertex shader implementation which in most cases is not required
 * to be changed. Most effects can be rendered by simply changing the fragment shader, thus
 * by overriding [.getFragmentShader].
 *
 * All [BaseFilter]s should have a no-arguments public constructor.
 * This class will try to automatically implement [.copy] thanks to this.
 * If your filter implements public parameters, please implement [OneParameterFilter]
 * and [TwoParameterFilter] to handle them and have them passed automatically to copies.
 *
 * NOTE - This class expects variable to have a certain name:
 * - [.vertexPositionName]
 * - [.vertexTransformMatrixName]
 * - [.vertexModelViewProjectionMatrixName]
 * - [.vertexTextureCoordinateName]
 * - [.fragmentTextureCoordinateName]
 * You can either change these variables, for example in your constructor, or change your
 * vertex and fragment shader code to use them.
 *
 * NOTE - the [android.graphics.SurfaceTexture] restrictions apply:
 * We only support the [android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES] texture target
 * and it must be specified in the fragment shader as a samplerExternalOES texture.
 * You also have to explicitly require the extension: see
 * [.createDefaultFragmentShader].
 *
 */
abstract class BaseFilter : Filter {
    @VisibleForTesting
    var program: GlTextureProgram? = null
    private var programDrawable: GlDrawable? = null

    var transform: Transform? = null
    private var shaderParameters: Array<ShaderParameter>? = null

    private var mvpMatrix = FloatArray(16)
    private var inputFrameTextureMatrix = FloatArray(16)
    private var mvpMatrixOffset = 0
    private val triangleVertices: java.nio.FloatBuffer
    private val triangleVerticesData = floatArrayOf( // X, Y, Z, U, V
        -1.0f, -1.0f, 0f, 0f, 0f,
        1.0f, -1.0f, 0f, 1f, 0f,
        -1.0f, 1.0f, 0f, 0f, 1f,
        1.0f, 1.0f, 0f, 1f, 1f
    )
    private var vertexShaderHandle = 0
    private var fragmentShaderHandle = 0
    private var glProgram = 0
    private var mvpMatrixHandle = 0
    private var uStMatrixHandle = 0
    private var inputFrameTextureHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0

    init {
        this.transform = transform ?: Transform(PointF(1f, 1f), PointF(0.5f, 0.5f), 0f)
        triangleVertices = java.nio.ByteBuffer.allocateDirect(
            triangleVerticesData.size * FLOAT_SIZE_BYTES
        )
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        triangleVertices.put(triangleVerticesData).position(0)
    }


    @VisibleForTesting
    var size: Size? = null
    protected var vertexPositionName = DEFAULT_VERTEX_POSITION_NAME
    protected var vertexTextureCoordinateName = DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME
    protected var vertexModelViewProjectionMatrixName = DEFAULT_VERTEX_MVP_MATRIX_NAME
    protected var vertexTransformMatrixName = DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME
    protected var fragmentTextureCoordinateName = DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME
    protected fun createDefaultVertexShader(): String {
        return createDefaultVertexShader(
            vertexPositionName,
            vertexTextureCoordinateName,
            vertexModelViewProjectionMatrixName,
            vertexTransformMatrixName,
            fragmentTextureCoordinateName
        )
    }

    protected fun createDefaultFragmentShader(): String {
        return createDefaultFragmentShader(fragmentTextureCoordinateName)
    }


    override fun onCreate(programHandle: Int) {
        /*program = GlTextureProgram(
            programHandle,
            vertexPositionName,
            vertexModelViewProjectionMatrixName,
            vertexTextureCoordinateName,
            vertexTransformMatrixName
        )
        programDrawable = GlRect()
        programDrawable?.vertexArray = mBitmapTriangleVertices
        programDrawable?.vertexArray?.put(mBitmapTriangleVerticesData)?.position(0)
        mainVertexArray?.invoke(programDrawable?.vertexArray) //mBitmapTriangleVertices*/
        init()
    }

    fun initInputFrameTexture(textureHandle: Int, transformMatrix: FloatArray) {
        inputFrameTextureHandle = textureHandle
        inputFrameTextureMatrix = transformMatrix
    }

    private fun init(){
        Matrix.setIdentityM(inputFrameTextureMatrix, 0)
        vertexShaderHandle = GlRenderUtils.loadShader(GLES20.GL_VERTEX_SHADER, DEFAULT_VERTEX_SHADER)
        if (vertexShaderHandle == 0) {
            throw RuntimeException("failed loading vertex shader")
        }
        fragmentShaderHandle = GlRenderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, DEFAULT_FRAGMENT_SHADER)
        if (fragmentShaderHandle == 0) {
            onDestroy()
            throw RuntimeException("failed loading fragment shader")
        }
        glProgram = GlRenderUtils.createProgram(vertexShaderHandle, fragmentShaderHandle)
        if (glProgram == 0) {
            onDestroy()
            throw RuntimeException("failed creating glProgram")
        }
        aPositionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
        GlRenderUtils.checkGlError("glGetAttribLocation aPosition")
        if (aPositionHandle == -1) {
            throw RuntimeException("Could not get attrib location for aPosition")
        }
        aTextureHandle = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")
        GlRenderUtils.checkGlError("glGetAttribLocation aTextureCoord")
        if (aTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for aTextureCoord")
        }
        mvpMatrixHandle = GLES20.glGetUniformLocation(glProgram, "uMVPMatrix")
        GlRenderUtils.checkGlError("glGetUniformLocation uMVPMatrix")
        if (mvpMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uMVPMatrix")
        }
        uStMatrixHandle = GLES20.glGetUniformLocation(glProgram, "uTexMatrix")
        GlRenderUtils.checkGlError("glGetUniformLocation uTexMatrix")
        if (uStMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uTexMatrix")
        }
    }

    fun setVpMatrix(vpMatrix: FloatArray, vpMatrixOffset: Int) {
        mvpMatrix = GlFilterUtil.createFilterMvpMatrix(vpMatrix, transform!!)
        mvpMatrixOffset = vpMatrixOffset
    }

    override fun onDestroy() {
        // Since we used the handle constructor of GlTextureProgram, calling release here
        // will NOT destroy the GL program. This is important because Filters are not supposed
        // to have ownership of programs. Creation and deletion happen outside, and deleting twice
        // would cause an error.
        program?.release()
        program = null
        programDrawable = null

        GLES20.glDeleteProgram(glProgram)
        GLES20.glDeleteShader(vertexShaderHandle)
        GLES20.glDeleteShader(fragmentShaderHandle)
        GLES20.glDeleteBuffers(1, intArrayOf(aTextureHandle), 0)
        glProgram = 0
        vertexShaderHandle = 0
        fragmentShaderHandle = 0
        aTextureHandle = 0
    }

    override fun getVertexShader(): String {
        return createDefaultVertexShader()
    }

    override fun setSize(width: Int, height: Int) {
        size = Size(width, height)
    }

    override fun draw(timestampUs: Long, transformMatrix: FloatArray) {
        /*if (program == null) {
            Log.w(
                TAG, "Filter.draw() called after destroying the filter. " +
                        "This can happen rarely because of threading."
            )
        } else {
            onPreDraw(timestampUs, transformMatrix)
            onDraw(timestampUs)
            onPostDraw(timestampUs)
        }*/
        apply(timestampUs = timestampUs)
    }

    private fun apply(timestampUs: Long){
        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        )
        GlRenderUtils.checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GlRenderUtils.checkGlError("glEnableVertexAttribArray aPositionHandle")
        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            aTextureHandle, 2, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        )
        GlRenderUtils.checkGlError("glVertexAttribPointer aTextureHandle")
        GLES20.glEnableVertexAttribArray(aTextureHandle)
        GlRenderUtils.checkGlError("glEnableVertexAttribArray aTextureHandle")
        GlRenderUtils.checkGlError("onDrawFrame start")
        GLES20.glUseProgram(glProgram)
        GlRenderUtils.checkGlError("glUseProgram")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputFrameTextureHandle)
        shaderParameters?.forEach { shaderParameter ->
            shaderParameter.apply(glProgram)
        }
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, mvpMatrixOffset)
        GLES20.glUniformMatrix4fv(uStMatrixHandle, 1, false, inputFrameTextureMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlRenderUtils.checkGlError("glDrawArrays")


    }

    protected fun onPreDraw(timestampUs: Long, transformMatrix: FloatArray) {
        program!!.textureTransform = transformMatrix
        program!!.onPreDraw(programDrawable!!, programDrawable!!.modelMatrix)
    }

    protected fun onDraw(timestampUs: Long) {
        program!!.onDraw(programDrawable!!)
    }

    protected fun onPostDraw(timestampUs: Long) {
        program!!.onPostDraw(programDrawable!!)
    }


    override fun copy(): BaseFilter {
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

    protected fun onCopy(): BaseFilter {
        return try {
            javaClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        } catch (e: InstantiationException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        }
    }

    companion object {
        private val TAG = BaseFilter::class.java.simpleName
        const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

        protected const val DEFAULT_VERTEX_POSITION_NAME = "aPosition"
        protected const val DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME = "aTextureCoord"
        protected const val DEFAULT_VERTEX_MVP_MATRIX_NAME = "uMVPMatrix"
        protected const val DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME = "uTexMatrix"
        protected const val DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME = "vTextureCoord"

        const val DEFAULT_VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main()\n" +
                "{\n" +
                "gl_Position = uMVPMatrix * aPosition;\n" +
                "vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                "}"
        protected const val DEFAULT_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +  // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main()\n" +
                    "{\n" +
                    "gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}"

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

        @JvmStatic
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
    }
}