package cn.rbc.termuc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.rbc.codeeditor.view.FreeScrollingTextField;

public class KeyPanel extends LinearLayout implements View.OnClickListener {
    private String mKeys;
    private FreeScrollingTextField mEditor;

    public KeyPanel(Context context) {
        super(context);
        init();
    }

    public KeyPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KeyPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        mKeys = "";
        addKey("⇥");
    }

    public void resetKeys(String keys) {
        if (mKeys.equals(keys)) return;
        removeAllViewsInLayout();
        addKey("⇥");
        for (int i=0,l=keys.length()-1;i<=l;i++) {
            addKey(keys.substring(i, i+1));
        }
        requestLayout();
    }

    public void setEditor(FreeScrollingTextField editor) {
        mEditor = editor;
    }

    private TextView addKey(String c) {
        TextView key = new TextView(getContext());
        key.setText(c);
        LayoutParams params = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        );
        key.setOnClickListener(this);
        addViewInLayout(key, getChildCount(), params);
        return key;
    }

    @Override
    public void onClick(View v) {
        FreeScrollingTextField editor = mEditor;
        if (editor == null) return;
        CharSequence text = ((TextView)v).getText();
        if ("⇥".equals(text)) {
            text = "\t";
        }
        editor.paste(text);
    }
}