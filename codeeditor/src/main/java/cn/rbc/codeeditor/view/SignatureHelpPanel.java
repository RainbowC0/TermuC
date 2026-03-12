// SignatureHelpPanel.java
package cn.rbc.codeeditor.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import cn.rbc.codeeditor.view.*;
import android.graphics.drawable.*;
import android.content.res.*;
import cn.rbc.codeeditor.util.*;
import android.widget.*;
import android.util.*;
import cn.rbc.codeeditor.R;

public class SignatureHelpPanel implements View.OnClickListener {
    private static final String TAG = "SignatureHelpPanel";

    private final PopupWindow _popupWindow;
    private final TextView _sigView, _prev, _next;
    private final View _root, _navi;
    private final FreeScrollingTextField _editor;

    private List<String> sigs;
    private int activeSig;

    public SignatureHelpPanel(FreeScrollingTextField editor) {
        _editor = editor;
        Context ctx = editor.getContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View root = inflater.inflate(R.layout.signature_panel, null);
        _root = root;
        View tmp = root.findViewById(R.id.sig_nav);
        _navi = tmp;
        tmp.measure(View.MeasureSpec.AT_MOST, View.MeasureSpec.AT_MOST);
        TextView tv = root.findViewById(R.id.sig_prev);
        tv.setOnClickListener(this);
        _prev = tv;
        tv = root.findViewById(R.id.sig_next);
        tv.setOnClickListener(this);
        _next = tv;
        tv = root.findViewById(R.id.sig_text);
        _sigView = tv;
        
        PopupWindow pw = new PopupWindow(
            root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        );
        _popupWindow = pw;
        
        TypedArray array = ctx.getTheme().obtainStyledAttributes(
            new int[]{
                android.R.attr.colorBackground,
                android.R.attr.textColorPrimary
            });
        int textColor = array.getColor(1, 0xFF00FF);
        int backgroundColor = array.getColor(0, 0xFF00FF);
        array.recycle();
        GradientDrawable g = new GradientDrawable();
        g.setColor(backgroundColor);
        pw.setBackgroundDrawable(g);
        pw.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        pw.setAnimationStyle(0);
        pw.setOutsideTouchable(true);
        tv.setTextColor(textColor);
        g.setStroke(1, textColor);
    }

    public void show(List<String> data, int idx) {
        if (data == null || data.isEmpty()) {
            hide();
            return;
        }
        sigs = data;
        select(idx);

        FreeScrollingTextField ed = _editor;
        int[] pos = updatePosition(ed);

        View v = _navi;
        if (data.size() > 1) {
            v.setVisibility(View.VISIBLE);
            pos[0] -= v.getWidth();
        } else v.setVisibility(View.GONE);

        _popupWindow.showAtLocation(ed, Gravity.NO_GRAVITY, pos[0], pos[1]);
    }

    public void hide() {
        _popupWindow.dismiss();
    }

    public boolean isShowing() {
        return _popupWindow.isShowing();
    }

    protected static int[] updatePosition(FreeScrollingTextField ed) {
        int[] pos = new int[2];
        ed.getLocationInWindow(pos);
        pos[0] += ed.getCaretX() - ed.getScrollX();
        pos[1] += ed.rowHeight() * (1 + ed.getCaretRow()) - ed.getScrollY();
        return pos;
    }
/*
    public void update(int x, int y) {
        _popupWindow.update(x, y, -1, -1, true);
    }*/

    public void setTextSize(float s) {
        _sigView.setTextSize(TypedValue.COMPLEX_UNIT_PX, s);
        _prev.setTextSize(TypedValue.COMPLEX_UNIT_PX, s);
        _next.setTextSize(TypedValue.COMPLEX_UNIT_PX, s);
        _navi.measure(View.MeasureSpec.AT_MOST, View.MeasureSpec.AT_MOST);
    }

    private void select(int pos) {
        if (pos < 0 || pos > sigs.size()) return;
        activeSig = pos;
        _sigView.setText(sigs.get(pos));
        _prev.setEnabled(pos > 0);
        _next.setEnabled(pos+1 < sigs.size());
    }

    @Override
    public void onClick(View v) {
        int i;
        if (v.getId() == R.id.sig_prev) i = -1;
        else i = 1;
        select(activeSig + i);
    }
}
