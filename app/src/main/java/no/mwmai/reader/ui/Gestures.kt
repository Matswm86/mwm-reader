package no.mwmai.reader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pinch to zoom that does not fight the list underneath it.
 *
 * `detectTransformGestures` swallows one-finger drags as pan, which would stop
 * a LazyColumn scrolling, so this watches the Initial pass and only claims the
 * gesture once a second finger is down. One finger keeps scrolling the page;
 * two fingers zoom and the list stays still until they lift.
 *
 * [onZoom] is called with the change since the previous event, so 1.0 means no
 * movement. The caller decides what zooming means: bigger type for text that
 * reflows, a bigger bitmap for a PDF page.
 */
fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var claimed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                claimed = true
                val change = event.calculateZoom()
                if (change != 1f && change > 0f) onZoom(change)
            }
            // Keep consuming until the fingers lift, so the list does not jump
            // when the second finger comes off mid-pinch.
            if (claimed) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Turns a stream of pinch ratios into whole steps of a setting, because the
 * reading size is an integer number of points and a continuous scale would
 * write to disk on every frame.
 */
class StepZoom(private val step: Float = 1.12f) {
    private var pending = 1f

    /** Calls [onStep] once per whole step, with +1 or -1. */
    fun accept(change: Float, onStep: (Int) -> Unit) {
        pending *= change
        var guard = 0
        while (pending > step && guard++ < 8) {
            pending /= step
            onStep(1)
        }
        while (pending < 1f / step && guard++ < 8) {
            pending *= step
            onStep(-1)
        }
    }

    fun reset() {
        pending = 1f
    }
}
