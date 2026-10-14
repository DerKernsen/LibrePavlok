package org.librepavlok.offline;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small native design system. No web view, fonts, assets or network required. */
final class AppUi {
    static final int BG=Color.rgb(245,245,237), INK=Color.rgb(30,45,36),
            MUTED=Color.rgb(104,116,103), GREEN=Color.rgb(44,83,62),
            LIME=Color.rgb(220,245,146), WHITE=Color.WHITE,
            SOFT=Color.rgb(235,239,226), BORDER=Color.rgb(220,225,212),
            PEACH=Color.rgb(249,227,212), GOLD=Color.rgb(248,236,202);

    static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(Context c,int fill,int radius,boolean border) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(c,radius));
        if(border)d.setStroke(dp(c,1),BORDER); return d;
    }
    static void surface(View view,int fill,int radius,boolean border) {
        view.setBackground(background(view.getContext(),fill,radius,border));
    }
    static LinearLayout column(Context c) {
        LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static LinearLayout card(LinearLayout parent,int fill) {
        Context c=parent.getContext(); LinearLayout card=column(c);
        card.setPadding(dp(c,20),dp(c,18),dp(c,20),dp(c,18)); surface(card,fill,26,fill==WHITE);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(c,14);
        parent.addView(card,p); return card;
    }
    static TextView text(LinearLayout parent,String value,int size,int color,boolean bold) {
        Context c=parent.getContext(); TextView t=new TextView(c); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setLineSpacing(dp(c,2),1); t.setPadding(0,dp(c,3),0,dp(c,3));
        parent.addView(t,new LinearLayout.LayoutParams(-1,-2)); return t;
    }
    static Button button(LinearLayout parent,String title,int fill,int ink,View.OnClickListener click) {
        Context c=parent.getContext(); Button b=new Button(c); b.setText(title); b.setAllCaps(false);
        b.setTextSize(15); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_enabled},{}},new int[]{ink,MUTED}));
        b.setMinHeight(dp(c,54)); b.setMinimumHeight(dp(c,54)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8)); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x182C533E),background(c,fill,17,false),null));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p=parent.getOrientation()==LinearLayout.HORIZONTAL
                ?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);
        if(parent.getOrientation()==LinearLayout.HORIZONTAL){ p.setMargins(dp(c,3),dp(c,8),dp(c,3),0); }
        else p.topMargin=dp(c,9);
        parent.addView(b,p); return b;
    }
    static LinearLayout row(LinearLayout parent) {
        LinearLayout r=new LinearLayout(parent.getContext()); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setBaselineAligned(false);
        r.setGravity(Gravity.CENTER_VERTICAL); parent.addView(r,new LinearLayout.LayoutParams(-1,-2)); return r;
    }
    static final class Icon extends View {
        private final String glyph;
        private int color;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c,String glyph,int color,int size) {
            super(c); this.glyph=glyph; this.color=color;
            setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        void tint(int color) { this.color=color; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/32f,getHeight()/32f);
            pen.setColor(color); pen.setStrokeWidth(2); pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
            if(glyph.equals("alarm")) {
                canvas.drawCircle(16,18,10,pen); line(canvas,16,12,16,18); line(canvas,16,18,21,21);
                line(canvas,8,5,4,9); line(canvas,24,5,28,9); line(canvas,9,27,7,30); line(canvas,23,27,25,30);
            } else if(glyph.equals("device")) {
                canvas.drawRoundRect(8,7,24,25,5,5,pen); line(canvas,12,2,20,2); line(canvas,12,30,20,30);
                line(canvas,12,2,11,7); line(canvas,20,2,21,7); line(canvas,11,25,12,30); line(canvas,21,25,20,30);
                line(canvas,13,16,15,18); line(canvas,15,18,20,13);
            } else if(glyph.equals("play")) {
                canvas.drawCircle(16,16,12,pen);
                Path path=new Path(); path.moveTo(13,10); path.lineTo(23,16);
                path.lineTo(13,22); path.close(); canvas.drawPath(path,pen);
            } else if(glyph.equals("bolt")) {
                Path path=new Path(); path.moveTo(19,2); path.lineTo(7,18); path.lineTo(15,18);
                path.lineTo(13,30); path.lineTo(26,13); path.lineTo(18,13); path.close(); canvas.drawPath(path,pen);
            } else if(glyph.equals("beep")) {
                canvas.drawRoundRect(5,9,19,23,5,5,pen); line(canvas,9,27,15,27);
                canvas.drawArc(17,6,29,26,-55,110,false,pen);
            } else {
                Path path=new Path(); path.moveTo(3,17); path.lineTo(8,17); path.lineTo(12,7);
                path.lineTo(17,25); path.lineTo(22,11); path.lineTo(25,17); path.lineTo(29,17); canvas.drawPath(path,pen);
            }
            canvas.restore();
        }
        private void line(Canvas c,float x,float y,float ex,float ey){ c.drawLine(x,y,ex,ey,pen); }
    }
}
