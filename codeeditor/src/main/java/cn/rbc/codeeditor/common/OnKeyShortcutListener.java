package cn.rbc.codeeditor.common;

import android.view.KeyEvent;

public interface OnKeyShortcutListener {
	boolean onKeyShortcut(int keyCode,KeyEvent event);
}
