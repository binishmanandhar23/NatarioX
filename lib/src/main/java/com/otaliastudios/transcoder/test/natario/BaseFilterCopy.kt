package com.otaliastudios.transcoder.test.natario

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.otaliastudios.opengl.draw.GlDrawable
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.program.GlTextureProgram
import com.otaliastudios.opengl.types.ByteBuffer
import com.otaliastudios.opengl.types.FloatBuffer
import com.otaliastudios.transcoder.test.BaseOverlayFilter
import java.nio.ByteOrder

/**
 * A base implementation of [Filter] that just leaves the fragment shader to subclasses.
 * See [*NoFilter*] for a non-abstract implementation.
 *
 * This class offers a default vertex shader implementation which in most cases is not required
 * to be changed. Most effects can be rendered by simply changing the fragment shader, thus
 * by overriding [.getFragmentShader].
 *
 * All [BaseFilterCopy]s should have a no-arguments public constructor.
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
abstract class BaseFilterCopy : Filter {
    @VisibleForTesting
    var program: GlTextureProgram? = null
    private var programDrawable: GlDrawable? = null

    private val mBitmapTriangleVerticesData = floatArrayOf( // X, Y, Z, U, V
        -1.0f, -1.0f, 0f, 0f, 0f,
        1.0f, -1.0f, 0f, 1f, 0f,
        -1.0f, 1.0f, 0f, 0f, 1f,
        1.0f, 1.0f, 0f, 1f, 1f
    )

    private var mBitmapTriangleVertices: FloatBuffer = ByteBuffer.allocateDirect(
        mBitmapTriangleVerticesData.size * BaseOverlayFilter.FLOAT_SIZE_BYTES
    )
        .order(ByteOrder.nativeOrder()).asFloatBuffer()


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

    private var mainVertexArray: ((FloatBuffer?) -> Unit)? = null
    fun getMainVertexArray(mainVertexArray: ((FloatBuffer?) -> Unit)){
        this.mainVertexArray = mainVertexArray
    }
    override fun onCreate(programHandle: Int) {
        program = GlTextureProgram(
            programHandle,
            vertexPositionName,
            vertexModelViewProjectionMatrixName,
            vertexTextureCoordinateName,
            vertexTransformMatrixName
        )
        programDrawable = GlRect()
        programDrawable?.vertexArray = mBitmapTriangleVertices
        programDrawable?.vertexArray?.put(mBitmapTriangleVerticesData)?.position(0)
        mainVertexArray?.invoke(programDrawable?.vertexArray) //mBitmapTriangleVertices
    }

    override fun onDestroy() {
        // Since we used the handle constructor of GlTextureProgram, calling release here
        // will NOT destroy the GL program. This is important because Filters are not supposed
        // to have ownership of programs. Creation and deletion happen outside, and deleting twice
        // would cause an error.
        program!!.release()
        program = null
        programDrawable = null
    }

    override fun getVertexShader(): String {
        return createDefaultVertexShader()
    }

    override fun setSize(width: Int, height: Int) {
        size = Size(width, height)
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

    override fun copy(): BaseFilterCopy {
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

    protected fun onCopy(): BaseFilterCopy {
        return try {
            javaClass.newInstance()
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        } catch (e: InstantiationException) {
            throw RuntimeException("Filters should have a public no-arguments constructor.", e)
        }
    }

    companion object {
        private val TAG = BaseFilterCopy::class.java.simpleName
        protected const val DEFAULT_VERTEX_POSITION_NAME = "aPosition"
        protected const val DEFAULT_VERTEX_TEXTURE_COORDINATE_NAME = "aTextureCoord"
        protected const val DEFAULT_VERTEX_MVP_MATRIX_NAME = "uMVPMatrix"
        protected const val DEFAULT_VERTEX_TRANSFORM_MATRIX_NAME = "uTexMatrix"
        protected const val DEFAULT_FRAGMENT_TEXTURE_COORDINATE_NAME = "vTextureCoord"
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