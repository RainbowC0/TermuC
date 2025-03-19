package cn.rbc.codeeditor.view;

import android.content.Context;
import android.text.ClipboardManager;
import android.view.inputmethod.InputMethodManager;

import cn.rbc.codeeditor.lang.Language;
import cn.rbc.codeeditor.util.Tokenizer;
import cn.rbc.codeeditor.util.Pair;
import cn.rbc.codeeditor.util.TextWarriorException;

import java.util.List;

import static cn.rbc.codeeditor.util.DLog.log;
import android.util.*;
import cn.rbc.codeeditor.util.*;
import java.util.*;
import android.widget.*;

//*********************************************************************
//************************ Controller logic ***************************
//*********************************************************************
public class TextFieldController implements Tokenizer.LexCallback, Runnable {
    private final Tokenizer _lexer = new Tokenizer(this);
    public boolean _isInSelectionMode = false;
    private boolean _isInSelectionMode2;
	public volatile boolean lexing;
    private FreeScrollingTextField field;
	private List<Pair> mRes;

    public TextFieldController(FreeScrollingTextField textField) {
        field = textField;
		lexing = false;
    }

    /**
     * Analyze the text for programming language keywords and redraws the
     * text view when done. The global programming language used is set with
     * the static method Lexer.setLanguage(Language)
     * <p>
     * Does nothing if the Lexer language is not a programming language
     */
    public void determineSpans() {
		lexing = true;
		_lexer.tokenize(field.hDoc);
    }

    public void cancelSpanning() {
		lexing = false;
        field.hDoc.edit0();
        _lexer.cancelTokenize();
    }

    @Override
    //This is usually called from a non-UI thread
    public void lexDone(final List<Pair> results) {
		mRes = results;
        field.post(this);
    }

	public void run() {
		FreeScrollingTextField fd = field;
		fd.hDoc.setSpans(mRes);
		lexing = false;
		fd.invalidate();
	}

    //- TextFieldController -----------------------------------------------
    //---------------------------- Key presses ----------------------------

    //TODO minimise invalidate calls from moveCaret(), insertion/deletion and word wrap
    public void onPrintableChar(char c) {
        // delete currently selected text, if any
		lexing = true;
        boolean selectionDeleted = false;
        if (_isInSelectionMode) {
            selectionDelete();
            selectionDeleted = true;
        }
		FreeScrollingTextField fld = field;
		int pos = fld.mCaretPosition;
		fld.hDoc.setTyping(true);
        switch (c) {
            case Language.BACKSPACE:
                if (selectionDeleted) break;
                if (pos > 0 ) {
					int l = pos > 1 && ((c = fld.hDoc.charAt(pos - 2)) == 0xd83d || c == 0xd83c) ? 2 : 1;
                    pos -= l;
					String s = fld.hDoc.subSequence(pos, pos + l).toString();
                    fld.hDoc.deleteAt(pos, l, System.nanoTime());
                    fld.onDel(s, fld.mCaretPosition, l);
                    moveCaretLeft(true);
					if (l == 2)
						moveCaretLeft(true);
                }
                break;
            case Language.DELETE:
                if (selectionDeleted) break;
                int l = fld.hDoc.length();
                if (pos < l) {
                    l = ((c=fld.hDoc.charAt(pos)) == 0xd83d || c == 0xd83c) ? 2 : 1;
                    String s = fld.hDoc.subSequence(pos, pos + l).toString();
                    fld.hDoc.deleteAt(pos, l, System.nanoTime());
                    fld.onDel(s, pos, l);
                }
                break;
            case Language.NEWLINE:
                if (fld.mAutoCompletePanel.isShow()) {
                    fld.mAutoCompletePanel.select(0);
                    return;
                }
				char[] ind;
                if (fld.isAutoIndent) {
                    ind = createAutoIndent();
                    fld.hDoc.insertBefore(ind, pos, System.nanoTime());
                    moveCaret(fld.mCaretPosition + ind.length);
                } else {
					fld.hDoc.insertBefore((ind=new char[]{c}), pos, System.nanoTime());
                	moveCaretRight(true);
				}
                field.onNewLine(new String(ind));
				break;
			case Language.TAB:
				if (fld.isUseSpace()) {
					int tl = fld.mTabLength;
                    Document doc = fld.hDoc;
                    int lineNumber = doc.findLineNumber(pos);
                    int offset = doc.getLineOffset(lineNumber);
					char[] cs = new char[tl - (pos - offset) % tl];
					Arrays.fill(cs, ' ');
					fld.hDoc.insertBefore(cs, pos, System.nanoTime());
					moveCaret(pos + cs.length);
					fld.onAdd(new String(cs), pos, cs.length);
					break;
				}
            default:
                fld.hDoc.insertBefore(new char[]{c}, pos, System.nanoTime());
                moveCaretRight(true);
                fld.onAdd(String.valueOf(c), pos, 1);
                break;
        }
        fld.setEdited(true);
        determineSpans();
		//tc
    }

    /**
     * Return a char[] with a newline as the 0th element followed by the
     * leading spaces and tabs of the line that the caret is on
     * 创建自动缩进
     */
    private char[] createAutoIndent() {
		FreeScrollingTextField fld = field;
		Document doc = fld.hDoc;
		int pos = fld.mCaretPosition;
        int lineNum = doc.findLineNumber(pos);
        int startOfLine = doc.getLineOffset(lineNum);
        int whitespaceCount = 0;
        //查找上一行的空白符个数
		int i, mTL = fld.mTabLength;
		char c;
        for (i = startOfLine; i < pos;) {
            c = doc.charAt(i++);
            if (c != ' ' && c != Language.TAB)
                break;
            if (c == Language.TAB)
                whitespaceCount += mTL-whitespaceCount%mTL;
            else if (c == ' ')
                ++whitespaceCount;
        }
        //寻找最后字符
        int endChar = 0;
        for (i = startOfLine;i < pos;) {
            c = doc.charAt(i++);
            if (c == Language.NEWLINE || c == Language.EOF)
                break;
            endChar = c;
        }
		//最后字符为'{',缩进
        if (endChar == '{')
            whitespaceCount += fld.mAutoIndentWidth;
        if (whitespaceCount < 0)
            return new char[]{Language.NEWLINE};
		char[] indent;
		if (fld.isUseSpace()) {
			indent = new char[1 + whitespaceCount];
			indent[0] = Language.NEWLINE;
			Arrays.fill(indent, 1, indent.length, ' ');
		} else {
			int tl = 1 + whitespaceCount / mTL;
			indent = new char[tl + whitespaceCount % mTL];
			indent[0] = Language.NEWLINE;
			Arrays.fill(indent, 1, tl, Language.TAB);
			Arrays.fill(indent, tl, indent.length, ' ');
		}
        return indent;
    }

    public void moveCaretDown() {
		FreeScrollingTextField fld = field;
        if (!fld.caretOnLastRowOfFile()) {
            int currCaret = fld.mCaretPosition;
            int currRow = fld.mCaretRow;
            int newRow = currRow + 1;
            int currColumn = fld.getColumn(currCaret);
            int currRowLength = fld.hDoc.getRowSize(currRow);
            int newRowLength = fld.hDoc.getRowSize(newRow);

            if (currColumn < newRowLength)
			// Position at the same column as old row.
                fld.mCaretPosition += currRowLength;
            else
			// Column does not exist in the new row (new row is too short).
			// Position at end of new row instead.
                fld.mCaretPosition +=
					currRowLength - currColumn + newRowLength - 1;
            ++fld.mCaretRow;

            updateSelectionRange(currCaret, fld.mCaretPosition);
            if (!fld.focusCaret())
                fld.invalidateRows(currRow, newRow + 1);
            // 拖动yoyo球滚动时，保证yoyo球的坐标与光标一致
            fld.crtLis.updateCaret(fld.mCaretPosition);
            fld.mRowListener.onRowChanged(newRow);
            stopTextComposing();
        }
    }

    public void moveCaretUp() {
		FreeScrollingTextField fld = field;
        if (!fld.caretOnFirstRowOfFile()) {
            int currCaret = fld.mCaretPosition;
            int currRow = fld.mCaretRow;
            int newRow = currRow - 1;
            int currColumn = fld.getColumn(currCaret);
            int newRowLength = fld.hDoc.getRowSize(newRow);

            if (currColumn < newRowLength)
			// Position at the same column as old row.
                fld.mCaretPosition -= newRowLength;
            else
			// Column does not exist in the new row (new row is too short).
			// Position at end of new row instead.
                fld.mCaretPosition -= (currColumn + 1);
            --fld.mCaretRow;

            updateSelectionRange(currCaret, fld.mCaretPosition);
            if (!fld.focusCaret())
                fld.invalidateRows(newRow, currRow + 1);
            // 拖动yoyo球滚动时，保证yoyo球的坐标与光标一致
            fld.crtLis.updateCaret(fld.mCaretPosition);
            fld.mRowListener.onRowChanged(newRow);
            stopTextComposing();
        }
    }

    /**
     * @param isTyping Whether caret is moved to a consecutive position as
     *                 a result of entering text
     */
    public void moveCaretRight(boolean isTyping) {
		FreeScrollingTextField fld = field;
        if (!fld.caretOnEOF()) {
            int originalRow = fld.mCaretRow;
            int pos = ++fld.mCaretPosition;
            updateCaretRow();
            updateSelectionRange(pos - 1, pos);
            if (!fld.focusCaret())
                fld.invalidateRows(originalRow, fld.mCaretRow + 1);

            if (!isTyping)
                stopTextComposing();
            // 拖动yoyo球滚动时，保证yoyo球的坐标与光标一致
            fld.crtLis.updateCaret(pos);
        }
    }

    /**
     * @param isTyping Whether caret is moved to a consecutive position as
     *                 a result of deleting text
     */
    public void moveCaretLeft(boolean isTyping) {
		FreeScrollingTextField fld = field;
        if (fld.mCaretPosition > 0) {
            int originalRow = fld.mCaretRow;
            int pos = --fld.mCaretPosition;
            updateCaretRow();
            updateSelectionRange(pos + 1, pos);
            if (!fld.focusCaret())
                fld.invalidateRows(fld.mCaretRow, originalRow + 1);

            if (!isTyping)
                stopTextComposing();
            // 拖动yoyo球滚动时，保证yoyo球的坐标与光标一致
            fld.crtLis.updateCaret(pos);
        }
    }

    public void moveCaret(int i) {
		FreeScrollingTextField fld = field;
        if (i < 0 || i >= fld.hDoc.length()) {
            TextWarriorException.fail("Invalid caret position");
            return;
        }
        updateSelectionRange(fld.mCaretPosition, i);

        fld.mCaretPosition = i;
        updateAfterCaretJump();
        
    }

    private void updateAfterCaretJump() {
		FreeScrollingTextField fld = field;
        int oldRow = fld.mCaretRow;
        updateCaretRow();
        if (!fld.focusCaret()) {
            fld.invalidateRows(oldRow, oldRow + 1); //old caret row
            fld.invalidateCaretRow(); //new caret row
        }
        stopTextComposing();
    }

    /**
     * This helper method should only be used by internal methods after setting
     * mTextFiledl.mCaretPosition, in order to to recalculate the new row the caret is on.
     */
    void updateCaretRow() {
		FreeScrollingTextField fld = field;
        int newRow = fld.hDoc.findRowNumber(fld.mCaretPosition);
        if (fld.mCaretRow != newRow) {
            fld.mCaretRow = newRow;
            fld.mRowListener.onRowChanged(newRow);
        }
    }

    public void stopTextComposing() {
		FreeScrollingTextField fld = field;
        InputMethodManager im = (InputMethodManager) fld.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        // This is an overkill way to inform the InputMethod that the caret
        // might have changed position and it should re-evaluate the
        // caps mode to use.
        im.restartInput(field);
		TextFieldInputConnection tf = fld.mInputConnection;

        if (tf != null && tf.isComposingStarted())
            tf.resetComposingState();
    }

    //- TextFieldController -----------------------------------------------
    //-------------------------- Selection mode ---------------------------
    public final boolean isSelectText() {
        return _isInSelectionMode;
    }

    /**
     * Enter or exit select mode.
     * Does not invalidate view.
     *
     * @param mode If true, enter select mode; else exit select mode
     */
    public void setSelectText(boolean mode) {
        if (mode == _isInSelectionMode)
            return;

		FreeScrollingTextField fld = field;
        if (mode)
            fld.mSelectionEdge = fld.mSelectionAnchor = fld.mCaretPosition;
        else
            fld.mSelectionEdge = fld.mSelectionAnchor = -1;
        _isInSelectionMode = _isInSelectionMode2 = mode;
        fld.selLis.onSelectionChanged(mode, fld.getSelectionStart(), fld.getSelectionEnd());
    }

    public final boolean isSelectText2() {
        return _isInSelectionMode2;
    }

    public boolean inSelectionRange(int charOffset) {
        if (field.mSelectionAnchor < 0)
            return false;
        return (field.mSelectionAnchor <= charOffset && charOffset < field.mSelectionEdge);
    }

    /**
     * Selects numChars count of characters starting from beginPosition.
     * Invalidates necessary areas.
     *
     * @param beginPosition
     * @param numChars
     * @param scrollToStart If true, the start of the selection will be scrolled
     *                      into view. Otherwise, the end of the selection will be scrolled.
     */

    public void setSelectionRange(int beginPosition, int numChars, boolean scrollToStart, boolean mode) {
		FreeScrollingTextField fld = field;
        TextWarriorException.assertVerbose(
			(beginPosition >= 0) && numChars <= (fld.hDoc.length() - 1) && numChars >= 0,
			"Invalid range to select");

        if (_isInSelectionMode)
		// unhighlight previous selection
            fld.invalidateSelectionRows();
        else {
            // unhighlight caret
            fld.invalidateCaretRow();
            if (mode)
                setSelectText(true);
            else
                _isInSelectionMode = true;
        }

        fld.mCaretPosition = fld.mSelectionEdge = (fld.mSelectionAnchor=beginPosition) + numChars;

        stopTextComposing();
        updateCaretRow();
        if (mode)
            fld.selLis.onSelectionChanged(isSelectText(), fld.mSelectionAnchor, fld.mSelectionEdge);
        boolean scrolled = fld.makeCharVisible(fld.mSelectionEdge);

        if (scrollToStart)
		//TODO reduce unnecessary scrolling and write a method to scroll
		// the beginning of multi-line selections as far left as possible
            scrolled = fld.makeCharVisible(fld.mSelectionAnchor);

        if (!scrolled)
            fld.invalidateSelectionRows();
    }

    /**
     * Moves the caret to an edge of selected text and scrolls it to view.
     *
     * @param start If true, moves the caret to the beginning of
     *              the selection. Otherwise, moves the caret to the end of the selection.
     *              In all cases, the caret is scrolled to view if it is not visible.
     */
    public void focusSelection(boolean start) {
        if (_isInSelectionMode) {
			FreeScrollingTextField fld = field;
            if (start && fld.mCaretPosition != fld.mSelectionAnchor) {
                fld.mCaretPosition = fld.mSelectionAnchor;
                updateAfterCaretJump();
            } else if (!start && fld.mCaretPosition != fld.mSelectionEdge) {
                fld.mCaretPosition = fld.mSelectionEdge;
                updateAfterCaretJump();
            }
        }
    }


    /**
     * Used by internal methods to update selection boundaries when a new
     * caret position is set.
     * Does nothing if not in selection mode.
     */
    private void updateSelectionRange(int oldCaretPosition, int newCaretPosition) {

        if (!_isInSelectionMode)
            return;

		FreeScrollingTextField fld = field;
        if (oldCaretPosition < fld.mSelectionEdge) {
            if (newCaretPosition > fld.mSelectionEdge) {
                fld.mSelectionAnchor = fld.mSelectionEdge;
                fld.mSelectionEdge = newCaretPosition;
            } else
                fld.mSelectionAnchor = newCaretPosition;

        } else if (newCaretPosition < fld.mSelectionAnchor) {
            fld.mSelectionEdge = fld.mSelectionAnchor;
            fld.mSelectionAnchor = newCaretPosition;
        } else
            fld.mSelectionEdge = newCaretPosition;
    }

    //- TextFieldController -----------------------------------------------
    //------------------------ Cut, copy, paste, delete ---------------------------

    /**
     * Convenience method for consecutive copy and paste calls
     */
    public void cut(ClipboardManager cb) {
        copy(cb);
        selectionDelete();
		determineSpans();
		//tc
    }

    /**
     * Copies the selected text to the clipboard.
     * <p>
     * Does nothing if not in select mode.
     */
    public void copy(ClipboardManager cb) {
        //TODO catch OutOfMemoryError
		FreeScrollingTextField fld = field;
        if (_isInSelectionMode &&
			fld.mSelectionAnchor < fld.mSelectionEdge) {
            CharSequence contents = fld.hDoc.subSequence(fld.mSelectionAnchor,
														   fld.mSelectionEdge);
            cb.setText(contents);
        }
    }

    /**
     * Inserts text at the caret position.
     * Existing selected text will be deleted and select mode will end.
     * The deleted area will be invalidated.
     * <p>
     * After insertion, the inserted area will be invalidated.
     */
    public void paste(String text) {
        if (text == null)
            return;

		FreeScrollingTextField fd = field;
		Document doc = fd.hDoc;
		doc.setTyping(true);
        selectionDelete();

        doc.insertBefore(text.toCharArray(), fd.mCaretPosition, System.nanoTime());
        fd.onAdd(text, fd.mCaretPosition, text.length());

        fd.mCaretPosition += text.length();
        updateCaretRow();

        fd.setEdited(true);
        determineSpans();
		//tc
        stopTextComposing();
    }

    /**
     * Deletes selected text, exits select mode and invalidates deleted area.
     * If the selected range is empty, this method exits select mode and
     * invalidates the caret.
     * <p>
     * Does nothing if not in select mode.
     */
    public void selectionDelete() {
        if (!_isInSelectionMode)
            return;
		FreeScrollingTextField fd = field;
        int totalChars = fd.mSelectionEdge - fd.mSelectionAnchor;

        if (totalChars > 0) {
			Document doc = fd.hDoc;
            final int sela = fd.mSelectionAnchor;
            int originalRow = doc.findRowNumber(sela);
            int originalOffset = doc.getRowOffset(originalRow);
            boolean isSingleRowSel = doc.findRowNumber(fd.mSelectionEdge) == originalRow;
            doc.deleteAt(fd.mSelectionAnchor, totalChars, System.nanoTime());
            fd.onDel(doc.subSequence(sela, sela + totalChars), fd.mCaretPosition, totalChars);
            fd.mCaretPosition = fd.mSelectionAnchor;
            updateCaretRow();
            fd.setEdited(true);
            setSelectText(false);
            stopTextComposing();

            if (!field.focusCaret()) {
                int invalidateStartRow = originalRow;
                //invalidate previous row too if its wrapping changed
                if (doc.isWordWrap() &&
					originalOffset != doc.getRowOffset(originalRow)) {
                    --invalidateStartRow;
                }

                if (isSingleRowSel && !doc.isWordWrap())
				//pasted text only affects current row
                    fd.invalidateRows(invalidateStartRow, invalidateStartRow + 1);
                else
				//TODO invalidate damaged rows only
                    fd.invalidateFromRow(invalidateStartRow);
            }
        } else {
            setSelectText(false);
            fd.invalidateCaretRow();
        }
    }

    void replaceText(int from, int charCount, CharSequence text) {
        int invalidateStartRow, originalOffset;
        boolean isInvalidateSingleRow = true;
        boolean dirty = false;
		FreeScrollingTextField fld = field;
        //delete selection
        if (_isInSelectionMode) {
            invalidateStartRow = fld.hDoc.findRowNumber(fld.mSelectionAnchor);
            originalOffset = fld.hDoc.getRowOffset(invalidateStartRow);

            int totalChars = fld.mSelectionEdge - fld.mSelectionAnchor;

            if (totalChars > 0) {
                fld.mCaretPosition = fld.mSelectionAnchor;
                fld.hDoc.deleteAt(fld.mSelectionAnchor, totalChars, System.nanoTime());

                if (invalidateStartRow != fld.mCaretRow)
                    isInvalidateSingleRow = false;
                dirty = true;
            }

            setSelectText(false);
        } else {
            invalidateStartRow = fld.mCaretRow;
            originalOffset = fld.hDoc.getRowOffset(fld.mCaretRow);
        }

        //delete requested chars
        if (charCount > 0) {
            int delFromRow = fld.hDoc.findRowNumber(from);
            if (delFromRow < invalidateStartRow) {
                invalidateStartRow = delFromRow;
                originalOffset = fld.hDoc.getRowOffset(delFromRow);
            }

            if (invalidateStartRow != fld.mCaretRow)
                isInvalidateSingleRow = false;

            fld.mCaretPosition = from;
            fld.hDoc.deleteAt(from, charCount, System.nanoTime());
            dirty = true;
        }

        //insert
        if (text != null && text.length() > 0) {
            int insFromRow = fld.hDoc.findRowNumber(from);
            if (insFromRow < invalidateStartRow) {
                invalidateStartRow = insFromRow;
                originalOffset = fld.hDoc.getRowOffset(insFromRow);
            }
            fld.hDoc.insertBefore(text.toString().toCharArray(), fld.mCaretPosition, System.nanoTime());
            fld.mCaretPosition += text.length();
            dirty = true;
        }

        if (dirty) {
            fld.setEdited(true);
            determineSpans();
			fld.focusCaret();
			return;
        }

        int originalRow = fld.mCaretRow;
        updateCaretRow();
        if (originalRow != fld.mCaretRow)
            isInvalidateSingleRow = false;

        if (!fld.focusCaret()) {
            //invalidate previous row too if its wrapping changed
            if (fld.hDoc.isWordWrap() &&
				originalOffset != fld.hDoc.getRowOffset(invalidateStartRow))
                --invalidateStartRow;

            if (isInvalidateSingleRow && !fld.hDoc.isWordWrap())
			//replaced text only affects current row
                fld.invalidateRows(fld.mCaretRow, fld.mCaretRow + 1);
            else
			//TODO invalidate damaged rows only
                fld.invalidateFromRow(invalidateStartRow);
        }
    }

    //- TextFieldController -----------------------------------------------
    //----------------- Helper methods for InputConnection ----------------

    /**
     * Deletes existing selected text, then deletes charCount number of
     * characters starting at from, and inserts text in its place.
     * <p>
     * Unlike paste or selectionDelete, does not signal the end of
     * text composing to the IME.
     */
    void replaceComposingText(int from, int charCount, String text) {
        int invalidateStartRow, originalOffset;
        boolean isInvalidateSingleRow = true;
        boolean dirty = false;

		FreeScrollingTextField fld = field;
		Document doc = fld.hDoc;
		doc.setTyping(true);
        //delete selection
        if (_isInSelectionMode) {
            invalidateStartRow = doc.findRowNumber(fld.mSelectionAnchor);
            originalOffset = doc.getRowOffset(invalidateStartRow);

            int totalChars = fld.mSelectionEdge - fld.mSelectionAnchor;

            if (totalChars > 0) {
                fld.mCaretPosition = fld.mSelectionAnchor;
                doc.deleteAt(fld.mSelectionAnchor, totalChars, System.nanoTime());

                if (invalidateStartRow != fld.mCaretRow)
                    isInvalidateSingleRow = false;
                dirty = true;
            }

            setSelectText(false);
        } else {
            invalidateStartRow = fld.mCaretRow;
            originalOffset = doc.getRowOffset(fld.mCaretRow);
        }

        //delete requested chars
        if (charCount > 0) {
            int delFromRow = doc.findRowNumber(from);
            if (delFromRow < invalidateStartRow) {
                invalidateStartRow = delFromRow;
                originalOffset = doc.getRowOffset(delFromRow);
            }

            if (invalidateStartRow != fld.mCaretRow)
                isInvalidateSingleRow = false;

            fld.mCaretPosition = from;
            doc.deleteAt(from, charCount, System.nanoTime());
            dirty = true;
        }

        //insert
        if (text != null && text.length() > 0) {
            int insFromRow = doc.findRowNumber(from);
            if (insFromRow < invalidateStartRow) {
                invalidateStartRow = insFromRow;
                originalOffset = doc.getRowOffset(insFromRow);
            }

            log("inserted text:" + text);
            doc.insertBefore(text.toCharArray(), fld.mCaretPosition, System.nanoTime());
            fld.mCaretPosition += text.length();
            dirty = true;
        	fld.onAdd(text, fld.mCaretPosition, text.length() - charCount);
		}
        if (dirty) {
            fld.setEdited(true);
            determineSpans();
			fld.focusCaret();
			return;
        }

        int originalRow = fld.mCaretRow;
        updateCaretRow();
        if (originalRow != fld.mCaretRow)
            isInvalidateSingleRow = false;

        if (!fld.focusCaret()) {
            //invalidate previous row too if its wrapping changed
            if (doc.isWordWrap() &&
				originalOffset != doc.getRowOffset(invalidateStartRow))
                --invalidateStartRow;

            if (isInvalidateSingleRow && !doc.isWordWrap())
			//replaced text only affects current row
                fld.invalidateRows(fld.mCaretRow, fld.mCaretRow + 1);
            else
			//TODO invalidate damaged rows only
                fld.invalidateFromRow(invalidateStartRow);
        }
    }

    /**
     * Delete leftLength characters of text before the current caret
     * position, and delete rightLength characters of text after the current
     * cursor position.
     * <p>
     * Unlike paste or selectionDelete, does not signal the end of
     * text composing to the IME.
     */
    void deleteAroundComposingText(int left, int right) {
		FreeScrollingTextField fld = field;
        int start = fld.mCaretPosition - left;
        if (start < 0)
            start = 0;
        int end = fld.mCaretPosition + right;
        int docLength = fld.hDoc.length();
        if (end > docLength - 1) //exclude the terminal EOF
            end = docLength - 1;
        replaceComposingText(start, end - start, "");
    }

    String getTextAfterCursor(int maxLen) {
		FreeScrollingTextField fld = field;
        int docLength = fld.hDoc.length();
        if ((maxLen += fld.mCaretPosition) > (docLength - 1))
		//exclude the terminal EOF
            return fld.hDoc.subSequence(fld.mCaretPosition, docLength - 1).toString();

        return fld.hDoc.subSequence(fld.mCaretPosition, maxLen).toString();
    }

    String getTextBeforeCursor(int maxLen) {
		FreeScrollingTextField fld = field;
        int start = fld.mCaretPosition - maxLen;
        if (start < 0)
            start = 0;
        return fld.hDoc.subSequence(start, fld.mCaretPosition).toString();
    }
}//end inner controller class
