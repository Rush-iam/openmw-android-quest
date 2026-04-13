package com.queststoredb.openmw_quest.utils

import kotlin.math.abs


fun smoothMotionJitter(
    oldX: Float, oldY: Float, newX: Float, newY: Float, threshold: Float
): Pair<Float, Float> {
    val deltaX = abs(newX - oldX) / threshold
    val deltaY = abs(newY - oldY) / threshold
    if (deltaX > 1f || deltaY > 1f)
        return Pair(newX, newY)
    return Pair(oldX * (1f - deltaX) + newX * deltaX, oldY * (1f - deltaY) + newY * deltaY)
}
