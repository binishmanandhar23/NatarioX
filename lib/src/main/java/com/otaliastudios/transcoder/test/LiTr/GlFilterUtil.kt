/*
 * Copyright 2019 LinkedIn Corporation
 * All Rights Reserved.
 *
 * Licensed under the BSD 2-Clause License (the "License").  See License in the project root for
 * license information.
 */
package com.otaliastudios.transcoder.test.LiTr

import android.opengl.Matrix
import kotlin.math.abs

object GlFilterUtil {
    /**
     * Takes target video VP matrix, along with filter rectangle parameters (size, position, rotation)
     * and calculates filter's MVP matrix
     * @param vpMatrix target video VP matrix, which defines target video canvas
     * @param transform [Transform] that defines drawable's positioning within target video frame
     * @return filter MVP matrix
     */
    @JvmStatic
    fun createFilterMvpMatrix(
        vpMatrix: FloatArray,
        transform: Transform
    ): FloatArray {
        // Let's use features of VP matrix to extract frame aspect ratio and orientation from it
        // for 90 and 270 degree rotations (portrait orientation) top left element will be zero
        val isPortraitVideo = vpMatrix[0] == 0f

        // orthogonal projection matrix is basically a scaling matrix, which scales along X axis.
        // 0 and 180 degree rotations keep the scaling factor in top left element (they don't move it)
        // 90 and 270 degree rotations move it to one position right in top row
        // Inverting scaling factor gives us the aspect ratio.
        // Scale can be negative if video is flipped, so we use absolute value.
        val videoAspectRatio: Float = if (isPortraitVideo) {
            1 / abs(vpMatrix[4])
        } else {
            1 / abs(vpMatrix[0])
        }

        // Size is respective to video frame, and frame will later be scaled by perspective and view matrices.
        // So we have to adjust the scale accordingly.
        val scaleX: Float
        val scaleY: Float
        if (isPortraitVideo) {
            scaleX = transform.size.x
            scaleY = transform.size.y * videoAspectRatio
        } else {
            scaleX = transform.size.x * videoAspectRatio
            scaleY = transform.size.y
        }

        // Position values are in relative (0, 1) range, which means they have to be mapped from (-1, 1) range
        // and adjusted for aspect ratio.
        val translateX: Float
        val translateY: Float
        if (isPortraitVideo) {
            translateX = transform.position.x * 2 - 1
            translateY = (1 - transform.position.y * 2) * videoAspectRatio
        } else {
            translateX = (transform.position.x * 2 - 1) * videoAspectRatio
            translateY = 1 - transform.position.y * 2
        }

        // Matrix operations in OpenGL are done in reverse. So here we scale (and flip vertically) first, then rotate
        // around the center, and then translate into desired position.
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, translateX, translateY, 0f)
        Matrix.rotateM(modelMatrix, 0, transform.rotation, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, 1f)

        // last, we multiply the model matrix by the view matrix to get final MVP matrix for an overlay
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        return mvpMatrix
    }
}