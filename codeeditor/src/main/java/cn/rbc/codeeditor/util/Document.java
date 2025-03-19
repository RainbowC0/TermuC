/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package cn.rbc.codeeditor.util;

import cn.rbc.codeeditor.lang.Language;

import java.util.ArrayList;
import java.util.Collections;
import android.os.*;
import android.text.*;

/**
 * A decorator of TextBuffer that adds word-wrap capabilities.
 *
 * Positions for word wrap row breaks are stored here.
 * Word-wrap is enabled by default.
 */
public class Document extends TextBuffer implements Parcelable {

	private boolean _isWordWrap = false;

	/** Contains info related to printing of characters, display size and so on */
	private TextFieldMetrics _metrics;

	/** A table containing the character offset of every row in the document.
	 * Values are valid only in word-wrap mode */
	private ArrayList<Integer> _rowTable;

	public Document(TextFieldMetrics metrics) {
		super();
		_metrics = metrics;
		resetRowTable();
	}

	public void setText(CharSequence text) {
		int lineCount=1;
		int len=text.length();
		char[] ca=new char[TextBuffer.memoryNeeded(len)];
		for (int i=0;i < len;i++) {
			ca[i] = text.charAt(i);
			if (text.charAt(i) == '\n')
				lineCount++;
		}
		setBuffer(ca, len, lineCount);
	}

	public void resetRowTable() {
		ArrayList<Integer> rowTable = new ArrayList<>();
		rowTable.add(0); //every document contains at least 1 row
		_rowTable = rowTable;
	}

	public void setMetrics(TextFieldMetrics metrics) {
		_metrics = metrics;
	}

	/**
	 * Enable/disable word wrap. If enabled, the document is immediately
	 * analyzed for word wrap breakpoints, which might take an arbitrarily long time.
	 */
	public void setWordWrap(boolean enable) {
		if (enable && !_isWordWrap) {
			_isWordWrap = true;
			analyzeWordWrap();
		} else if (!enable && _isWordWrap) {
			_isWordWrap = false;
			analyzeWordWrap();
		}
	}

	public boolean isWordWrap() {
		return _isWordWrap;
	}


	@Override
	public synchronized void delete(int charOffset, int totalChars, long timestamp, boolean undoable) {
		super.delete(charOffset, totalChars, timestamp, undoable);

		int startRow = findRowNumber(charOffset);
		int analyzeEnd = findNextLineFrom(charOffset);
		updateWordWrapAfterEdit(startRow, analyzeEnd, -totalChars);
	}

	@Override
	public synchronized void insert(char[] c, int start, int count, int charOffset, long timestamp, boolean undoable) {
		super.insert(c, start, count, charOffset, timestamp, undoable);

		int startRow = findRowNumber(charOffset);
		int analyzeEnd = findNextLineFrom(charOffset + count);
		updateWordWrapAfterEdit(startRow, analyzeEnd, count);
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
		super.shiftGapStart(displacement);

		if (displacement != 0) {
			int startOffset = (displacement > 0)
				? _gapStartIndex - displacement
				: _gapStartIndex;
			int startRow = findRowNumber(startOffset);
			int analyzeEnd = findNextLineFrom(_gapStartIndex);
			updateWordWrapAfterEdit(startRow, analyzeEnd, displacement);
		}
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

		return realToLogicalIndex(lineEnd) + 1;
	}

	private void updateWordWrapAfterEdit(int startRow, int analyzeEnd, int delta) {
		if (startRow > 0) {
			// if the first word becomes shorter or an inserted space breaks it
			// up, it may fit the previous line, so analyse that line too
			--startRow;
		}
		int analyzeStart = _rowTable.get(startRow);

		//changes only affect the rows after startRow
		removeRowMetadata(startRow + 1, analyzeEnd - delta);
		adjustOffsetOfRowsFrom(startRow + 1, delta);
		analyzeWordWrap(startRow + 1, analyzeStart, analyzeEnd);
	}

	/**
	 * Removes row offset info from fromRow to the row that endOffset is on,
	 * inclusive.
	 *
	 * No error checking is done on parameters.
	 */
	private void removeRowMetadata(int fromRow, int endOffset) {
        int end = fromRow, size = _rowTable.size();
		while (end < size &&
			   _rowTable.get(end) <= endOffset)
            end++;
		_rowTable.subList(fromRow, end).clear();
	}

	private void adjustOffsetOfRowsFrom(int fromRow, int offset) {
		for (int i = fromRow; i < _rowTable.size(); ++i) {
			_rowTable.set(i, _rowTable.get(i) + offset);
		}
	}

	public void analyzeWordWrap() {

		resetRowTable();

		if (_isWordWrap && !hasMinimumWidthForWordWrap()) {
			if (_metrics.getRowWidth() > 0) {
				TextWarriorException.fail("Text field has non-zero width but still too small for word wrap");
			}
			// _metrics.getRowWidth() might legitmately be zero when the text field has not been layout yet
			return;
		}

		analyzeWordWrap(1, 0, length());
	}

	private boolean hasMinimumWidthForWordWrap() {
		final int maxWidth = _metrics.getRowWidth();
		//assume the widest char is 2ems wide
		return (maxWidth >= 2 * _metrics.getAdvance('M'));
	}

	//No error checking is done on parameters.
	//A word consists of a sequence of 0 or more non-whitespace characters followed by
	//exactly one whitespace character. Note that EOF is considered whitespace.
	private void analyzeWordWrap(int rowIndex, int startOffset, int endOffset) {
		if (!_isWordWrap) {
			int offset = logicalToRealIndex(startOffset);
			int end = logicalToRealIndex(endOffset);
			ArrayList<Integer> rowTable = new ArrayList<>();

			while (offset < end) {
				// skip the gap
				if (offset == _gapStartIndex) {
					offset = _gapEndIndex;
				}
				char c = _contents[offset];
				if (c == Language.NEWLINE) {
					//start a new row
					rowTable.add(realToLogicalIndex(offset) + 1);
				}
				++offset;

			}
			_rowTable.addAll(rowIndex, rowTable);
			return;
		}
		if (!hasMinimumWidthForWordWrap()) {
			TextWarriorException.fail("Not enough space to do word wrap");
			return;
		}
		ArrayList<Integer> rowTable = new ArrayList<Integer>();
		int offset = logicalToRealIndex(startOffset);
		int end = logicalToRealIndex(endOffset);
		int potentialBreakPoint = startOffset;
		int wordExtent = 0;
		final int maxWidth = _metrics.getRowWidth();
		int remainingWidth = maxWidth;

		while (offset < end) {
			// skip the gap
			if (offset == _gapStartIndex) {
				offset = _gapEndIndex;
			}

			char c = _contents[offset];
			wordExtent += _metrics.getAdvance(c);
			boolean isWhitespace = ".,; \t\n\uFFFF".indexOf(c)>=0;
			//(c == ' ' || c == Language.TAB|| c == Language.NEWLINE || c == Language.EOF);

			if (isWhitespace) {
				//full word obtained
				if (wordExtent <= remainingWidth) {
					remainingWidth -= wordExtent;
				} else if (wordExtent > maxWidth) {
					//handle a word too long to fit on one row
					int current = logicalToRealIndex(potentialBreakPoint);
					remainingWidth = maxWidth;

					//start the word on a new row, if it isn't already
					if (potentialBreakPoint != startOffset && (rowTable.isEmpty() ||
						potentialBreakPoint != rowTable.get(rowTable.size() - 1))) {
						rowTable.add(potentialBreakPoint);
					}

					while (current <= offset) {
						// skip the gap
						if (current == _gapStartIndex) {
							current = _gapEndIndex;
						}

						int advance = _metrics.getAdvance(_contents[current]);
						if (advance > remainingWidth) {
							rowTable.add(realToLogicalIndex(current));
							remainingWidth = maxWidth - advance;
						} else {
							remainingWidth -= advance;
						}

						++current;
					}
				} else {
					//invariant: potentialBreakPoint != startOffset
					//put the word on a new row
					rowTable.add(potentialBreakPoint);
					remainingWidth = maxWidth - wordExtent;
				}

				wordExtent = 0;
				potentialBreakPoint = realToLogicalIndex(offset) + 1;
			}

			if (c == Language.NEWLINE) {
				//start a new row
				rowTable.add(potentialBreakPoint);
				remainingWidth = maxWidth;
			}

			++offset;
		}

		//merge with existing row table
		_rowTable.addAll(rowIndex, rowTable);
	}

	public String getRow(int rowNumber) {

		int rowSize = getRowSize(rowNumber);
		if (rowSize == 0) {
			return new String();
		}

		int startIndex = _rowTable.get(rowNumber);
		return subSequence(startIndex, startIndex + rowSize).toString();
	}

	public int getRowSize(int rowNumber) {

		if (isInvalidRow(rowNumber)) {
			return 0;
		}

		if (rowNumber != (_rowTable.size() - 1)) {
			return _rowTable.get(rowNumber + 1) - _rowTable.get(rowNumber);
		} else {
			//last row
			return length() - _rowTable.get(rowNumber);
		}
	}

	public int getRowCount() {

		return _rowTable.size();
	}

	public int getRowOffset(int rowNumber) {


		if (isInvalidRow(rowNumber)) {
			return -1;
		}

		return _rowTable.get(rowNumber);
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
		int i = Collections.binarySearch(_rowTable, charOffset);
		if (i<0)
			i = (~i)-1;
		return i;
	}

	protected boolean isInvalidRow(int rowNumber) {
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
