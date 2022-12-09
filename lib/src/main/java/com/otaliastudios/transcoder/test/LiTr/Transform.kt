/*
 * Copyright 2019 LinkedIn Corporation
 * All Rights Reserved.
 *
 * Licensed under the BSD 2-Clause License (the "License").  See License in the project root for
 * license information.
 */
package com.otaliastudios.transcoder.test.LiTr

import android.graphics.PointF

/**
 * A data class that defines geometric transform of a drawable object (video frame, bitmap overlay)
 * within a target video frame. Definition and transformation order matches positioning of an Android
 * [android.view.View] in a parent [android.view.View]: object is first scaled to size,
 * then moved into position, then rotated around its center.
 */
class Transform
/**
 * Create a geometric transform
 * @param size size in X and Y direction, relative to target video frame
 * @param position position of source video frame  center, in relative coordinate in 0 - 1 range
 * in fourth quadrant (0,0 is top left corner)
 * @param rotation rotation angle of overlay, relative to target video frame, counter-clockwise, in degrees
 */(val size: PointF, val position: PointF, val rotation: Float, val opacity: Float = 1f)