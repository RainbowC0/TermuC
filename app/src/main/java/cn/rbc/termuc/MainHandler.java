package cn.rbc.termuc;
import android.os.*;
import cn.rbc.codeeditor.util.*;
import java.util.*;
import android.app.*;
import android.util.*;
import android.widget.*;
import java.io.*;
import cn.rbc.codeeditor.view.autocomplete.*;
import static android.util.JsonToken.*;
import java.nio.charset.*;
import android.net.*;

public class MainHandler extends Handler implements Comparator<ErrSpan> {
	private MainActivity ma;
	private static final String
	ADDEDIT = "additionalTextEdits",
	CAPA = "capabilities",
	COMPLE = "completionProvider",
	DG = "diagnostics",
	END = "end",
	IT = "items",
	KIND = "kind",
	LABEL = "label",
	L = "line",
	MSG = "message",
	NEWTX = "newText",
	PARA = "params",
	RNG = "range",
	RESU = "result",
	SEVE = "severity",
	TEDIT = "textEdit",
	TG = "triggerCharacters",
	URI = "uri";

	MainHandler(MainActivity ma) {
		super();
		this.ma = ma;
	}

	void updateActivity(MainActivity ma) {
		this.ma = ma;
	}

	@Override
	public void handleMessage(Message msg) {
		switch (msg.what) {
			case Lsp.INITIALIZE:
				ma.lsp.initialized();
				break;
			case Lsp.ERROR:
				synchronized(this) {
					FragmentManager fm = ma.getFragmentManager();
					for (int i=ma.getActionBar().getNavigationItemCount()-1;i>=0;i--) {
						Fragment f = fm.findFragmentByTag(ma.getTag(i));
						if (f==null) continue;
						TextEditor te = (TextEditor)f.getView();
						te.getText().setDiag(null);
						te.invalidate();
					}
				}
				return;
			case Lsp.UNLOCK:
				ma.lsp.lock.unlock();
			case Lsp.CLOSE:
				return;
		}
		try {
			JsonReader jr = (JsonReader)msg.obj;
			jr.beginObject();
			Deque<String> stack = new ArrayDeque<>();
			int sl = 0, sc = 0, el = 0, ec = 0;
			Object tmp1 = null, tmp2 = null, tmp3 = null;
			while (true) {
				switch (jr.peek()) {
					case NAME:
						String n = jr.nextName();
						switch (n) {
							case NEWTX:
								n = jr.nextString();
								if (tmp3 instanceof Edit)
									((Edit)tmp3).text = n;
								//else tmp3 = n;
								break;
							case LABEL:
								if (tmp2 instanceof ListItem)
									((ListItem)tmp2).label = jr.nextString();
								break;
							case KIND:
								if (tmp2 instanceof ListItem)
									((ListItem)tmp2).kind = jr.nextInt();
								break;
							case MSG:
								if (tmp2 instanceof ErrSpan)
									((ErrSpan)tmp2).msg = jr.nextString();
								break;
							case SEVE:
								if (tmp2 instanceof ErrSpan)
									((ErrSpan)tmp2).severity = jr.nextInt() - 1;
								break;
							case IT:
							case DG:
								tmp1 = new ArrayList();
							case ADDEDIT:
								jr.beginArray();
								stack.push(n);
								break;
							case TG:
								jr.beginArray();
								StringBuilder sb = new StringBuilder();
								while (jr.hasNext())
									sb.append(jr.nextString());
								jr.close();
								ma.lsp.setCompTrigs(sb.toString().toCharArray());
								// what == Lsp.INITIALIZE
								FragmentManager fm = ma.getFragmentManager();
								for (int i=ma.getActionBar().getNavigationItemCount()-1;i>=0;i--) {
									EditFragment ef = (EditFragment)fm.findFragmentByTag(ma.getTag(i));
									int tp = ef.type&EditFragment.TYPE_MASK;
									if (tp != EditFragment.TYPE_TXT)
										ma.lsp.didOpen(ef.getFile(), tp==EditFragment.TYPE_CPP?"cpp":"c", ((TextEditor)ef.getView()).getText().toString());
								}
								return;
							case RNG:
								jr.beginObject();
								while (jr.hasNext()) {
									String tp = jr.nextName();
									jr.beginObject();
									if (END.equals(tp))
										while (jr.hasNext())
											if (L.equals(jr.nextName()))
												el = jr.nextInt();
											else
												ec = jr.nextInt();
									else
										while (jr.hasNext())
											if (L.equals(jr.nextName()))
												sl = jr.nextInt();
											else
												sc = jr.nextInt();
									jr.endObject();
								}
								jr.endObject();
								break;
							case RESU:
								if (jr.peek()==BEGIN_ARRAY) {
									jr.beginArray();
									tmp2 = new ArrayList<Edit>();
								} else if (jr.peek()==NULL)
									jr.nextNull();
								else
									jr.beginObject();
								stack.push(n);
								break;
							case URI:
								if (DG.equals(stack.peek())) {
									String tag = Uri.parse(jr.nextString()).getPath();
									jr.close();
									Fragment f = ma.getFragmentManager().findFragmentByTag(tag);
									if (f==null)
										return;
									TextEditor te = (TextEditor)f.getView();
									ArrayList<ErrSpan> a = (ArrayList<ErrSpan>)tmp1;
									Collections.sort(a, this);
									te.getText().setDiag(a);
									te.invalidate();
									return;
								}
								break;
							case TEDIT:
								tmp3 = new Edit();
							case COMPLE:
							case CAPA:
							case PARA:
								jr.beginObject();
								stack.push(n);
								break;
							default:
								jr.skipValue();
								break;
						}
						break;
					case BEGIN_OBJECT:
						jr.beginObject();
						if (!stack.isEmpty()) {
						switch (stack.peek()) {
							case ADDEDIT:
							case RESU:
								tmp3 = new Edit();
								break;
							case IT:
								tmp2 = new ListItem();
								break;
							case DG:
								tmp2 = new ErrSpan();
								break;
						}
						}
						break;
					case END_OBJECT:
						jr.endObject();
						if (!stack.isEmpty())
						switch (stack.peek()) {
							case ADDEDIT:
							case RESU:
								if (!(tmp3 instanceof Edit))
									break;
								Edit _p = (Edit)tmp3;
								Document te = ma.getEditor().getText();
								_p.start = te.getLineOffset(sl) + sc;
								_p.len = te.getLineOffset(el) + ec - _p.start;
								if (tmp2 instanceof ListItem)
									((ListItem)tmp2).edits.addLast(_p);
								else
									((List)tmp2).add(0, _p);
								break;
							case TEDIT:
								_p = (Edit)tmp3;
								te = ma.getEditor().getText();
								_p.start = te.getLineOffset(sl) + sc;
								_p.len = te.getLineOffset(el) + ec - _p.start;
								((ListItem)tmp2).edits.addFirst(_p);
								stack.pop();
								break;
							case IT:
								((ArrayList)tmp1).add(tmp2);
								break;
							case DG:
								if (sc != ec || sl != el) {
									ErrSpan e = (ErrSpan)tmp2;
									e.stl = sl + 1;
									e.stc = sc;
									e.enl = el + 1;
									e.enc = ec;
									((ArrayList<ErrSpan>)tmp1).add(e);
								}
								break;
							default:
								stack.pop();
						}
						break;
					case END_ARRAY:
						jr.endArray();
						if (!stack.isEmpty())
						switch (stack.peek()) {
							case ADDEDIT:
								stack.pop();
								break;
							case IT:
								ma.getEditor().getAutoCompletePanel().update((ArrayList<ListItem>)tmp1);
								return;
							//case DG:
							case RESU:
								jr.close();
								TextEditor te = ma.getEditor();
								Document doc = te.getText();
								doc.beginBatchEdit();
								long tpl = System.nanoTime();
								int mc = te.getCaretPosition();
								for (Edit e:(List<Edit>)tmp2) {
									doc.deleteAt(e.start, e.len, tpl);
									doc.insertBefore(e.text.toCharArray(), e.start, tpl);
									if (e.start + e.len <= mc)
										mc += e.text.length() - e.len;
									else if (e.start < mc)
										mc = e.start + e.text.length();
								}
								doc.endBatchEdit();
								te.moveCaret(mc);
								te.mCtrlr.determineSpans();
								return;
						}
						break;
					case END_DOCUMENT:
						jr.close();
						return;
					default:
						jr.skipValue();
				}
			}
		} catch (IOException j) {
			Log.e("LSP", j.getMessage());
		}
	}

	public int compare(ErrSpan p1, ErrSpan p2) {
		return p1.stl - p2.stl;
	}
}
