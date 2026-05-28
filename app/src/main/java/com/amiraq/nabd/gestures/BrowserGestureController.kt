package com.amiraq.nabd.gestures

import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Detects edge swipe gestures for back/forward navigation.
 * Attaches as an OnTouchListener to the browser content container.
 */
class BrowserGestureController(
    context: Context,
    private val onSwipeBack: () -> Unit,
    private val onSwipeForward: () -> Unit
) : View.OnTouchListener {

    private val edgeWidthPx = (EDGE_WIDTH_DP * context.resources.displayMetrics.density).toInt()
    private val minSwipeDistPx = (MIN_SWIPE_DIST_DP * context.resources.displayMetrics.density).toInt()
    private val maxVerticalSlopPx = (MAX_VERTICAL_SLOP_DP * context.resources.displayMetrics.density).toInt()

    var isEnabled = true

    private var startX = 0f
    private var startY = 0f
    private var isTracking = false
    private var edgeSide = EDGE_NONE
    private var screenWidth = 0

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (!isEnabled) return false

        screenWidth = view.width

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                edgeSide = when {
                    startX < edgeWidthPx -> EDGE_LEFT
                    startX > screenWidth - edgeWidthPx -> EDGE_RIGHT
                    else -> EDGE_NONE
                }
                isTracking = edgeSide != EDGE_NONE
            }

            MotionEvent.ACTION_UP -> {
                if (isTracking) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (abs(dx) > minSwipeDistPx && abs(dx) > abs(dy) && abs(dy) < maxVerticalSlopPx) {
                        if (edgeSide == EDGE_LEFT && dx > 0) {
                            onSwipeBack()
                        } else if (edgeSide == EDGE_RIGHT && dx < 0) {
                            onSwipeForward()
                        }
                    }
                    isTracking = false
                    edgeSide = EDGE_NONE
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                edgeSide = EDGE_NONE
            }
        }

        // Never consume the event — let GeckoView handle scrolling
        return false
    }

    companion object {
        private const val EDGE_NONE = 0
        private const val EDGE_LEFT = 1
        private const val EDGE_RIGHT = 2
        private const val EDGE_WIDTH_DP = 40f
        private const val MIN_SWIPE_DIST_DP = 80f
        private const val MAX_VERTICAL_SLOP_DP = 80f
    }
}
