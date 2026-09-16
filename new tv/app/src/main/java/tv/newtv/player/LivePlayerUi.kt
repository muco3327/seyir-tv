package tv.newtv.player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

/**
 * C:\tv projesindeki Ui.focus görsel odak motorunun birebir kopyası.
 */
object LivePlayerUi {
    val BG = Color.rgb(10, 16, 26)
    val PANEL = Color.rgb(20, 31, 46)
    val MINT = Color.rgb(100, 229, 192)
    val WHITE = Color.rgb(242, 246, 252)
    val MUTED = Color.rgb(156, 174, 195)

    fun dp(c: Context, n: Float): Int = Math.round(n * c.resources.displayMetrics.density)

    fun shape(color: Int, stroke: Int, c: Context): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(c, 12f).toFloat()
        if (stroke != 0) d.setStroke(dp(c, 2f), stroke)
        return d
    }

    fun focus(v: View) {
        val c = v.context
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_focused), shape(Color.rgb(32, 65, 66), MINT, c))
        s.addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(32, 65, 66), MINT, c))
        s.addState(intArrayOf(), shape(PANEL, 0, c))
        v.background = s
        v.isFocusable = true
        v.isFocusableInTouchMode = false
        if (v is android.widget.Button) {
            v.setTextColor(WHITE)
            v.isAllCaps = false
            v.setPadding(dp(c, 14f), dp(c, 6f), dp(c, 14f), dp(c, 6f))
        }
        v.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            view.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(150).start()
            if (hasFocus) view.elevation = dp(c, 8f).toFloat() else view.elevation = 0f
        }
    }
}
