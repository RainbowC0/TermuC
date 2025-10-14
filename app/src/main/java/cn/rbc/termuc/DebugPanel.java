package cn.rbc.termuc;
import android.annotation.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import cn.rbc.codeeditor.util.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static android.view.WindowManager.LayoutParams.*;

public class DebugPanel extends Handler
implements View.OnClickListener, View.OnTouchListener, Runnable,
ListView.OnItemClickListener,
DialogInterface.OnShowListener, EditText.OnEditorActionListener,
TabHost.OnTabChangeListener {
    private Activity mCtx;
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private View ctrlr;
    private TextView consoleMsg, dbgMsg;
    private Socket sock;
    private Dialog dg;
    private ArrayList<String> stackList;
    private ArrayList<Map<String, Object>> vars;
    private ArrayAdapter<String> sa, stackAdp;
    private SimpleAdapter varAdp;
    private ListView bkpts;
    private Sender sender;
    private TabHost holder;
    private float startX, startY;
    private static final int
    STAT_IDLE = 0,
    STAT_RUNNING = 1,
    STAT_STOPPED = 2;
    private static final int
    ACT_ENABLE = 0,
    ACT_NOTIFY = 1,
    ACT_LOG = 2;
    private int status = STAT_IDLE, vnum;
    private boolean started;
    private final static String KM = "numchild", KN = "name", KE="exp", KV = "value", KT = "type";

    public DebugPanel(Activity context) {
        super(context.getMainLooper());
        mCtx = context;
        init();
    }

    private void init() {
        WindowManager wm = (WindowManager)mCtx.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        this.wm = wm;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = Build.VERSION.SDK_INT < 26 ? TYPE_SYSTEM_ALERT : TYPE_APPLICATION_OVERLAY;
        params.flags = FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN;
        params.format = PixelFormat.RGBA_8888;
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.width = WRAP_CONTENT;
        params.height = WRAP_CONTENT;
        this.params = params;
        ViewGroup ctr = (ViewGroup)View.inflate(mCtx, R.layout.debug_controller, null);
        View close = ctr.findViewById(R.id.debug_close);
        close.setOnTouchListener(this);
        Typeface codicon = Typeface.createFromAsset(mCtx.getAssets(), "codicon.ttf");
        for (int i = 0, l = ctr.getChildCount(); i < l; i++) {
            View v = ctr.getChildAt(i);
            v.setOnClickListener(this);
            if (v instanceof TextView) {
                ((TextView)v).setTypeface(codicon);
            }
        }
        ctrlr = ctr;
        Context ctx = mCtx;
        sa = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_multiple_choice);
        TabHost th = (TabHost)View.inflate(mCtx, R.layout.debug_panel, null);
        holder = th;
        th.setup();
        //TextView varPath = th.findViewById(R.id.var_path);
        //vpath = varPath;
        //varPath.setText("", TextView.BufferType.EDITABLE);
        //path = varPath.getEditableText();
        ArrayList<Map<String,Object>> list = new ArrayList<>();
        vars = list;
        SimpleAdapter vdp = new SimpleAdapter(
            ctx, list, R.layout.var_item,
            new String[]{KM, KE, KV, KT},
            new int[]{R.id.var_expand, android.R.id.text1, android.R.id.text2, R.id.var_type}
        );
        varAdp = vdp;
        ListView var = th.findViewById(R.id.var_list);
        var.setEmptyView(th.findViewById(android.R.id.text1));
        var.setAdapter(vdp);
        var.setOnItemClickListener(this);
        TabHost.TabSpec ts = th.newTabSpec("var");
        ts.setIndicator(ctx.getString(R.string.variable));
        ts.setContent(R.id.var);
        th.addTab(ts);
        ts = th.newTabSpec("frame");
        ts.setIndicator(ctx.getString(R.string.frame));
        ArrayList<String> stl = new ArrayList<>();
        stackList = stl;
        ArrayAdapter<String> st = new ArrayAdapter<>(ctx, android.R.layout.simple_list_item_1, stl);
        stackAdp = st;
        ListView backtrace = th.findViewById(R.id.frame_list);
        backtrace.setEmptyView(th.findViewById(android.R.id.text2));
        backtrace.setAdapter(st);
        ts.setContent(R.id.frame);
        th.addTab(ts);
        ts = th.newTabSpec("bkpt");
        ts.setIndicator(ctx.getString(R.string.bkpt));
        ts.setContent(R.id.bkpt);
        th.addTab(ts);
        ListView bkpts = th.findViewById(R.id.bkpt);
        this.bkpts = bkpts;
        bkpts.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        bkpts.setAdapter(sa);
        bkpts.setOnItemClickListener(this);
        ts = th.newTabSpec("cmd");
        ts.setIndicator(ctx.getString(R.string.console));
        ts.setContent(R.id.dbg_console);
        th.addTab(ts);
        consoleMsg = th.findViewById(R.id.dbg_cmsg);
        consoleMsg.setText("", TextView.BufferType.EDITABLE);
        ((EditText)th.findViewById(R.id.dbg_cmd)).setOnEditorActionListener(this);
        ts = th.newTabSpec("msg");
        ts.setIndicator(ctx.getString(R.string.message));
        ts.setContent(R.id.tab4);
        dbgMsg = th.findViewById(R.id.dbg_msg);
        dbgMsg.setText("", TextView.BufferType.EDITABLE);
        th.addTab(ts);
        th.setOnTabChangedListener(this);
    }

    @Override
    public void run() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Activity ctx = mCtx;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)) {
                show();
            } else {
                Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+ctx.getPackageName()));
                ctx.startActivityForResult(it, MainActivity.SHOW_FLOATING);
            }
            return;
        }
        Socket sock = null;
        int i = 0;
        try {
            do {
                try {
                    sock = new Socket("127.0.0.1", 48456);
                } catch (IOException ioe) {
                    Thread.sleep(250L);
                }
                i++;
            } while (i < 20 && (sock == null || !sock.isConnected()));
            if (i == 20) {
                throw new IOException("connect timeout");
            }
            this.sock = sock;
            Sender sender = new Sender(sock);
            post(this);
            new Thread(sender).start();
            sender.add("-gdb-set mi-async 1");
            sender.add("-enable-pretty-printing");
            for (int j=0,l=sa.getCount();j < l;j++) {
                sender.add("-break-insert " + sa.getItem(j));
            }
            sender.add("-exec-run");
            this.sender = sender;
            BufferedReader rd = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                processMsg(line, rd);
            }
        } catch (IOException ioe) {
            Log.e("Lsp", ioe.getMessage(), ioe);
        } catch (InterruptedException ie) {
            Log.e("Lsp", ie.getMessage(), ie);
        }
        started = false;
    }

    void show() {
        Intent it = new Intent(Intent.ACTION_MAIN);
        it.setPackage(Utils.TERMUX);
        mCtx.startActivity(it);
        wm.addView(ctrlr, params);
    }

    private void processMsg(@NonNull String line, BufferedReader rd) throws IOException {
        if (line.isEmpty())
            return;
        final StreamTokenizer st = new StreamTokenizer(new StringReader(line));
        st.wordChars('-', '-');
        int tok = -1;
        switch (st.nextToken()) {
            case '&':
            case '~':
                if (st.nextToken() == '"') {
                    Message.obtain(this, ACT_LOG, R.id.dbg_cmsg, 0, st.sval).sendToTarget();
                }
                break;
            case '*':
                if (StreamTokenizer.TT_WORD == st.nextToken()) {
                    final String stat = st.sval;
                    st.nextToken();
                    switch (stat) {
                        case "stopped":
                            status = STAT_STOPPED;
                            if (StreamTokenizer.TT_WORD == st.nextToken() && "reason".equals(st.sval)) {
                                st.nextToken();
                                if (st.nextToken() == '"' && st.sval != null && st.sval.startsWith("exited")) {
                                    hide();
                                    break;
                                }
                            }
                            Message msg = Message.obtain(this, ACT_ENABLE, 1, 1);
                            if ("breakpoint-hit".equals(st.sval)) {
                                st.nextToken(); // ,
                                st.nextToken();st.nextToken();st.nextToken();st.nextToken(); // disp=x,
                                st.nextToken();
                                if ("bkptno".equals(st.sval)) {
                                    st.nextToken(); //=
                                    if (st.nextToken() == StreamTokenizer.TT_WORD) {
                                        msg.arg2 = Integer.parseInt(st.sval);
                                    }
                                }
                            }
                            msg.sendToTarget();
                            break;
                        case "running":
                            status = STAT_RUNNING;
                            Message.obtain(this, ACT_ENABLE, 0, 0).sendToTarget();
                            break;
                    }
                }
            case '=':
                Message.obtain(this, ACT_LOG, R.id.dbg_msg, 0, line.concat("\n")).sendToTarget();
                break;
            case '^':
                Log.i("Lsp", line);
                if (st.nextToken() == StreamTokenizer.TT_WORD && "done".equals(st.sval)) {
                    tok = 1;
                    if (st.nextToken() == ','
                        && st.nextToken() == StreamTokenizer.TT_WORD) {
                        switch (st.sval) {
                            case "stack":
                                final ArrayList<String> stl = stackList;
                                int count = stl.size();
                                st.nextToken();//=
                                st.nextToken();//[
                                int i = 0;
                                do {
                                    st.nextToken(); // frame
                                    st.nextToken(); // =
                                    st.nextToken(); // {
                                    String level = "", func = "", file = "", lineno = null;
                                    do {
                                        if (st.nextToken() == StreamTokenizer.TT_WORD) {
                                            String name = st.sval;
                                            st.nextToken(); // =
                                            st.nextToken(); // "
                                            switch (name) {
                                                case "level": level = st.sval; break;
                                                case "func": func = st.sval; break;
                                                case "file": file = st.sval; break;
                                                case "line": lineno = st.sval; break;
                                                case "from": file = st.sval; break;
                                            }
                                        }
                                    } while (st.nextToken() == ','); // , }
                                    String li = "#" + level + " " + func + "() " + (lineno != null ? file + ":" + lineno : file);
                                    if (i < count) {
                                        stl.set(i, li);
                                    } else {
                                        stl.add(i, li);
                                    }
                                    i++;
                                } while (st.nextToken() == ','); // , ]
                                Message.obtain(this, ACT_NOTIFY, i, 0, stackAdp).sendToTarget();
                                break;
                            case "stack-args":
                                st.nextToken(); //=
                                st.nextToken(); //[
                                st.nextToken(); //frames
                                st.nextToken(); //=
                                st.nextToken(); //[
                                st.nextToken();st.nextToken();st.nextToken();st.nextToken(); //level=",
                                st.nextToken(); //args
                                vnum = 0;
                                tok = 2;
                            case "locals":
                                st.nextToken(); //=
                                st.nextToken(); //[
                                ArrayList<Map<String,Object>> vs = vars;
                                i = vnum; count = vs.size();
                                while (st.nextToken() == StreamTokenizer.TT_WORD) { //name
                                    st.nextToken(); //=
                                    st.nextToken(); //"
                                    sender.add("-var-create - * " + st.sval);
                                    rd.readLine();
                                    String l = rd.readLine();
                                    Log.i("Lsp", l);
                                    Map<String,Object> mp = new ArrayMap<>();
                                    StreamTokenizer tk = new StreamTokenizer(new StringReader(l));
                                    tk.nextToken();tk.nextToken(); // ^done
                                    parseObject(tk, mp);
                                    mp.put(KE, st.sval);
                                    mp.put(KM, "0".equals(mp.get(KM)) ? 0 : R.drawable.ic_chevron_right_24);
                                    if (i < count) {
                                        vs.set(i, mp);
                                    } else {
                                        vs.add(mp);
                                    }
                                    i++;
                                    st.nextToken(); //,
                                }
                                if (tok == 2) {
                                    vnum = i;
                                    sender.add("-stack-list-locals 0");
                                } else {
                                    Message.obtain(this, ACT_NOTIFY, i, 0, varAdp).sendToTarget();
                                }
                                break;
                            case "numchild":
                                st.nextToken(); //=
                                st.nextToken(); //"
                                st.nextToken(); //,
                                st.nextToken(); //children
                                st.nextToken(); //=
                                st.nextToken(); //[
                                vs = vars;
                                count = vs.size(); i = 0;
                                do {
                                    st.nextToken();st.nextToken(); //child=
                                    Map<String,Object> mp = new ArrayMap<>();
                                    parseObject(st, mp);
                                    mp.put(KM, "0".equals(mp.get(KM)) ? 0 : R.drawable.ic_chevron_right_24);
                                    if (i < count)
                                        vs.set(i, mp);
                                    else
                                        vs.add(mp);
                                    i++;
                                } while (st.nextToken() == ','); // ]
                                Message.obtain(this, ACT_NOTIFY, i, 0, varAdp).sendToTarget();
                                break;
                        }
                    }
                }
                if (tok != -1)
                    break;
                Message.obtain(this, ACT_LOG, R.id.dbg_msg, 0, line.concat("\n")).sendToTarget();
                break;
            case '(':
                break;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACT_ENABLE:
                final boolean enabled = msg.arg1 != 0;
                final View ctr = ctrlr;
                TextView cont = ctr.findViewById(R.id.debug_continue);
                cont.setText(enabled ? "\uEACF" : "\uEAD1");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    cont.setTooltipText(mCtx.getString(enabled ? R.string.cont : R.string.pause));
                }
                ctr.findViewById(R.id.debug_next).setEnabled(enabled);
                ctr.findViewById(R.id.debug_step).setEnabled(enabled);
                ctr.findViewById(R.id.debug_finish).setEnabled(enabled);
                int i = msg.arg2;
                if (i > 0 && i <= sa.getCount()) {
                    Context ctx = mCtx.getApplicationContext();
                    HelperUtils.show(Toast.makeText(ctx, ctx.getString(R.string.bkpt_at, sa.getItem(i-1)), Toast.LENGTH_SHORT));
                }
                break;
            case ACT_NOTIFY:
                BaseAdapter adp = (BaseAdapter)msg.obj;
                i = msg.arg1;
                List l = adp == varAdp ? vars : stackList;
                int s = l.size();
                if (i < s) {
                    l.subList(i, s).clear();
                }
                adp.notifyDataSetChanged();
                break;
            case ACT_LOG:
                TextView tv = msg.arg1 == R.id.dbg_cmsg ? consoleMsg : dbgMsg;
                tv.append((String)msg.obj);
                break;
        }
    }

    public void connect() {
        if (!started) {
            started = true;
            new Thread(this).start();
        }
    }

    public void hide() {
        if (ctrlr.isAttachedToWindow())
            wm.removeView(ctrlr);
        if (dg != null) {
            dg.dismiss();
            dg = null;
        }
        sa.clear();
        if (sender != null) {
            sender.add("-gdb-exit");
            sender.add("");
            sender = null;
        }
        sock = null;
        status = STAT_IDLE;
    }

    public void clean() {
        hide();
        dbgMsg.getEditableText().clear();
        consoleMsg.getEditableText().clear();
        sa.clear();
        vars.clear();
        stackList.clear();
    }

    @Override
    public void onClick(View p1) {
        final int id = p1.getId();
        if (id != R.id.debug_close) {
            Intent it = new Intent(Intent.ACTION_MAIN);
            it.setPackage(Utils.TERMUX);
            mCtx.startActivity(it);
        }
        switch (p1.getId()) {
            case R.id.debug_continue:
                sender.add(status == STAT_STOPPED ? "-exec-continue" : "-exec-interrupt");
                break;
            case R.id.debug_next:
                sender.add("-exec-next");
                break;
            case R.id.debug_step:
                sender.add("-exec-step");
                break;
            case R.id.debug_finish:
                sender.add("-exec-finish");
                break;
            case R.id.debug_more:
                showDialog();
                break;
            case R.id.debug_close:
                hide();
                break;
        }
    }

    private void showDialog() {
        sa.notifyDataSetChanged();
        if (dg != null && dg.isShowing()) {
            return;
        }
        View v = holder;
        dg = new AlertDialog.Builder(mCtx)
            .setView(v)
            .create();
        dg.setOnShowListener(this);
        Window wm = dg.getWindow();
        wm.setType(params.type);
        wm.setEnterTransition(null);
        wm.setExitTransition(null);
        wm.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE | SOFT_INPUT_ADJUST_RESIZE);
        ViewParent vp = v.getParent();
        if (vp instanceof ViewGroup) {
            ((ViewGroup)vp).removeView(v);
        }
        dg.show();
    }

    @Override
    public boolean onTouch(View p1, MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = params.x - ev.getRawX();
                startY = params.y - ev.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                params.x = -(int)(ev.getRawX() + startX);
                params.y = (int)(ev.getRawY() + startY);
                wm.updateViewLayout(ctrlr, params);
                break;
        }
        return false;
    }

    public void addBkpt(String file, int lineno) {
        sa.add(file + ":" + lineno);
        bkpts.setItemChecked(sa.getCount() - 1, true);
    }

    @Override
    public void onItemClick(AdapterView<?> p1, View p2, int p3, long p4) {
        switch (p1.getId()) {
            case R.id.var_list:
                Map<String,Object> mp = vars.get(p3);
                if ((Integer)mp.get(KM) > 0) {
                    sender.add("-var-list-children --all-values " + mp.get(KN));
                }
                break;
            case R.id.bkpt:
                sender.add((((Checkable)p2).isChecked() ? "-break-enable " : "-break-disable ") + (p3 + 1));
                break;
        }
    }

    @Override
    public void onShow(DialogInterface p1) {
        String tag = holder.getCurrentTabTag();
        onTabChanged(holder.getCurrentTabTag());
        switch (tag) {
            case "cmd":
                ((ScrollView)consoleMsg.getParent()).fullScroll(View.FOCUS_DOWN);
                break;
            case "msg":
                ((ScrollView)dbgMsg.getParent()).fullScroll(View.FOCUS_DOWN);
                break;
        }
    }

    @Override
    public boolean onEditorAction(TextView p1, int p2, KeyEvent p3) {
        Editable ed = ((EditText)p1).getText();
        if (p2 == EditorInfo.IME_ACTION_SEND && ed.length() > 0) {
            consoleMsg.append("(gdb) " + ed + "\n");
            sender.add("-interpreter-exec console \"" + Utils.escape(ed.toString()) + "\"");
            ed.clear();
            return true;
        }
        return false;
    }

    @Override
    public void onTabChanged(String p1) {
        switch (p1) {
            case "var":
                if (status == STAT_STOPPED) {
                    Sender send = sender;
                    for (Map<String,Object> e : vars) {
                        send.add("-var-delete " + e.get(KN));
                    }
                    send.add("-stack-list-arguments 0 0 0");
                }
                break;
            case "frame":
                if (status == STAT_STOPPED) {
                    sender.add("-stack-list-frames");
                }
                break;
        }
    }

    private static void parseObject(StreamTokenizer st, Map<String,Object> mp) throws IOException {
        st.nextToken(); //{
        do {
            st.nextToken(); //key
            String key = st.sval;
            if (st.nextToken() != '=') { //=
                break;
            }
            if (st.nextToken() != '"') {
                throw new IOException("Format error: value token is '" + st.ttype + "'");
            }
            mp.put(key, st.sval);
        } while (st.nextToken() == ','); //}
    }

    static class Sender extends LinkedBlockingQueue<String> implements Runnable {
        private final Socket sock;
        Sender(@NonNull Socket sock) {
            super();
            this.sock = sock;
        }

        @Override
        public void run() {
            try {
                String msg;
                OutputStream os = sock.getOutputStream();
                while (!(msg = take()).isEmpty()) {
                    os.write(msg.getBytes());
                    os.write('\r');
                    os.write('\n');
                    os.flush();
                }
                clear();
            } catch (Exception e) {
                android.util.Log.e("Lsp", e.getMessage());
            } finally {
                try {
                    sock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public final Stream<String> stream() {
            return null;
        }

        @Override
        public final Stream<String> parallelStream() {
            return null;
        }
    }
}
