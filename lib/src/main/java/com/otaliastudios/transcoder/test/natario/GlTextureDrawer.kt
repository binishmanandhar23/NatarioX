package com.otaliastudios.transcoder.test.natario

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.otaliastudios.opengl.core.Egloo.IDENTITY_MATRIX
import com.otaliastudios.opengl.core.Egloo.checkGlError
import com.otaliastudios.opengl.extensions.*
import com.otaliastudios.opengl.program.GlProgram
import com.otaliastudios.opengl.texture.GlTexture

class GlTextureDrawer @JvmOverloads constructor(
    val texture: GlTexture = GlTexture(
        TEXTURE_UNIT, TEXTURE_TARGET
    )
) {
    var textureTransform: FloatArray = IDENTITY_MATRIX.clone()
    private var mFilter: Filter = NoFilter()
    private var mFilter2: Filter? = NoFilter()
    private var mPendingFilter: Filter? = null
    private var mProgramHandle = -1
    private var mProgramHandle2 = -1

    constructor(textureId: Int) : this(GlTexture(TEXTURE_UNIT, TEXTURE_TARGET, textureId)) {}

    fun setFilter(filter: Filter) {
        mPendingFilter = filter
    }

    fun draw(timestampUs: Long) {
        if (mPendingFilter != null) {
            release()
            mFilter2 = mPendingFilter
            mPendingFilter = null
        }
        if (mProgramHandle == -1) {
            mProgramHandle = GlProgram.create(
                mFilter.vertexShader,
                mFilter.fragmentShader
            )
            mFilter.onCreate(mProgramHandle)
            checkGlError("program creation")
        }
        if (mProgramHandle2 == -1) {
            mProgramHandle2 = GlProgram.create(
                mFilter2!!.vertexShader,
                mFilter2!!.fragmentShader
            )
            mFilter2?.onCreate(mProgramHandle2)
            checkGlError("program creation")
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(mProgramHandle)
        checkGlError("glUseProgram(handle)")
        texture.bind()
        mFilter.draw(timestampUs,textureTransform)
        GLES20.glUseProgram(mProgramHandle2)
        mFilter2?.draw(timestampUs, textureTransform)
        texture.unbind()
        GLES20.glUseProgram(0)
        checkGlError("glUseProgram(0)")
    }

    fun release() {
        if (mProgramHandle == -1) return
        if (mProgramHandle2 == -1) return
        mFilter.onDestroy()
        mFilter2?.onDestroy()
        GLES20.glDeleteProgram(mProgramHandle)
        GLES20.glDeleteProgram(mProgramHandle2)
        mProgramHandle = -1
        mProgramHandle2 = -1
    }

    companion object {
        private val TAG = GlTextureDrawer::class.java.simpleName
        private const val TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        private const val TEXTURE_UNIT = GLES20.GL_TEXTURE0
    }
}