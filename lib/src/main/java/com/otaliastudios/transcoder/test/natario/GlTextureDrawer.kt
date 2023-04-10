package com.otaliastudios.transcoder.test.natario

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.otaliastudios.opengl.core.Egloo.IDENTITY_MATRIX
import com.otaliastudios.opengl.core.Egloo.checkGlError
import com.otaliastudios.opengl.program.GlProgram
import com.otaliastudios.opengl.texture.GlTexture

class GlTextureDrawer @JvmOverloads constructor(
    val texture: GlTexture = GlTexture(
        TEXTURE_UNIT, TEXTURE_TARGET
    )
) {
    var textureTransform: FloatArray = IDENTITY_MATRIX.clone()
    private var mFilter: Filter = NoFilter()
    private var mPendingFilter: Filter? = null
    private var mProgramHandle = -1

    constructor(textureId: Int) : this(GlTexture(TEXTURE_UNIT, TEXTURE_TARGET, textureId)) {}

    fun setFilter(filter: Filter) {
        mPendingFilter = filter
        filter.onCreate(GlProgram.create(
            mFilter.vertexShader,
            mFilter.fragmentShader
        ))
    }

    fun draw(timestampUs: Long) {
        if (mPendingFilter != null) {
            release()
            mPendingFilter?.let {
                mFilter = it
            }
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
        GLES20.glUseProgram(mProgramHandle)
        checkGlError("glUseProgram(handle)")
        texture.bind()
        mFilter.draw(timestampUs, textureTransform)
        texture.unbind()
        GLES20.glFinish()
        checkGlError("glUseProgram(0)")
    }

    fun release() {
        if (mProgramHandle == -1) return
        mFilter.onDestroy()
        GLES20.glDeleteProgram(mProgramHandle)
        mProgramHandle = -1
    }

    companion object {
        private val TAG = GlTextureDrawer::class.java.simpleName
        private const val TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        private const val TEXTURE_UNIT = GLES20.GL_TEXTURE0
    }
}