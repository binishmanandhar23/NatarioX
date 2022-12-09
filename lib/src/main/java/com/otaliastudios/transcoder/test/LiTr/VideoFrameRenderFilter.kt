/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// from: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/TextureRender.java
// blob: 4125dcfcfed6ed7fddba5b71d657dec0d433da6a
// modified: removed unused method bodies
// modified: use GL_LINEAR for GL_TEXTURE_MIN_FILTER to improve quality.
// modified: added filters
package com.otaliastudios.transcoder.test.LiTr

import android.graphics.PointF
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.otaliastudios.transcoder.test.LiTr.GlFilterUtil.createFilterMvpMatrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


/**
 * Implementation of GlFrameRenderFilter, which renders source video frame onto target video frame,
 * optionally applying pixel and geometric transformation.
 */
open class VideoFrameRenderFilter protected constructor(
    private val vertexShader: String,
    private val fragmentShader: String,
    private val shaderParameters: Array<ShaderParameter>?,
    transform: Transform? = null
) :
    GlFrameRenderFilter {
    private val transform: Transform
    private var mvpMatrix = FloatArray(16)
    private var inputFrameTextureMatrix = FloatArray(16)
    private var mvpMatrixOffset = 0
    private val triangleVertices: FloatBuffer
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
    override fun init() {
        Matrix.setIdentityM(inputFrameTextureMatrix, 0)
        vertexShaderHandle = GlRenderUtils.loadShader(
            GLES20.GL_VERTEX_SHADER,
            vertexShader
        )
        if (vertexShaderHandle == 0) {
            throw RuntimeException("failed loading vertex shader")
        }
        fragmentShaderHandle = GlRenderUtils.loadShader(
            GLES20.GL_FRAGMENT_SHADER,
            fragmentShader
        )
        if (fragmentShaderHandle == 0) {
            release()
            throw RuntimeException("failed loading fragment shader")
        }
        glProgram = GlRenderUtils.createProgram(vertexShaderHandle, fragmentShaderHandle)
        if (glProgram == 0) {
            release()
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
        uStMatrixHandle = GLES20.glGetUniformLocation(glProgram, "uSTMatrix")
        GlRenderUtils.checkGlError("glGetUniformLocation uSTMatrix")
        if (uStMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uSTMatrix")
        }
    }

    override fun setVpMatrix(vpMatrix: FloatArray, vpMatrixOffset: Int) {
        mvpMatrix = createFilterMvpMatrix(vpMatrix, transform)
        mvpMatrixOffset = vpMatrixOffset
    }

    override fun initInputFrameTexture(textureHandle: Int, transformMatrix: FloatArray) {
        inputFrameTextureHandle = textureHandle
        inputFrameTextureMatrix = transformMatrix
    }

    override fun apply(presentationTimeNs: Long) {
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
        if (shaderParameters != null) {
            for (shaderParameter in shaderParameters) {
                shaderParameter.apply(glProgram)
            }
        }
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, mvpMatrixOffset)
        GLES20.glUniformMatrix4fv(uStMatrixHandle, 1, false, inputFrameTextureMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlRenderUtils.checkGlError("glDrawArrays")
    }

    override fun release() {
        GLES20.glDeleteProgram(glProgram)
        GLES20.glDeleteShader(vertexShaderHandle)
        GLES20.glDeleteShader(fragmentShaderHandle)
        GLES20.glDeleteBuffers(1, intArrayOf(aTextureHandle), 0)
        glProgram = 0
        vertexShaderHandle = 0
        fragmentShaderHandle = 0
        aTextureHandle = 0
    }

    companion object {
        const val DEFAULT_VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main()\n" +
                "{\n" +
                "gl_Position = uMVPMatrix * aPosition;\n" +
                "vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                "}"
        const val DEFAULT_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +  // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main()\n" +
                    "{\n" +
                    "gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}"
        private const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3
    }
    /**
     * Create frame render filter with source video frame, then scale, then position and then rotate the bitmap around its center as specified.
     * Use provided vertex and fragment filter, to do things like pixel modification.
     * @param vertexShader vertex shader
     * @param fragmentShader fragment shader
     * @param shaderParameters shader parameters (uniforms and/or attributes) if any, null otherwise
     * @param transform [Transform] that defines positioning of source video frame within target video frame
     */
    /**
     * Create filter which scales source frame to fit target frame, apply vertex and frame shaders. Can be used for
     * things like pixel modification.
     * @param vertexShader vertex shader
     * @param fragmentShader fragment shader
     * @param shaderParameters shader parameters (uniforms and/or attributes) if any, null otherwise
     */
    init {
        this.transform = transform
            ?: Transform(PointF(1f, 1f), PointF(0.5f, 0.5f), 0f)
        triangleVertices = ByteBuffer.allocateDirect(
            triangleVerticesData.size * FLOAT_SIZE_BYTES
        )
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        triangleVertices.put(triangleVerticesData).position(0)
    }
}