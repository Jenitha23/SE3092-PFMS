package com.pfms.app.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Vector viewport strokes scale consistently across Android screen densities. */
private fun authIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
    }.build()

val AuthMailIcon = authIcon("Email") {
    moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
    moveTo(3f, 6f); lineTo(12f, 13f); lineTo(21f, 6f)
}

val AuthLockIcon = authIcon("Password") {
    moveTo(7f, 10f); lineTo(7f, 7f)
    curveTo(7f, 0.5f, 17f, 0.5f, 17f, 7f); lineTo(17f, 10f)
    moveTo(5f, 10f); lineTo(19f, 10f); lineTo(19f, 22f); lineTo(5f, 22f); close()
    moveTo(12f, 15f); lineTo(12f, 17f)
}

val AuthPersonIcon = authIcon("Full name") {
    moveTo(12f, 3f)
    curveTo(17.3f, 3f, 17.3f, 11f, 12f, 11f)
    curveTo(6.7f, 11f, 6.7f, 3f, 12f, 3f); close()
    moveTo(5f, 21f); lineTo(5f, 18f)
    curveTo(5f, 13f, 19f, 13f, 19f, 18f); lineTo(19f, 21f); close()
}

private fun eyePath(): PathBuilder.() -> Unit = {
    moveTo(2f, 12f)
    curveTo(7f, 4f, 17f, 4f, 22f, 12f)
    curveTo(17f, 20f, 7f, 20f, 2f, 12f); close()
    moveTo(12f, 9f)
    curveTo(16f, 9f, 16f, 15f, 12f, 15f)
    curveTo(8f, 15f, 8f, 9f, 12f, 9f); close()
}

val AuthEyeIcon = authIcon("Visible password", eyePath())
val AuthEyeOffIcon = authIcon("Hidden password") {
    eyePath().invoke(this)
    moveTo(3f, 3f); lineTo(21f, 21f)
}
