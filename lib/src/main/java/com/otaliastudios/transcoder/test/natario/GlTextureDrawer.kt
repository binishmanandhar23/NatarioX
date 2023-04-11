package com.otaliastudios.transcoder.test.natario

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.otaliastudios.opengl.core.Egloo.IDENTITY_MATRIX
import com.otaliastudios.opengl.core.Egloo.checkGlError
import com.otaliastudios.opengl.program.GlProgram
import com.otaliastudios.opengl.texture.GlTexture
import com.otaliastudios.opengl.types.FloatBuffer
import com.otaliastudios.transcoder.test.BaseOverlayFilter
import java.util.*

class GlTextureDrawer @JvmOverloads constructor(
    val texture: GlTexture = GlTexture(
        TEXTURE_UNIT, TEXTURE_TARGET
    )
) {
    var textureTransform: FloatArray = IDENTITY_MATRIX.clone()
    private var mFilter: Filter = NoFilter()
    private var mFilter2: Filter = NoFilter()
    private var mPendingFilter: Filter? = null
    private var mProgramHandle = -1
    private var mProgramHandle2 = -1

    var mBitmapaTextureHandle = -1
    var mBitmapaPositionHandle = -1
    private var mBitmapTriangleVertices: FloatBuffer? = null

    private val mvpMatrix = FloatArray(16)

    constructor(textureId: Int) : this(GlTexture(TEXTURE_UNIT, TEXTURE_TARGET, textureId)) {}

    init {
        initMvpMatrix(rotation = 0, videoAspectRatio = (9f/16f))
    }
    fun setFilter(filter: Filter) {
        mPendingFilter = filter
        mPendingFilter?.let {
            filter.onCreate(
                GlProgram.create(
                    it.vertexShader,
                    it.fragmentShader
                )
            )
        }
        /*(mFilter as BaseFilter).getMainVertexArray {
            mBitmapTriangleVertices = it
        }*/
    }

    fun draw(timestampUs: Long) {
        if (mPendingFilter != null) {
            release()
            mPendingFilter?.let {
                mFilter2 = it
            }
            mPendingFilter = null
        }
        if (mProgramHandle == -1) {
            mProgramHandle = GlProgram.create(
                mFilter.vertexShader,
                mFilter.fragmentShader
            )

            (mFilter as BaseFilter).initInputFrameTexture(textureHandle = texture.id, transformMatrix = textureTransform)

            mFilter.onCreate(mProgramHandle)
            checkGlError("program creation")
            (mFilter as BaseFilter).setVpMatrix(vpMatrix = mvpMatrix.copyOf(mvpMatrix.size), vpMatrixOffset = 0)


            /*mBitmapaPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "aPosition")
            GlRenderUtils.checkGlError("glGetAttribLocation aPosition")
            if (mBitmapaPositionHandle == -1) {
                throw RuntimeException("Could not get attrib location for aPosition")
            }
            mBitmapaTextureHandle = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord")
            GlRenderUtils.checkGlError("glGetAttribLocation aTextureCoord")
            if (mBitmapaTextureHandle == -1) {
                throw RuntimeException("Could not get attrib location for aTextureCoord")
            }*/
        }
        if (mProgramHandle2 == -1 || mProgramHandle2 == 0) {
            mProgramHandle2 = GlProgram.create(
                mFilter.vertexShader,
                mFilter2.fragmentShader
            )
            mFilter2.onCreate(mProgramHandle2)
            checkGlError("program creation")

            (mFilter2 as BaseOverlayFilter).setVpMatrix(vpMatrix = mvpMatrix.copyOf(mvpMatrix.size), vpMatrixOffset = 0)
        }
        /*GLES20.glUseProgram(mProgramHandle)
        checkGlError("glUseProgram(handle)")*/
        //texture.bind()
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        mFilter.draw(timestampUs, textureTransform)
        mFilter2.draw(timestampUs, textureTransform)

        /*(mFilter2 as? BitmapOverlayFilter)?.let {
            it.setAllData(
                textureId = it.overlayTextureID,
                mBitmapaPositionHandle = mBitmapaPositionHandle,
                mBitmapaTextureHandle = mBitmapaTextureHandle,
                mBitmapTriangleVertices = mBitmapTriangleVertices!!
            )
        }
        mFilter2.draw(timestampUs, textureTransform)*/
        //texture.unbind()
        GLES20.glFinish()
        checkGlError("glUseProgram(0)")
    }

    fun release() {
        if (mProgramHandle == -1) return
        mFilter.onDestroy()
        mFilter2.onDestroy()
        GLES20.glDeleteProgram(mProgramHandle)
        mProgramHandle = -1
        mProgramHandle2 = -1
    }

    private fun initMvpMatrix(rotation: Int, videoAspectRatio: Float) {
        val projectionMatrix = FloatArray(16)
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.orthoM(projectionMatrix, 0, -videoAspectRatio, videoAspectRatio, -1f, 1f, -1f, 1f)

        // rotate the camera to match video frame rotation
        val viewMatrix = FloatArray(16)
        Matrix.setIdentityM(viewMatrix, 0)
        val upX: Float
        val upY: Float
        when (rotation) {
            0 -> {
                upX = 0f
                upY = 1f
            }
            90 -> {
                upX = 1f
                upY = 0f
            }
            180 -> {
                upX = 0f
                upY = -1f
            }
            270 -> {
                upX = -1f
                upY = 0f
            }
            else -> {
                // this should never happen, but if it does, use trig as a last resort
                upX = Math.sin(rotation / 180 * Math.PI).toFloat()
                upY = Math.cos(rotation / 180 * Math.PI).toFloat()
            }
        }
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, 1f,
            0f, 0f, 0f,
            upX, upY, 0f
        )
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    companion object {
        private val TAG = GlTextureDrawer::class.java.simpleName
        const val TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        const val TEXTURE_UNIT = GLES20.GL_TEXTURE0
    }
}