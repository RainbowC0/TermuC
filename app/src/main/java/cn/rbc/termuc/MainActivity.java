package cn.rbc.termuc;

import android.app.*;
import android.app.AlertDialog.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.database.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.util.*;
import android.view.*;
import android.view.ViewTreeObserver.*;
import android.view.inputmethod.*;
import android.widget.*;
import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.xmlpull.v1.*;

import static android.Manifest.permission.*;

public class MainActivity extends Activity implements
ActionBar.OnNavigationListener, OnGlobalLayoutListener,
AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
DialogInterface.OnClickListener, MenuItem.OnMenuItemClickListener,
TextEditor.OnEditedListener, View.OnClickListener, Runnable {

	public final static int SETTING = 0, ACCESS_FILE = 1, SHOW_FLOATING = 2, REQ_FOLDER = 3;
	public final static String PWD = "p", SHOWLIST = "l", FILES = "o", TESTAPP = "t", INITAPP = "i";
	private HeaderAdapter hda;
	private FileAdapter adp;
	private EditFragment lastFrag = null;
	private boolean byhand = true, keyboardShown = false, transZ;
    private View keys, showlist, transV;
    private File pwd, prj, root;
    private TextView pwdpth, msgEmpty, transTxV;
    private LinearLayout subc;
    private TextEditor codeEditor;
	private Menu appMenu;
	private SearchAction mSearchAction;
	private String transStr;
	private Dialog transDlg;
    private AttributeSet editAttr;
    private DebugPanel panel;
    private DataSetObserver obs;
    private int reqCode;

	private void envInit(SharedPreferences pref) {
		pwd = new File(pref.getString(PWD, Utils.ROOT.getPath()));
		Application app = Application.getInstance();
		if (app.treeUri != null && FileHelper.isTermuxFile(pwd)) {
			root = new File(DocumentsContract.getTreeDocumentId(app.treeUri));
		} else {
			root = Utils.ROOT;
		}
		for (File f = pwd; !f.equals(root); f = f.getParentFile()) {
			if (FileHelper.isFile(new File(f, Project.PROJ))) {
				prj = f;
				break;
			}
		}
		if (app.lsp == null) {
            app.lsp = new Lsp();
			app.hand = new MainHandler(this);
		} else
			app.hand.updateActivity(this);
	}

	private void showFrag(Fragment frag) {
		if (frag == lastFrag)
			return;
		FragmentTransaction mTans = getFragmentManager().beginTransaction();
		if (lastFrag != null)
			mTans.hide(lastFrag);
		mTans.show(frag).commit();
		lastFrag = (EditFragment)frag;
	}

	public boolean onNavigationItemSelected(int p1, long p2) {
		if (byhand)
			showFrag(getFragmentManager().findFragmentByTag(hda.getItem(p1)));
		return true;
	}

	String getTag(int idx) {
		return hda.getItem(idx);
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
		SharedPreferences pref = getPreferences(MODE_PRIVATE);
		envInit(pref);
		Utils.setNightMode(this, Application.theme);
        Configuration conf = getResources().getConfiguration();
        if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        super.onCreate(savedInstanceState);
		hda = new HeaderAdapter(new ContextThemeWrapper(getBaseContext(), android.R.style.Theme_Holo), R.layout.header_item);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Resources.Theme rt = getResources().newTheme();
            rt.applyStyle(android.R.style.Theme_Holo, true);
            hda.setDropDownViewTheme(rt);
        }
        hda.setOnCloseListener(this);
		getActionBar().setListNavigationCallbacks(hda, this);
		hda.registerDataSetObserver(obs = new DataSetObserver() {
				private int lastCount = 0;
				public void onChanged() {
					int count = hda.getCount();
					if (count == 0) {
						ActionBar ab = getActionBar();
						ab.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
						ab.setDisplayShowTitleEnabled(true);
						msgEmpty.setVisibility(View.VISIBLE);
						showFullMenu(false);
					} else if (lastCount == 0) {
						ActionBar ab = getActionBar();
						ab.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
						ab.setDisplayShowTitleEnabled(false);
						msgEmpty.setVisibility(View.GONE);
						showFullMenu(true);
					}
					lastCount = count;
				}
			});
		setContentView(R.layout.activity_main);
		showlist = findViewById(R.id.show_list);
		keys = findViewById(R.id.keys);
		subc = findViewById(R.id.subcontainer);
		ListView l = findViewById(R.id.file_list);
		View hd = View.inflate(this, R.layout.list_header, null);
		pwdpth = hd.findViewById(R.id.pwd);
		msgEmpty = findViewById(R.id.msg_empty);
		l.addHeaderView(hd);
		adp = new FileAdapter(this, pwd);
		l.setAdapter(adp);
		l.setOnItemClickListener(this);
		l.setOnItemLongClickListener(this);
        panel = new DebugPanel(this);
		getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(this);
		mSearchAction = new SearchAction(this);
		final int sdk = android.os.Build.VERSION.SDK_INT;
		String[] s = null;
		if (sdk >= android.os.Build.VERSION_CODES.R) {
			if (!Environment.isExternalStorageManager()) {
				Intent it = new Intent();
				it.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				it.setData(Uri.parse("package:" + getPackageName()));
				startActivityForResult(it, ACCESS_FILE);
			}
			s = new String[]{Utils.PERM_EXEC};
		} else if (sdk >= android.os.Build.VERSION_CODES.M)
			s = new String[]{
					Utils.PERM_EXEC,
					READ_EXTERNAL_STORAGE,
					WRITE_EXTERNAL_STORAGE,
				};
		if (s != null)
			requestPermissions(s, PackageManager.PERMISSION_GRANTED);
		if (pref.getBoolean(INITAPP, true))
			Utils.initBack(this, false);
        if (pref.getBoolean(TESTAPP, true))
			Utils.testApp(this, false);
    }

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PackageManager.PERMISSION_GRANTED) {
			for (int i:grantResults)
				if (i!=PackageManager.PERMISSION_GRANTED) {
					toast(getText(R.string.request_failed));
					break;
				}
			if (grantResults.length>1 && grantResults[1]==PackageManager.PERMISSION_GRANTED)
				refresh();
		}
	}

    private void refresh() {
		pwdpth.setText(pwd.getPath());
		adp.setPath(pwd);
        adp.notifyDataSetChanged();
    }

	@Override
	public void onGlobalLayout() {
		try {
			InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
			Method declaredMethod = inputMethodManager.getClass().getDeclaredMethod("getInputMethodWindowVisibleHeight");
			declaredMethod.setAccessible(true);
			boolean b = ((Integer)declaredMethod.invoke(inputMethodManager, new Object[0])).intValue() > 0;
			if (keyboardShown != b && (transTxV == null || !transTxV.isAttachedToWindow())) {
				keyboardShown = b;
				int showas, slvis;
				if (b) {
					slvis = View.GONE;
					subc.setVisibility(slvis);
					showas = MenuItem.SHOW_AS_ACTION_IF_ROOM;
				} else {
					slvis = View.VISIBLE;
					showas = MenuItem.SHOW_AS_ACTION_ALWAYS;
				}
				showlist.setVisibility(slvis);
				keys.setVisibility(View.VISIBLE ^ View.GONE ^ slvis);
				Menu menu = appMenu;
				menu.findItem(R.id.redo).setShowAsAction(
					MenuItem.SHOW_AS_ACTION_ALWAYS
					^ MenuItem.SHOW_AS_ACTION_IF_ROOM
					^ showas);
				menu.findItem(R.id.run).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void onItemClick(AdapterView<?> av, View v, int i, long n) {
		String _it = adp.getItem(i - 1).name;
		if ("..".equals(_it)) {
			if (FileHelper.isFile(new File(pwd, Project.PROJ)))
				prj = null;
			pwd = pwd.getParentFile();
		} else {
			File f = new File(pwd, _it);
			if (FileHelper.isFile(f)) {
				transStr = openFile(f) ? f.getAbsolutePath() : null;
				if (Project.rootPath == null && prj!=null)
					openProject();
				return;
			}
			File trj = new File(f, Project.PROJ);
			if (FileHelper.isFile(trj))
				prj = f;
			pwd = f;
		}
		refresh();
	}

	public boolean onItemLongClick(AdapterView<?> av, View v, final int i, long l) {
		if (i == 0 || "..".equals(adp.getItem(i - 1).name))
			return false;
		PopupMenu pm = new PopupMenu(MainActivity.this, v);
		Menu _m = pm.getMenu();
		transStr = adp.getItem(i - 1).name;
		_m.add(Menu.NONE, R.id.delete, Menu.NONE, cn.rbc.codeeditor.R.string.delete).setOnMenuItemClickListener(this);
        _m.add(Menu.NONE, R.id.rename, Menu.NONE, R.string.rename).setOnMenuItemClickListener(this);
		pm.show();
		return true;
	}

	@Override
	public boolean onMenuItemClick(MenuItem p1) {
		int id = p1.getItemId();
		if (id == R.id.run || id == R.id.debug) {
			try {
				if (lastFrag!=null) {
					lastFrag.save();
				}
				Project.reload();
				StringBuilder sb;
				String pth;
				File f = lastFrag==null?null:lastFrag.getFile();
				if (Project.rootPath == null) {
					sb = new StringBuilder("x=$TMPDIR/m;");
					sb.append(lastFrag.getC());
					sb.append(" \"");
					sb.append(Utils.escape(f.getAbsolutePath()));
					sb.append("\" ");
					sb.append(Application.cflags);
					if (!Application.cflags.contains("-g"))
						sb.append(" -g");
					sb.append(" -o $x && ");
					pth = pwd.getAbsolutePath();
				} else {
					sb = Project.buildEnvironment(f);
					sb.append("x=$TMPDIR/termuc;find $o -maxdepth 1 -type f \\( -iname '*.so' -o ! -name '*.*' \\) -exec install -D -t $x {} \\;;x=(");
					sb.append(Project.runCmd);
					sb.append(") && ");
					pth = Project.rootPath;
				}
				if (id == R.id.run)
					sb.append("${x[@]}");
				else {
					sb.append("/system/bin/toybox nc -l -s 127.0.0.1 -p 48456 nice -n -20 gdb -q -i=mi -ret -tty `tty` --args ${x[@]} 2>/dev/null");
					String fn = f.getName();
					Document dc = codeEditor.getText();
					int l = dc.getMarksCount();
					for (int i=0;i < l;i++) {
                        int lineno = dc.getMark(i);
                        panel.addBkpt(fn, lineno);
                    }
				}
                sb.append(" && read -n1 -rsp \"\nPress any key to exit...\"");
				Utils.run(this, Utils.PREF.concat("/usr/bin/bash"), new String[]{"-c",
							  sb.toString()},
						  pth, false);
                if (id == R.id.debug) {
					panel.connect();
                }
			} catch (android.util.MalformedJsonException je) {
				toast(getString(R.string.parse_failed));
			} catch (IOException ioe) {
				Log.e("LSP", ioe.toString());
			}
			return true;
		} else if (id==R.id.build || id==R.id.compile) {
			try {
				File f;
				if (lastFrag!=null) {
					lastFrag.save();
					f = lastFrag.getFile();
				} else f = null;
				Project.reload();
				File out = new File(Project.rootPath, Project.outputDir);
				if (!out.exists()) {
					out.mkdir();
					refresh();
				}
				StringBuilder sb = Project.buildEnvironment(f);
				String cmd = id==R.id.build?Project.buildCmd:Project.compileCmd;
				sb.append(cmd);
				Utils.run(this, Utils.PREF.concat("/usr/bin/bash"), new String[]{
					"-c", sb.toString()
				}, Project.rootPath, false);
			} catch (android.util.MalformedJsonException je) {
				toast(getString(R.string.parse_failed));
			} catch(IOException ioe) {
				Log.e("LSP", ioe.getMessage());
			}
			return true;
		} else if (id == R.id.exstorage) {
			pwd = root = Utils.ROOT;
			prj = null;
			refresh();
			return true;
		} else if (id == R.id.tmxstorage) {
			Uri u = Application.getInstance().treeUri;
			if (u == null) {
				Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				startActivityForResult(it, REQ_FOLDER);
			} else {
				pwd = root = new File(DocumentsContract.getTreeDocumentId(u));
				prj = null;
				refresh();
			}
			return true;
		}
		Builder bd = new Builder(this);
		if (id == R.id.delete) {
			bd.setTitle(cn.rbc.codeeditor.R.string.delete);
			bd.setMessage(getString(R.string.confirm_delete, transStr));
			bd.setPositiveButton(android.R.string.ok, this);
			transZ = false;
		} else if (id == R.id.rename) {
            bd.setTitle(R.string.rename);
            EditText ed = new EditText(this);
            ed.setSingleLine();
            ed.setMaxLines(1);
            bd.setView(ed);
            transTxV = ed;
            ed.setLayoutParams(
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            ed.setId(id);
            ed.setText(transStr);
            ed.selectAll();
            bd.setPositiveButton(android.R.string.ok, this);
            transZ = false;
        } else {
			bd.setTitle(R.string.new_);
            if (id == R.id.newfile) {
			    EditText ed = new EditText(this);
                ed.setSingleLine();
                ed.setMaxLines(1);
			    bd.setView(ed);
			    transTxV = ed;
			    ed.setLayoutParams(
				new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.FILL_PARENT,
					ViewGroup.LayoutParams.FILL_PARENT
				));
				ed.setId(R.id.newfile);
				ed.setHint(R.string.hint_filename);
				bd.setPositiveButton(R.string.file, onc);
				bd.setNeutralButton(R.string.folder, onc);
			} else {
                View v = View.inflate(this, R.layout.new_project, null);
                bd.setView(v);
                transV = v;
                transTxV = v.findViewById(R.id.newprj);
				bd.setPositiveButton(android.R.string.ok, onc);
			}
		}
		bd.setNegativeButton(android.R.string.cancel, null);
		bd.create().show();
		return true;
	}

	void setFileRunnable(boolean exec) {
		if (Project.rootPath == null||exec)
			appMenu.findItem(R.id.run).setVisible(exec);
	}

	boolean openFile(File f) {
		String _it = f.getAbsolutePath();
		int _i;
		if (getFragmentManager().findFragmentByTag(_it) != null) {
			for (_i = hda.getCount() - 1; _i >= 0 && !_it.equals(hda.getItem(_i)); _i--);
		} else if ((_i = EditFragment.fileType(f)) >= 0) {
            Application app = Application.getInstance();
            Lsp lsp;
            EditFragment ef = new EditFragment(f, _i);
			if ("s".equals(Application.completion) && ef.hasLsp() && (lsp=app.lsp).isEnded()) {
				lsp.end();
				lsp.start(this, app.hand);
				lsp.initialize(Project.rootPath);
			}
			FragmentTransaction mts = getFragmentManager().beginTransaction();
			mts.add(R.id.editFrag, ef, _it);
			if (lastFrag != null)
				mts.hide(lastFrag);
			mts.show(ef).commit();
			lastFrag = ef;
			hda.add(_it);
			byhand = false;
			setFileRunnable(EditFragment.isExecutable(_i));
			_i = hda.getCount() - 1;
		}
		boolean b = _i>=0;
		if (b) {
			getActionBar().setSelectedNavigationItem(_i);
			byhand = b;
		}
		return b;
	}

	private void openProject() {
		Builder bd = new Builder(this);
		bd.setTitle(R.string.open_prj);
		bd.setMessage(getString(R.string.confirm_open, prj));
		bd.setPositiveButton(android.R.string.ok, this);
		bd.setNegativeButton(android.R.string.cancel, null);
		transZ = true;
		bd.create().show();
	}

	private void openProjFiles(List<String> opens) {
		String pth = transStr;
		FragmentManager fm = getFragmentManager();
		FragmentTransaction fts = fm.beginTransaction();
        Lsp lsp = Application.getInstance().lsp;
		for (int i=0;i < hda.getCount();) {
			String str = hda.getItem(i);
			if (str.equals(pth)) {
				i++;
			} else {
				hda.remove(str);
				fts.remove(fm.findFragmentByTag(str));
				lsp.didClose(new File(str));
			}
		}
		boolean s = "s".equals(Application.completion);
		if (s && lsp.isEnded()) {
			lsp.start(this, Application.getInstance().hand);
			lsp.initialize(Project.rootPath);
		}
		int tp;
		EditFragment ef = null;
		for (String i:opens) {
			if (i.equals(pth)) {
                if (s) lastFrag.onOpen();
				continue;
			}
			File f = new File(i);
			if (FileHelper.isFile(f) && (tp = EditFragment.fileType(f)) >= 0) {
				ef = new EditFragment(f, tp);
				fts.add(R.id.editFrag, ef, i);
				fts.hide(ef);
				hda.add(i);
			}
		}
		if (ef!=null) {
			byhand = false;
			getActionBar().setSelectedNavigationItem(hda.getCount()-1);
			if (ef!=lastFrag && lastFrag!=null) {
				fts.hide(lastFrag);
				lastFrag = ef;
			}
			fts.show(ef);
			byhand = true;
		}
		fts.commit();
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.run:
				boolean nbreaks = lastFrag==null || codeEditor.getText().getMarksCount()==0;
				boolean nproj = Project.rootPath == null;
				if (nproj && nbreaks)
					onMenuItemClick(menuItem);
				else {
					View de = getWindow().getDecorView();
					View r = de.findViewById(R.id.run);
					if (r == null)
						r = de.findViewById(R.id.redo);
					PopupMenu pm = new PopupMenu(this, r);
					Menu m = pm.getMenu();
					if (!nproj) {
						m.add(0, R.id.build, 0, R.string.build).setOnMenuItemClickListener(this);
						if (lastFrag!=null && !lastFrag.isText())
							m.add(0, R.id.compile, 0, R.string.compile).setOnMenuItemClickListener(this);
					}
					m.add(0, R.id.run, 0, R.string.run).setOnMenuItemClickListener(this);
					if (!nbreaks)
						m.add(0, R.id.debug, 0, R.string.debug).setOnMenuItemClickListener(this);
					pm.show();
				}
				break;
			case R.id.undo:
                codeEditor.undo();
                break;
            case R.id.redo:
                codeEditor.redo();
                break;
            case R.id.save:
				try {
					lastFrag.save();
					toast(getText(R.string.saved));
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case R.id.search:
				mSearchAction.show();
				break;
			case R.id.close:
				closePage(getActionBar().getSelectedNavigationIndex());
				break;
			case R.id.prj_attr:
				openFile(new File(Project.rootPath, Project.PROJ));
				break;
			case R.id.prj_close:
				Project.save(hda);
				Project.close();
				FragmentManager fm = getFragmentManager();
				FragmentTransaction trans = fm.beginTransaction();
                Lsp lsp = Application.getInstance().lsp;
				while (!hda.isEmpty()) {
					String s = hda.getItem(0);
					hda.remove(s);
					trans.remove(fm.findFragmentByTag(s));
					lsp.didClose(new File(s));
				}
				trans.commit();
				lastFrag = null;
				appMenu.findItem(R.id.prj).setEnabled(false);
				setFileRunnable(false);
				lsp.end();
				break;
			case R.id.settings:
				Intent it = new Intent(this, SettingsActivity.class);
				startActivityForResult(it, SETTING);
				break;
        }
        return true;
    }

    @Override
    public void onClick(View p1) {
        closePage((Integer)p1.getTag());
    }

	public void popStorage(View v) {
		PopupMenu pm = new PopupMenu(this, ((View)v.getParent()));
		Menu m = pm.getMenu();
		m.add(Menu.NONE, R.id.exstorage, Menu.NONE, R.string.external_storage).setOnMenuItemClickListener(this);
		m.add(Menu.NONE, R.id.tmxstorage, Menu.NONE, R.string.termux_storage).setOnMenuItemClickListener(this);
		pm.show();
	}

    private void closePage(int pos) {
        String _t = hda.getItem(pos);
        hda.remove(_t);
        FragmentManager fm = getFragmentManager();
        FragmentTransaction mTans = fm.beginTransaction();
        mTans.remove(fm.findFragmentByTag(_t));
        int cnt = hda.getCount();
        if (hda.getCount() > 0) {
            if (cnt == pos)
                pos--;
            lastFrag = (EditFragment)fm.findFragmentByTag(hda.getItem(pos));
            mTans.show(lastFrag);
        } else lastFrag = null;
        mTans.commit();
        Application app = Application.getInstance();
        app.lsp.didClose(new File(_t));
        app.hand.cacheData.remove(_t);
        app.load(_t);
    }

    public void inputKey(View view) {
        String charSequence = ((TextView) view).getText().toString();
        if ("⇥".equals(charSequence)) {
			codeEditor.sendPrintableChar(Language.TAB);
            return;
		}
        codeEditor.paste(charSequence);
    }

    @Override
    public boolean onKeyUp(int i, KeyEvent keyEvent) {
        if (i != keyEvent.KEYCODE_BACK || subc.getVisibility() != View.VISIBLE) {
            return super.onKeyUp(i, keyEvent);
        }
        subc.setVisibility(View.GONE);
        return false;
    }

    @Override
    public void onEdited(boolean edited) {
        final int idx = getActionBar().getSelectedNavigationIndex();
        if (hda.getEdit(idx) != edited) {
            hda.setEdit(idx, edited);
            hda.notifyDataSetChanged();
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        pwd = new File(bundle.getString(PWD));
		int i = 0, j = 0, _tp = 0;
		List<String> files = bundle.getStringArrayList(FILES);
		if (files != null) {
			FragmentManager fm = getFragmentManager();
            hda.load(bundle);
			for (String s:bundle.getStringArrayList(FILES)) {
				hda.add(s);
				EditFragment f = (EditFragment)fm.findFragmentByTag(s);
				if (!f.isHidden()) {
					j = i;
					_tp = f.type;
					codeEditor = (TextEditor)f.getView();
				}
				i++;
			}
			if (!hda.isEmpty()) {
                hda.load(bundle);
				byhand = false;
				getActionBar().setSelectedNavigationItem(j);
				byhand = true;
				setFileRunnable(EditFragment.isExecutable(_tp));
			}
		}
		if (subc != null)
			subc.setVisibility(bundle.getInt(SHOWLIST));
		appMenu.findItem(R.id.prj).setEnabled(Project.rootPath!=null);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putString(PWD, pwd.getPath());
		bundle.putInt(SHOWLIST, subc.getVisibility());
		ArrayList<String> al = new ArrayList<>(hda.getCount());
		for (String s:hda)
			al.add(s);
		bundle.putStringArrayList(FILES, al);
        hda.store(bundle);
        super.onSaveInstanceState(bundle);
    }

	@Override
	public void onClick(DialogInterface di, int id) {
		if (transZ) {
			File c = new File(prj, Project.PROJ);
			if (FileHelper.isFile(c)) {
				try {
					List<String> opens = new ArrayList<>();
					Project.load(c, opens);
					appMenu.findItem(R.id.prj).setEnabled(true);
					openProjFiles(opens);
                    setFileRunnable(true);
					return;
				} catch (IOException e) {
                    e.printStackTrace();
				}
			}
			toast(getString(R.string.open_failed));
			return;
		}
        TextView tv = transTxV;
        if (tv != null) {
            transTxV = null;
            CharSequence name = tv.getText();
            if (name.length() > 0
                && FileHelper.rename(new File(pwd, transStr), name.toString())) {
                refresh();
            } else {
				toast(getString(R.string.rename_failed));
			}
            return;
        }
		ProgressDialog pd = new ProgressDialog(MainActivity.this);
		pd.setMessage(getString(R.string.deleting, transStr));
		pd.setIndeterminate(true);
		pd.show();
		transDlg = pd;
		new Thread(this).start();
	}

	public void run() {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			transZ = FileHelper.removeFiles(new File(pwd, transStr));
			runOnUiThread(this);
		} else {
			if (transZ) {
				toast(getText(R.string.deleted));
				refresh();
			}
			transDlg.dismiss();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
		onGlobalLayout();
        if (reqCode != SHOW_FLOATING) {
            panel.clean();
            reqCode = 0;
        }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case SETTING:
				if (resultCode == RESULT_OK) {
					boolean s = "s".equals(Application.completion);
                    Application app = Application.getInstance();
                    Lsp lsp = app.lsp;
					boolean chg = s==lsp.isEnded();
					if (chg) {
						lsp.end();
						if (s) {
							lsp.start(this, app.hand);
							lsp.initialize(Project.rootPath);
						}
						chg = s;
					}
					FragmentManager fm = getFragmentManager();
					Typeface tf = Application.typeface();
					for (int i=hda.getCount() - 1;i >= 0;i--) {
						EditFragment f = (EditFragment)fm.findFragmentByTag(hda.getItem(i));
						TextEditor ed = (TextEditor)f.getView();
                        ed.setPureMode(Application.pure_mode);
						ed.setFormatter(s ? f : null);
						ed.setAutoComplete("l".equals(Application.completion));
						ed.setTypeface(tf);
						ed.setWordWrap(Application.wordwrap);
						ed.setShowNonPrinting(Application.whitespace);
						ed.setUseSpace(Application.usespace);
						ed.setTabSpaces(Application.tabsize);
                        ed.setSuggestion(Application.suggestion);
                        ed.setAutoCaps(Application.auto_caps);
                        if (chg) f.onOpen();
					}	
				} else if (resultCode == RESULT_FIRST_USER) {
					recreate();
				}
				break;
			case ACCESS_FILE:
				if (resultCode == RESULT_OK)
					refresh();
				break;
            case SHOW_FLOATING:
                reqCode = requestCode;
                if (Settings.canDrawOverlays(this)) {
                    panel.show();
                } else {
                    panel.clean();
					toast(getString(R.string.request_failed));
				}
                break;
			case REQ_FOLDER:
				if (resultCode == RESULT_OK) {
					Uri dat = data.getData();
					File tmxfile = new File(DocumentsContract.getTreeDocumentId(dat));
					if (FileHelper.isTermuxFile(tmxfile)) {
						getContentResolver().takePersistableUriPermission(dat, Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
						Application.getInstance().saveTermuxUri(dat);
						pwd = tmxfile;
						refresh();
					} else
						toast(getString(R.string.not_termux_dir));
				}
				break;
		}
	}

	private final DialogInterface.OnClickListener onc = new DialogInterface.OnClickListener(){
		public void onClick(DialogInterface p1, int p2) {
			TextView tv = transTxV;
            transTxV = null;
            String name = tv.getText().toString();
            if (name.isEmpty()) {
                toast(getText(R.string.empty_name));
                return;
            }
			File f = new File(pwd, name);
			if (tv.getId() == R.id.newfile) {
				try {
					FileHelper.createFile(f, p2 == DialogInterface.BUTTON_NEUTRAL);
					refresh();
				} catch (IOException e) {
					e.printStackTrace();
					toast(e.getMessage());
				}
                return;
			}
            View v = transV;
            transV = null;
            String s = ((Spinner)v.findViewById(R.id.prj_temp)).getSelectedItem().toString();
            if (Utils.extractTemplate(MainActivity.this, s, f)) {
                AssetManager am = getAssets();
                try {
                    if (((CompoundButton)v.findViewById(R.id.prj_cld)).isChecked()) {
                        Utils.dumpFile(am.open("cld"), new File(f, ".clangd"));
                    }
                    if (((CompoundButton)v.findViewById(R.id.prj_fmt)).isChecked()) {
                        Utils.dumpFile(am.open("fmt"), new File(f, ".clang-format"));
                    }
                } catch (IOException ioe) {
                   ioe.printStackTrace();
                }
				appMenu.findItem(R.id.prj).setEnabled(true);
                setFileRunnable(true);
				pwd = f;
				prj = f;
				refresh();
			}
		}
	};

    public void createFile(View view) {
		PopupMenu pm = new PopupMenu(this, view);
		Menu m = pm.getMenu();
		m.add(Menu.NONE, R.id.newfile, Menu.NONE, R.string.new_f).setOnMenuItemClickListener(this);
		m.add(Menu.NONE, R.id.newprj, Menu.NONE, R.string.new_prj).setOnMenuItemClickListener(this);
		pm.show();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		appMenu = menu;
		showFullMenu(false);
		return true;
	}

	private void showFullMenu(boolean show) {
		Menu menu = appMenu;
		int i = menu.size() - 3;
		boolean prj = Project.rootPath == null;
		for (; i >= 0; i--) {
			MenuItem mi = menu.getItem(i);
			if (prj || mi.getItemId() != R.id.run)
				menu.getItem(i).setVisible(show);
		}
	}

    @Override
    protected void onStop() {
        getPreferences(MODE_PRIVATE).edit().putString(PWD, pwd.getPath()).commit();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        hda.unregisterDataSetObserver(obs);
        obs = null;
        hda = null;
        panel.clean();
        panel = null;
        adp = null;
        mSearchAction = null;
        codeEditor = null;
        msgEmpty = null;
        Application.getInstance().hand.updateActivity(null);
        super.onDestroy();
    }

    final void toast(CharSequence charSequence) {
        HelperUtils.show(Toast.makeText(this, charSequence, Toast.LENGTH_SHORT));
    }

    public void showList(View view) {
		View v = subc;
        v.setVisibility(View.VISIBLE ^ View.GONE ^ v.getVisibility());
    }

	public void setEditor(TextEditor edit) {
		codeEditor = edit;
	}

	public TextEditor getEditor() {
		return codeEditor;
	}

    public TextEditor newEditor() {
        if (editAttr == null) {
            XmlPullParser xml;
            try {
                xml = getResources().getLayout(R.layout.edit);
                int i;
                do {
                    i = xml.next();
                } while(i != XmlPullParser.START_TAG);
                editAttr = android.util.Xml.asAttributeSet(xml);
            } catch (Exception ioe) {
                ioe.printStackTrace();
            }
        }
        return new TextEditor(this, editAttr);
    }
}