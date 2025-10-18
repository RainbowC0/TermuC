package cn.rbc.codeeditor.view;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import cn.rbc.codeeditor.util.*;
import java.util.*;

//TODO minimise unnecessary invalidate calls

/**
 * TouchNavigationMethod classes implementing their own carets have to override
 * getCaretBloat() to return the size of the drawing area it needs, in excess of
 * the bounding box of the character the caret is on, and use
 * onTextDrawComplete(Canvas) to draw the caret. Currently, only a fixed size
 * caret is allowed, but scalable carets may be implemented in future.
 */
public class TouchNavigationMethod extends GestureDetector.SimpleOnGestureListener {
    private final static Rect mCaretBloat = new Rect(0, 0, 0, 0);
    // When the caret is dragged to the edges of the text field, the field will
    // scroll automatically. SCROLL_EDGE_SLOP is the width of these edges in pixels
    // and extends inside the content area, not outside to the padding area
    //protected static int SCROLL_EDGE_SLOP = 100;
    /**
     * The radius, in density-independent pixels, around a point of interest
     * where any touch event within that radius is considered to have touched
     * the point of interest itself
     */
    protected static int TOUCH_SLOP;
    protected FreeScrollingTextField mTextField;
    protected boolean isCaretTouched = false, mIsFastScrolling = false;
    private GestureDetector mGestureDetector;
    private float lastDist, lastSize;
    private float lastX, lastY;
    private int fling, mThumbHeight;

    public TouchNavigationMethod(FreeScrollingTextField textField) {
        mTextField = textField;
        mGestureDetector = new GestureDetector(textField.getContext(), this);
        mGestureDetector.setIsLongpressEnabled(true);
		ViewConfiguration vc = ViewConfiguration.get(textField.getContext());
		TOUCH_SLOP = vc.getScaledTouchSlop();
    }

    @SuppressWarnings("unused")
    private TouchNavigationMethod() {
        // do not invoke; always needs a valid mTextField
    }

    @Override
    public boolean onDown(MotionEvent e) {
		// mTextField.getParent().requestDisallowInterceptTouchEvent(e.getX() > 10);
        int x = screenToViewX((int) e.getX());
        int y = screenToViewY((int) e.getY());
        FreeScrollingTextField field = mTextField;
        isCaretTouched = isNearChar(x, y, field.getCaretPosition());

        if (field.isFlingScrolling()) {
            field.stopFlingScrolling();
        }
        if (!mIsFastScrolling) {
            int grabWidth = 4 * field.getVerticalScrollbarWidth();
            if (field.getWidth() - grabWidth < e.getX()) {
                computeThumbHeight();
                field.awakeScrollBars();
                int h = field.getHeight();
                float scrollStart = ((float)field.getScrollY()) / (h + field.getMaxScrollY()) * h;
                int thumbHeight = Math.max(h / 8, mThumbHeight);
                if (Math.abs(e.getY() - scrollStart - thumbHeight / 2) < thumbHeight) {
                    mIsFastScrolling = true;
                    return true;
                }
            }
        }
        if (field.isSelectText()) {
            if (isNearChar(x, y, field.getSelectionStart())) {
                field.focusSelectionStart();
                field.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                isCaretTouched = true;
            } else if (isNearChar(x, y, field.getSelectionEnd())) {
                field.focusSelectionEnd();
                field.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                isCaretTouched = true;
            }
        }

        if (isCaretTouched) field.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        return true;
    }

    private void computeThumbHeight() {
        FreeScrollingTextField fld = mTextField;
        int height = fld.getHeight();
        mThumbHeight = (int)((float)height * height / (height + fld.getMaxScrollY()));
    }

    @Override
    public void onShowPress(MotionEvent e) {
        // do nothing
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        int x = screenToViewX((int) e.getX());
        int y = screenToViewY((int) e.getY());
		FreeScrollingTextField tf = mTextField;
        int charOffset = tf.coordToCharIndex(x, y);

		if (x < tf.mLeftOffset) {
            Document doc = tf.hDoc;
			y = doc.findLineNumber(charOffset);
			if (y == -1)
				y = doc.getLineCount() - 1;
		    doc.markLine(1 + y);
			tf.invalidate();
			return true;
		}
        boolean b = tf.isSelectText();
        if (b) {
            tf.selectText(false);
        }
        if (charOffset >= 0 && charOffset != tf.mCaretPosition) {
            tf.moveCaret(charOffset);
            tf.mSigHelpPanel.hide();
        }
        tf.showIME(true);
        return true;
    }

    /**
     * Note that up events from a fling are NOT captured here.
     * Subclasses have to call super.onUp(MotionEvent) in their implementations
     * of onFling().
     * <p>
     * Also, up events from non-primary pointers in a multi-touch situation are
     * not captured here.
     *
     * @param e
     * @return
     */
    public boolean onUp(MotionEvent e) {
        mTextField.stopAutoScrollCaret();
        mTextField.mClipboardPanel.invalidateContentRect();
        isCaretTouched = false;
        mIsFastScrolling = false;
        lastDist = 0;
        fling = 0;
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

        //onTouchZoon(e2);

        if (isCaretTouched) {
            dragCaret(e2);
        } else if (e2.getPointerCount() == 1) {
            View fld = mTextField;
            if (mIsFastScrolling) {
                fld.scrollTo(fld.getScrollX(), (int)((e2.getY() / mThumbHeight - .5f) * fld.getHeight()));
            } else {
                if (fling == 0)
                    if (Math.abs(distanceX) > Math.abs(distanceY))
                        fling = 1;
                    else
                        fling = -1;
                if (fling == 1)
                    distanceY = 0;
                else if (fling == -1)
                    distanceX = 0;
                fld.scrollBy((int)distanceX, (int)distanceY);
            }
        }

        //TODO find out if ACTION_UP events are actually passed to onScroll
        if ((e2.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            onUp(e2);
        }
        return true;
    }

    protected void dragCaret(MotionEvent e) {
        FreeScrollingTextField field = mTextField;
        if (!field.isSelectText() && isDragSelect()) {
            field.selectText(true);
        }

        int x = (int) e.getX() - field.getPaddingLeft();
        int y = (int) e.getY() - field.getPaddingTop();
        boolean scrolled = false;

        // If the edges of the textField content area are touched, scroll in the
        // corresponding direction.
        if (x < field.SCROLL_EDGE_SLOP) {
            scrolled = field.autoScrollCaret(FreeScrollingTextField.SCROLL_LEFT);
        } else if (x >= (field.getContentWidth() - field.SCROLL_EDGE_SLOP)) {
            scrolled = field.autoScrollCaret(FreeScrollingTextField.SCROLL_RIGHT);
        } else if (y < field.SCROLL_EDGE_SLOP) {
            scrolled = field.autoScrollCaret(FreeScrollingTextField.SCROLL_UP);
        } else if (y >= (field.getContentHeight() - field.SCROLL_EDGE_SLOP)) {
            scrolled = field.autoScrollCaret(FreeScrollingTextField.SCROLL_DOWN);
        }

        if (!scrolled) {
            field.stopAutoScrollCaret();
            int newCaretIndex = field.coordToCharIndex(
				screenToViewX((int) e.getX()),
				screenToViewY((int) e.getY())
            );
            if (newCaretIndex >= 0) {
                field.moveCaret(newCaretIndex);
            }
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (!isCaretTouched && !mIsFastScrolling) {

            if (fling == 1)
                velocityY = 0;
            else if (fling == -1)
                velocityX = 0;

            mTextField.flingScroll((int) -velocityX, (int) -velocityY);
        }
        onUp(e2);
        return true;
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private boolean onTouchZoom(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (e.getPointerCount() == 2) {
				float rt = spacing(e);
                if (lastDist == 0.f) {
                    lastDist = rt;
                    lastX = (e.getX(0) + e.getX(1)) * .5f;
                    lastY = (e.getY(0) + e.getY(1)) * .5f;
                    lastSize = mTextField.getTextSize();
                } else
					mTextField.setTextSize((int)(lastSize * rt / lastDist), lastX, lastY);
                return true;
            }
        }
        lastDist = 0.f;
        return false;
    }

    /**
     * Subclasses overriding this method have to call the superclass method
     */
    public boolean onTouchEvent(MotionEvent event) {
        onTouchZoom(event);
        boolean handled = mGestureDetector.onTouchEvent(event);
        if (!handled
			&& (event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            // propagate up events since GestureDetector does not do so
            handled = onUp(event);
        }
        return handled;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        onDoubleTap(e);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        isCaretTouched = true;
        int x = screenToViewX((int) e.getX());
        int y = screenToViewY((int) e.getY());
        int charOffset = mTextField.coordToCharIndex(x, y);

        FreeScrollingTextField field = mTextField;
        if (field.isSelectText() && field.inSelectionRange(charOffset)) {
            Document doc = field.getText();
            int line = doc.findLineNumber(charOffset);
            int start = doc.getLineOffset(line);
            int end = doc.getLineOffset(line + 1) - 1;
            field.setSelectionRange(start, end - start);
        } else if (charOffset >= 0) {
            field.moveCaret(charOffset);
            Document doc = field.getText();
            int start, end;
            for (start = charOffset; start >= 0; start--)
                if (!Character.isJavaIdentifierPart(doc.charAt(start)))
                    break;
            if (start != charOffset)
                start++;
            for (end = charOffset; Character.isJavaIdentifierPart(doc.charAt(end)); end++);
            field.selectText(true);
            field.setSelectionRange(start, end - start);
            // toast err msg
            List<ErrSpan> dg = doc.getDiag();
            if (!(dg == null || dg.isEmpty())) {
                y = dg.size() - 1;
                int m, line = 1 + doc.findLineNumber(charOffset);
                x = doc.getLineOffset(line - 1);
                start -= x;
                end -= x;
                x = 0;
                ErrSpan errspan;
                while (x < y) {
                    m = (x + y) >> 1;
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
                    Context ctx = field.getContext();
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
        }
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    /**
     * Android lifecyle event. See {@link android.app.Activity#onPause()}.
     */
    void onPause() {
        //do nothing
    }

    /**
     * Android lifecyle event. See {@link android.app.Activity#onResume()}.
     */
    void onResume() {
        //do nothing
    }

    /**
     * Called by FreeScrollingTextField when it has finished drawing text.
     * Classes extending TouchNavigationMethod can use this to draw, for
     * example, a custom caret.
     * <p>
     * The canvas includes padding in it.
     *
     * @param canvas
     */
    public void onTextDrawComplete(Canvas canvas) {
        // Do nothing. Basic caret drawing is handled by FreeScrollingTextField.
    }

    public void onColorSchemeChanged(ColorScheme colorScheme) {
        // Do nothing. Derived classes can use this to change their graphic assets accordingly.
    }


    //*********************************************************************
    //**************************** Utilities ******************************
    //*********************************************************************

    public void onChiralityChanged(boolean isRightHanded) {
        // Do nothing. Derived classes can use this to change their input
        // handling and graphic assets accordingly.
    }

    /**
     * For any printed character, this method returns the amount of space
     * required in excess of the bounding box of the character to draw the
     * caret.
     * Subclasses should override this method if they are drawing their
     * own carets.
     */
    public Rect getCaretBloat() {
        return mCaretBloat;
    }

    final protected int getPointerId(MotionEvent e) {
        return (e.getAction() & MotionEvent.ACTION_POINTER_ID_MASK)
			>> MotionEvent.ACTION_POINTER_ID_SHIFT;
    }

    /**
     * Converts a x-coordinate from screen coordinates to local coordinates,
     * excluding padding
     */
    final protected int screenToViewX(int x) {
        return x - mTextField.getPaddingLeft() + mTextField.getScrollX();
    }

    /**
     * Converts a y-coordinate from screen coordinates to local coordinates,
     * excluding padding
     */
    final protected int screenToViewY(int y) {
        return y - mTextField.getPaddingTop() + mTextField.getScrollY();
    }

    final public boolean isRightHanded() {
        return true;
    }

    final private boolean isDragSelect() {
        return true;
    }

    /**
     * Determine if a point(x,y) on screen is near a character of interest,
     * specified by its index charOffset. The radius of proximity is defined
     * by TOUCH_SLOP.
     *
     * @param x          X-coordinate excluding padding
     * @param y          Y-coordinate excluding padding
     * @param charOffset the character of interest
     * @return Whether (x,y) lies close to the character with index charOffset
     */
    public boolean isNearChar(int x, int y, int charOffset) {
        Rect bounds = mTextField.getBoundingBox(charOffset);

        return (y >= (bounds.top - TOUCH_SLOP)
			&& y < (bounds.bottom + TOUCH_SLOP)
			&& x >= (bounds.left - TOUCH_SLOP)
			&& x < (bounds.right + TOUCH_SLOP)
			);
    }
}
