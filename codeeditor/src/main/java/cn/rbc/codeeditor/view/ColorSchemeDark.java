package cn.rbc.codeeditor.view;
import android.graphics.*;
import static cn.rbc.codeeditor.view.ColorScheme.Colorable.*;

public class ColorSchemeDark extends ColorScheme
{
	private static ColorScheme mColorScheme;

	private ColorSchemeDark() {
		setColor(FOREGROUND, 0xFFF0F0F0);
		setColor(BACKGROUND, 0xFF202020);
        setColor(BACKGROUND_PURE, 0xFF000000);
		setColor(TYPE, 0xFF99CCEE);
		setColor(KEYWORD, 0xFF6AB0E2);
		setColor(NOTE, 0xFF8AB0E2);
		setColor(OPERATOR, 0xFF8AB0E2);
		setColor(SECONDARY, 0xFFAAAAAA);
		setColor(COMMENT, 0xFF50BB50);
		setColor(STRING, 0xFFFF8E8E);
		setColor(NUMBER, 0xFFFF8E8E);
		setColor(CARET_DISABLED, 0xFFF0F0F0);
		setColor(CARET_BACKGROUND, 0xFF42A5F5);
		setColor(NON_PRINTING_GLYPH, 0xFF686868);
		setColor(LINE_HIGHLIGHT, 0x1E888888);
		setColor(SELECTION_BACKGROUND, 0xFF505050);
		setColor(HIGHLIGHT, 0x80888888);
	}

	public static ColorScheme getInstance() {
		if (mColorScheme==null)
			mColorScheme = new ColorSchemeDark();
		return mColorScheme;
	}

	@Override
	public boolean isDark() {
		return true;
	}
}
