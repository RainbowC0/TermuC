package cn.rbc.termuc;
import android.app.*;
import android.net.*;
import android.os.*;
import android.util.*;
import cn.rbc.codeeditor.util.*;
import cn.rbc.codeeditor.view.*;
import cn.rbc.codeeditor.view.autocomplete.*;
import java.io.*;
import java.util.*;
import org.json.*;

import static android.util.JsonToken.*;

import cn.rbc.codeeditor.util.Range;
import cn.rbc.codeeditor.util.Pair;
import android.view.*;

public class MainHandler extends Handler {
	private static final String
    ACTSIG = "activeSignature",
	ADDEDIT = "additionalTextEdits",
    ARGS = "arguments",
	CAPA = "capabilities",
    CHANGES = "changes",
    CODE = "code",
    CMD = "command",
	COMPLE = "completionProvider",
    DAT = "data",
	DG = "diagnostics",
    EDIT = "edit",
	END = "end",
    ID = "id",
	IT = "items",
	KIND = "kind",
	LABEL = "label",
    LEGEND = "legend",
	L = "line",
	MSG = "message",
	NEWTX = "newText",
	PARA = "params",
	RNG = "range",
	RESU = "result",
	SEVE = "severity",
    SEMTOK = "semanticTokensProvider",
    SGNHELP = "signatureHelpProvider",
    SIGS = "signatures",
	TEDIT = "textEdit",
    TITLE = "title",
    TOKTYPE = "tokenTypes",
	TG = "triggerCharacters",
	URI = "uri";
    private MainActivity ma;
    final WeakHashMap<String, Set<String>> cacheData;
    private int oldHash;

	MainHandler(MainActivity ma) {
		super();
		this.ma = ma;
        cacheData = new WeakHashMap<>();
	}

	void updateActivity(MainActivity ma) {
		this.ma = ma;
	}

	@Override
	public void handleMessage(Message msg) {
        Lsp lsp = Application.getInstance().lsp;
		switch (msg.what) {
			case Lsp.INITIALIZE:
				lsp.initialized();
				break;
			case Lsp.ERROR:
                synchronized(this) {
                    cacheData.clear();
                    Tokenizer.getLanguage().setTypes(EditFragment.DEFTYPES);
                    if (ma == null) return;
					FragmentManager fm = ma.getFragmentManager();
					for (int i=ma.getItemCount()-1;i>=0;i--) {
						Fragment f = fm.findFragmentByTag(ma.getTag(i));
						if (f==null) continue;
						TextEditor te = (TextEditor)f.getView();
						Document doc = te.getText();
						doc.setDiag(null);
						doc.setHighlights(null);
                        if (f.isVisible())
						    te.postInvalidateOnAnimation();
					}
				}
				return;
			case Lsp.CLOSE:
				return;
		}
		try {
			JsonReader jr = (JsonReader)msg.obj;
			jr.beginObject();
			Deque<String> stack = new ArrayDeque<>();
			int id = -1, sl = 0, sc = 0, el = 0, ec = 0;
			Object tmp1 = null, tmp2 = null, tmp3 = null;
			while (true) {
				switch (jr.peek()) {
					case NAME:
						String n = jr.nextName();
						switch (n) {
                            // Assuming we parse the id first than the body
                            case ID: id = jr.nextInt(); break;
							case NEWTX:
								n = jr.nextString();
								if (tmp3 instanceof Edit)
									((Edit)tmp3).text = n;
								//else tmp3 = n;
								break;
							case LABEL:
                                n = jr.nextString();
								if (tmp2 instanceof ListItem)
									((ListItem)tmp2).label = n;
                                else if (SIGS.equals(stack.peek()))
                                    ((List)tmp1).add(n);
								break;
							case KIND: {
                                int i = jr.nextInt();
								if (tmp2 instanceof ListItem)
									((ListItem)tmp2).kind = i;
								break;
                            }
							case MSG:
                                n = jr.nextString();
								if (tmp2 instanceof ErrSpan)
									((ErrSpan)tmp2).msg = n;
								break;
							case SEVE: {
                                int i = jr.nextInt();
								if (tmp2 instanceof ErrSpan) {
									((ErrSpan)tmp2).severity = i - 1;
                                }
								break;
                            }
                            case CODE:
                                if (tmp2 instanceof Diagnostic) {
                                    if (jr.peek() == JsonToken.STRING)
                                        ((Diagnostic)tmp2).code = jr.nextString();
                                    else
                                        ((Diagnostic)tmp2).code = jr.nextLong();
                                } else jr.skipValue();
                                break;
							case IT:
							case DG:
                            case SIGS:
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
                                jr.endArray();
                                char[] trigs = sb.toString().toCharArray();
                                if (COMPLE.equals(stack.peek())) {
								    lsp.setCompTrigs(trigs);
                                    break;
                                } else {
                                    jr.close();
                                    lsp.setSigTrigs(trigs);
                                    return;
                                }
                            case TOKTYPE:
                                jr.beginArray();
                                List<Lsp.SemToken> toks = new ArrayList<>();
                                while (jr.hasNext()) {
                                    toks.add(Lsp.semValueOf(jr.nextString().toUpperCase()));
                                }
                                jr.endArray();
                                lsp.setSemToks(toks.toArray(new Lsp.SemToken[toks.size()]));
                                break;
							case RNG:
                                if (SEMTOK.equals(stack.peek())) {
                                    jr.skipValue();
                                    break;
                                }
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
									tmp2 = new ArrayList();
								} else if (jr.peek()==NULL)
									jr.nextNull();
								else
									jr.beginObject();
								stack.push(n);
								break;
							case URI:
                                tmp3 = jr.nextString();
								if (DG.equals(stack.peek())) {
									updateDiag((String)tmp3, (List<ErrSpan>)tmp1);
                                    jr.close();
                                    return;
								}
                                if (id == Lsp.DEFIN) {
                                    jr.close();
                                    performGoto((String)tmp3, sl, sc);
                                    return;
                                }
								break;
							case TEDIT:
								tmp3 = new Edit();
							case COMPLE:
                            case SGNHELP:
                            case SEMTOK:
                            case LEGEND:
							case CAPA:
							case PARA:
                            case EDIT:
								jr.beginObject();
								stack.push(n);
								break;
                            case ACTSIG:
                                sl = jr.nextInt();
                                break;
                            case TITLE:
                                n = jr.nextString();
                                if (tmp3 instanceof Command)
                                    ((Command)tmp3).title = n;
                                break;
                            case CMD:
                                n = jr.nextString();
                                if (tmp3 instanceof Command)
                                    ((Command)tmp3).command = n;
                                break;
                            case ARGS:
                                if (tmp3 instanceof Command) {
                                    ((Command)tmp3).args = nextValue(jr);
                                } else jr.skipValue();
                                break;
                            case CHANGES:
                                jr.beginObject();
                                stack.push(n);
                                tmp2 = null;
                                break;
                            case DAT:
                                if (id == Lsp.SEMTOK) {
                                    Set<String> typs = parseSemTokens(lsp, jr);
                                    jr.close();
                                    cacheData.put(ma.getTag(ma.getSelectedItem()), typs);
                                    sc = typs.hashCode();
                                    if (oldHash != sc) {
                                        Tokenizer.getLanguage().setTypes(typs);
                                        ma.getEditor().postInvalidateOnAnimation();
                                        oldHash = sc;
                                    }
                                    return;
                                }
                                jr.skipValue();
                                break;
							default:
                                if (CHANGES.equals(stack.peek()) && n.startsWith("file://")) {
                                    tmp2 = n;
                                    jr.beginArray();
                                    tmp1 = new ArrayList();
                                    break;
                                }
								jr.skipValue();
								break;
						}
						break;
					case BEGIN_OBJECT:
						jr.beginObject();
						if (!stack.isEmpty()) {
						switch (stack.peek()) {
                            case RESU:
                                if (id == Lsp.CODEACTION) {
                                    tmp3 = new Command();
                                    break;
                                }
							case ADDEDIT:
								tmp3 = new Edit();
								break;
							case IT:
								tmp2 = new ListItem();
								break;
							case DG:
								tmp2 = new Diagnostic();
								break;
                            case CHANGES:
                                tmp3 = new Edit();
                                break;
						}
						}
						break;
					case END_OBJECT:
						jr.endObject();
						if (!stack.isEmpty())
						switch (stack.peek()) {
							case RESU:
                                if (sc == -1) { // signature flag
                                    List<String> ls = (List<String>)tmp1;
                                    TextEditor ed = ma.getEditor();
                                    ed.getAutoCompletePanel().dismiss();
                                    SignatureHelpPanel sp = ed.getSigHelpPanel();
                                    if (ls.size() > sl)
                                        sp.show(ls, sl);
                                    else sp.hide();
                                    jr.close();
                                    return;
                                }
                                if (tmp3 instanceof Command) {
                                    ((List)tmp2).add(tmp3);
                                    break;
                                }
                            case ADDEDIT:
								if (!(tmp3 instanceof Edit))
									break;
								Edit _p = (Edit)tmp3;
								Document te = ma.getEditor().getText();
								_p.start = te.getLineOffset(sl) + sc;
								_p.len = te.getLineOffset(el) + ec - _p.start;
								if (tmp2 instanceof ListItem)
									((ListItem)tmp2).edits.addLast(_p);
								else
									((List)tmp2).add(_p);
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
								((List)tmp1).add(tmp2);
								break;
							case DG:
								if (sc != ec || sl != el) {
									ErrSpan e = (ErrSpan)tmp2;
									e.stl = sl + 1;
									e.stc = sc;
									e.enl = el + 1;
									e.enc = ec;
									((List)tmp1).add(e);
								}
								break;
                            case SIGS:
                                break;
                            case CHANGES:
                                if (tmp3 instanceof Edit) {
                                    if (tmp2 instanceof String) {
                                        Edit p = (Edit)tmp3;
                                        te = ma.getEditor().getText();
                                        p.start = te.getLineOffset(sl) + sc;
                                        p.len = te.getLineOffset(el) + ec - p.start;
                                        ((List)tmp1).add(p);
                                    } else {
                                        lsp.reply(id, "{\"applied\":true}");
                                        jr.close();
                                    }
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
                            case SIGS:
                                sc = -1; // signature flag
							case ADDEDIT:
								stack.pop();
								break;
							case IT: {
                                TextEditor ed = ma.getEditor();
								AutoCompletePanel pan = ed.getAutoCompletePanel();
                                pan.update((ArrayList<ListItem>)tmp1);
                                if (pan.isShow()) {
                                    ed.getSigHelpPanel().hide();
                                }
								return;
                            }
							case DG:
                                if (tmp3 instanceof String) {
                                    jr.close();
                                    updateDiag((String)tmp3, (List<ErrSpan>)tmp1);
                                    return;
                                }
                                break;
							case RESU:
								jr.close();
								List l = (List)tmp2;
								TextEditor te = ma.getEditor();
								Document doc = te.getText();
								if (tmp3 instanceof Edit) {
                                    if (((Edit)l.get(0)).text == null) {
										doc.setHighlights(l);
										te.postInvalidateOnAnimation();
									} else {
                                        applyEdit(te, (List<Edit>)l);
									}
								} else if (tmp3 instanceof Command) {
                                    int tit = ma.getSelectedItem();
                                    EditFragment frag = (EditFragment)ma.getFragmentManager().findFragmentByTag(ma.getTag(tit));
                                    frag.setActions(l);
                                } else if (doc.getHighlights() != null) {
									doc.setHighlights(null);
									te.postInvalidateOnAnimation();
								}
								return;
                            case CHANGES:
                                if (tmp2 instanceof String) {
                                    String t = ((String)tmp2).substring(7);
                                    EditFragment frag = (EditFragment)ma.getFragmentManager().findFragmentByTag(t);
                                    TextEditor ed = (TextEditor)frag.getView();
                                    applyEdit(ed, (List<Edit>)tmp1);
                                    tmp2 = null;
                                }
                                break;
						}
						break;
					case END_DOCUMENT:
						jr.close();
						return;
					default:
						jr.skipValue();
				}
			}
		} catch (Exception j) {
			Log.e("LSP", j.toString(), j);
            //j.printStackTrace(pw);
	    }
	}

    private void updateDiag(String uri, List<ErrSpan> list) {
        String tag = Uri.parse(uri).getPath();
        Fragment f = ma.getFragmentManager().findFragmentByTag(tag);
        if (f==null)
            return;
        TextEditor te = (TextEditor)f.getView();
        Collections.sort(list);
        te.getText().setDiag(list);
        te.postInvalidateOnAnimation();
        return;
    }

    private static String nextValue(JsonReader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int st = 0;
        do {
            switch (rd.peek()) {
                case BEGIN_ARRAY:
                    sb.append('[');
                    rd.beginArray();
                    st++;
                    break;
                case BEGIN_OBJECT:
                    sb.append('{');
                    rd.beginObject();
                    st++;
                    break;
                case END_ARRAY:
                    if (sb.charAt(sb.length()-1) == ',')
                        sb.deleteCharAt(sb.length()-1);
                    sb.append("],");
                    rd.endArray();
                    st--;
                    break;
                case END_OBJECT:
                    if (sb.charAt(sb.length()-1) == ',')
                        sb.deleteCharAt(sb.length()-1);
                    sb.append("},");
                    rd.endObject();
                    st--;
                    break;
                case NUMBER: sb.append(rd.nextLong()); sb.append(','); break;
                case STRING: sb.append(JSONObject.quote(rd.nextString())); sb.append(','); break;
                case NAME: sb.append('"'); sb.append(rd.nextName()); sb.append("\":"); break;
                case BOOLEAN: sb.append(rd.nextBoolean()); sb.append(","); break;
                case END_DOCUMENT: break;
            }
        } while (st > 0);
        int l = sb.length()-1;
        if (sb.charAt(l) == ',')
            sb.deleteCharAt(l);
        return sb.toString();
    }

    private static void applyEdit(TextEditor te, List<Edit> edits) {
        Document doc = te.getText();
        doc.beginBatchEdit();
        long tpl = System.nanoTime();
        int mc = te.getCaretPosition();
        for (int i = edits.size()-1; i>=0; i--) {
            Edit e = edits.get(i);
            doc.deleteAt(e.start, e.len, tpl);
            doc.insertBefore(e.text.toCharArray(), e.start, tpl);
            if (e.start + e.len <= mc)
                mc += e.text.length() - e.len;
            else if (e.start < mc)
                mc = e.start + e.text.length();
        }
        doc.endBatchEdit();
        te.setSelection(mc);
        te.setEdited(doc.getMarkedVersion() != doc.getCurrentVersion());
        te.mCtrlr.determineSpans();
    }

    private Set<String> parseSemTokens(Lsp lsp, JsonReader jr) throws IOException {
        jr.beginArray();
        MainActivity m = ma;
        TextEditor te = m.getEditor();
        Document doc = te.getText();
        List<Pair> spans = doc.getSpans();
        int curr = 0;
        int line = 0, pos = 0;
        final int dlen = doc.length();
        Set<String> tps =new ArraySet<>();
        while (jr.hasNext()) {
            int deltaLine = jr.nextInt();
            line += deltaLine;
            if (deltaLine != 0) pos = doc.getLineOffset(line);
            int deltaStart = jr.nextInt(), len = jr.nextInt(), tok = jr.nextInt();
            jr.nextInt();
            pos += deltaStart;
            int rpos = doc.logicalToRealIndex(pos);
            while (curr < spans.size()) {
                Pair p = spans.get(curr++);
                if (p.first >= rpos) {
                    if (p.first == rpos && (curr == spans.size() || spans.get(curr).first == doc.logicalToRealIndex(pos+len))) {
                        tok = lsp.nativeToken(tok);
                        if (tok != Tokenizer.UNKNOWN) {
                            p.second = tok;
                            if (tok == Tokenizer.TYPE)
                                tps.add(doc.subSequence(pos, Math.min(dlen, pos+len)).toString());
                        }
                        curr++;
                    }
                    break;
                }
            }
        }
        jr.endArray();
        return tps;
    }

    private void performGoto(String uri, int line, int off) {
        uri = uri.substring(7);
        if (uri.startsWith(Utils.PREF) && !uri.startsWith("/home", Utils.PREF.length())) {
            uri = Utils.PREF + "/home/.." + uri.substring(Utils.PREF.length());
        }
        String curr = ma.getTag(ma.getSelectedItem());
        if (!curr.equals(uri)) {
            ma.openFile(new File(uri));
            ma.getFragmentManager().executePendingTransactions();
        }
        TextEditor ed = ma.getEditor();
        Document doc = ed.getText();
        line = doc.getLineOffset(line);
        if (line < 0) return;
        off += line;
        if (off >= doc.length()) return;
        ed.setSelection(off);
        ed.requestFocus();
    }
}
