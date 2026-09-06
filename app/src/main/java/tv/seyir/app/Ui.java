package tv.seyir.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.*;

public final class Ui {
    public static final int BG=Color.rgb(10,16,26), PANEL=Color.rgb(20,31,46), MINT=Color.rgb(100,229,192), WHITE=Color.rgb(242,246,252), MUTED=Color.rgb(156,174,195);
    private Ui(){}
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static GradientDrawable shape(int color,int stroke,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,12));if(stroke!=0)d.setStroke(dp(c,2),stroke);return d;}
    public static void focus(View v){
        Context c=v.getContext();StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(32,65,66),MINT,c));
        s.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(32,65,66),MINT,c));
        s.addState(new int[]{},shape(PANEL,0,c));v.setBackground(s);v.setFocusable(true);v.setFocusableInTouchMode(false);
        v.setOnFocusChangeListener((view, hasFocus) -> {
            view.animate().scaleX(hasFocus ? 1.05f : 1.0f).scaleY(hasFocus ? 1.05f : 1.0f).setDuration(150).start();
            if (hasFocus) view.setElevation(dp(c, 8)); else view.setElevation(0);
        });
    }
    public static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    public static Button button(Context c,String label,Runnable action){Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(WHITE);b.setMinHeight(0);b.setMinimumHeight(dp(c,44));b.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));focus(b);b.setOnClickListener(v->action.run());return b;}
    public static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(android.view.Gravity.CENTER_VERTICAL);return l;}
    public static void bold(TextView t){t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    public static void gap(LinearLayout l,int h){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),h)));}
}
