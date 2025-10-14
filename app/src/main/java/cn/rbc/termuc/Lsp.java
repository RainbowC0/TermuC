package cn.rbc.termuc;
import android.content.*;
import android.net.*;
import android.os.*;
import android.util.*;
import cn.rbc.codeeditor.util.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

import cn.rbc.codeeditor.util.Range;

public class Lsp extends Semaphore implements Runnable {
	final static int INITIALIZE = 0, INITIALIZED = 1,
	OPEN = 2, CLOSE = 3,
	COMPLETION = 4, FIX = 5, CHANGE = 6, SAVE = 7, NOTI = 8, SIGN_HELP = 9,
	ERROR = -1;
	private final static String TAG = "LSP";
	private final static byte[] CONTENTLEN = "Content-Length: ".getBytes(StandardCharsets.UTF_8);
	private int tp;
	private Socket sk = new Socket();
	private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	private char[] compTrigs = {}, sigTrigs = {};
	private long mLastReceivedTime;
	private Handler mRead;

    public Lsp() {
        super(0);
    }

	// In main thread
	public void start(Context mC, Handler read) {
		Utils.run(mC, "/system/bin/nc", new String[]{"-l", "-s", Application.lsp_host, "-p", Integer.toString(Application.lsp_port), "-w", "6", "nice", "-n", "-20", "clangd", "--header-insertion-decorators=0"}, Utils.ROOT.getAbsolutePath(), true);
		mRead = read;
		new Thread(this).start();
		mExecutor.execute(new Runnable(){
			public void run() {
                try {
			        acquire();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
			}
		});
	}

	public void end() {
		shutdown();
		exit();
		try {
			sk.close();
		} catch(IOException ioe) {
            ioe.printStackTrace();
        }
	}

	public boolean isEnded() {
		return sk.isClosed() || !sk.isConnected();
	}

	public void run() {
		int i = 0;
		try{
			do {
				try {
					sk = new Socket(Application.lsp_host, Application.lsp_port);
				} catch (SocketException s) {
					Thread.sleep(250L);
				}
				i++;
			} while (i<=20 && isEnded());
			release();
			if (i>20)
				throw new IOException("Connection failed");
			InputStream is = sk.getInputStream();
			byte[] b = new byte[16];
			OUTER:	while (true) {
				for (i=0;i<16;i++) {
					int t = is.read();
					if (t==-1)
						break OUTER;
					b[i] = (byte)t;
				}
				if (Arrays.equals(b, CONTENTLEN)) {
					Message msg = new Message();
					msg.what = tp;
					int len = 0;
					while (Character.isDigit(i = is.read()))
						len = len * 10 + i - 48;
					mLastReceivedTime = System.currentTimeMillis();
					is.skip(3L);
					byte[] strb = new byte[len];
					for (i=0; i<len; i++)
						strb[i] = (byte)is.read();
					InputStream r = new ByteArrayInputStream(strb);
					JsonReader limitInput = new JsonReader(new InputStreamReader(r, StandardCharsets.UTF_8));
					msg.obj = limitInput;
					mRead.sendMessage(msg);
				}
			}
			is.close();
		} catch (Exception ioe) {
			Log.e(TAG, ioe.getMessage());
		}
		mRead.sendEmptyMessage(ERROR);
	}

	public long lastReceivedTime() {
		return mLastReceivedTime;
	}

	private static String wrap(String m, Object p, boolean req) {
		StringBuilder s = new StringBuilder("{\"jsonrpc\":\"2.0\"");
		if (req)
			s.append(",\"id\":0");
		s.append(",\"method\":\"");s.append(m);
		s.append("\",\"params\":");s.append(JSONObject.wrap(p));
		s.append("}");
		return s.toString();
	}

	public void initialize(String root) {
		tp = INITIALIZE;
		StringBuilder sb = new StringBuilder("{\"processId\":");
		sb.append(android.os.Process.myPid());
		sb.append(",\"capabilities\":{\"workspace\":{\"applyEdit\":true,\"workspaceFolders\":true}}");
		if (root!=null) {
			sb.append(",\"rootUri\":");
			sb.append(JSONObject.quote(new File(root).toURI().toString()));
		}
		sb.append(",\"initializationOptions\":{\"fallbackFlags\":[\"-Wall\"]}}");
		mExecutor.execute(new Send("initialize", sb.toString(), true));
	}

	public void setCompTrigs(char[] c) {
		compTrigs = c;
	}

	public byte isCompTrig(char c) {
		if (Character.isJavaIdentifierPart(c))
			return 1;
		for (int i=0,l=compTrigs.length; i<l; i++)
			if (compTrigs[i] == c)
				return 2;
		return 0;
	}

    public void setSigTrigs(char[] c) {
        sigTrigs = c;
    }

    public boolean isSigTrig(char c) {
        char[] sigs = sigTrigs;
        for (int i=0,l=sigs.length; i<l; i++)
            if (sigs[i] == c)
                return true;
        return false;
    }

	public void initialized() {
		tp = INITIALIZED;
		mExecutor.execute(new Send("initialized", new HashMap<>(), false));
	}

	public void didClose(File f) {
		HashMap<String,String> m = new HashMap<>();
		m.put("uri", Uri.fromFile(f).toString());
		HashMap<String,HashMap<String,String>> k = new HashMap<>();
		k.put("textDocument", m);
		tp = CLOSE;
		mExecutor.execute(new Send("textDocument/didClose", k, false));
	}

	public void didOpen(File f, String lang, String ct) {
		StringBuilder m = new StringBuilder("{\"textDocument\":{\"uri\":");
		m.append(JSONObject.quote(Uri.fromFile(f).toString()));
		m.append(",\"languageId\":\"");m.append(lang);
		m.append("\",\"version\":0,\"text\":");
		m.append(JSONObject.quote(ct));
		m.append("}}");
		tp = OPEN;
		mExecutor.execute(new Send("textDocument/didOpen", m.toString(), false));
	}

	public void didSave(File f) {
		StringBuilder s = new StringBuilder("{\"textDocument\":{\"uri\":");
		s.append(JSONObject.quote(Uri.fromFile(f).toString()));
		s.append("}}");
		tp = SAVE;
		mExecutor.execute(new Send("textDocument/didSave", s.toString(), false));
	}

	public void didChange(File f, int version, String text) {
		StringBuilder sb = new StringBuilder("{\"textDocument\":{\"uri\":");
		sb.append(JSONObject.quote(Uri.fromFile(f).toString()));
		sb.append(",\"version\":");
		sb.append(version);
		sb.append("},\"contentChanges\":[{\"text\":");
		sb.append(JSONObject.quote(text));
		sb.append("}]}").toString();
		tp = CHANGE;
		mExecutor.execute(new Send("textDocument/didChange", sb.toString(), false));
	}

	public void didChange(File f, int version, List<Range> chs) {
		StringBuilder sb = new StringBuilder("{\"textDocument\":{\"uri\":");
		sb.append(JSONObject.quote(Uri.fromFile(f).toString()));
		sb.append(",\"version\":");
		sb.append(version);
		sb.append("},\"contentChanges\":[");
		for (int i=0,j=chs.size(); i<j; i++) {
			Range c = chs.get(i);
			sb.append("{\"range\":{\"start\":{\"line\":");
			sb.append(c.stl);
			sb.append(",\"character\":");
			sb.append(c.stc);
			sb.append("},\"end\":{\"line\":");
			sb.append(c.enl);
			sb.append(",\"character\":");
			sb.append(c.enc);
			sb.append("}},\"text\":");
			sb.append(JSONObject.quote(c.msg));
			sb.append("},");
		}
		sb.setCharAt(sb.length()-1, ']');
		sb.append('}');
		tp = CHANGE;
		mExecutor.execute(new Send("textDocument/didChange", sb.toString(), false));
	}

	public boolean completionTry(File f, int l, int c, char tgc) {
		byte b = isCompTrig(tgc);
		if (b==0)
			return false;
		StringBuilder sb = new StringBuilder("{\"textDocument\":{\"uri\":");
		sb.append(JSONObject.quote(Uri.fromFile(f).toString()));
		sb.append("},\"position\":{\"line\":");
		sb.append(l);
		sb.append(",\"character\":");
		sb.append(c);
		sb.append("},\"context\":{\"triggerKind\":");
		sb.append(b);
		if (b==2) {
			sb.append(",\"triggerCharacter\":\"");
			sb.append(tgc);
			sb.append('"');
		}
		sb.append("}}");
		tp = COMPLETION;
		//Log.d(TAG, sb.toString());
		mExecutor.execute(new Send("textDocument/completion", sb.toString(), true));
		return true;
	}

    public boolean signatureHelpTry(File f, int l, int c, char tgc, boolean retrig) {
        if (!isSigTrig(tgc)) return false;
        StringBuilder sb = new StringBuilder("{\"textDocument\":{\"uri\":");
        sb.append(JSONObject.quote(Uri.fromFile(f).toString()));
        sb.append("},\"position\":{\"line\":");
        sb.append(l);
        sb.append(",\"character\":");
        sb.append(c);
        sb.append("},\"context\":{\"triggerKind\":2,\"triggerCharacter\":\"");
        sb.append(tgc);
        sb.append("\",\"isRetrigger\":");
        sb.append(retrig);
        sb.append("}}");
        tp = SIGN_HELP;
        mExecutor.execute(new Send("textDocument/signatureHelp", sb.toString(), true));
        return true;
    }

	public void formatting(File fl, int tabSize, boolean useSpace) {
		StringBuilder sb = new StringBuilder("{\"textDocument\":{\"uri\":");
		sb.append(JSONObject.quote(Uri.fromFile(fl).toString()));
		sb.append("},\"options\":{\"tabSize\":");
		sb.append(tabSize);
		sb.append(",\"insertSpaces\":");
		sb.append(useSpace);
		sb.append("}}");
		//Log.d(TAG, sb.toString());
		mExecutor.execute(new Send("textDocument/formatting", sb.toString(), true));
	}

	public void rangeFormatting(File fl, Range range, int tabSize, boolean useSpace) {
		StringBuilder sb = new StringBuilder("{\"textDocument\":{\"uri\":");
		sb.append(JSONObject.quote(Uri.fromFile(fl).toString()));
		sb.append("},\"range\":{\"start\":{\"line\":");
		sb.append(range.stl);
		sb.append(",\"character\":");
		sb.append(range.stc);
		sb.append("},\"end\":{\"line\":");
		sb.append(range.enl);
		sb.append(",\"character\":");
		sb.append(range.enc);
		sb.append("}},\"options\":{\"tabSize\":");
		sb.append(tabSize);
		sb.append(",\"insertSpaces\":");
		sb.append(useSpace);
		sb.append("}}");
		mExecutor.execute(new Send("textDocument/rangeFormatting", sb.toString(), true));
	}

	public void shutdown() {
		mExecutor.execute(new Send("shutdown", "{}", true));
	}

	public void exit() {
		mExecutor.execute(new Send("exit", "{}", false));
	}

	public boolean isConnected() {
		return sk.isConnected();
	}

	class Send implements Runnable {
		private byte[] s;

		public Send(String cmd, Object hm, boolean req) {
			s = wrap(cmd, hm, req).getBytes(StandardCharsets.UTF_8);
		}

		public void run() {
			try {
				OutputStream ow = sk.getOutputStream();
				ow.write(CONTENTLEN);
				ow.write((s.length+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				ow.write(s);
				ow.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}
}
