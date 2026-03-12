package cn.rbc.codeeditor.view;

import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.util.*;

import static cn.rbc.codeeditor.util.DLog.log;

//*********************************************************************
//************************** InputConnection **************************
//*********************************************************************
/*
 * Does not provide ExtractedText related methods
 */
public class TextFieldInputConnection extends BaseInputConnection {
    private boolean isComposing = false;
    private int composingCharCount = 0;
    private int mCaretPosition = -1;
    private FreeScrollingTextField textField;

    public TextFieldInputConnection(FreeScrollingTextField v ) {
        super(v, true);
        textField = v;
    }

    public void resetComposingState() {
        composingCharCount = 0;
        isComposing = false;
        Document doc = textField.hDoc;
        if (doc.isBatchEdit())
            doc.endBatchEdit();
    }

    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        ExtractedText et = new ExtractedText();
        FreeScrollingTextField fld = textField;
        if (fld.isSelectText()) {
            et.flags |= et.FLAG_SELECTING;
        }
        et.text = fld.hDoc;
        et.startOffset = 0;
        et.selectionStart = fld.getSelectionStart();
        et.selectionEnd = fld.getSelectionEnd();
        et.partialStartOffset = -1;
        et.partialEndOffset = -1;
        return et;
    }

    @Override
    public boolean performContextMenuAction(int id) {
        switch (id) {
            case android.R.id.copy:
                textField.copy();
                break;
            case android.R.id.cut:
                textField.cut();
                break;
            case android.R.id.paste:
                textField.paste();
                break;
            case android.R.id.startSelectingText:
            case android.R.id.stopSelectingText:
            case android.R.id.selectAll:
                textField.selectAll();
                break;
        }

        return false;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN)
            return super.sendKeyEvent(event);
        FreeScrollingTextField fld = textField;
        int code = event.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                fld.selectText(!textField.isSelected());
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                fld.moveCaretLeft();
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                fld.moveCaretUp();
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                fld.moveCaretRight();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                fld.moveCaretDown();
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                fld.moveCaret(event.isCtrlPressed() ? 0 : fld.hDoc.getRowOffset(fld.getCaretRow()));
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                int off;
                fld.moveCaret(event.isCtrlPressed() || (off = fld.hDoc.getRowOffset(fld.getCaretRow() + 1)) < 0
                    ? fld.hDoc.length() - 1 : off - 1);
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                int row = fld.mCaretRow;
                int nrows = fld.getNumVisibleRows();
                Document doc = fld.hDoc;
                if (code == KeyEvent.KEYCODE_PAGE_UP) {
                    row = Math.max(row - nrows, 0);
                } else {
                    row = Math.min(row + nrows, doc.getRowCount()-1);
                }
                off = doc.getRowOffset(row) + Math.min(fld.getColumn(fld.mCaretPosition), doc.getRowSize(row)-1);
                if (off < 0) off = 0;
                int len = doc.length()-1;
                if (off > len) off = len;
                fld.moveCaret(off);
                break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (textField.mAutoCompletePanel.isShow()) {
                    textField.mAutoCompletePanel.select(0);
					break;
				}
            default:
				return super.sendKeyEvent(event);
        }
		return true;
    }

    /**
     * Only true when the InputConnection has not been used by the IME yet.
     * Can be programatically cleared by resetComposingState()
     */
    public boolean isComposingStarted() {
        return isComposing;
    }

    /**
     * 输入法传递过来的字符串
     *
     * @param text
     * @param newCursorPosition
     * @return
     */
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        isComposing = true;
        if(!textField.hDoc.isBatchEdit())
            textField.hDoc.beginBatchEdit();
		boolean sel = false;
		TextFieldController tf = textField.mCtrlr;
        if (tf._isInSelectionMode) {
            tf.selectionDelete();
            composingCharCount = 0;
			sel = true;
        } else {
            tf.replaceComposingText(
                    textField.getCaretPosition() - composingCharCount,
                    composingCharCount,
                    text.toString());
            composingCharCount = text.length();
        }
        //TODO reduce invalidate calls
        if(newCursorPosition > 1)
            tf.moveCaret(mCaretPosition + newCursorPosition - 1);
        else if(newCursorPosition <= 0)
            tf.moveCaret(mCaretPosition - text.length() - newCursorPosition);
		if (sel)
			tf.determineSpans();
        // log("setComposingText:"+text+","+newCursorPosition);
        return true;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        log("commitText:"+text+","+newCursorPosition+","+composingCharCount);
             /*
            mCtrlr.replaceComposingText(
                    getCaretPosition() - composingCharCount,
                    composingCharCount,
                    text.toString());
            composingCharCount = 0;
            hDoc.endBatchEdit();
            //TODO reduce invalidate calls
            if (newCursorPosition > 1) {
                mCtrlr.moveCaret(mCaretPosition + newCursorPosition - 1);
            }
          else if(newCursorPosition==1){
              mCtrlr.moveCaret(mCaretPosition + newCursorPosition);
        }
         //   else if (newCursorPosition <= 0) {
             //   mCtrlr.moveCaret(mCaretPosition - text.length() - newCursorPosition);
            //}
            isComposing = false;
            return true;

         */
        return setComposingText(text, newCursorPosition) && finishComposingText();
    }


    @Override
    public boolean deleteSurroundingText(int leftLength, int rightLength) {
        if (composingCharCount != 0)
            DLog.d("codeeditor","Warning: Implmentation of InputConnection.deleteSurroundingText" +
                    " will not skip composing text");

        textField.mCtrlr.deleteAroundComposingText(leftLength, rightLength);
        return true;
    }

    @Override
    public boolean finishComposingText() {
        resetComposingState();
        return true;
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        if (!textField.isAutoCaps()) {
            return 0;
        }
        int capsMode = 0;

        // Ignore InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; not used in TextWarrior

        if ((reqModes & InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                == InputType.TYPE_TEXT_FLAG_CAP_WORDS) {
            int prevChar = mCaretPosition - 1;
            if (prevChar < 0 || Tokenizer.getLanguage().isWhitespace(textField.hDoc.charAt(prevChar))) {
                capsMode |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;

                //set CAP_SENTENCES if client is interested in it
                if ((reqModes & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                        == InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                    capsMode |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            }
        }

        // Strangely, Android soft keyboard does not set TYPE_TEXT_FLAG_CAP_SENTENCES
        // in reqModes even if it is interested in doing auto-capitalization.
        // Android bug? Therefore, we assume TYPE_TEXT_FLAG_CAP_SENTENCES
        // is always set to be on the safe side.
        else {
            Language lang = Tokenizer.getLanguage();

            int prevChar = mCaretPosition - 1;
            int whitespaceCount = 0;
            boolean capsOn = true;

            // Turn on caps mode only for the first char of a sentence.
            // A fresh line is also considered to start a new sentence.
            // The position immediately after a period is considered lower-case.
            // Examples: "abc.com" but "abc. Com"
            while (prevChar >= 0) {
                char c = textField.hDoc.charAt(prevChar);
                if (c == Language.NEWLINE)
                    break;

                if (!lang.isWhitespace(c)) {
                    if (whitespaceCount == 0 || !lang.isSentenceTerminator(c))
                        capsOn = false;
                    break;
                }

                ++whitespaceCount;
                --prevChar;
            }

            if (capsOn)
                capsMode |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        }

        return capsMode;
    }

    @Override
    public CharSequence getTextAfterCursor(int maxLen, int flags) {
        return textField.mCtrlr.getTextAfterCursor(maxLen); //ignore flags
    }

    @Override
    public CharSequence getTextBeforeCursor(int maxLen, int flags) {
        return textField.mCtrlr.getTextBeforeCursor(maxLen); //ignore flags
    }

    @Override
    public boolean setComposingRegion(int start, int end){
        isComposing = true;
        mCaretPosition = start;
        composingCharCount = end - start;
        return true;

    }
    @Override
    public boolean setSelection(int start, int end) {
        log("setSelection:"+start+","+end);
		TextFieldController fc = textField.mCtrlr;
        if (start == end) {
            if (start == 0) {
                //适配搜狗输入法
                if (textField.getCaretPosition() > 0)
                    fc.moveCaret(textField.getCaretPosition() - 1);
            } else
                fc.moveCaret(start);
        } else
            fc.setSelectionRange(start, end - start, false, true);
        return true;
    }
}// end inner class
