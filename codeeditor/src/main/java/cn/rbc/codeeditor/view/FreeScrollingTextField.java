/*
 *****************************************************************************
 *
 * --------------------------------- row length
 * Hello World(\n)                 | 12
 * This is a test the caret(\n) | 28
 * func|t|ions(\n)                 | 10
 * of this program(EOF)            | 16
 * ---------------------------------
 *
 * The figure illustrates the convention for counting characters
 * Rows 36 to 39 of a hypothetical text file are shown.
 * The 0th char of the file is off-screen.
 * Assume the first char on screen is the 257th char.
 * The caret is before the char 't' of the word "functions". The caret is drawn
 * as a filled blue rectangle enclosing the 't'.
 *
 * mCaretPosition == 257 + 12 + 28 + 4 == 301
 *
 * Note 1: EOF (End Of File) is a real char with a length of 1
 * Note 2: Characters enclosed in parentheses are non-printable
 *
 *****************************************************************************
 *
 * There is a difference between rows and lines in TextWarrior.
 * Rows are displayed while lines are a pure logical construct.
 * When there is no word-wrap, a line of text is displayed as a row on screen.
 * With word-wrap, a very long line of text may be split across several rows
 * on screen.
 *
 */
package cn.rbc.codeeditor.view;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.text.method.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import cn.rbc.codeeditor.common.*;
import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.util.*;
import cn.rbc.codeeditor.view.ColorScheme.*;
import cn.rbc.codeeditor.view.autocomplete.*;
import java.util.*;

import android.content.ClipboardManager;
import cn.rbc.codeeditor.util.Pair;

/**
 * A custom text view that uses a solid shaded caret (aka cursor) instead of a
 * blinking caret and allows a variety of navigation methods to be easily
 * integrated.
 * <p>
 * It also has a built-in syntax highlighting feature. The global programming
 * language syntax to use is specified with Lexer.setLanguage(Language).
 * To disable syntax highlighting, simply pass LanguageNonProg to that function.
 * <p>
 * Responsibilities
 * 1. Display text
 * 2. Display padding
 * 3. Scrolling
 * 4. Store and display caret position and selection range
 * 5. Store font type, font size, and tab length
 * 6. Interpret non-touch input events and shortcut keystrokes, triggering
 * the appropriate inner class controller actions
 * 7. Reset view, set cursor position and selection range
 * <p>
 * Inner class controller responsibilities
 * 1. Caret movement
 * 2. Activate/deactivate selection mode
 * 3. Cut, copy, paste, delete, insert
 * 4. Schedule areas to repaint and analyze for spans in response to edits
 * 5. Directs scrolling if caret movements or edits causes the caret to be off-screen
 * 6. Notify rowListeners when caret row changes
 * 7. Provide helper methods for InputConnection to setComposingText from the IME
 * <p>
 * This class is aware that the underlying text buffer uses an extra char (EOF)
 * to mark the end of the text. The text size reported by the text buffer includes
 * this extra char. Some bounds manipulation is done so that this implementation
 * detail is hidden from client classes.
 */
public abstract class FreeScrollingTextField extends View
implements Document.TextFieldMetrics, OnRowChangedListener, OnSelectionChangedListener,
DialogInterface.OnDismissListener, Runnable {

    //---------------------------------------------------------------------
    //--------------------------  Caret Scroll  ---------------------------
    public final static int SCROLL_UP = 0;
    public final static int SCROLL_DOWN = 1;
    public final static int SCROLL_LEFT = 2;
    public final static int SCROLL_RIGHT = 3;
    /**
     * Scale factor for the width of a caret when on a NEWLINE or EOF char.
     * A factor of 1.0 is equals to the width of a space character
     */
    protected static float EMPTY_CARET_WIDTH_SCALE = 0.75f;
    /**
     * When in selection mode, the caret height is scaled by this factor
     */
    protected static float SEL_CARET_HEIGHT_SCALE = 0.5f;
    protected static int DEFAULT_TAB_LENGTH_SPACES = 4;
    protected static int BASE_TEXT_SIZE_PIXELS = 16;
    protected static long SCROLL_PERIOD = 250; //in milliseconds
    protected static int SCROLL_EDGE_SLOP = 150;
    /*
     * Hash map for determining which characters to let the user choose from when
     * a hardware key is long-pressed. For example, long-pressing "e" displays
     * choices of "é, è, ê, ë" and so on.
     * This is biased towards European locales, but is standard Android behavior
     * for TextView.
     *
     * Copied from android.text.method.QwertyKeyListener, dated 2006
     */
    /*
     * Hash map for determining which characters to let the user choose from when
     * a hardware key is long-pressed. For example, long-pressing "e" displays
     * choices of "é, è, ê, ë" and so on.
     * This is biased towards European locales, but is standard Android behavior
     * for TextView.
     *
     * Copied from android.text.method.QwertyKeyListener, dated 2006
     */
    private final static SparseArray<String> PICKER_SETS = new SparseArray<>();

    static {
        SparseArray<String> psets = PICKER_SETS;
        psets.put('A', "\u00C0\u00C1\u00C2\u00C4\u00C6\u00C3\u00C5\u0104\u0100");
        psets.put('C', "\u00C7\u0106\u010C");
        psets.put('D', "\u010E");
        psets.put('E', "\u00C8\u00C9\u00CA\u00CB\u0118\u011A\u0112");
        psets.put('G', "\u011E");
        psets.put('L', "\u0141");
        psets.put('I', "\u00CC\u00CD\u00CE\u00CF\u012A\u0130");
        psets.put('N', "\u00D1\u0143\u0147");
        psets.put('O', "\u00D8\u0152\u00D5\u00D2\u00D3\u00D4\u00D6\u014C");
        psets.put('R', "\u0158");
        psets.put('S', "\u015A\u0160\u015E");
        psets.put('T', "\u0164");
        psets.put('U', "\u00D9\u00DA\u00DB\u00DC\u016E\u016A");
        psets.put('Y', "\u00DD\u0178");
        psets.put('Z', "\u0179\u017B\u017D");
        psets.put('a', "\u00E0\u00E1\u00E2\u00E4\u00E6\u00E3\u00E5\u0105\u0101");
        psets.put('c', "\u00E7\u0107\u010D");
        psets.put('d', "\u010F");
        psets.put('e', "\u00E8\u00E9\u00EA\u00EB\u0119\u011B\u0113");
        psets.put('g', "\u011F");
        psets.put('i', "\u00EC\u00ED\u00EE\u00EF\u012B\u0131");
        psets.put('l', "\u0142");
        psets.put('n', "\u00F1\u0144\u0148");
        psets.put('o', "\u00F8\u0153\u00F5\u00F2\u00F3\u00F4\u00F6\u014D");
        psets.put('r', "\u0159");
        psets.put('s', "\u00A7\u00DF\u015B\u0161\u015F");
        psets.put('t', "\u0165");
        psets.put('u', "\u00F9\u00FA\u00FB\u00FC\u016F\u016B");
        psets.put('y', "\u00FD\u00FF");
        psets.put('z', "\u017A\u017C\u017E");
        psets.put(KeyCharacterMap.PICKER_DIALOG_INPUT,
                  "\u2026\u00A5\u2022\u00AE\u00A9\u00B1[]{}\\|");
        psets.put('/', "\\");

        // From packages/inputmethods/LatinIME/res/xml/kbd_symbols.xml

        psets.put('1', "\u00b9\u00bd\u2153\u00bc\u215b");
        psets.put('2', "\u00b2\u2154");
        psets.put('3', "\u00b3\u00be\u215c");
        psets.put('4', "\u2074");
        psets.put('5', "\u215d");
        psets.put('7', "\u215e");
        psets.put('0', "\u207f\u2205");
        psets.put('$', "\u00a2\u00a3\u20ac\u00a5\u20a3\u20a4\u20b1");
        psets.put('%', "\u2030");
        psets.put('*', "\u2020\u2021");
        psets.put('-', "\u2013\u2014");
        psets.put('+', "\u00b1");
        psets.put('(', "[{<");
        psets.put(')', "]}>");
        psets.put('!', "\u00a1");
        psets.put('"', "\u201c\u201d\u00ab\u00bb\u02dd");
        psets.put('?', "\u00bf");
        psets.put(',', "\u201a\u201e");

        // From packages/inputmethods/LatinIME/res/xml/kbd_symbols_shift.xml

        psets.put('=', "\u2260\u2248\u221e");
        psets.put('<', "\u2264\u00ab\u2039");
        psets.put('>', "\u2265\u00bb\u203a");
    }

    //光标宽度
    public int mCursorWidth;
    public TextFieldController mCtrlr; // the controller in MVC
    public TextFieldInputConnection mInputConnection;
    public OnRowChangedListener mRowListener;
    public OnSelectionChangedListener selLis;
    public OnCaretScrollListener crtLis;
    public int mCaretRow = 0; // can be calculated, but stored for efficiency purposes
    protected boolean isEdited = false; // whether the text field is dirtied
    protected TouchNavigationMethod mNavMethod;
    protected Document hDoc; // the model in MVC
    protected int mCaretPosition = 0, mCaretCol;
    protected int mSelectionAnchor = -1; // inclusive
    protected int mSelectionEdge = -1; // exclusive
    protected int mTabLength = DEFAULT_TAB_LENGTH_SPACES;
    protected ColorScheme mColorScheme;
    protected boolean isHighlightRow = true;
    protected boolean isShowNonPrinting = false;
    protected boolean isAutoIndent = true;
    protected int mAutoIndentWidth = 4;
    protected boolean isLongPressCaps = false;
    protected boolean isPureMode = false;
    protected AutoCompletePanel mAutoCompletePanel;
    protected SignatureHelpPanel mSigHelpPanel;
    private OverScroller mScroller;
    private Paint mTextPaint, mLineBrush;
    /**
     * Max amount that can be scrolled horizontally based on the longest line
     * displayed on screen so far
     */
    private int mTopOffset;
	protected int mLeftOffset;
    private int mLineMaxWidth, xExtent;
    private int mAlphaWidth, mSpaceWidth;
	private int mSizeMax, mSizeMin;
    private boolean isAutoCompeted = true; //代码提示
    private boolean isShowLineNumbers = true;
    private boolean isCursorVisible = true;
    private boolean isLayout = false;
    private boolean isTextChanged = false;
    private boolean isCaretScrolled = false;
	private boolean useSpace = false;
    private final Runnable mScrollCaretDownTask = this;
    private final Runnable mScrollCaretUpTask = new Runnable() {
        @Override
        public void run() {
            mCtrlr.moveCaretUp();
            if (!caretOnFirstRowOfFile())
                postDelayed(this, SCROLL_PERIOD);
        }
    };
    private final Runnable mScrollCaretLeftTask = new Runnable() {
        @Override
        public void run() {
            mCtrlr.moveCaretLeft(false);
            if (mCaretPosition > 0 &&
				mCaretRow == hDoc.findRowNumber(mCaretPosition - 1))
                postDelayed(this, SCROLL_PERIOD);
        }
    };
    private final Runnable mScrollCaretRightTask = new Runnable() {
        @Override
        public void run() {
            mCtrlr.moveCaretRight(false);
            if (!caretOnEOF() &&
				mCaretRow == hDoc.findRowNumber(mCaretPosition + 1))
                postDelayed(this, SCROLL_PERIOD);
        }
    };
    private boolean isUseGboard = false;
    private RectF mRect;
    private EdgeEffect mTopEdge;
    private EdgeEffect mBottomEdge;
    ClipboardPanel mClipboardPanel;
    private ClipboardManager mClipboardManager;
    private float mZoomFactor = 1;
    private int mCaretX, mCaretY;
    private char mCharEmoji = '\0';
    //private Pair mCaretSpan = new Pair(0, 0);
    private Typeface defTypeface = Typeface.DEFAULT;
    private Typeface boldTypeface = Typeface.DEFAULT_BOLD;
    protected int mTypeInput = InputType.TYPE_CLASS_TEXT;
    private Context mContext;
	private SparseIntArray chrAdvs = new SparseIntArray();

    public FreeScrollingTextField(Context context) {
        super(context);
        initTextField(context);
    }

    public FreeScrollingTextField(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTextField(context);
    }

    public FreeScrollingTextField(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initTextField(context);
    }

    public void setCaretListener(OnCaretScrollListener caretScrollListener) {
        crtLis = caretScrollListener;
    }

    protected void initTextField(Context context) {
        mContext = context;
        hDoc = new Document(this);
        mNavMethod = new TouchNavigationMethod(this);
        mScroller = new OverScroller(context);

        initView(context);
    }

    public int getTopOffset() {
        return mTopOffset;
    }

    public int getAutoIndentWidth() {
        return mAutoIndentWidth;
    }

    //换行空格数目
    public void setAutoIndentWidth(int autoIndentWidth) {
        mAutoIndentWidth = autoIndentWidth;
    }

    public int getCaretY() {
        return mCaretY;
    }

    public int getCaretX() {
        return mCaretX;
    }

    public void setCursorVisiable(boolean isCursorVisible) {
        this.isCursorVisible = isCursorVisible;
    }

    public boolean isShowLineNumbers() {
        return isShowLineNumbers;
    }

    public void setShowLineNumbers(boolean showLineNumbers) {
        isShowLineNumbers = showLineNumbers;
    }

	public void setShowNonPrinting(boolean showNonPrinting) {
		isShowNonPrinting = showNonPrinting;
	}

    public void setCaretScrolled(boolean scrolled) {
        isCaretScrolled = scrolled;
    }

    public boolean getTextChanged() {
        return isTextChanged;
    }

    public void setTextChanged(boolean changed) {
        isTextChanged = changed;
    }

    public int getLeftOffset() {
        return mLeftOffset;
    }

    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

	public boolean setTextSize(int pix) {
		return setTextSize(pix, 0.f, 0.f);
	}

    //private boolean showed = false;

	@Override
	public void scrollTo(int x, int y) {
		x = Math.max(Math.min(x, getMaxScrollX()), 0);
		y = Math.max(Math.min(y, getMaxScrollY()), 0);
		super.scrollTo(x, y);
        /*if (mSigHelpPanel != null && mSigHelpPanel.isShowing()) {
            int cy = getCaretY();
            if (y <= cy && cy <= y + getHeight()) {
               //  if (showed) mSigHelpPanel.show();
               int[] pos = SignatureHelpPanel.updatePosition(this);
               mSigHelpPanel.update(pos[0], pos[1]);
             /*  showed = false;
            } else {
               mSigHelpPanel.hide();
               showed = true;*
            }
        }*/
	}

    public boolean setTextSize(int pix, float cx, float cy) {
        if (pix < mSizeMin || pix > mSizeMax || pix == (int)mTextPaint.getTextSize())
            return false;
		//pix = Math.max(mSizeMin, Math.min(mSizeMax, pix));
        float oldHeight = rowHeight();
        float oldWidth = getCharAdvance('a');
        mZoomFactor = pix / BASE_TEXT_SIZE_PIXELS;
        mTextPaint.setTextSize(pix);
		mLineBrush.setTextSize(pix);
		chrAdvs.clear();
        mSpaceWidth = (int) mTextPaint.measureText(" ");
		mAlphaWidth = getCharAdvance('a');
        int y = 0;
		if (hDoc.isWordWrap()) {
            int r = (int)((getScrollY()+cy)/oldHeight);
            int i = hDoc.getRowOffset(r);
            hDoc.analyzeWordWrap();
            i = hDoc.findRowNumber(i);
            y = rowHeight()*(i-r);
        }
        mCtrlr.updateCaretRow();
		mLineBrush.setStrokeWidth(mAlphaWidth * .15f);
        int x = (int)((getScrollX() + cx) * mAlphaWidth / oldWidth - cx);
        y += (getScrollY() + cy) * rowHeight() / oldHeight - cy;
       // xExtent = 0;
        x = Math.max(0, Math.min(getMaxScrollX(), x));
        y = Math.max(0, Math.min(getMaxScrollY(), y));
        if (x != getScrollX() || y != getScrollY()) {
            super.scrollTo(x, y);
        } else {
            postInvalidate();
        }
        xExtent = 0;
        /*if (mSigHelpPanel.isShowing()) {
            int[] pos = SignatureHelpPanel.updatePosition(this);
            mSigHelpPanel.update(pos[0], pos[1]);
        }*/
        mSigHelpPanel.setTextSize(pix);
		return true;
    }

    public void replaceText(int from, int charCount, CharSequence text) {
        mCtrlr.replaceText(from, charCount, text);
        mCtrlr.stopTextComposing();
    }

    public int getLength() {
        return hDoc.length();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean isSaveEnabled() {
        return true;
    }

    private void initView(Context context) {

        mCtrlr = new TextFieldController(this);
        mClipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(BASE_TEXT_SIZE_PIXELS);
        mTextPaint.setFontFeatureSettings("'liga' on");
        mLineBrush = new Paint();
		mLineBrush.setAntiAlias(true);
		mLineBrush.setTextSize(BASE_TEXT_SIZE_PIXELS);
        mTopEdge = new EdgeEffect(mContext);
        mBottomEdge = new EdgeEffect(mContext);
        mRect = new RectF();
        setLongClickable(true);
        setFocusableInTouchMode(true);
        setHapticFeedbackEnabled(true);
        setColorScheme(ColorSchemeLight.getInstance());
		mSpaceWidth = (int)mTextPaint.measureText(" ");
		mCursorWidth = (int)(HelperUtils.getDpi(mContext) * 1.5f);
		android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
		mSizeMin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 8.f, dm);
		mSizeMax = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 32.f, dm);

        mRowListener = this;

        selLis = this;

        resetView();
        mClipboardPanel = new ClipboardPanel(this);
        mAutoCompletePanel = new AutoCompletePanel(this);
        mSigHelpPanel = new SignatureHelpPanel(this);
        mSigHelpPanel.setTextSize(BASE_TEXT_SIZE_PIXELS);
        //TODO find out if this function works
        setScrollContainer(true);
        invalidate();
    }

	public void run() {
		mCtrlr.moveCaretDown();
		if (!caretOnLastRowOfFile())
			postDelayed(mScrollCaretDownTask, SCROLL_PERIOD);
	}

	public void onRowChanged(int newRowIndex) {
		// Do nothing
	}

	public void onSelectionChanged(boolean active, int selStart, int selEnd) {
		if (active)
			mClipboardPanel.show();
		else
			mClipboardPanel.hide();
	}

	public void onDel(CharSequence text, int cursorPosition, int delCount) {
		isTextChanged = true;
		/*if (delCount <= mCaretSpan.first) {
			mCaretSpan.first--;
            //editOff--;
        }*/
		mAutoCompletePanel.dismiss(); 
        mScroller.abortAnimation();
        mSigHelpPanel.hide();
	}

	public void onNewLine(CharSequence adds) {
		isTextChanged = true;
		//mCaretSpan.first++;
		mAutoCompletePanel.dismiss();
        mScroller.abortAnimation();
        mSigHelpPanel.hide();
	}

	public void onAdd(CharSequence text, int cursorPosition, int addCount) {
		isTextChanged = true;
		//mCaretSpan.first += addCount;
		if (addCount == 0) return;
		//找到空格或者其他
		int curr = cursorPosition;
		for (; curr > 0; curr--) {
			char c = hDoc.charAt(curr - 1);
			if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.'))
				break;
		}
		mAutoCompletePanel._off = cursorPosition - curr;
		char ch = text.charAt(0);
        mScroller.abortAnimation();
		if (isAutoCompeted) {
			if (cursorPosition - curr > 0 && Character.isLetterOrDigit(ch))
			//是否开启代码提示
			// log("subSequence:"+hDoc.subSequence(curr, caretPosition - curr));
			// if (isAutoCompeted) {
			// Log.i("AutoCompete", text+" "+cursorPosition+" "+curr);
                mAutoCompletePanel.update(hDoc.subSequence(curr, cursorPosition));
			// }
			else
				mAutoCompletePanel.dismiss();
		}
	}

    private void resetView() {
        mCaretPosition = mCaretRow = 0;
        xExtent = mLineMaxWidth = 0;
        mCtrlr.setSelectText(false);
        mCtrlr.stopTextComposing();
        ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(this);
        hDoc.clearSpans();
        if (getContentWidth() > 0 || !hDoc.isWordWrap())
            hDoc.analyzeWordWrap();
        mRowListener.onRowChanged(0);
        scrollTo(0, 0);
    }

    public void setSuggestion(boolean enable) {
        mTypeInput = enable ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    }

    /**
     * Sets the text displayed to the document referenced by hDoc. The view
     * state is reset and the view is invalidated as a side-effect.
     */
    public void setDocument(Document doc) {
        hDoc = doc;
        resetView();
        mCtrlr.cancelSpanning(); //stop existing lex threads
        mCtrlr.determineSpans();
    }

    /**
     * Returns a DocumentProvider that references the same Document used by the
     * FreeScrollingTextField.
     */
    public Document getText() {
        return hDoc;
    }

    public void setRowListener(OnRowChangedListener rLis) {
        mRowListener = rLis;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener sLis) {
        selLis = sLis;
    }

    /**
     * Sets the caret navigation method used by this text field
     */
    public void setNavigationMethod(TouchNavigationMethod navMethod) {
        mNavMethod = navMethod;
    }

    //---------------------------------------------------------------------
    //-------------------------- Paint methods ----------------------------

    public void setChirality(boolean isRightHanded) {
        mNavMethod.onChiralityChanged(isRightHanded);
    }

    // this used to be isDirty(), but was renamed to avoid conflicts with Android API 11
    public boolean isEdited() {
        return isEdited;
    }

    public void setEdited(boolean set) {
        isEdited = set;
    }

    public void setUseGboard(boolean set) {
        isUseGboard = set;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = mTypeInput | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION | EditorInfo.IME_ACTION_DONE
			| EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        if (isUseGboard) {
            outAttrs.initialSelStart = getCaretPosition();
            outAttrs.initialSelEnd = getCaretPosition();
        }
        if (mInputConnection == null)
            mInputConnection = new TextFieldInputConnection(this);
        else
            mInputConnection.resetComposingState();
        return mInputConnection;
    }

    //---------------------------------------------------------------------
    //------------------------- Layout methods ----------------------------
    //TODO test with height less than 1 complete row
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(useAllDimensions(widthMeasureSpec), useAllDimensions(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            Rect rect = new Rect();
            getWindowVisibleDisplayFrame(rect);
            mTopOffset = rect.top + rect.height() - getHeight();
            if (!isLayout)
                mCtrlr.determineSpans();
            isLayout = right > 0;
			if (!mCtrlr.lexing)
				invalidate();
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (hDoc.isWordWrap() && oldw != w)
            hDoc.analyzeWordWrap();
        mCtrlr.updateCaretRow();
        if (h < oldh)
            makeCharVisible(mCaretPosition);
    }

    private int useAllDimensions(int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int result = MeasureSpec.getSize(measureSpec);

        if (specMode != MeasureSpec.EXACTLY && specMode != MeasureSpec.AT_MOST) {
            result = Integer.MAX_VALUE;
            TextWarriorException.fail("MeasureSpec cannot be UNSPECIFIED. Setting dimensions to max.");
        }

        return result;
    }

    protected int getNumVisibleRows() {
        return (int) Math.ceil((double) getContentHeight() / rowHeight());
    }

    public int rowHeight() {
        Paint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        return (metrics.descent - metrics.ascent);
    }

    /*
     The only methods that have to worry about padding are invalidate, draw
	 and computeVerticalScrollRange() methods. Other methods can assume that
	 the text completely fills a rectangular viewport given by getContentWidth()
	 and getContentHeight()
	 */
    protected int getContentHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    protected int getContentWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    /**
     * Determines if the View has been layout or is still being constructed
     */
    public boolean hasLayout() {
        return (getWidth() == 0); // simplistic implementation, but should work for most cases
    }

    /**
     * The first row of text to paint, which may be partially visible.
     * Deduced from the clipping rectangle given to onDraw()
     */
    private int getBeginPaintRow(Canvas canvas) {
        Rect bounds = canvas.getClipBounds();
        return bounds.top / rowHeight();
    }

    /**
     * The last row of text to paint, which may be partially visible.
     * Deduced from the clipping rectangle given to onDraw()
     */
    private int getEndPaintRow(Canvas canvas) {
        //clip top and left are inclusive; bottom and right are exclusive
        Rect bounds = canvas.getClipBounds();
        return (bounds.bottom - 1) / rowHeight();
    }

    /**
     * @return The x-value of the baseline for drawing text on the given row
     */
    public int getPaintBaseline(int row) {
        Paint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        return (row + 1) * rowHeight() - metrics.descent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        //translate clipping region to create padding around edges
        canvas.clipRect(getScrollX() + getPaddingLeft(),
						getScrollY() + getPaddingTop(),
						getScrollX() + getWidth() - getPaddingRight(),
						getScrollY() + getHeight() - getPaddingBottom());
        canvas.translate(getPaddingLeft(), getPaddingTop());
        realDraw(canvas);

        canvas.restore();
        mNavMethod.onTextDrawComplete(canvas);
    }

    private void realDraw(Canvas canvas) {
        int currRowNum = getBeginPaintRow(canvas);
        int currIndex = hDoc.getRowOffset(currRowNum);

        if (currIndex < 0) {
            return;
        }
        int currLineNum = 1 + (isWordWrap() ? hDoc.findLineNumber(currIndex) : currRowNum);
        int lastLineNum = 0;
        mLeftOffset = isShowLineNumbers ? (int) mTextPaint.measureText("  " + hDoc.getLineCount()) : 0;
        int paintX = 0;
        int paintY = getPaintBaseline(currRowNum);
        //----------------------------------------------
        // set up span coloring settings
        //----------------------------------------------
        //得到一个词法分析的结果
        List<Pair> spans = hDoc.getSpans();
        if (spans.isEmpty()) return;

		int spanSize = spans.size();
		int spanIndex=0, r=spanSize-1, m;
        /*int k = 0;
        float j = estp;
        if (hDoc.charAt(0)==' ') {*
            spanIndex = 0; r = spanSize-1;*/
		while (spanIndex < r) {
			m = (spanIndex + r + 1) >> 1;
			if (hDoc.realToLogicalIndex(spans.get(m).first) <= currIndex)
				spanIndex = m;
			else
				r = m - 1;
		}/*
        } else {
            spanIndex = hDoc.charAt(0)=='&'?sidx:Math.max(0, Math.min(spanSize-1, sidx + (int)((currIndex-lidx)/estp)));
            //if (spanIndex<spanSize && spanIndex>=0)
            m = spanIndex;
        while ((r=spanIndex+1)<spanSize && spans.get(r).first <= currIndex) {
            spanIndex = r;k++;
        }
        while (spanIndex>0 && spans.get(spanIndex).first > currIndex) {
            spanIndex--;k++;
        }
        lidx = spans.get(spanIndex).first;
        if (spanIndex!=m&&m>=0)
            estp = (lidx-spans.get(m).first)/(float)(spanIndex-m);
        }*
        mTextPaint.setColor(0xff000000);
        canvas.drawText(paintY+"", getScrollX(), getScrollY()+rowHeight(), mTextPaint);
       // sidx = spanIndex;
        //soff = spans.get(spanIndex).first;*/
		Pair currSpan = spans.get(spanIndex++);
		Pair nextSpan = spanIndex < spanSize ? spans.get(spanIndex++) : null;

        //mTextPaint.setTypeface(currSpan.second == Tokenizer.KEYWORD ? boldTypeface : defTypeface);

        int spanColor = mColorScheme.getTokenColor(currSpan.second);
        mTextPaint.setColor(spanColor);

        //----------------------------------------------
        // start painting!
        //----------------------------------------------
		boolean showLN = isShowLineNumbers && mLeftOffset >= getScrollX();
		int width = canvas.getClipBounds().right;
		int mL = hDoc.length();
		int rowheight = rowHeight();

		int idx, diagLen;
		List<ErrSpan> diagList = hDoc.getDiag();
		ErrSpan diag;
		if (diagList == null || diagList.isEmpty()) {
			diag = null;
			diagLen = 0;
			idx = 1;
		} else {
			diagLen = diagList.size();
			r = 0;
			idx = diagLen - 1;
			while (r < idx) {
				m = (idx + r) >> 1;
				if (diagList.get(m).stl >= currLineNum)
					idx = m;
				else
					r = m + 1;
			}
			diag = diagList.get(idx++);
		}
		int mHt = 1 + hDoc.findLineNumber(mCaretPosition);
		int mI = hDoc.findMark(currLineNum);
		if (mI < 0)
			mI = ~mI;
		// row by row
		int rowEnd = 0;
        int endRowNum = Math.min(hDoc.getRowCount(), 1+getEndPaintRow(canvas));
        for (m = -1; currRowNum < endRowNum && currIndex < mL; currRowNum++) {
			if (currLineNum != lastLineNum) {
				if (showLN) {
                    ColorScheme.Colorable ca;
                    if (mI < hDoc.getMarksCount() && hDoc.getMark(mI) == currLineNum) {
                        mLineBrush.setColor(0xffa00000);
                        canvas.drawRect(0, rowheight * currRowNum, mLeftOffset - (mSpaceWidth >> 1), rowheight * (currRowNum + 1), mLineBrush);
                        ca = ColorScheme.Colorable.SELECTION_FOREGROUND;
                        mI++;
                    } /*else if (hDoc.isInMarkGap(currLineNum))
                        ca = ColorScheme.Colorable.STRING;*/
                    else
                        ca = ColorScheme.Colorable.NON_PRINTING_GLYPH;
                    mLineBrush.setColor(mColorScheme.getColor(ca));
                    String num = String.valueOf(currLineNum);
                    int padx = (int) (mLeftOffset - mTextPaint.measureText(num) - mSpaceWidth);
                    lastLineNum = currLineNum;
                    canvas.drawText(num, padx, paintY, mLineBrush);
                }
				if (currLineNum == mHt && isHighlightRow && !mCtrlr.isSelectText()) {
					int eRow = currRowNum + 1, rows = hDoc.getRowCount();
					while (eRow <= endRowNum && (eRow != rows && hDoc.charAt(hDoc.getRowOffset(eRow) - 1) != Language.NEWLINE))
						eRow++;
					int orc = mTextPaint.getColor();
					mTextPaint.setColor(mColorScheme.getColor(ColorScheme.Colorable.LINE_HIGHLIGHT));
					canvas.drawRect(mLeftOffset,
									rowheight * currRowNum,
									width,
									rowheight * eRow,
									mTextPaint);
					mTextPaint.setColor(orc);
				}
            }
            paintX = mLeftOffset;

			int i = rowEnd;
            r = paintX;
            byte currState = -1; // invalid initial state
            int drawStart = currIndex, tp = currSpan.second;
            for (rowEnd += hDoc.getRowSize(currRowNum); i <= rowEnd && currIndex<mL; i++,currIndex++) {
                // calculate new state
                byte newState = 0;
                if (mCtrlr.inSelectionRange(currIndex))
                    newState |= 4;
                // test state change
                boolean reachSpanEnd = nextSpan != null && currIndex >= hDoc.realToLogicalIndex(nextSpan.first);
                tp = currSpan.second;
                if (reachSpanEnd) {
                    currSpan = nextSpan;
                    nextSpan = spanIndex<spanSize ? spans.get(spanIndex++) : null;
                }
                if (currSpan.second == Tokenizer.KEYWORD)
                    newState |= 1;
                char c = hDoc.charAt(currIndex);
                if (Character.isWhitespace(c))
                    newState |= 2;
                // err line status
                boolean flow = false;
                if (idx <= diagLen) {
                    if (m < 0
                    // start position
                        && (diag.stl == currLineNum && diag.stc == i
                    // following position
                        || diag.stl < currLineNum && diag.enl >= currLineNum && r == mLeftOffset))
                        m = r;
                    boolean end;
                    if (m >= 0 && m < width && ((end = diag.enl == currLineNum && diag.enc == i) || (flow = i + 1 == rowEnd))) {
                        newState |= 8;
                        if (idx < diagLen && end)
                            diag = diagList.get(idx++);
                    }
                }
                //DLog.i("Lsp", i+","+c+","+rowEnd);
                if (newState != currState
                        || reachSpanEnd && (newState&4)==0
                        || i==rowEnd
                        || currIndex+1 == mL) {
                    // draw the last text
                    if (drawStart < currIndex) {
                       if (r >= getScrollX() && paintX < width) {
                           drawTextBlock(canvas, drawStart, currIndex, tp, paintX, paintY, Math.min(r, width), currState);
                       }
                       paintX = r;
                    }
                    // draw err line
                    if ((newState&8)!=0) {
                        mLineBrush.setColor(ColorScheme.DIAG[diag.severity]);
                        canvas.drawLine(m, paintY, Math.min(r, width), paintY, mLineBrush);
                        m = flow ? mLeftOffset : -1;
                        newState &= 7;
                    }
                    drawStart = currIndex;
                    currState = newState;
                }
               // if (i==rowEnd) break;
                if (currIndex == mCaretPosition && i<rowEnd && isCursorVisible)
                //draw cursor
                    drawCaret(canvas, r, paintY);
                //else if (currIndex + 1 == mCaretPosition)
                //    mCaretSpan = currSpan;
                /*if (i<rowEnd&& currIndex == hDoc._gapStartIndex) {
                    mTextPaint.setColor(0xffff0000);
                    canvas.drawLine(r, paintY, r, paintY-rowheight, mTextPaint);
                    canvas.drawText(""+currIndex, r, paintY, mTextPaint);
                }*/
                r += getAdvance(c, r);
            }
            if (currIndex > 1 && hDoc.charAt(currIndex-2) == Language.NEWLINE) {
				while (idx < diagLen && diag.enl == currLineNum)
					diag = diagList.get(idx++);
				++currLineNum;
				rowEnd = 0;
				m = -1;
                //byte b = 2;
                //if (mCtrlr.inSelectionRange(currIndex))
                //    b |= 4;
                //drawTextBlock(canvas, currIndex, 1+currIndex, 0, r-getEOLAdvance(), paintY, r, b);
			}
            /*if (c!=Language.EOF) {
                mTextPaint.setColor(mColorScheme.getColor(Colorable.NON_PRINTING_GLYPH));
                canvas.drawText("" + hDoc.getRowOffset(currRowNum + 1), r, paintY, mTextPaint);
            }*/
            currIndex--;
            paintY += rowheight;
            paintX = r;

            if (paintX > xExtent)
            // record widest line seen so far
                xExtent = paintX;
            if (paintX > mLineMaxWidth)
                mLineMaxWidth = paintX;
        }
        // end while
		if (showLN) {
			int left = mLeftOffset - mSpaceWidth / 2;
            mTextPaint.setColor(mColorScheme.getColor(Colorable.NON_PRINTING_GLYPH));
            canvas.drawLine(left, getScrollY(), left, getScrollY() + getHeight(), mTextPaint);
        }
    }

    // map spacing
    private char[] buf = new char[2];

    private char mapSpace(char c) {
        switch (c) {
            case ' ':
                return Language.GLYPH_SPACE;
            case Language.EOF: //fall-through
            case Language.NEWLINE:
                return Language.GLYPH_NEWLINE;
            case Language.TAB:
                return Language.GLYPH_TAB;
            default:
                return 0;
        }
    }

    // draw text block: a word or continuing spacing
    private void drawTextBlock(Canvas canvas, int start, int end, int token, int x, int y, int w, byte flags) {
        Paint paint = mTextPaint;
        paint.setTypeface(token == Tokenizer.KEYWORD ? boldTypeface : defTypeface);
        ColorScheme cs = mColorScheme;
        if ((flags&4) != 0) { // selected text
            paint.setColor(cs.getColor(Colorable.SELECTION_BACKGROUND));
            drawTextBackground(canvas, x, y, w);
            paint.setColor(cs.getColor(Colorable.SELECTION_FOREGROUND));
        } else // normal
            paint.setColor(cs.getTokenColor(token));
        if ((flags & 2) == 0) // not spaces
            canvas.drawText(hDoc, start, end, x, y, paint);
        else if (isShowNonPrinting) { // spaces & showNonPrinting
            paint = mLineBrush;
            paint.setColor(cs.getColor(Colorable.NON_PRINTING_GLYPH));
            while (start < end) {
                char c = hDoc.charAt(start);
                buf[0] = mapSpace(c);
                if (buf[0]!=0)
                    canvas.drawText(buf, 0, 1, x, y, paint);
                x += getAdvance(c, x);
                start++;
            }
        }
    }

    // paintY is the baseline for text, NOT the top extent
    private void drawTextBackground(Canvas canvas, float paintX, float paintY, float endX) {
        Paint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        canvas.drawRect(paintX,
						paintY + metrics.ascent,
						endX,
						paintY + metrics.descent,
						mTextPaint);
    }

    private void drawCaret(Canvas canvas, int paintX, int paintY) {
        int originalColor = mTextPaint.getColor();
        mCaretX = paintX - mCursorWidth / 2;
        mCaretY = paintY;
        int caretColor = mColorScheme.getColor(Colorable.CARET_DISABLED);
        mTextPaint.setColor(caretColor);
        // draw full caret
        drawTextBackground(canvas, mCaretX, paintY, mCaretX + mCursorWidth);
        mTextPaint.setColor(originalColor);
    }

    @Override
    final public int getRowWidth() {
        return getContentWidth() - mLeftOffset;
    }

    /**
     * Get line Length
     *
     * @return Length of single line
     */
    public int getLineLength() {
        return Math.max(getWidth(), mLineMaxWidth + mLeftOffset);
    }

    /**
     * Returns printed width of c.
     * <p>
     * Takes into account user-specified tab width and also handles
     * application-defined widths for NEWLINE and EOF
     *
     * @param c Character to measure
     * @return Advance of character, in pixels
     */
    @Override
    public int getAdvance(char c) {
        return getAdvance(c, 0);
    }

    public int getAdvance(char c, int x) {
        int advance;
        switch (c) {
            case 0xd83c:
            case 0xd83d:
                advance = 0;
                mCharEmoji = c;
                break;
            case ' ':
                advance = getSpaceAdvance();
                break;
            case Language.NEWLINE: // fall-through
            case Language.EOF:
                advance = getEOLAdvance();
                break;
            case Language.TAB:
                advance = getTabAdvance(x);
                break;
            default:
                if (mCharEmoji != 0) {
                    buf[0] = mCharEmoji;
                    buf[1] = c;
                    mCharEmoji = 0;
                    advance = (int) mTextPaint.measureText(buf, 0, 2);
                } else
                    advance = getCharAdvance(c);
                break;
        }

        return advance;
    }

    //---------------------------------------------------------------------
    //------------------- Scrolling and touch -----------------------------

    public int getCharAdvance(char c) {
		int advance;
        if ('\u4E00'<=c&&c<='\u9FFF')
            c = '\u4E00'; // chinese characters are mono-space
		if ((advance = chrAdvs.get(c, -1)) == -1) {
			buf[0] = c;
			advance = (int) mTextPaint.measureText(buf, 0, 1);
			chrAdvs.append(c, advance);
		}
        return advance;
    }

    protected int getSpaceAdvance() {
        return isShowNonPrinting
            ? getCharAdvance(Language.GLYPH_SPACE) //(int) mTextPaint.measureText(Language.GLYPH_SPACE,
               //                            0, Language.GLYPH_SPACE.length())
            : mSpaceWidth;
    }

    protected int getEOLAdvance() {
        if (isShowNonPrinting) {
            return getCharAdvance(Language.GLYPH_NEWLINE); //mTextPaint.measureText(Language.GLYPH_NEWLINE,
					//							0, Language.GLYPH_NEWLINE.length());
        } else {
            return (int) (EMPTY_CARET_WIDTH_SCALE * mTextPaint.measureText(" ", 0, 1));
        }
    }

    protected int getTabAdvance() {
        return mTabLength * /* (isShowNonPrinting
             ? (int) mTextPaint.measureText(Language.GLYPH_SPACE,
             0, Language.GLYPH_SPACE.length())
             :*/ mSpaceWidth;
    }

    protected int getTabAdvance(int x) {
        /*if (isShowNonPrinting)
         return mTabLength * (int) mTextPaint.measureText(Language.GLYPH_SPACE,
         0, Language.GLYPH_SPACE.length());
         else {*/
        int i = (x - mLeftOffset) / mSpaceWidth % mTabLength;
        return (mTabLength - i) * mSpaceWidth;
    }

    /**
     * Invalidate rows from startRow (inclusive) to endRow (exclusive)
     */
    void invalidateRows(int startRow, int endRow) {
		if (mCtrlr.lexing)
			return;
		TextWarriorException.assertVerbose(startRow <= endRow && startRow >= 0,
										   "Invalid startRow and/or endRow");
        Rect caretSpill = mNavMethod.getCaretBloat();
        //TODO The ascent of (startRow+1) may jut inside startRow, so part of
        // that rows have to be invalidated as well.
        // This is a problem for Thai, Vietnamese and Indic scripts
        Paint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        int top = startRow * rowHeight() + getPaddingTop();
        top -= Math.max(caretSpill.top, metrics.descent);
        top = Math.max(0, top);

        super.invalidate(0,
						 top,
						 getScrollX() + getWidth(),
						 endRow * rowHeight() + getPaddingTop() + caretSpill.bottom);
    }

    /**
     * Invalidate rows from startRow (inclusive) to the end of the field
     */
    void invalidateFromRow(int startRow) {
		if (mCtrlr.lexing)
			return;
        TextWarriorException.assertVerbose(startRow >= 0,
										   "Invalid startRow");

        Rect caretSpill = mNavMethod.getCaretBloat();
        //TODO The ascent of (startRow+1) may jut inside startRow, so part of
        // that rows have to be invalidated as well.
        // This is a problem for Thai, Vietnamese and Indic scripts
        Paint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        int top = startRow * rowHeight() + getPaddingTop();
        top -= Math.max(caretSpill.top, metrics.descent);
        top = Math.max(0, top);

        super.invalidate(0,
						 top,
						 getScrollX() + getWidth(),
						 getScrollY() + getHeight());
    }

    void invalidateCaretRow() {
        invalidateRows(mCaretRow, mCaretRow + 1);
    }

    void invalidateSelectionRows() {
        int startRow = hDoc.findRowNumber(mSelectionAnchor);
        int endRow = hDoc.findRowNumber(mSelectionEdge);

        invalidateRows(startRow, endRow + 1);
    }

    /**
     * Scrolls the text horizontally and/or vertically if the character
     * specified by charOffset is not in the visible text region.
     * The view is invalidated if it is scrolled.
     *
     * @param charOffset The index of the character to make visible
     * @return True if the drawing area was scrolled horizontally
     * and/or vertically
     */
    public boolean makeCharVisible(int charOffset) {
        TextWarriorException.assertVerbose(charOffset >= 0 && charOffset < hDoc.length(), "Invalid charOffset given");
        int scrollVerticalBy = makeCharRowVisible(charOffset);
        int scrollHorizontalBy = makeCharColumnVisible(charOffset);

        if (scrollVerticalBy == 0 && scrollHorizontalBy == 0)
            return false;
        else {
            scrollBy(scrollHorizontalBy, scrollVerticalBy);
            return true;
        }
    }

    /**
     * Calculates the amount to scroll vertically if the char is not
     * in the visible region.
     *
     * @param charOffset The index of the character to make visible
     * @return The amount to scroll vertically
     */
    private int makeCharRowVisible(int charOffset) {
        int scrollBy = 0;
        int currLine = hDoc.findRowNumber(charOffset);
        int charTop = currLine * rowHeight();
        int charBottom = charTop + rowHeight();

        if (isCaretScrolled) {
            // 拖动水滴滚动在距离SCROLL_EDGE_SLOP的时候就开始滚动
            if (charTop < getScrollY())
                scrollBy = charTop - getScrollY();
            else if (charBottom + SCROLL_EDGE_SLOP > (getScrollY() + getContentHeight()))
                scrollBy = charBottom + SCROLL_EDGE_SLOP - getScrollY() - getContentHeight();
        } else
        // 默认情况在水滴移动到屏幕上下边缘时才开始滚动
        if (charTop < getScrollY())
            scrollBy = charTop - getScrollY();
        else if (charBottom > (getScrollY() + getContentHeight()))
            scrollBy = charBottom - getScrollY() - getContentHeight();

        return scrollBy;
    }

    /**
     * Calculates the amount to scroll horizontally if the char is not
     * in the visible region.
     *
     * @param charOffset The index of the character to make visible
     * @return The amount to scroll horizontally
     */
    private int makeCharColumnVisible(int charOffset) {
        int scrollBy = 0;
        Pair visibleRange = getCharExtent(charOffset);

        int charLeft = visibleRange.first;
        int charRight = visibleRange.second;

        if (isCaretScrolled) {
            // 拖动水滴滚动在距离SCROLL_EDGE_SLOP / 3的时候就开始滚动
            if (charRight + SCROLL_EDGE_SLOP / 3 >= (getScrollX() + getContentWidth()))
                scrollBy = charRight + SCROLL_EDGE_SLOP / 3 - getScrollX() - getContentWidth();
			else if (charLeft - SCROLL_EDGE_SLOP / 3 <= getScrollX() + mAlphaWidth) {
                scrollBy = charLeft - SCROLL_EDGE_SLOP / 3 - getScrollX() - mAlphaWidth;
                if (charLeft <= mLeftOffset)
                    scrollBy = 0;
            }
        } else
        // 默认情况在水滴移动到屏幕左右边缘时才开始滚动
        if (getContentWidth() == 0)
            scrollBy = 0;
        else if (charRight > (getScrollX() + getContentWidth()))
            scrollBy = charRight - getScrollX() - getContentWidth();
        else if (charLeft < getScrollX() + mAlphaWidth)
            scrollBy = charLeft - getScrollX() - mAlphaWidth;

        return scrollBy;
    }

    /**
     * Calculates the x-coordinate extent of charOffset.
     *
     * @return The x-values of left and right edges of charOffset. Pair.first
     * contains the left edge and Pair.second contains the right edge
     */
    protected Pair getCharExtent(int charOffset) {
        int row = hDoc.findRowNumber(charOffset);
        int rowOffset = hDoc.getRowOffset(row);
        int left = mLeftOffset;
        int right = mLeftOffset;
        boolean isEmoji = false;
        CharSequence rowText = hDoc.getRow(row);
        int i = 0;

        int len = rowText.length();
        while (rowOffset + i <= charOffset && i < len) {
            char c = rowText.charAt(i);
            left = right;
            switch (c) {
                case 0xd83c:
                case 0xd83d:
                    isEmoji = true;
                    buf[0] = c;
                    buf[1] = rowText.charAt(i+1);
                    right += (int) mTextPaint.measureText(buf, 0, 2);
                    break;
                case Language.NEWLINE:
                case Language.EOF:
                    right += getEOLAdvance();
                    break;
                case ' ':
                    right += getSpaceAdvance();
                    break;
                case Language.TAB:
                    right += getTabAdvance(right);
                    break;
                default:
                    if (isEmoji)
                        isEmoji = false;
                    else
                        right += getCharAdvance(c);
                    break;
            }
            ++i;
        }
        return new Pair(left, right);
    }

    /**
     * Returns the bounding box of a character in the text field.
     * The coordinate system used is one where (0, 0) is the top left corner
     * of the text, before padding is added.
     *
     * @param charOffset The character offset of the character of interest
     * @return Rect(left, top, right, bottom) of the character bounds,
     * or Rect(-1, -1, -1, -1) if there is no character at that coordinate.
     */
    Rect getBoundingBox(int charOffset) {
        if (charOffset < 0 || charOffset >= hDoc.length())
            return new Rect(-1, -1, -1, -1);

        int row = hDoc.findRowNumber(charOffset);
        int top = row * rowHeight();
        int bottom = top + rowHeight();

        Pair xExtent = getCharExtent(charOffset);
        int left = xExtent.first;
        int right = xExtent.second;

        return new Rect(left, top, right, bottom);
    }

    public ColorScheme getColorScheme() {
        return mColorScheme;
    }

    public void setColorScheme(ColorScheme colorScheme) {
        mColorScheme = colorScheme;
        mNavMethod.onColorSchemeChanged(colorScheme);
        setBackgroundColor(colorScheme.getColor(isPureMode?Colorable.BACKGROUND_PURE:Colorable.BACKGROUND));
    }

    public boolean isPureMode() {
        return isPureMode;
    }

    public void setPureMode(boolean pureMode) {
        isPureMode = pureMode;
        setBackgroundColor(mColorScheme.getColor(pureMode?Colorable.BACKGROUND_PURE:Colorable.BACKGROUND));
    }
    /**
     * Maps a coordinate to the character that it is on. If the coordinate is
     * on empty space, the nearest character on the corresponding row is returned.
     * If there is no character on the row, -1 is returned.
     * <p>
     * The coordinates passed in should not have padding applied to them.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @return The index of the closest character, or -1 if there is
     * no character or nearest character at that coordinate
     */
    int coordToCharIndex(int x, int y) {
        int row = y / rowHeight();
        if (row > hDoc.getRowCount())
            return hDoc.length()-1;

        int charIndex = hDoc.getRowOffset(row);
        if (charIndex < 0)
        //non-existent row
            return -1;

        if (x < 0)
            return charIndex; // coordinate is outside, to the left of view

        CharSequence rowText = hDoc.getRow(row);

        int extent = mLeftOffset;
        int i = 0;
        boolean isEmoji = false;

        //x-=getAdvance('a')/2;
        int len = rowText.length();
        while (i < len) {
            char c = rowText.charAt(i);
            switch (c) {
                case 0xd83c:
                case 0xd83d:
                    isEmoji = true;
                    buf[0] = c;
                    buf[1] = rowText.charAt(i + 1);
                    extent += (int) mTextPaint.measureText(buf, 0, 2);
                    break;
                case Language.NEWLINE:
                case Language.EOF:
                    extent += getEOLAdvance();
                    break;
                case ' ':
                    extent += getSpaceAdvance();
                    break;
                case Language.TAB:
                    extent += getTabAdvance(extent);
                    break;
                default:
                    if (isEmoji)
                        isEmoji = false;
                    else
                        extent += getCharAdvance(c);
            }

            if (extent >= x)
                break;

            ++i;
        }


        if (i < rowText.length())
            return charIndex + i;
        //nearest char is last char of line
        return charIndex + i - 1;
    }

    /**
     * Maps a coordinate to the character that it is on.
     * Returns -1 if there is no character on the coordinate.
     * <p>
     * The coordinates passed in should not have padding applied to them.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @return The index of the character that is on the coordinate,
     * or -1 if there is no character at that coordinate.
     */
    int coordToCharIndexStrict(int x, int y) {
        int row = y / rowHeight();
        int charIndex = hDoc.getRowOffset(row);

        if (charIndex < 0 || x < 0) {
            //non-existent row
            return -1;
        }

        CharSequence rowText = hDoc.getRow(row);

        int extent = 0;
        int i = 0;
        boolean isEmoji = false;

        //x-=getAdvance('a')/2;
        int len = rowText.length();
        while (i < len) {
            char c = rowText.charAt(i);
            switch (c) {
                case 0xd83c:
                case 0xd83d:
                    isEmoji = true;
                    buf[0] = c;
                    buf[1] = rowText.charAt(i + 1);
                    extent += (int) mTextPaint.measureText(buf, 0, 2);
                    break;
                case Language.NEWLINE:
                case Language.EOF:
                    extent += getEOLAdvance();
                    break;
                case ' ':
                    extent += getSpaceAdvance();
                    break;
                case Language.TAB:
                    extent += getTabAdvance(extent);
                    break;
                default:
                    if (isEmoji)
                        isEmoji = false;
                    else
                        extent += getCharAdvance(c);
            }
            if (extent >= x) {
                break;
            }

            ++i;
        }

        if (i < rowText.length()) {
            return charIndex + i;
        }

        //no char enclosing x
        return -1;
    }

    /**
     * Not private to allow access by TouchNavigationMethod
     *
     * @return The maximum x-value that can be scrolled to for the current rows
     * of text in the viewport.
     */
    int getMaxScrollX() {
        if (isWordWrap())
            return 0;
        else
            return Math.max(0, xExtent - getContentWidth() + mNavMethod.getCaretBloat().right + mAlphaWidth);
    }

    /**
     * Not private to allow access by TouchNavigationMethod
     *
     * @return The maximum y-value that can be scrolled to.
     */
    int getMaxScrollY() {
        //滚动时最后一行下面允许的空高度
        return Math.max(0, hDoc.getRowCount() * rowHeight() - getContentHeight() / 2 + mNavMethod.getCaretBloat().bottom);
    }

    @Override
    protected int computeVerticalScrollRange() {
        return getHeight() + getMaxScrollY();
    }

    @Override
    public void computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (oldX != x || oldY != y) {
                scrollTo(x, y);
            }
            
			/* ViewCompat */
            if (!awakenScrollBars())
                postInvalidateOnAnimation();
        }
    }

    boolean awakeScrollBars() {
        return awakenScrollBars();
    }
    //---------------------------------------------------------------------
    //------------------------- Caret methods -----------------------------

    /**
     * Start fling scrolling
     */

    void flingScroll(int velocityX, int velocityY) {
        mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY,
						0, getMaxScrollX(), 0, getMaxScrollY());
        // Keep on drawing until the animation has finished.
        postInvalidate();
        //postInvalidateOnAnimation();
    }

    public boolean isFlingScrolling() {
        return !mScroller.isFinished();
    }

    public void stopFlingScrolling() {
        mScroller.forceFinished(true);
    }

    /**
     * Starting scrolling continuously in scrollDir.
     * Not private to allow access by TouchNavigationMethod.
     *
     * @return True if auto-scrolling started
     */
    boolean autoScrollCaret(int scrollDir) {
        boolean scrolled = false;
        switch (scrollDir) {
            case SCROLL_UP:
                removeCallbacks(mScrollCaretUpTask);
                if ((!caretOnFirstRowOfFile())) {
                    post(mScrollCaretUpTask);
                    scrolled = true;
                }
                break;
            case SCROLL_DOWN:
                removeCallbacks(mScrollCaretDownTask);
                if (!caretOnLastRowOfFile()) {
                    post(mScrollCaretDownTask);
                    scrolled = true;
                }
                break;
            case SCROLL_LEFT:
                removeCallbacks(mScrollCaretLeftTask);
                if (mCaretPosition > 0 &&
					mCaretRow == hDoc.findRowNumber(mCaretPosition - 1)) {
                    post(mScrollCaretLeftTask);
                    scrolled = true;
                }
                break;
            case SCROLL_RIGHT:
                removeCallbacks(mScrollCaretRightTask);
                if (!caretOnEOF() &&
					mCaretRow == hDoc.findRowNumber(mCaretPosition + 1)) {
                    post(mScrollCaretRightTask);
                    scrolled = true;
                }
                break;
            default:
                TextWarriorException.fail("Invalid scroll direction");
                break;
        }
        return scrolled;
    }

    /**
     * Stops automatic scrolling initiated by autoScrollCaret(int).
     * Not private to allow access by TouchNavigationMethod
     */
    void stopAutoScrollCaret() {
        removeCallbacks(mScrollCaretDownTask);
        removeCallbacks(mScrollCaretUpTask);
        removeCallbacks(mScrollCaretLeftTask);
        removeCallbacks(mScrollCaretRightTask);
    }

    /**
     * Stops automatic scrolling in scrollDir direction.
     * Not private to allow access by TouchNavigationMethod
     */
    void stopAutoScrollCaret(int scrollDir) {
        switch (scrollDir) {
            case SCROLL_UP:
                removeCallbacks(mScrollCaretUpTask);
                break;
            case SCROLL_DOWN:
                removeCallbacks(mScrollCaretDownTask);
                break;
            case SCROLL_LEFT:
                removeCallbacks(mScrollCaretLeftTask);
                break;
            case SCROLL_RIGHT:
                removeCallbacks(mScrollCaretRightTask);
                break;
            default:
                TextWarriorException.fail("Invalid scroll direction");
                break;
        }
    }

    public int getCaretRow() {
        return mCaretRow;
    }

    public int getCaretPosition() {
        return mCaretPosition;
    }

    /**
     * Sets the caret to position i, scrolls it to view and invalidates
     * the necessary areas for redrawing
     *
     * @param i The character index that the caret should be set to
     */
    public void moveCaret(int i) {
        mCtrlr.moveCaret(i);
    }

    /**
     * Sets the caret one position back, scrolls it on screen, and invalidates
     * the necessary areas for redrawing.
     * <p>
     * If the caret is already on the first character, nothing will happen.
     */
    public void moveCaretLeft() {
        mCtrlr.moveCaretLeft(false);
    }

    /**
     * Sets the caret one position forward, scrolls it on screen, and
     * invalidates the necessary areas for redrawing.
     * <p>
     * If the caret is already on the last character, nothing will happen.
     */
    public void moveCaretRight() {
        mCtrlr.moveCaretRight(false);
    }

    /**
     * Sets the caret one row down, scrolls it on screen, and invalidates the
     * necessary areas for redrawing.
     * <p>
     * If the caret is already on the last row, nothing will happen.
     */
    public void moveCaretDown() {
        mCtrlr.moveCaretDown();
    }


    //---------------------------------------------------------------------
    //------------------------- Text Selection ----------------------------

    /**
     * Sets the caret one row up, scrolls it on screen, and invalidates the
     * necessary areas for redrawing.
     * <p>
     * If the caret is already on the first row, nothing will happen.
     */
    public void moveCaretUp() {
        mCtrlr.moveCaretUp();
    }

    /**
     * Scrolls the caret into view if it is not on screen
     */
    public final boolean focusCaret() {
        return makeCharVisible(mCaretPosition);
    }

    /**
     * @return The column number where charOffset appears on
     */
    protected int getColumn(int charOffset) {
        int row = hDoc.findRowNumber(charOffset);
        TextWarriorException.assertVerbose(row >= 0,
										   "Invalid char offset given to getColumn");
        int firstCharOfRow = hDoc.getRowOffset(row);
        return mCaretCol = charOffset - firstCharOfRow;
    }

    protected boolean caretOnFirstRowOfFile() {
        return (mCaretRow == 0);
    }

    protected boolean caretOnLastRowOfFile() {
        return (mCaretRow == (hDoc.getRowCount() - 1));
    }

    protected boolean caretOnEOF() {
        return (mCaretPosition == (hDoc.length() - 1));
    }

    public final boolean isSelectText() {
        return mCtrlr.isSelectText();
    }

    public final boolean isSelectText2() {
        return mCtrlr.isSelectText2();
    }

    /**
     * Enter or exit select mode.
     * Invalidates necessary areas for repainting.
     *
     * @param mode If true, enter select mode; else exit select mode
     */
    public void selectText(boolean mode) {
        if (mCtrlr.isSelectText() && !mode) {
            invalidateSelectionRows();
            mCtrlr.setSelectText(false);
        } else if (!mCtrlr.isSelectText() && mode) {
            invalidateCaretRow();
            mCtrlr.setSelectText(true);
        }
    }

    public void selectAll() {
        mCtrlr.setSelectionRange(0, hDoc.length() - 1, false, true);
        mClipboardPanel.invalidateContentRect();
    }

    public void setSelection(int beginPosition, int numChars) {
        mCtrlr.setSelectionRange(beginPosition, numChars, true, false);
    }

    public void setSelectionRange(int beginPosition, int numChars) {
        mCtrlr.setSelectionRange(beginPosition, numChars, true, true);
    }

    public boolean inSelectionRange(int charOffset) {
        return mCtrlr.inSelectionRange(charOffset);
    }

    public int getSelectionStart() {
        if (mSelectionAnchor < 0)
            return mCaretPosition;
        return mSelectionAnchor;
    }

    public int getSelectionEnd() {
        if (mSelectionEdge < 0)
            return mCaretPosition;
        return mSelectionEdge;
    }

    public void focusSelectionStart() {
        mCtrlr.focusSelection(true);
    }

    public void focusSelectionEnd() {
        mCtrlr.focusSelection(false);
    }

    //---------------------------------------------------------------------
    //------------------------- Formatting methods ------------------------

    public void cut() {
        if (mSelectionAnchor != mSelectionEdge)
            mCtrlr.cut(mClipboardManager);
    }

    public void copy() {
        if (mSelectionAnchor != mSelectionEdge)
            mCtrlr.copy(mClipboardManager);
        selectText(false);
    }

    public void paste() {
        CharSequence text = mClipboardManager.getText();
        if (text != null)
            mCtrlr.paste(text.toString());
    }

    public void cut(ClipboardManager cb) {
        mCtrlr.cut(cb);
    }

    public void copy(ClipboardManager cb) {
        mCtrlr.copy(cb);
    }

    public void paste(String text) {
        mCtrlr.paste(text);
    }

    public void delete() {
        mCtrlr.selectionDelete();
		mCtrlr.determineSpans();
		//tc
    }

    public void sendPrintableChar(char c) {
        mCtrlr.onPrintableChar(c);
    }

	public void format() {
        mCtrlr.setSelectText(false);
		Tokenizer.getLanguage().getFormatter().format(hDoc, mAutoIndentWidth);
	}

    public void cancelSpanning() {
        mCtrlr.cancelSpanning();
    }

    /**
     * Sets the text to use the new typeface, scrolls the view to display the
     * caret if needed, and invalidates the entire view
     */
    public void setTypeface(Typeface typeface) {
        defTypeface = typeface;
        boldTypeface = Typeface.create(typeface, Typeface.BOLD);
        //italicTypeface = Typeface.create(typeface, Typeface.ITALIC);
        mTextPaint.setTypeface(typeface);
        mLineBrush.setTypeface(typeface);
		chrAdvs.clear();
        mSpaceWidth = (int) mTextPaint.measureText(" ");
		mAlphaWidth = getCharAdvance('a');
		if (hDoc.isWordWrap())
            hDoc.analyzeWordWrap();
        mCtrlr.updateCaretRow();
        if (!makeCharVisible(mCaretPosition))
            invalidate();
    }

    /*public void setItalicTypeface(Typeface typeface) {
     italicTypeface = typeface;
     }*/

    public void setBoldTypeface(Typeface typeface) {
        boldTypeface = typeface;
    }

    public boolean isWordWrap() {
        return hDoc.isWordWrap();
    }

    public void setWordWrap(boolean enable) {
        hDoc.setWordWrap(enable);
        if (enable) {
            xExtent = 0;
            scrollTo(0, 0);
        }
        mCtrlr.updateCaretRow();
        if (!makeCharVisible(mCaretPosition))
            invalidate();
    }

    public float getZoom() {
        return mZoomFactor;
    }

    /**
     * Sets the text size to be factor of the base text size, scrolls the view
     * to display the caret if needed, and invalidates the entire view
     */
    public void setZoom(float factor) {
        if (factor <= 0.5 || factor >= 5 || factor == mZoomFactor)
            return;
        mZoomFactor = factor;
        float newSize = factor * BASE_TEXT_SIZE_PIXELS;
        mTextPaint.setTextSize(newSize);
        mLineBrush.setTextSize(newSize);
        if (hDoc.isWordWrap())
            hDoc.analyzeWordWrap();
        mCtrlr.updateCaretRow();
        mAlphaWidth = (int) mTextPaint.measureText("a");
        //if(!makeCharVisible(mCaretPosition)){
        invalidate();
        //}
    }

    /**
     * Sets the length of a tab character, scrolls the view to display the
     * caret if needed, and invalidates the entire view
     *
     * @param spaceCount The number of spaces a tab represents
     */
    public void setTabSpaces(int spaceCount) {
        if (spaceCount < 0)
            return;
        mTabLength = spaceCount;
        if (hDoc.isWordWrap())
            hDoc.analyzeWordWrap();
        mCtrlr.updateCaretRow();
        if (!makeCharVisible(mCaretPosition))
            invalidate();
    }

	public int getTabSpaces() {
		return mTabLength;
	}

	public void setUseSpace(boolean enable) {
		useSpace = enable;
	}

	public boolean isUseSpace() {
		return useSpace;
	}
    /**
     * Enable/disable auto-indent
     */
    public void setAutoIndent(boolean enable) {
        isAutoIndent = enable;
    }

    public void setAutoComplete(boolean enable) {
        isAutoCompeted = enable;
    }

	public boolean isAutoComplete() {
		return isAutoCompeted;
	}
    /**
     * Enable/disable long-pressing capitalization.
     * When enabled, a long-press on a hardware key capitalizes that letter.
     * When disabled, a long-press on a hardware key bring up the
     * CharacterPickerDialog, if there are alternative characters to choose from.
     */
    public void setLongPressCaps(boolean enable) {
        isLongPressCaps = enable;
    }

    /**
     * Enable/disable highlighting of the current row. The current row is also
     * invalidated
     */
    public void setHighlightCurrentRow(boolean enable) {
        isHighlightRow = enable;
        invalidateCaretRow();
    }

    /**
     * Enable/disable display of visible representations of non-printing
     * characters like spaces, tabs and end of lines
     * Invalidates the view if the enable state changes
     */
    public void setNonPrintingCharVisibility(boolean enable) {
        if (enable ^ isShowNonPrinting) {
            isShowNonPrinting = enable;
            if (hDoc.isWordWrap())
                hDoc.analyzeWordWrap();
            mCtrlr.updateCaretRow();
            if (!makeCharVisible(mCaretPosition))
                invalidate();
        }
    }

    //---------------------------------------------------------------------
    //------------------------- Event handlers ----------------------------
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        //Intercept multiple key presses of printing characters to implement
        //long-press caps, because the IME may consume them and not pass the
        //event to onKeyDown() for long-press caps logic to work.
        //TODO Technically, long-press caps should be implemented in the IME,
        //but is put here for end-user's convenience. Unfortunately this may
        //cause some IMEs to break. Remove this feature in future.
        if (isLongPressCaps
			&& event.getRepeatCount() == 1
			&& event.getAction() == KeyEvent.ACTION_DOWN) {

            char c = KeysInterpreter.keyEventToPrintableChar(event);
            if (Character.isLowerCase(c)
				&& c == Character.toLowerCase(hDoc.charAt(mCaretPosition - 1))) {
                mCtrlr.onPrintableChar(Language.BACKSPACE);
                mCtrlr.onPrintableChar(Character.toUpperCase(c));
                return true;
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Let touch navigation method intercept key event first
        if (mNavMethod.onKeyDown(keyCode, event))
            return true;

        //check if direction or symbol key
        if (KeysInterpreter.isNavigationKey(event)) {
            handleNavigationKey(keyCode, event);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_SYM ||
				   keyCode == KeyCharacterMap.PICKER_DIALOG_INPUT) {
            showCharacterPicker(
				PICKER_SETS.get(KeyCharacterMap.PICKER_DIALOG_INPUT), false);
            return true;
        }

        //check if character is printable
        char c = KeysInterpreter.keyEventToPrintableChar(event);
        if (c == Language.NULL_CHAR)
            return super.onKeyDown(keyCode, event);

        int repeatCount = event.getRepeatCount();
        //handle multiple (held) key presses
        if (repeatCount == 1) {
            if (isLongPressCaps)
                handleLongPressCaps(c);
            else
                handleLongPressDialogDisplay(c);
        } else if (repeatCount == 0
				   || isLongPressCaps && !Character.isLowerCase(c)
				   || !isLongPressCaps && PICKER_SETS.get(c) == null)
            mCtrlr.onPrintableChar(c);

        return true;
    }

    private void handleNavigationKey(int keyCode, KeyEvent event) {
        if (event.isShiftPressed() && !isSelectText()) {
            invalidateCaretRow();
            mCtrlr.setSelectText(true);
        } else if (!event.isShiftPressed() && isSelectText()) {
            invalidateSelectionRows();
            mCtrlr.setSelectText(false);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mCtrlr.moveCaretRight(false);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mCtrlr.moveCaretLeft(false);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mCtrlr.moveCaretDown();
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                mCtrlr.moveCaretUp();
                break;
            default:
                break;
        }
    }

    private void handleLongPressCaps(char c) {
        if (Character.isLowerCase(c)
			&& c == hDoc.charAt(mCaretPosition - 1)) {
            mCtrlr.onPrintableChar(Language.BACKSPACE);
            mCtrlr.onPrintableChar(Character.toUpperCase(c));
        } else
            mCtrlr.onPrintableChar(c);
    }

    //Precondition: If c is alphabetical, the character before the caret is
    //also c, which can be lower- or upper-case
    private void handleLongPressDialogDisplay(char c) {
        //workaround to get the appropriate caps mode to use
        boolean isCaps = Character.isUpperCase(hDoc.charAt(mCaretPosition - 1));
        char base = (isCaps) ? Character.toUpperCase(c) : c;

        String candidates = PICKER_SETS.get(base);
        if (candidates != null) {
            mCtrlr.stopTextComposing();
            showCharacterPicker(candidates, true);
        } else
            mCtrlr.onPrintableChar(c);
    }

    /**
     * @param candidates A string of characters to for the user to choose from
     * @param replace    If true, the character before the caret will be replaced
     *                   with the user-selected char. If false, the user-selected char will
     *                   be inserted at the caret position.
     */
	private boolean mTransBool;
	private CharSequence mTransTx;

    private void showCharacterPicker(String candidates, boolean replace) {
        mTransBool = replace;
        SpannableStringBuilder dummyString = new SpannableStringBuilder();
        Selection.setSelection(dummyString, 0);

        CharacterPickerDialog dialog = new CharacterPickerDialog(getContext(),
																 this, dummyString, candidates, true);
		mTransTx = dummyString;
        dialog.setOnDismissListener(this);
        dialog.show();
    }

	public void onDismiss(DialogInterface dialog) {
		if (mTransTx.length() > 0) {
			if (mTransBool)
				mCtrlr.onPrintableChar(Language.BACKSPACE);
			mCtrlr.onPrintableChar(mTransTx.charAt(0));
		}
	}

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mNavMethod.onKeyUp(keyCode, event))
            return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        // TODO Test on real device
        int deltaX = Math.round(event.getX());
        int deltaY = Math.round(event.getY());
        while (deltaX-- > 0)
            mCtrlr.moveCaretRight(false);
        while (deltaX++ < 0)
            mCtrlr.moveCaretLeft(false);
        while (deltaY-- > 0)
            mCtrlr.moveCaretDown();
        while (deltaY++ < 0)
            mCtrlr.moveCaretUp();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP
                 && isPointInView((int) event.getX(), (int) event.getY()))
        // somehow, the framework does not automatically change the focus
        // to this view when it is touched
            requestFocus();
        mNavMethod.onTouchEvent(event);
        return true;
    }

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && event.getAction() == MotionEvent.ACTION_SCROLL) {
			// if (!isDragging)
			float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            float hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if (vscroll == 0f && hscroll == 0f) {
                return super.onGenericMotionEvent(event);
            }
            int metaState = event.getMetaState();
            // Horizontal scrolling condition convertion
            if (Math.abs(hscroll) > Math.abs(vscroll)) {
                metaState |= KeyEvent.META_SHIFT_ON;
            } else {
                hscroll = vscroll;
            }
            final int scrollFactor = 3 * rowHeight();
            // Text font size zoom
            if ((metaState & KeyEvent.META_CTRL_ON) != 0) {
                return setTextSize((int)(getTextSize() + hscroll * HelperUtils.getDpi(mContext)));
            }
            final int oldScrollX = getScrollX();
            final int oldScrollY = getScrollY();
            // Horizontal scrolling
            if ((metaState & KeyEvent.META_SHIFT_ON) != 0) {
                hscroll *= scrollFactor;
                int newScroll = oldScrollX - (int)hscroll;
                newScroll = Math.max(Math.min(newScroll, getMaxScrollX()), 0);
                if (newScroll != oldScrollX) {
                    super.scrollTo(newScroll, oldScrollY);
                    return true;
                }
            // Regular scrolling
            } else {
                vscroll *= scrollFactor;
                int newScroll = oldScrollY - (int)vscroll;
                newScroll = Math.max(Math.min(newScroll, getMaxScrollY()), 0);
                if (newScroll != oldScrollY) {
                    super.scrollTo(oldScrollX, newScroll);
                    return true;
                }
			}
		}
		return super.onGenericMotionEvent(event);
	}

    private boolean isPointInView(int x, int y) {
        return (x >= 0 && x < getWidth() &&
			y >= 0 && y < getHeight());
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        isCursorVisible = gainFocus;
        invalidateCaretRow();
    }

	@Override
	public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
		return PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_TEXT);
	}

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSigHelpPanel.hide();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE)
            mSigHelpPanel.hide();
    }

    /**
     * Not public to allow access by {@link TouchNavigationMethod}
     */
    public void showIME(boolean show) {
        InputMethodManager im = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show)
            im.showSoftInput(this, 0);
        else
            im.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    /**
     * Some navigation methods use sensors or have states for their widgets.
     * They should be notified of application lifecycle events so they can
     * start/stop sensing and load/store their GUI state.
     */
    void onPause() {
        mNavMethod.onPause();
    }

    void onResume() {
        mNavMethod.onResume();
    }

    void onDestroy() {
        mCtrlr.cancelSpanning();
    }
}
