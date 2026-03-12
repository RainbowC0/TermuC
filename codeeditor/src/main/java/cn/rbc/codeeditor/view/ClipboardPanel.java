package cn.rbc.codeeditor.view;

import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import cn.rbc.codeeditor.R;

public class ClipboardPanel implements ActionMode.Callback {
    protected FreeScrollingTextField _textField;
    private Context _context;

    private ActionMode _clipboardActionMode;
    private ActionMode.Callback _clipboardActionModeCallback2;
    private ActionMode.Callback _customCallback;
    private int _createMode;
    public final static int ID_SELECTALL = 1, ID_CUT = 2, ID_COPY = 3, ID_PASTE = 4, ID_DELETE = 5, ID_FORMAT = 6;
    public final static int CREATE_BEFORE = 1, CREATE_AFTER = 2;

    public ClipboardPanel(FreeScrollingTextField textField) {
        _textField = textField;
        _context = textField.getContext();
    }

    public final Context getContext() {
        return _context;
    }

    public final void show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initData();
            startClipboardActionNew();
        } else
            startClipboardAction();
    }

    public final void hide() {
        if (_clipboardActionMode != null) {
            _clipboardActionMode.finish();
            _clipboardActionMode = null;
        }
    }

    private void startClipboardAction() {
        ActionMode mode = _clipboardActionMode;
        if (mode == null)
            _clipboardActionMode = _textField.startActionMode(this);
        else
            mode.invalidate();
    }

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		//mode.setTitle(android.R.string.selectTextMode);
        int bm = _createMode;
        ActionMode.Callback callback = _customCallback;
        if (callback != null && (bm&CREATE_BEFORE)!=0) {
            callback.onCreateActionMode(mode, menu);
        }
		TypedArray array = _context.getTheme().obtainStyledAttributes(
			new int[]{
				android.R.attr.actionModeSelectAllDrawable,
				android.R.attr.actionModeCutDrawable,
				android.R.attr.actionModeCopyDrawable,
				android.R.attr.actionModePasteDrawable,
			});
		menu.add(0, ID_SELECTALL, 0, _context.getString(android.R.string.selectAll))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('a')
			.setIcon(array.getDrawable(0));

		menu.add(0, ID_CUT, 0, _context.getString(android.R.string.cut))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('x')
			.setIcon(array.getDrawable(1));

		menu.add(0, ID_COPY, 0, _context.getString(android.R.string.copy))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('c')
			.setIcon(array.getDrawable(2));

		menu.add(0, ID_PASTE, 0, _context.getString(android.R.string.paste))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('v')
			.setIcon(array.getDrawable(3));

		menu.add(0, ID_DELETE, 0, _context.getString(R.string.delete))
			.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
			.setAlphabeticShortcut('d');

		menu.add(0, ID_FORMAT, 0, _context.getString(R.string.format))
			.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
			.setAlphabeticShortcut('f');
		array.recycle();
        if (callback != null && (bm&CREATE_AFTER)!=0) {
            callback.onCreateActionMode(mode, menu);
        }
		return menu.size() > 0;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        FreeScrollingTextField fld = _textField;
        boolean isSel = fld.getSelectionStart() != fld.getSelectionEnd();
        menu.findItem(ID_CUT).setVisible(isSel);
        menu.findItem(ID_COPY).setVisible(isSel);
        menu.findItem(ID_DELETE).setVisible(isSel);
        ActionMode.Callback callback = _customCallback;
		return callback != null && callback.onPrepareActionMode(mode, menu);
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case ID_SELECTALL:
				_textField.selectAll();
				break;
			case ID_CUT:
				_textField.cut();
				mode.finish();
				break;
			case ID_COPY:
				_textField.copy();
				mode.finish();
				break;
			case ID_PASTE:
				_textField.paste();
				mode.finish();
				break;
			case ID_DELETE:
				_textField.delete();
				mode.finish();
				break;
			case ID_FORMAT:
				_textField.format();
				mode.finish();
                break;
            default:
                ActionMode.Callback callback = _customCallback;
                return callback != null && callback.onActionItemClicked(mode, item);
		}
		return true;
	}

	@Override
	public void onDestroyActionMode(ActionMode p1) {
		//_textField.selectText(false);
		_clipboardActionMode = null;
        ActionMode.Callback callback = _customCallback;
        if (callback != null)
            callback.onDestroyActionMode(p1);
	}

    public void setCustomCallback(ActionMode.Callback callback, int createMode) {
        _customCallback = callback;
        _createMode = createMode;
    }

    private void startClipboardActionNew() {
        ActionMode mode = _clipboardActionMode;
        if (mode == null) {
            _clipboardActionMode = _textField.startActionMode(_clipboardActionModeCallback2, ActionMode.TYPE_FLOATING);
        } else
            mode.invalidate();
	}

    private void initData() {
        if (_clipboardActionModeCallback2 == null)
        _clipboardActionModeCallback2 = new ActionMode.Callback2() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return ClipboardPanel.this.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return ClipboardPanel.this.onPrepareActionMode(mode, menu);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return ClipboardPanel.this.onActionItemClicked(mode, item);
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
                ClipboardPanel.this.onDestroyActionMode(p1);
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                FreeScrollingTextField fld = _textField;
                Rect caret = fld.getBoundingBox(fld.getSelectionStart());
                int x = fld.getScrollX(), y = fld.getScrollY();
				caret.top -= y;
                caret.bottom = Math.max(0, caret.bottom-y);
				caret.left -= x;
                caret.right -= x;
                outRect.set(caret);
            }
        };

    }

    public final void invalidateContentRect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _clipboardActionMode != null)
            _clipboardActionMode.invalidateContentRect();
    }

    public final void invalidate() {
        if (_clipboardActionMode != null) {
            _clipboardActionMode.invalidate();
        }
    }
}
