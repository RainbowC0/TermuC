package cn.rbc.termuc;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.util.*;
import android.view.*;
import cn.rbc.codeeditor.common.*;
import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.util.*;
import cn.rbc.codeeditor.view.*;
import cn.rbc.codeeditor.view.autocomplete.*;
import java.io.*;
import java.util.List;
import android.widget.*;

public class TextEditor extends FreeScrollingTextField implements OnCaretScrollListener {
    // private Document _inputtingDoc;
    // private boolean _isWordWrap;
    private Context mContext;
    private String _lastSelectFile;
    private int _index;
	private Formatter mFormatter;
    private OnEditedListener mEditedListener;
    private OnCaretScrollListener mCrtLis;
    //private boolean mDirty;

    /*
     private Handler handler = new Handler() {
     @Override
     public void handleMessage(Message msg) {
     switch (msg.what) {
     case ReadThread.MSG_READ_OK:
     setText(msg.obj.toString());
     break;
     case ReadThread.MSG_READ_FAIL:
     showToast("打开失败");
     break;
     case WriteThread.MSG_WRITE_OK:
     showToast("保存成功");
     break;
     case WriteThread.MSG_WRITE_FAIL:
     showToast("保存失败");
     break;
     }
     }
     };*/

    public TextEditor(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public TextEditor(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        init();
    }

    private void init() {
        setTypeface(Typeface.MONOSPACE);
        setShowLineNumbers(true);
        setHighlightCurrentRow(true);
        setAutoComplete(false);
        setAutoIndent(true);
        setUseGboard(true);
        setNavigationMethod(new EditorNavigationMethod(this));
        crtLis = this;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (_index != 0 && right > 0) {
            moveCaret(_index);
            _index = 0;
        }
    }

    public static void setLanguage(Language language) {
        Tokenizer.setLanguage(language);
    }

    public String getSelectedText() {
        return hDoc.subSequence(getSelectionStart(), getSelectionEnd()).toString();
    }

    public void gotoLine(int line) {
        if (line > hDoc.getRowCount()) {
            line = hDoc.getRowCount();
        }
        int i = getText().getLineOffset(line - 1);
        setSelection(i);
    }

    @Override
    public void setSuggestion(boolean enable) {
        mTypeInput = enable ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        final int filteredMetaState = event.getMetaState() & ~KeyEvent.META_CTRL_MASK;
        if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    selectAll();
                    return true;
                case KeyEvent.KEYCODE_X:
                    cut();
                    return true;
                case KeyEvent.KEYCODE_C:
                    copy();
                    return true;
                case KeyEvent.KEYCODE_V:
                    paste();
                    return true;
				case KeyEvent.KEYCODE_EQUALS:
					setTextSize((int)(getTextSize() + HelperUtils.getDpi(mContext)));
					return true;
				case KeyEvent.KEYCODE_MINUS:
					setTextSize((int)(getTextSize() - HelperUtils.getDpi(mContext)));
					return true;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

	@Override
	public void setTabSpaces(int spaceCount) {
		super.setTabSpaces(spaceCount);
		setAutoIndentWidth(spaceCount);
	}

	@Override
	public void format() {
		if (mFormatter != null) {
			mFormatter.format(hDoc, mAutoIndentWidth);
		} else
			super.format();
	}

    public void setFormatter(Formatter fmt) {
        mFormatter = fmt;
    }

    public void setText(CharSequence c) {
        Document doc = new Document(this);
        doc.setWordWrap(isWordWrap());
        doc.setText(c);
        setDocument(doc);
    }

    public AutoCompletePanel getAutoCompletePanel() {
        return mAutoCompletePanel;
    }

    public SignatureHelpPanel getSigHelpPanel() {
        return mSigHelpPanel;
    }

    public ClipboardPanel getClipboardPanel() {
        return mClipboardPanel;
    }

    public File getOpenedFile() {
        if (_lastSelectFile != null)
            return new File(_lastSelectFile);

        return null;
    }

    public void setOpenedFile(String file) {
        _lastSelectFile = file;
    }

    public void replaceAll(CharSequence c) {
        replaceText(0, getLength() - 1, c);
    }

    public void setSelection(int index) {
        selectText(false);
        if (!hasLayout())
            moveCaret(index);
        else
            _index = index;
    }

    public void undo() {
        int newPosition = hDoc.undo();
        if (newPosition >= 0) {
            //TODO editor.setEdited(false);
            // if reached original condition of file
            setEdited(hDoc.getMarkedVersion() != hDoc.getCurrentVersion());
            mCtrlr.determineSpans();
			//tc
            selectText(false);
            moveCaret(newPosition);
        }
    }

    public void redo() {
        int newPosition = hDoc.redo();

        if (newPosition >= 0) {
            setEdited(hDoc.getMarkedVersion() != hDoc.getCurrentVersion());
            mCtrlr.determineSpans();
			//tc
            selectText(false);
            moveCaret(newPosition);
        }
    }

    @Override
    public void setEdited(boolean set) {
        isEdited = set;
        if (mEditedListener!=null) {
            mEditedListener.onEdited(set);
        }
    }

    public void setOnEditedListener(OnEditedListener edlis) {
        mEditedListener = edlis;
    }

    public void updateCaret(int caretIndex) {
        ((YoyoNavigationMethod)mNavMethod).updateCaret(caretIndex);
        if (mCrtLis != null)
            mCrtLis.updateCaret(caretIndex);
	}

	public void addCaretListener(OnCaretScrollListener crtlis) {
		mCrtLis = crtlis;
	}

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        int pos;
        if ("s".equals(Application.completion)
            && ev.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
            && ev.getButtonState() == MotionEvent.BUTTON_PRIMARY
            && (ev.getMetaState() & KeyEvent.META_CTRL_ON) != 0
            && (pos = coordToCharIndex(
            (int)ev.getX()-getPaddingLeft()+getScrollX(),
            (int)ev.getY()-getPaddingTop()+getScrollY())) >= 0
        ) {
            Document doc = hDoc;
            int line = doc.findLineNumber(pos);
            pos -= doc.getLineOffset(line);
            MainActivity act = (MainActivity)getContext();
            String fl = act.getTag(act.getActionBar().getSelectedNavigationIndex());
            Application.getInstance().lsp.definition(new File(fl), line, pos);
            ((EditorNavigationMethod)mNavMethod).intercept = true;
            return true;
        }
        return super.onGenericMotionEvent(ev);
    }
    /*
     public void open(String filename) {
     _lastSelectFile = filename;

     File inputFile = new File(filename);
     _inputtingDoc = new Document(this);
     _inputtingDoc.setWordWrap(this.isWordWrap());
     ReadThread readThread = new ReadThread(inputFile.getAbsolutePath(), handler);
     readThread.start();
     }

     /**
     * 保存文件
     * * @param file
     */
    /*
     public void save(String file) {
     WriteThread writeThread = new WriteThread(getText().toString(), file, handler);
     writeThread.start();
     }*/
     interface OnEditedListener {
         void onEdited(boolean edited);
     }

     private static class EditorNavigationMethod extends YoyoNavigationMethod {
         protected boolean intercept;
         EditorNavigationMethod(FreeScrollingTextField fld) {
             super(fld);
         }

         @Override
         public boolean onDoubleTap(MotionEvent e)
         {
             boolean ret = super.onDoubleTap(e);
             FreeScrollingTextField fld = mTextField;
             Document doc = fld.getText();
             List<ErrSpan> dg = doc.getDiag();
             int x;
             if (dg != null && dg.size() > 0
                 && (x = fld.coordToCharIndex(
                             screenToViewX((int)e.getX()),
                             screenToViewY((int)e.getY())
                         )
                     ) >= 0) {
                 int line = 1 + doc.findLineNumber(x);
                 int y = dg.size() - 1;
                 x = doc.getLineOffset(line - 1);
                 int start = fld.getSelectionStart() - x;
                 int end = fld.getSelectionEnd() - x;
                 x = 0;
                 ErrSpan errspan;
                 while (x < y) {
                     int m = (x + y) >> 1;
                     errspan = dg.get(m);
                     if (errspan.enl > line || errspan.enl == line && errspan.enc >= start)
                         y = m;
                     else
                         x = m + 1;
                 }
                 errspan = dg.get(y);
                 if ((errspan.stl < line || errspan.stl == line && errspan.stc <= end)
                     && (line < errspan.enl || line == errspan.enl && start <= errspan.enc)
                     && errspan.msg != null) {
                     Context ctx = mTextField.getContext();
                     Toast t = new Toast(ctx);
                     LinearLayout ll = new LinearLayout(ctx);
                     ll.setOrientation(LinearLayout.VERTICAL);
                     TextView tv = new TextView(ctx);
                     tv.setTextColor(0xffffffff);
                     tv.setText(errspan.msg);
                     int pd = (int)(12 * HelperUtils.getDpi(ctx) + .5f);
                     ll.setPadding(pd, pd, pd, pd);
                     ll.setBackgroundColor(ColorScheme.DIAG[errspan.severity] & 0xf0ffffff);
                     ll.addView(tv);
                     t.setView(ll);
                     HelperUtils.show(t);
                 }
             }
             return ret;
         }

         @Override
         public void onLongPress(MotionEvent e)
         {
             if (intercept) {
                 intercept = false;
                 return;
             }
             super.onLongPress(e);
         }

         @Override
         public boolean onSingleTapUp(MotionEvent e)
         {
             if (intercept) {
                 intercept = false;
                 return true;
             }
             return super.onSingleTapUp(e);
         }
     }
}

