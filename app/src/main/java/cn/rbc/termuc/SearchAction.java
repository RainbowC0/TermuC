package cn.rbc.termuc;
import android.app.AlertDialog.*;
import android.content.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import cn.rbc.codeeditor.util.*;

public class SearchAction implements
ActionMode.Callback, TextWatcher, TextView.OnEditorActionListener,
DialogInterface.OnClickListener {
	private MainActivity ma;
	private EditText e;
	private int idx = 0;
    private View transV;

	public SearchAction(MainActivity ma) {
		this.ma = ma;
	}

	public void onDestroyActionMode(ActionMode p1) {
		transV = null;
        TextEditor te = ma.getEditor();
        te.setSelection(te.getCaretPosition());
        te.requestFocus();
	}

	public boolean onPrepareActionMode(ActionMode p1, Menu p2) {
		return false;
	}

	public boolean onCreateActionMode(ActionMode p1, Menu p2) {
		View v = View.inflate(ma, R.layout.search_action, null);
		e = v.findViewById(R.id.search_edit);
		TextEditor ed = ma.getEditor();
		String st = ed.getSelectedText();
		if (!st.isEmpty()) {
			e.setText(st);
			idx = ed.getSelectionStart();
		} else
			idx = 0;
		e.addTextChangedListener(this);
		e.setOnEditorActionListener(this);
		p1.setCustomView(v);
		ma.getMenuInflater().inflate(R.menu.search, p2);
		return true;
	}

	public boolean onActionItemClicked(ActionMode p1, MenuItem p2) {
        int id = p2.getItemId();
        if (id!=R.id.menu_replace)
		    search(p2.getItemId());
        else {
            Builder bd = new Builder(ma);
            bd.setTitle(R.string.replace);
            View v = View.inflate(ma, R.layout.replace, null);
            final EditText ed = v.findViewById(R.id.replace_find);
            ed.setText(e.getText());
            transV = v;
            bd.setView(v);
            bd.setPositiveButton(android.R.string.ok, this);
            bd.setNegativeButton(android.R.string.cancel, null);
            bd.create().show();
        }
		return false;
	}

	private void search(int id) {
		int i;
		CharSequence ed = e.getText();
		int len = ed.length();
		if (len==0)
			return;
		TextEditor te = ma.getEditor();
		if (id==R.id.menu_last) {
			i = rindexDoc(ed, idx-1);
		} else {
			i = indexDoc(ed, idx+1);
		}
		if (i<0) {
			HelperUtils.show(Toast.makeText(ma, R.string.find_completed, Toast.LENGTH_SHORT));
			idx = 0;
			te.setSelection(0,0);
		} else {
			te.setSelection(i, len);
			idx = i;
		}
		return;
	}

	public void beforeTextChanged(CharSequence cs, int p1, int p2, int p3) {
	}

	public void onTextChanged(CharSequence cs, int p1, int p2, int p3) {
	}

	public void afterTextChanged(Editable ed) {
		if (ed.length()==0)
			return;
		int i = indexDoc(ed, idx);
		if (i < 0) {
			idx = 0;
		} else {
			idx = i;
			ma.getEditor().setSelection(i, ed.length());
		}
	}

	@Override
	public boolean onEditorAction(TextView p1, int p2, KeyEvent p3) {
		search(R.id.menu_next);
		return true;
	}

	private int indexDoc(CharSequence cs, int idx) {
		int len = cs.length();
        Document doc = ma.getEditor().getText();
		int i, ldp = doc.length()-len;
		D: for (i=idx; i<ldp; i++) {
			for (int k=0;k<len;k++)
				if (cs.charAt(k)!=doc.charAt(i+k))
					continue D;
			return i;
		}
		return -1;
	}

	private int rindexDoc(CharSequence cs, int idx) {
		int len = cs.length();
        Document doc = ma.getEditor().getText();
		D: for (int i=idx; i>=0; i--) {
			for (int k=0;k<len;k++)
				if (cs.charAt(k)!=doc.charAt(i+k))
					continue D;
			return i;
		}
		return -1;
	}

    @Override
    public void onClick(DialogInterface p1, int p2) {
        Document doc = ma.getEditor().getText();
        CharSequence s = ((EditText)transV.findViewById(R.id.replace_find)).getText();
        final int slen = s.length();
        if (slen == 0) return;
        final char[] replacement = ((EditText)transV.findViewById(R.id.replace_with)).getText().toString().toCharArray();
        int i = -1;
        doc.setTyping(false);
        doc.beginBatchEdit();
        long t = System.nanoTime();
        while ((i=TextUtils.indexOf(doc, s, i+1)) != -1) {
            doc.deleteAt(i, slen, t);
            doc.insertBefore(replacement, i, t);
            i += replacement.length - slen;
        }
        doc.endBatchEdit();
        ma.getEditor().mCtrlr.determineSpans();
    }
}
