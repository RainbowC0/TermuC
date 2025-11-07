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
implements OnTextChangeListener, DialogInterface.OnClickListener, Formatter {
	public final static int
	TYPE_C = 1,
	TYPE_CPP = 2,
	TYPE_HEADER = 4,
	TYPE_TXT = 0,
	TYPE_BLOB = 0x80000000,
	TYPE_MASK = 3;
	final static String FL = "f", TP = "t", CS = "c", TS = "s", MK = "m", VS = "v";
	private File fl;
	private TextEditor ed;
	int type = -1;
	private String C;
	private long lastModified;
	private List<Range> changes = new ArrayList<>();

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
                if (tp != TYPE_TXT && "s".equals(Application.completion))
                    Application.getInstance().lsp.didOpen(fl, tp == TYPE_CPP ?"cpp": "c", doc.toString());
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
		lastModified = fl.lastModified();
		return editor;
	}

	public String getC() {
		return C;
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
		boolean wordwrap = editor.isWordWrap();
		Range range = new Range();
		if (wordwrap) {
			range.stl = text.findLineNumber(start);
			range.stc = text.getLineOffset(range.stl);
		} else {
			range.stl = text.findRowNumber(start);
			range.stc = text.getRowOffset(range.stl);
            android.util.Log.i("Lsp", range.stl + "," + start + "-" + range.stc);
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
		changes.clear();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (isVisible())
			refresh();
	}

	private void refresh() {
		long mLast = fl.lastModified();
		if (mLast != lastModified) {
			lastModified = mLast;
			Builder bd = new Builder(getContext());
			bd.setTitle(fl.getName());
			bd.setMessage(getString(R.string.file_modified, fl.getName()));
			bd.setPositiveButton(android.R.string.ok, this);
			bd.setNegativeButton(android.R.string.cancel, null);
			bd.create().show();
		}
	}

	@Override
	public void onClick(DialogInterface diag, int id) {
		try {
			Document cs = load();
            ed.mCtrlr.determineSpans();
            if ("s".equals(Application.completion))
			    Application.getInstance().lsp.didChange(fl, 0, cs.toString());
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
        Writer writer = new FileWriter(fl);
        Document doc = ed.getText();
        doc.markVersion();
        Reader rd = new CharSeqReader(doc);
        char[] buf = new char[1024];
        int i;
        while ((i = rd.read(buf)) > 0) {
            writer.write(buf, 0, i);
        }
        rd.close();
        writer.close();
        lastModified = fl.lastModified();
        ed.setEdited(doc.getMarkedVersion() != doc.getCurrentVersion());
    }

	public Document load() throws IOException {
		Reader fr = new FileReader(fl);
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
		if ((type & TYPE_MASK) != TYPE_TXT && "s".equals(Application.completion))
			doc.setOnTextChangeListener(this);
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
		else if (FileAdapter.isCpp(_it))
			_tp = TYPE_CPP;
		else if (_it.endsWith(".h") || _it.endsWith(".hpp"))
			_tp = TYPE_CPP | TYPE_HEADER;
		else if (!Utils.isBlob(pwd))
			_tp = TYPE_TXT | TYPE_HEADER;
		else
			_tp = TYPE_BLOB;
		return _tp;
	}
}
