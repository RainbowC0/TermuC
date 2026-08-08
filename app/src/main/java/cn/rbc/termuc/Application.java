package cn.rbc.termuc;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.preference.*;
import android.util.*;
import cn.rbc.codeeditor.util.*;
import java.util.*;

public class Application extends android.app.Application {
	final static String
    KEY_THEME = "theme",
	KEY_PUREMODE = "puremode",
    KEY_NAVTAB = "navtab",
	KEY_FONT = "font",
	KEY_MYFONT = "myfont",
	KEY_WORDWRAP = "wordwrap",
	KEY_WHITESPACE = "whitespace",
	KEY_TEXTSIZE = "fontsize",
	KEY_USESPACE = "usespace",
	KEY_TABSIZE = "tabsize",
    KEY_SUGGUESTION = "suggestion",
    KEY_AUTOCAPS = "autocaps",
    KEY_SYMS = "custom_sym",
	KEY_SHOW_HIDDEN = "showhidden",
	KEY_CHECKAPP = "checkapp",
	KEY_INITAPP = "initapp",
	KEY_CFLAGS = "cflags",
	KEY_COMPLETION = "completion",
	KEY_LSP_HOST = "lsphost",
	KEY_LSP_PORT = "lspport",
	KEY_TERMUX_URI = "_termux_uri";

	public static boolean pure_mode, navtab, wordwrap, whitespace, show_hidden, usespace, suggestion, auto_caps;
	public static String theme, font, syms, cflags, completion, lsp_host;
	public static int lsp_port, textsize, tabsize;

    MainHandler hand;
    Lsp lsp;
    Uri treeUri;
    private Map<String,Document> ls;
    private static Application app;
    private static Typeface customTypeface;

	@Override
	public void onCreate() {
        super.onCreate();
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		initConfs();
        ls = new ArrayMap<>();
        app = this;
	}

    @Override
    public void onTerminate() {
        lsp.end();
        ls.clear();
        super.onTerminate();
        ls = null;
        app = null;
        customTypeface = null;
    }

    void store(String key, Document obj) {
        ls.put(key, obj);
    }

    Document load(String key) {
        return ls.remove(key);
    }

    public static Application getInstance() {
        return app;
    }

	void saveTermuxUri(Uri uri) {
		treeUri = uri;
		SharedPreferences.Editor se = PreferenceManager.getDefaultSharedPreferences(this).edit();
		se.putString(KEY_TERMUX_URI, uri.toString());
		se.commit();
	}

    private void initConfs() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        theme = sp.getString(KEY_THEME, getResources().getString(R.string.def_thm));
        pure_mode = sp.getBoolean(KEY_PUREMODE, false);
        navtab = sp.getBoolean(KEY_NAVTAB, false);
		String f = sp.getString(KEY_FONT, "m");
		if ("c".equals(f))
			f = sp.getString(KEY_MYFONT, "");
		font = f;
		textsize = Integer.parseInt(sp.getString(KEY_TEXTSIZE, "14"));
        wordwrap = sp.getBoolean(KEY_WORDWRAP, true);
		whitespace = sp.getBoolean(KEY_WHITESPACE, false);
		usespace = sp.getBoolean(KEY_USESPACE, false);
		tabsize = Integer.parseInt(sp.getString(KEY_TABSIZE, "4"));
        suggestion = sp.getBoolean(KEY_SUGGUESTION, false);
        auto_caps = sp.getBoolean(KEY_AUTOCAPS, false);
        syms = sp.getString(KEY_SYMS, "{}();,.=\"|&![]<>#+-/*?:_");
		show_hidden = sp.getBoolean(KEY_SHOW_HIDDEN, true);
        cflags = sp.getString(KEY_CFLAGS, "-lm -Wall");
		completion = sp.getString(KEY_COMPLETION, "s").intern();
		lsp_host = sp.getString(KEY_LSP_HOST, "127.0.0.1");
		lsp_port = Integer.parseInt(sp.getString(KEY_LSP_PORT, "48455"));
		String uri = sp.getString(KEY_TERMUX_URI, "");
		if (!uri.isEmpty())
			treeUri = Uri.parse(uri);
    }

	static Typeface typeface() {
		try {
			return "n".equals(font) ? Typeface.SANS_SERIF
				: "s".equals(font) ? Typeface.SERIF
				: "m".equals(font) ? Typeface.MONOSPACE
				: customTypeface == null ? (customTypeface = Typeface.createFromFile(font)) : customTypeface;
		} catch (RuntimeException re) {
			return Typeface.MONOSPACE;
		}
	}

    static void clearCache() {
        customTypeface = null;
    }
}
