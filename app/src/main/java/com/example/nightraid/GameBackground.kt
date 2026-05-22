package com.example.nightraid

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object GameBackground {


    private val edgePaint = Paint().apply {
        filterQuality = FilterQuality.Low
    }


    fun DrawScope.drawSeamlessFloor(floorTexture: ImageBitmap?, mapScrollY: Float) {
        floorTexture?.let { texture ->

            val targetTileWidth = size.width / 2f
            val targetTileHeight = size.height / 2f


            val tileOffsetY = (mapScrollY % targetTileHeight)


            for (xIdx in 0..1) {
                for (yIdx in -1..1) {
                    val drawX = xIdx * targetTileWidth
                    val drawY = tileOffsetY + (yIdx * targetTileHeight)

                    val safeWidth = targetTileWidth.toInt() + 2
                    val safeHeight = targetTileHeight.toInt() + 2

                    drawIntoCanvas { canvas ->
                        canvas.drawImageRect(
                            image = texture,
                            dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                            dstSize = IntSize(safeWidth, safeHeight),
                            paint = edgePaint
                        )
                    }
                }
            }
        }
    }
}
