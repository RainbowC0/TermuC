package cn.rbc.codeeditor.view.autocomplete;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filter;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import cn.rbc.codeeditor.R;
import cn.rbc.codeeditor.lang.Language;
import cn.rbc.codeeditor.lang.LanguageNonProg;
import cn.rbc.codeeditor.view.FreeScrollingTextField;
import android.view.*;
import android.widget.*;
import java.util.*;
import android.annotation.*;
import android.graphics.*;
import android.util.*;
import cn.rbc.codeeditor.util.*;
import android.os.*;


public class AutoCompletePanel implements OnItemClickListener {

    public int _off;
    private FreeScrollingTextField _textField;
    private Context _context;
    private PopupWindow _autoCompletePanel;
	private ListView _list;
	private View mItemView;
    private AutoPanelAdapter _adapter;
    private Filter _filter;
    private int _height;
    private int _backgroundColor;
    private int mMaxWidth;
    private GradientDrawable gd;
    public int _textColor, _mxHeight;
    private boolean isShow = false;

    public AutoCompletePanel(FreeScrollingTextField textField) {
        _textField = textField;
        _context = textField.getContext();
        initAutoCompletePanel();
    }

    public void setTextColor(int color) {
        _textColor = color;
		GradientDrawable vgd = gd;
        vgd.setStroke(1, color);
        _autoCompletePanel.setBackgroundDrawable(vgd);
    }

    public void setBackgroundColor(int color) {
        _backgroundColor = color;
		GradientDrawable vgd = gd;
        vgd.setColor(color);
        _autoCompletePanel.setBackgroundDrawable(vgd);
    }

    public void setBackground(Drawable color) {
        _autoCompletePanel.setBackgroundDrawable(color);
    }

    @SuppressWarnings("ResourceType")
    private void initAutoCompletePanel() {
		Context ct = _context;
        ListView list = new ListView(ct);
		_list = list;
		list.setLayoutParams(new ViewGroup.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT,
								 ViewGroup.LayoutParams.WRAP_CONTENT
							 ));
		list.setFocusable(true);
		list.setFocusableInTouchMode(true);
		list.setFastScrollEnabled(true);
		list.setOnItemClickListener(this);
		AutoPanelAdapter adp = new AutoPanelAdapter(ct, this, _textField);
		_adapter = adp;
		list.setAdapter(adp);
		_filter = adp.getFilter();
		View v = adp.getItemView();
		v.measure(0, 0);
		mItemView = v;
		_mxHeight = v.getMeasuredHeight() << 2;
        DisplayMetrics metrices = ct.getResources().getDisplayMetrics();
        mMaxWidth = Math.min(metrices.widthPixels,
            (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 492, metrices));
		PopupWindow pw = new PopupWindow(list);
		_autoCompletePanel = pw;
		pw.setAnimationStyle(0);
		pw.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
		pw.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
		    pw.setOverlapAnchor(true);
        TypedArray array = ct.getTheme().obtainStyledAttributes(
			new int[]{
				android.R.attr.colorBackground,
				android.R.attr.textColorPrimary
			});
        int textColor = array.getColor(1, 0xFF00FF);
        int backgroundColor = array.getColor(0, 0xFF00FF);
        array.recycle();
        GradientDrawable vgd = new GradientDrawable();
		gd = vgd;
		vgd.setColor(backgroundColor);
        vgd.setCornerRadius(4);
		setTextColor(textColor);
    }

	public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
		select(p3);
	}

    public void select(int pos) {
		ListItem itm = _adapter.getItem(pos);
		Deque<Edit> edits = itm.edits;
		if (edits.isEmpty()) {
			String text = itm.label;
			int idx = text.indexOf('(');
			if (idx >= 0)
				text = text.substring(0, idx+1);
			_textField.replaceText(_textField.getCaretPosition()-_off, _off, text);
		} else {
			Document doc = _textField.getText();
			doc.beginBatchEdit();
			doc.setTyping(false);
			Edit it = edits.pollFirst();
			int mc = it.start + it.text.length();
			long tp = System.nanoTime();
			doc.deleteAt(it.start, it.len, tp);
			doc.insertBefore(it.text.toCharArray(), it.start, tp);
			while (!edits.isEmpty()) {
				it = edits.pop();
				int st = it.start, l = it.len;
				String tx = it.text;
				doc.deleteAt(st, l, tp);
				doc.insertBefore(tx.toCharArray(), st, tp);
				if (st + l <= mc)
					mc += tx.length() - l;
				else if (st < mc)
					mc = st + tx.length();
			}
			doc.endBatchEdit();
			FreeScrollingTextField tf = _textField;
			tf.moveCaret(mc);
			tf.mCtrlr.determineSpans();
		}
        _adapter.abort();
        dismiss();
    }

    public void setWidth(int width) {
        _autoCompletePanel.setWidth(width);
    }

    public void setHeight(int height) {
        if (_height != height) {
            _height = height;
            _autoCompletePanel.setHeight(height);
        }
    }

    public void update(CharSequence constraint) {
        _adapter.restart();
        _filter.filter(constraint);
    }

	public void update(ArrayList<ListItem> l) {
		if (l == null || l.isEmpty()) {
			_adapter.notifyDataSetInvalidated();
			dismiss();
		} else {
			_adapter.setData(l);
			_adapter.notifyDataSetChanged();
			show();
		}
	}

    public void show() {
		int mH = 0, i, l;
		final int mxH = _mxHeight;
		final View v = mItemView;
		TextView tv = v.findViewById(R.id.auto_panel_text);
		int mw = View.MeasureSpec.makeMeasureSpec(_list.getWidth(), View.MeasureSpec.AT_MOST);
		for (i = 0,l = _adapter.getCount();i < l && mH < mxH;i++) {
			tv.setText(_adapter.getItem(i).label);
			v.measure(mw, 0);
			mH += v.getMeasuredHeight();
		}
		if (mH > mxH) mH = mxH;
		setHeight(mH);
		FreeScrollingTextField tf = _textField;
		int y = tf.getCaretY() + tf.rowHeight() / 2 - tf.getScrollY();
		int dh = y + mH - tf.getHeight();
		if (dh > 0) {
			tf.scrollBy(0, dh);
			y -= dh;
		}
		setWidth(mMaxWidth - (AutoPanelAdapter.PADDING << 1));
		if (!isShow()) {
			_list.requestFocus();
            int caretx = tf.getCaretX();
            final int leftWidth = _context.getResources().getDisplayMetrics().widthPixels-mMaxWidth;
			_autoCompletePanel.showAsDropDown(tf,
											  caretx<leftWidth
                                              ?caretx:leftWidth+AutoPanelAdapter.PADDING,
											  y,
											  Gravity.TOP);
        }
		_list.setSelection(0);
		isShow = true;
    }

    public void dismiss() {
        if (isShow()) {
			_autoCompletePanel.dismiss();
        }
		isShow = false;
    }

    public boolean isShow() {
        return isShow && _autoCompletePanel.isShowing();
    }
}

