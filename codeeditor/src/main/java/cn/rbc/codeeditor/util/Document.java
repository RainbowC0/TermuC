/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package cn.rbc.codeeditor.util;

import android.os.*;
import cn.rbc.codeeditor.lang.*;
import java.lang.ref.*;
import java.util.*;

/**
 * A decorator of TextBuffer that adds word-wrap capabilities.
 * <p>
 * Positions for word wrap row breaks are stored here.
 * Word-wrap is disabled by default.
 */
public class Document extends TextBuffer implements Parcelable {

    private boolean _isWordWrap = false;

    /**
     * Contains info related to printing of characters, display size and so on
     */
    private TextFieldMetrics _metrics;

    /**
     * A table containing the character offset of every row in the document.
     * Values are valid only in word-wrap mode
     */
    private ArrayList<Integer> _rowTable;

    public Document(TextFieldMetrics metrics) {
        super();
        _metrics = metrics;
        _rowTable = new ArrayList<>();
        resetRowTable();
    }

    public void setText(CharSequence text) {
        int lineCount = 1;
        int len = text.length();
        char[] ca = new char[TextBuffer.memoryNeeded(len)];
        for (int i = 0; i < len; i++) {
            ca[i] = text.charAt(i);
            if (text.charAt(i) == '\n')
                lineCount++;
        }
        setBuffer(ca, len, lineCount);
    }

    public void resetRowTable() {//every document contains at least 1 row
        _rowTable.clear();
        _rowTable.add(logicalToRealIndex(0));
    }

    public void setMetrics(TextFieldMetrics metrics) {
        _metrics = metrics;
    }

    /**
     * Enable/disable word wrap. If enabled, the document is immediately
     * analyzed for word wrap breakpoints, which might take an arbitrarily long time.
     */
    public void setWordWrap(boolean enable) {
        if (enable != _isWordWrap) {
            _isWordWrap = enable;
            analyzeWordWrap();
        }
    }

    public final boolean isWordWrap() {
        return _isWordWrap;
    }


    @Override
    public synchronized void delete(int charOffset, int totalChars, long timestamp, boolean undoable) {
        super.delete(charOffset, totalChars, timestamp, undoable);
        if (totalChars <= 0)
            return;
        int startRow = Collections.binarySearch(_rowTable, charOffset);
        if (startRow >= 0) {
            // if (tmp == charOffset)
            _rowTable.set(startRow, _gapEndIndex);
            startRow++;
        } else startRow = ~startRow;
        //Log.i("Lsp", "b"+charOffset+_rowTable.toString()+_gapStartIndex+" "+_gapEndIndex);

        removeRowMetadata(startRow, _gapEndIndex);
        if (startRow > 1) {
            int k = _rowTable.get(startRow-1);
            if (k == _gapEndIndex)
                k = _gapStartIndex;
            if (_contents[k-1] != Language.NEWLINE)
                startRow--;
        }
        analyzeWordWrap(startRow, charOffset, _gapEndIndex);
        //Log.i("Lsp", "a"+startRow+_rowTable.toString()+_gapStartIndex+" "+_gapEndIndex);
        //updateWordWrapAfterEdit(startRow, analyzeEnd, -totalChars);

    }

    @Override
    public synchronized void insert(char[] c, int start, int count, int charOffset, long timestamp, boolean undoable) {
        int gapStart = _gapStartIndex;
        super.insert(c, start, count, charOffset, timestamp, undoable);
        int startRow = Collections.binarySearch(_rowTable, logicalToRealIndex(charOffset));
        if (startRow >= 0) {
            if (charOffset == gapStart)
                _rowTable.set(startRow, charOffset);
            startRow++;
        } else startRow = ~startRow;
        // Log.i("Lsp", "f"+_rowTable.toString()+ _gapStartIndex+" "+_gapEndIndex);
        
        // Log.i("Lsp", "b"+charOffset+_rowTable.toString()+_gapStartIndex+" "+_gapEndIndex);
        
        /*if (startRow < 0) startRow = ~startRow;
        else startRow++;*/
        //int analyzeEnd = findNextLineFrom(charOffset + count);
        //updateWordWrapAfterEdit(startRow, analyzeEnd, count);
        analyzeWordWrap(startRow, charOffset, charOffset + count);
        //Log.i("Lsp", "a"+startRow+_rowTable.toString()+_gapStartIndex+" "+_gapEndIndex);

    }

    public void insertBefore(char[] cArray, int insertionPoint, long timestamp) {
        if (!isValid(insertionPoint) || cArray.length == 0) {
            return;
        }

        insert(cArray, insertionPoint, timestamp, true);
    }

    public void deleteAt(int deletionPoint, long timestamp) {
        if (!isValid(deletionPoint)) {
            return;
        }
        delete(deletionPoint, 1, timestamp, true);
    }


    /**
     * Deletes up to maxChars number of characters starting from deletionPoint
     * If deletionPoint is invalid, or maxChars is not positive, nothing happens.
     */
    public void deleteAt(int deletionPoint, int maxChars, long time) {
        if (!isValid(deletionPoint) || maxChars <= 0) {
            return;
        }
        int totalChars = Math.min(maxChars, length() - deletionPoint);
        delete(deletionPoint, totalChars, time, true);
    }

    @Override
    /**
     * Moves _gapStartIndex by displacement units. Note that displacement can be
     * negative and will move _gapStartIndex to the left.
     *
     * Only UndoStack should use this method to carry out a simple undo/redo
     * of insertions/deletions. No error checking is done.
     */
    synchronized void shiftGapStart(int displacement) {
        int oldStart = _gapStartIndex;
        int startRow = 0;
        if (displacement > 0) {
            // insert
            int tmp = logicalToRealIndex(oldStart);
            startRow = Collections.binarySearch(_rowTable, tmp);
            //Log.i("Lsp", "s"+tmp+","+oldStart+","+_gapEndIndex+","+startRow);
            if (startRow >= 0) {
                _rowTable.set(startRow, oldStart);
                startRow++;
            } else startRow = ~startRow;
        } else if (displacement < 0) {
            // delete
            startRow = Collections.binarySearch(_rowTable, oldStart + displacement);
            if (startRow >= 0) {
                _rowTable.set(startRow, _gapEndIndex);
                startRow++;
            } else startRow = ~startRow;
            //Log.i("Lsp", "b"+charOffset+_rowTable.toString()+_gapStartIndex+" "+_gapEndIndex);

            removeRowMetadata(startRow, _gapEndIndex);
            if (startRow > 1) {
                int k = _rowTable.get(startRow-1);
                if (k==_gapEndIndex)
                    k = _gapStartIndex;
                if (_contents[k-1] != Language.NEWLINE)
                    startRow--;
            }
        }
        super.shiftGapStart(displacement);
        if (displacement > 0)
            analyzeWordWrap(startRow, oldStart, oldStart + displacement);
        else
            analyzeWordWrap(startRow, oldStart + displacement, oldStart);
    }

    @Override
    protected final void shiftGapLeft(int newGapStart) {
        final int oldStart = _gapStartIndex;
        super.shiftGapLeft(newGapStart);

        int startRow = Collections.binarySearch(_rowTable, newGapStart);
        if (startRow < 0) startRow = ~startRow;
        else startRow++;
        adjustOffsetOfRows(startRow, oldStart, gapSize());
    }

    @Override
    protected final void shiftGapRight(int newGapEnd) {
        final int oldEnd = _gapEndIndex;
        super.shiftGapRight(newGapEnd);

        int startRow = Collections.binarySearch(_rowTable, oldEnd);
        if (startRow < 0) startRow = ~startRow;
        adjustOffsetOfRows(startRow, newGapEnd + 1, -gapSize());
    }

    @Override
    public void growBufferBy(int increment) {
        int gapEnd = _gapEndIndex;
        super.growBufferBy(increment);
        increment = _gapEndIndex - gapEnd;
        int i = Collections.binarySearch(_rowTable, gapEnd);
        if (i < 0) i = ~i;
        adjustOffsetOfRows(i, _contents.length, increment);
    }

    //No error checking is done on parameters.
    private int findNextLineFrom(int charOffset) {
        int lineEnd = logicalToRealIndex(charOffset);

        while (lineEnd < _contents.length) {
            // skip the gap
            if (lineEnd == _gapStartIndex) {
                lineEnd = _gapEndIndex;
            }

            if (_contents[lineEnd] == Language.NEWLINE ||
                    _contents[lineEnd] == Language.EOF) {
                break;
            }

            ++lineEnd;
        }

        return lineEnd + 1;
    }

    private void updateWordWrapAfterEdit(int startRow, int analyzeEnd, int delta) {
        if (startRow > 0) {
            // if the first word becomes shorter or an inserted space breaks it
            // up, it may fit the previous line, so analyse that line too
            --startRow;
        }
        int analyzeStart = _rowTable.get(startRow);

        //changes only affect the rows after startRow
        //_rowTable.shift(analyzeEnd - delta, delta);
        //adjustOffsetOfRowsFrom(startRow + 1, delta);
        //DLog.i("Lsp", analyzeStart+" "+_rowTable.get(startRow)+" "+startRow);
        analyzeWordWrap(startRow + 1, analyzeStart, analyzeEnd);
    }

    /**
     * Removes row offset info from fromRow to the row that endOffset is on,
     * inclusive.
     * <p>
     * No error checking is done on parameters.
     */
    private final void removeRowMetadata(int fromRow, int endOffset) {
        int end = fromRow, size = _rowTable.size();
        while (end < size &&
                _rowTable.get(end) <= endOffset) {
            end++;
        }
        _rowTable.subList(fromRow, end).clear();
    }

    private final void adjustOffsetOfRows(int fromRow, int endOffset, int offset) {
        int i;
        final int l = _rowTable.size();
        while (fromRow < l && (i = _rowTable.get(fromRow)) < endOffset) {
            _rowTable.set(fromRow++, i + offset);
        }
    }

    public void analyzeWordWrap() {

        resetRowTable();
        //_rowTable.clear();

        if (_isWordWrap && !hasMinimumWidthForWordWrap()) {
            if (_metrics.getRowWidth() > 0) {
                TextWarriorException.fail("Text field has non-zero width but still too small for word wrap");
            }
            // _metrics.getRowWidth() might legitmately be zero when the text field has not been layout yet
            return;
        }

        analyzeWordWrap(1, 0, _contents.length);
    }

    private boolean hasMinimumWidthForWordWrap() {
        final int maxWidth = _metrics.getRowWidth();
        //assume the widest char is 2ems wide
        return (maxWidth >= 2 * _metrics.getAdvance('M'));
    }

    private WeakReference<ArrayList<Integer>> tempref = new WeakReference<>(null);

    //No error checking is done on parameters.
    //A word consists of a sequence of 0 or more non-whitespace characters followed by
    //exactly one whitespace character. Note that EOF is considered whitespace.
    private void analyzeWordWrap(int rowIndex, int offset, int end) {
        ArrayList<Integer> temp = tempref.get();
        if (temp == null) {
            temp = new ArrayList<>();
            tempref = new WeakReference<>(temp);
        }
        if (!_isWordWrap) {
            //  Log.w("Lsp", rowIndex+","+offset+","+end+","+_gapStartIndex+","+_gapEndIndex+","+_contents[offset]);
            while (offset < end) {
                // skip the gap
                if (offset == _gapStartIndex) {
                    offset = _gapEndIndex;
                }
                char c = _contents[offset];
                if (c == Language.NEWLINE) {
                    //start a new row
                    int t = offset + 1;
                    temp.add(t != _gapStartIndex ? t : _gapEndIndex);
                }
                ++offset;

            }
            if (!temp.isEmpty())
                removeRowMetadata(rowIndex, temp.get(temp.size()-1));
            _rowTable.addAll(rowIndex, temp);
            temp.clear();
            return;
        }
        if (!hasMinimumWidthForWordWrap()) {
            TextWarriorException.fail("Not enough space to do word wrap");
            return;
        }
        //int potentialBreakPoint = offset;
        offset = rowIndex > 0 ? _rowTable.get(rowIndex - 1) : logicalToRealIndex(0);
        int soff = offset;
        //int potentialBreakPoint = offset;
        int j = end + 1;
        for (int i = rowIndex, l = _rowTable.size();
             i < l && _contents[((j = _rowTable.get(i)) == _gapEndIndex ? j = _gapStartIndex : j) - 1] != Language.NEWLINE;
             i++)
            ;
        if (j > end)
            end = j - 1;
        //Log.i("Lsp", offset + "," + end);
        //int wordExtent = 0;
        final int maxWidth = _metrics.getRowWidth();
        //Log.i("Lsp", maxWidth + ":" + _metrics.getAdvance('c'));
        //int remainingWidth = maxWidth;
        int lineStart = offset;
        int lastBreakPos = -1;
        int currWidth = 0;

        for (; offset < end; offset++) {
            // skip the gap
            if (offset == _gapStartIndex) {
                offset = _gapEndIndex;
            }
            char c = _contents[offset];
            if (c == Language.NEWLINE) {
                //start a new row
                lineStart = offset + 1;
                if (lineStart == _gapStartIndex)
                    lineStart = _gapEndIndex;
                temp.add(lineStart);
                soff = lineStart;
                //remainingWidth = maxWidth;
                currWidth = 0;
                lastBreakPos = -1;
                continue;
            }

            if ((currWidth += _metrics.getAdvance(c)) > maxWidth) {
                if (lastBreakPos >= lineStart) {
                    lineStart = lastBreakPos + 1;
                    if (lineStart == _gapStartIndex)
                        lineStart = _gapEndIndex;
                    temp.add(lineStart);
                    soff = lineStart;
                    // TODO 不用回溯方法
                    offset = lineStart;
                } else {
                    if (soff == lineStart) {
                        temp.add(offset);
                    } else if (temp.isEmpty() || temp.get(temp.size() - 1) < lineStart) {
                        temp.add(lineStart);
                    }
                    soff = offset;
                    lineStart = offset;
                }
                currWidth = _metrics.getAdvance(c);
                lastBreakPos = -1;
                continue;
            }
            if (" ,;.\t\uFFFF".indexOf(c) >= 0) {
                lastBreakPos = offset;
            }
			/*wordExtent += _metrics.getAdvance(c);
			final boolean isWhitespace = ".,; \t\n\uFFFF".indexOf(c)>=0;

			if (isWhitespace) {
				//full word obtained
				if (wordExtent <= remainingWidth) {
					remainingWidth -= wordExtent;
				} else if (wordExtent > maxWidth) {
					//handle a word too long to fit on one row
					int current = potentialBreakPoint;
					remainingWidth = maxWidth;

					//start the word on a new row, if it isn't already
					if (current != offset && (temp.isEmpty() ||
						current != temp.get(temp.size() - 1))) {
						temp.add(current);
                        Log.i("Lsp", "cu"+current);
					}

					while (current <= offset) {
						// skip the gap
						if (current == _gapStartIndex) {
							current = _gapEndIndex;
						}

						int advance = _metrics.getAdvance(_contents[current]);
						if (advance > remainingWidth) {
							temp.add(current);
							remainingWidth = maxWidth - advance;
						} else {
							remainingWidth -= advance;
						}

						++current;
					}
				} else {
					//invariant: potentialBreakPoint != startOffset
					//put the word on a new row
					temp.add(potentialBreakPoint);
                    Log.i("Lsp", "pB"+potentialBreakPoint);
					remainingWidth = maxWidth - wordExtent;
				}

				wordExtent = 0;
				potentialBreakPoint = offset + 1;
                if (potentialBreakPoint == _gapStartIndex)
                    potentialBreakPoint = _gapEndIndex;
			}*/
        }
        if (!temp.isEmpty()) {
            //Log.i("Lsp", temp.toString());
            removeRowMetadata(rowIndex, temp.get(temp.size()-1));
            //merge with existing row table
            _rowTable.addAll(rowIndex, temp);
            //Log.i("Lsp", _rowTable.toString());
            temp.clear();
        } else removeRowMetadata(rowIndex, end);
    }

    public CharSequence getRow(int rowNumber) {

        int rowSize = getRowSize(rowNumber);
        if (rowSize == 0) {
            return new String();
        }

        int startIndex = realToLogicalIndex(_rowTable.get(rowNumber));
        return subSequence(startIndex, startIndex + rowSize);
    }

    public int getRowSize(int rowNumber) {

        if (isInvalidRow(rowNumber)) {
            return 0;
        }

        if (rowNumber != (_rowTable.size() - 1)) {
            return realToLogicalIndex(_rowTable.get(rowNumber + 1)) - realToLogicalIndex(_rowTable.get(rowNumber));
        } else {
            //last row
            return length() - realToLogicalIndex(_rowTable.get(rowNumber));
        }
    }

    public String rows() {
        StringBuilder sb = new StringBuilder("[");
        sb.append(realToLogicalIndex(_rowTable.get(0)));
        for (int i = 1, l = _rowTable.size(); i < l; i++) {
            int k = _rowTable.get(i);
            sb.append(k >= _gapStartIndex ? ':' : ',');
            sb.append(realToLogicalIndex(k));
        }
        sb.append(']');
        return sb.toString();
    }

    public final int getRowCount() {

        return _rowTable.size();
    }

    public int getRowOffset(int rowNumber) {


        if (isInvalidRow(rowNumber)) {
            return -1;
        }

        return realToLogicalIndex(_rowTable.get(rowNumber));
    }

    /**
     * Get the row number that charOffset is on
     *
     * @return The row number that charOffset is on, or -1 if charOffset is invalid
     */
    public int findRowNumber(int charOffset) {

        if (!isValid(charOffset)) {
            return -1;
        }
        int i = Collections.binarySearch(_rowTable, logicalToRealIndex(charOffset));
        if (i < 0)
            i = (~i) - 1;
        return i;
    }

    protected final boolean isInvalidRow(int rowNumber) {
        return rowNumber < 0 || rowNumber >= _rowTable.size();
    }

    public static interface TextFieldMetrics {
        /**
         * Returns printed width of c.
         *
         * @param c Character to measure
         * @return Advance of character, in pixels
         */
        public int getAdvance(char c);

        /**
         * Returns the maximum width available for a row of text to be layout. This
         * should not be larger than the width of the text field.
         *
         * @return Maximum width of a row, in pixels
         */
        public int getRowWidth();
    }

    @Override
    public void writeToParcel(Parcel p1, int p2) {
        p1.writeString(toString());
        p1.writeInt(_marks.size());
        p1.writeIntArray(_marks.getData());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private Document(Parcel in) {
        resetRowTable();
        setText(in.readString());
        _marks = new GapIntSet(1);
        int[] m = new int[in.readInt()];
        in.readIntArray(m);
        _marks.setData(m);
    }

    public static final Parcelable.Creator<Document> CREATOR
            = new Parcelable.Creator<Document>() {
        public Document createFromParcel(Parcel in) {
            return new Document(in);
        }

        public Document[] newArray(int size) {
            return new Document[size];
        }
    };
}
