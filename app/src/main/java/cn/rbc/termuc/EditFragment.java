package cn.rbc.termuc;
import android.app.*;
import android.app.AlertDialog.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import cn.rbc.codeeditor.common.*;
import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.lang.c.*;
import cn.rbc.codeeditor.util.*;
import cn.rbc.codeeditor.view.*;
import java.io.*;
import java.util.*;

import cn.rbc.codeeditor.lang.Formatter;
import cn.rbc.codeeditor.util.Range;

public class EditFragment extends Fragment
implements OnTextChangeListener, DialogInterface.OnClickListener, Formatter, OnCaretScrollListener, ActionMode.Callback {
	public final static int
	TYPE_C = 1,
	TYPE_CPP = 2,
	TYPE_HEADER = 4,
	TYPE_TXT = 0,
	TYPE_BLOB = 0x80000000,
	TYPE_MASK = 3;
    private final static int ACTION_BASE_ID = 0x80000000;
	final static String FL = "f", TP = "t", CS = "c", TS = "s", MK = "m", VS = "v";
	private File fl;
	private TextEditor ed;
	int type = -1;
	private String C;
	private long lastModified;
	private List<Range> changes = new ArrayList<>();
    static final Set<String> DEFTYPES = new ArraySet<String>(0);

	public EditFragment() {
	}

	public EditFragment(File path, int type) {
		fl = path;
		this.type = type;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final MainActivity ma = (MainActivity)getActivity();
		TextEditor editor = ma.newEditor();
		ed = editor;
		if ("d".equals(Application.theme)
            || "s".equals(Application.theme)
            && ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES))
			editor.setColorScheme(ColorSchemeDark.getInstance());
        editor.setPureMode(Application.pure_mode);
		DisplayMetrics dm = getResources().getDisplayMetrics();
		editor.setTypeface(Application.typeface());
		editor.setTextSize((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Application.textsize, dm));
		editor.setWordWrap(Application.wordwrap);
		editor.setShowNonPrinting(Application.whitespace);
		editor.setUseSpace(Application.usespace);
		editor.setTabSpaces(Application.tabsize);
        editor.setSuggestion(Application.suggestion);
        editor.setAutoCaps(Application.auto_caps);
		editor.setLayoutParams(new ViewGroup.LayoutParams(
								   ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editor.setOnEditedListener(ma);
        editor.addCaretListener(this);
        editor.setClipboardCallback(this, ClipboardPanel.CREATE_BEFORE|ClipboardPanel.CREATE_AFTER);
        try {
            Document doc = null;
            if (savedInstanceState != null) {
                String pth = (String)savedInstanceState.getCharSequence(FL);
                fl = new File(pth);
                type = savedInstanceState.getInt(TP, type);
                mVer = savedInstanceState.getInt(VS, mVer);
                doc = Application.getInstance().load(pth);
                editor.setTextSize(savedInstanceState.getInt(TS));
            }
            int tp = type & TYPE_MASK;
            if (tp == TYPE_C) {
                C = "clang";
                TextEditor.setLanguage(CLanguage.getInstance());
            } else if (tp == TYPE_CPP) {
                C = "clang++";
                TextEditor.setLanguage(CppLanguage.getInstance());
            } else {
                C = null;
                TextEditor.setLanguage(LanguageNonProg.getInstance());
            }
            if (doc != null) {
                doc.setMetrics(editor);
                doc.resetRowTable();
                editor.setDocument(doc);
            } else {
                ma.setEditor(editor);
                doc = load();
                if ("s".equals(Application.completion))
                    onOpen();
            } 
        } catch (IOException fnf) {
			fnf.printStackTrace();
			HelperUtils.show(Toast.makeText(ma, getString(R.string.open_failed) + fnf.getMessage(), Toast.LENGTH_SHORT));
		}
		if ((type & TYPE_MASK) != TYPE_TXT) {
			if ("s".equals(Application.completion))
				editor.setFormatter(this);
			editor.setAutoComplete("l".equals(Application.completion));
		}
		lastModified = FileHelper.lastModified(fl);
		return editor;
	}

	public String getC() {
		return C;
	}

	@Override
	public void updateCaret(int caretIndex) {
        if (!"s".equals(Application.completion)) return;
		Document text = ed.getText();
		if (ed.isSelectText2()
            || !(Character.isUnicodeIdentifierPart(text.charAt(caretIndex))
            || caretIndex>0 && Character.isUnicodeIdentifierPart(text.charAt(caretIndex-1)))) {
			text.setHighlights(null);
			return;
		}
		Lsp ls = Application.getInstance().lsp;
		int stl = text.findLineNumber(caretIndex);
		int stc = caretIndex - text.getLineOffset(stl);
		ls.documentHighlight(fl, stl, stc);
	}

	@Override
	public void format(Document txt, int width) {
		int start = ed.getSelectionStart(), end = ed.getSelectionEnd();
		Lsp lsp = Application.getInstance().lsp;
        if (start == end)
			lsp.formatting(fl, width, ed.isUseSpace());
		else {
			Range range = new Range();
			Document text = ed.getText();
			if (ed.isWordWrap()) {
				range.stl = text.findLineNumber(start);
				range.stc = text.getLineOffset(range.stl);
				range.enl = text.findLineNumber(end);
				range.enc = text.getLineOffset(range.enl);
			} else {
				range.stl = text.findRowNumber(start);
				range.stc = text.getRowOffset(range.stl);
				range.enl = text.findRowNumber(end);
				range.enc = text.getRowOffset(range.enl);
			}
			range.stc = start - range.stc;
			range.enc = end - range.enc;
			lsp.rangeFormatting(fl, range, width, ed.isUseSpace());
	    }
	}

	private volatile int mVer = 0;

	public void onChanged(String c, int start, boolean ins, boolean typ) {
        if (!"s".equals(Application.completion))
            return;
		TextEditor editor = ed;
		Document text = editor.getText();
		text.setHighlights(null);
		boolean wordwrap = editor.isWordWrap();
		Range range = new Range();
		if (wordwrap) {
			range.stl = text.findLineNumber(start);
			range.stc = text.getLineOffset(range.stl);
		} else {
			range.stl = text.findRowNumber(start);
			range.stc = text.getRowOffset(range.stl);
		}
		range.stc = start - range.stc;
		if (ins) { // insert
			range.enl = range.stl;
			range.enc = range.stc;
		} else { // delete
			int e = start + c.length();
			c = "";
			if (wordwrap) {
				range.enl = text.findLineNumber(e);
				range.enc = text.getLineOffset(range.enl);
			} else {
				range.enl = text.findRowNumber(e);
				range.enc = text.getRowOffset(range.enl);
			}
			range.enc = e - range.enc;
		}
		range.msg = c;
		changes.add(range);
		Lsp lsp = Application.getInstance().lsp;
		lsp.didChange(fl, ++mVer, changes);
		// when inserting text and typing, call for completion
		if (ins && typ && c.length() == 1) {
            lsp.signatureHelpTry(fl, range.enl, range.enc + 1, c.charAt(0), editor.getSigHelpPanel().isShowing());
			lsp.completionTry(fl, range.enl, range.enc + 1, c.charAt(0));
        }
        lsp.semanticTokensFull(fl);
		changes.clear();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (isVisible())
			refresh();
	}

	private void refresh() {
		long mLast = FileHelper.lastModified(fl);
		if (mLast != lastModified) {
			lastModified = mLast;
			Builder bd = new Builder(getContext());
			bd.setTitle(fl.getName());
			bd.setMessage(getString(R.string.file_modified, fl.getName()));
			bd.setPositiveButton(android.R.string.ok, this);
			bd.setNegativeButton(android.R.string.cancel, null);
			bd.create().show();
		} else if ("s".equals(Application.completion)) {// TODO: delay for throttle
            Set<String> typs = Application.getInstance().hand.cacheData.getOrDefault(fl.toString(), DEFTYPES);
            Language lang = Tokenizer.getLanguage();
            if (typs != lang.getTypes()) {
                lang.setTypes(typs);
                ed.mCtrlr.determineSpans();
            }
        }
	}

    public void onOpen() {
        int tp = type & TYPE_MASK;
        if (tp != TYPE_TXT) {
            Lsp lsp = Application.getInstance().lsp;
            lsp.didOpen(fl, tp == TYPE_C ? "c" : "cpp", ed.getText().toString());
            lsp.semanticTokensFull(fl);
        }
    }

	@Override
	public void onClick(DialogInterface diag, int id) {
		try {
			Document cs = load();
            ed.mCtrlr.determineSpans();
            if ("s".equals(Application.completion)) {
			    Lsp lsp = Application.getInstance().lsp;
                lsp.didChange(fl, 0, cs.toString());
                lsp.semanticTokensFull(fl);
            }
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			MainActivity ma = (MainActivity)getActivity();
			ma.setEditor(ed);
			ma.setFileRunnable((type & TYPE_HEADER) == 0);
			int tp = type & TYPE_MASK;
			if (tp == TYPE_C) {// C
				TextEditor.setLanguage(CLanguage.getInstance());
				C = "clang";
			} else if (tp == TYPE_CPP) {
				TextEditor.setLanguage(CppLanguage.getInstance());
				C = "clang++";
			} else {
				TextEditor.setLanguage(LanguageNonProg.getInstance());
				C = null;
			}
			refresh();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
        String pth = fl.getAbsolutePath();
        Application.getInstance().store(pth, ed.getText());
		outState.putCharSequence(FL, pth);
		outState.putInt(TP, type);
		outState.putInt(TS, (int)ed.getTextSize());
		outState.putInt(VS, mVer);
	}

	public void save() throws IOException {
        Writer writer = new OutputStreamWriter(FileHelper.openOutputStream(fl));
        Document doc = ed.getText();
        doc.markVersion();
        Reader rd = new CharSeqReader(doc);
        char[] buf = new char[1024];
        int i;
        while ((i = rd.read(buf)) != -1) {
            writer.write(buf, 0, i);
        }
        rd.close();
        writer.close();
        lastModified = FileHelper.lastModified(fl);
        ed.setEdited(doc.getMarkedVersion() != doc.getCurrentVersion());
    }

	public Document load() throws IOException {
		Reader fr = new InputStreamReader(FileHelper.openInputStream(fl));
		char[] buf = new char[1024];
        int i;
        Document doc = ed.getText();
        StringBuilder sb = new StringBuilder();
        doc.delete(0, doc.length() - 1, 0L, false);
		while ((i = fr.read(buf)) != -1) {
            sb.append(buf, 0, i);
        }
		fr.close();
        doc.setText(sb);
        doc.resetUndos();
        doc.clearSpans();
        doc.analyzeWordWrap();
		if ((type & TYPE_MASK) != TYPE_TXT && "s".equals(Application.completion)) {
			doc.setOnTextChangeListener(this);
        }
		return doc;
	}

	public File getFile() {
		return fl;
	}

	public static int fileType(File pwd) {
		String _it = pwd.getName();
		int _tp;
		if (_it.endsWith(".c"))
			_tp = TYPE_C;
		else if (FileHelper.isCpp(_it))
			_tp = TYPE_CPP;
		else if (_it.endsWith(".h") || _it.endsWith(".hpp"))
			_tp = TYPE_CPP | TYPE_HEADER;
		else if (!Utils.isBlob(pwd))
			_tp = TYPE_TXT | TYPE_HEADER;
		else
			_tp = TYPE_BLOB;
		return _tp;
	}
/*
    private void semTokensSend() {
        Application app = Application.getInstance();
        Set<String> tk = app.hand.cacheData.getOrDefault(fl.toString(), null);
        if (tk != null) { // delta
            //app.lsp.semanticTokensDelta(fl);
        }
        app.lsp.semanticTokensFull(fl);
    }*/

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu)
    {
        if (menu.size() == 0) {
            TypedArray tv = getContext().getTheme().obtainStyledAttributes(
                new int[]{
                    android.R.attr.actionModeFindDrawable
                });
            menu.add(0, R.id.search, 0, R.string.find).setIcon(tv.getDrawable(0)).setShowAsActionFlags(2);
            tv.recycle();
        } else {
            menu.findItem(ClipboardPanel.ID_PASTE).setShowAsActionFlags(1);
            menu.add(0, R.id.goto_, 0, R.string.goto_).setShowAsActionFlags(1);
        }
        return false;
    }

    private List<Command> tActs;
    void setActions(List<Command> actions) {
        tActs = actions;
        ed.getClipboardPanel().invalidate();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode p1, Menu p2)
    {
        
        FreeScrollingTextField te = ed;
        
        int start = te.getSelectionStart(), end = te.getSelectionEnd();
        p2.removeGroup(1);
        List<Command> acts = tActs;
        int flag = acts == null ? 2 : 1;
        p2.findItem(R.id.search).setVisible(start != end).setShowAsActionFlags(flag);
        p2.findItem(ClipboardPanel.ID_SELECTALL).setShowAsActionFlags(flag);
        p2.findItem(ClipboardPanel.ID_CUT).setShowAsActionFlags(flag);
        p2.findItem(ClipboardPanel.ID_COPY).setShowAsActionFlags(flag);
        if (acts != null) {
            for (int i = 0, l = acts.size(); i<l; i++) {
                int id = i + ACTION_BASE_ID;
                p2.add(1, id, 0, acts.get(i).title).setShowAsActionFlags(2);
            }
            p1.setTag(acts);
            tActs = null;
            return true;
        }
        List<ErrSpan> dg;
        if ((dg = ed.getText().getDiag()) != null && dg.size()>0) {
            // before
            Lsp lsp = Application.getInstance().lsp;
            int stl, stc, enl, enc;
            Document doc = te.getText();
            if (doc.isWordWrap()) {
                stl = doc.findLineNumber(start);
                stc = doc.getLineOffset(stl);
                enl = doc.findLineNumber(end);
                enc = doc.getLineOffset(enl);
            } else {
                stl = doc.findRowNumber(start);
                stc = doc.getRowOffset(stl);
                enl = doc.findRowNumber(end);
                enc = doc.getRowOffset(enl);
            }
            Range rng = new Range();
            rng.stl = stl;
            rng.stc = start -= stc;
            rng.enl = enl;
            rng.enc = end - enc;
            stl += 1;

            int y = dg.size() - 1;
            int x = 0;
            ErrSpan errspan;
            while (x < y) {
                int m = (x + y) >> 1;
                errspan = dg.get(m);
                if (errspan.enl > stl || errspan.enl == stl && errspan.enc >= start)
                    y = m;
                else
                    x = m + 1;
            }
            errspan = dg.get(y);
            if ((errspan.stl < stl || errspan.stl == stl && errspan.stc <= end)
                && (stl < errspan.enl || stl == errspan.enl && start <= errspan.enc)
                && errspan.msg != null) {
                lsp.codeAction(fl, rng, dg.subList(y,y+1));
            }
        }
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode p1, MenuItem p2)
    {
        int id = p2.getItemId();
        switch (id) {
            case R.id.goto_:
                if ("s".equals(Application.completion)) {
                    int pos = ed.getCaretPosition();
                    Document doc = ed.getText();
                    int line = doc.findLineNumber(pos);
                    int off = pos-doc.getLineOffset(line);
                    Application.getInstance().lsp.definition(fl, line, off);
                }
                p1.finish();
                break;
            case R.id.search:
                ed.getText().setHighlights(null);
                MainActivity ma = (MainActivity)getActivity();
                ma.onOptionsItemSelected(p2);
                p1.finish();
                break;
            default:
                id -= ACTION_BASE_ID;
                if (id < 0)
                    break;
                List<Command> cmds = (List<Command>)p1.getTag();
                if (cmds != null && id < cmds.size()) {
                    Application.getInstance().lsp.executeCommand(cmds.get(id));
                    p1.finish();
                }
                break;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode p1)
    {
        p1.setTag(null);
    }
}
