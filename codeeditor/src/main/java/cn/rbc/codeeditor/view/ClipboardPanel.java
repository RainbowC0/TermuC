package cn.rbc.codeeditor.view;

import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import cn.rbc.termuc.*;

public class ClipboardPanel implements ActionMode.Callback {
    protected FreeScrollingTextField _textField;
    private Context _context;

    private ActionMode _clipboardActionMode;
    private ActionMode.Callback2 _clipboardActionModeCallback2;

    public ClipboardPanel(FreeScrollingTextField textField) {
        _textField = textField;
        _context = textField.getContext();
    }

    public Context getContext() {
        return _context;
    }

    public void show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initData();
            startClipboardActionNew();
        } else
            startClipboardAction();
    }

    public void hide() {
        stopClipboardAction();
    }

    public void startClipboardAction() {
        if (_clipboardActionMode == null)
            _textField.startActionMode(this);
    }

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		_clipboardActionMode = mode;
		//mode.setTitle(android.R.string.selectTextMode);
		TypedArray array = _context.getTheme().obtainStyledAttributes(
			new int[]{
				android.R.attr.actionModeSelectAllDrawable,
				android.R.attr.actionModeCutDrawable,
				android.R.attr.actionModeCopyDrawable,
				android.R.attr.actionModePasteDrawable
			});
		menu.add(0, 0, 0, _context.getString(android.R.string.selectAll))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('a')
			.setIcon(array.getDrawable(0));

		menu.add(0, 1, 0, _context.getString(android.R.string.cut))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('x')
			.setIcon(array.getDrawable(1));

		menu.add(0, 2, 0, _context.getString(android.R.string.copy))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('c')
			.setIcon(array.getDrawable(2));

		menu.add(0, 3, 0, _context.getString(android.R.string.paste))
			.setShowAsActionFlags(2)
			.setAlphabeticShortcut('v')
			.setIcon(array.getDrawable(3));

		menu.add(0, 4, 0, _context.getString(R.string.delete))
			.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
			.setAlphabeticShortcut('d');

		menu.add(0, 5, 0, _context.getString(R.string.format))
			.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
			.setAlphabeticShortcut('f');
		array.recycle();
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case 0:
				_textField.selectAll();
				break;
			case 1:
				_textField.cut();
				mode.finish();
				break;
			case 2:
				_textField.copy();
				mode.finish();
				break;
			case 3:
				_textField.paste();
				mode.finish();
				break;
			case 4:
				_textField.delete();
				mode.finish();
				break;
			case 5:
				_textField.format();
				mode.finish();
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode p1) {
		_textField.selectText(false);
		_clipboardActionMode = null;
	}

    public void startClipboardActionNew() {
        if (_clipboardActionMode == null) {
            _textField.startActionMode(_clipboardActionModeCallback2, ActionMode.TYPE_FLOATING);
        }
	}

    private void initData() {
        _clipboardActionModeCallback2 = new ActionMode.Callback2() {
           // private Rect caret;
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                _clipboardActionMode = mode;
                FreeScrollingTextField fld = _textField;
                boolean isSel = fld.getSelectionStart() != fld.getSelectionEnd();
                menu.add(0, 0, 0, _context.getString(android.R.string.selectAll))
					.setShowAsActionFlags(2)
					.setAlphabeticShortcut('a');
                menu.add(0, 1, 0, _context.getString(android.R.string.cut))
					.setShowAsActionFlags(2)
					.setAlphabeticShortcut('x').setEnabled(isSel);
                menu.add(0, 2, 0, _context.getString(android.R.string.copy))
					.setShowAsActionFlags(2)
					.setAlphabeticShortcut('c').setEnabled(isSel);
                menu.add(0, 3, 0, _context.getString(android.R.string.paste))
					.setShowAsActionFlags(2)
					.setAlphabeticShortcut('v');
                menu.add(0, 4, 0, _context.getString(R.string.delete))
					.setShowAsActionFlags(2)
					.setAlphabeticShortcut('d').setEnabled(isSel);
				menu.add(0, 5, 0, _context.getString(R.string.format))
					.setShowAsActionFlags(2)
					.setAlphabeticShortcut('f');
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return ClipboardPanel.this.onActionItemClicked(mode, item);
            }

            @Override
            public void onDestroyActionMode(ActionMode p1) {
                _textField.selectText(false);
                _clipboardActionMode = null;
                //caret = null;
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
                Menu menu = mode.getMenu();
                boolean isSel = fld.getSelectionStart() != fld.getSelectionEnd();
                menu.findItem(1).setEnabled(isSel);
                menu.findItem(2).setEnabled(isSel);
                menu.findItem(4).setEnabled(isSel);
            }
        };

    }

    public void stopClipboardAction() {
        if (_clipboardActionMode != null) {
            //_clipboardActionModeCallback2.onDestroyActionMode(_clipboardActionMode);
            _clipboardActionMode.finish();
            _clipboardActionMode = null;
        }
    }

    public void invalidateContentRect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _clipboardActionMode != null)
            _clipboardActionMode.invalidateContentRect();
    }
}
