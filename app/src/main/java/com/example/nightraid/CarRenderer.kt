package com.example.nightraid

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

class CarRenderer(context: Context) {
    val texGreen = try { ImageBitmap.imageResource(context.resources, R.drawable.car1_2) } catch (e: Exception) { null }
    val texYellow = try { ImageBitmap.imageResource(context.resources, R.drawable.car2) } catch (e: Exception) { null }
    val texBlue = try { ImageBitmap.imageResource(context.resources, R.drawable.car3) } catch (e: Exception) { null }
    val texRed = try { ImageBitmap.imageResource(context.resources, R.drawable.car4) } catch (e: Exception) { null }




    fun DrawScope.renderCar(car: GameCar, drawY: Float) {

        val texture = when {
            car.carType == 1 -> texGreen
            car.carType in 2..3 -> texYellow
            car.carType in 4..5 -> texBlue
            else -> texRed
        }


        val carWidth = 140f
        val carHeight = 70f

/
        val topLeftX = car.x - (carWidth / 2f)
        val topLeftY = drawY - (carHeight / 2f)



        val stateFilter = when {

            car.isHacked -> ColorFilter.tint(Color(0xFF3E3E42), BlendMode.Multiply)

            car.lockTimeOut > 0 -> ColorFilter.tint(Color(0xFF962D2D), BlendMode.Color)

            else -> null
        }

        if (texture != null) {
            drawImage(
                image = texture,
                dstOffset = IntOffset(topLeftX.toInt(), topLeftY.toInt()),
                dstSize = IntSize(carWidth.toInt(), carHeight.toInt()),
                colorFilter = stateFilter
            )
        } else {

            val fallbackColor = when {
                car.isHacked -> Color(0xFF3E3E42)
                car.lockTimeOut > 0 -> Color(0xFF962D2D)
                car.carType == 1 -> Color(0xFF2ECC71)
                car.carType in 2..3 -> Color(0xFFF1C40F)
                car.carType in 4..5 -> Color(0xFF3498DB)
                else -> Color(0xFFE74C3C)
            }
            drawRect(
                color = fallbackColor,
                topLeft = Offset(topLeftX, topLeftY),
                size = Size(carWidth, carHeight)
            )
        }
    }
}

