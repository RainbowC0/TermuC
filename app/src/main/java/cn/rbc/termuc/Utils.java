package cn.rbc.termuc;
import android.app.*;
import android.app.AlertDialog.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import cn.rbc.codeeditor.util.*;
import java.io.*;
import java.nio.channels.*;

public class Utils {
	public final static File ROOT = Environment.getExternalStorageDirectory();
    public final static String TERMUX = "com.termux";
	public final static String PREF = "/data/data/" + TERMUX + "/files";
	public final static String PERM_EXEC = TERMUX + ".permission.RUN_COMMAND";
    private final static String PREFC = TERMUX + ".RUN_COMMAND_";

	public static void run(Context cont, String cmd, String[] args, String pwd, boolean background) {
        Intent it = new Intent();
        it.setClassName(TERMUX, TERMUX + ".app.RunCommandService");
        it.setAction(TERMUX + ".RUN_COMMAND");
        it.putExtra(PREFC + "PATH", cmd);
        it.putExtra(PREFC + "ARGUMENTS", args);
        it.putExtra(PREFC + "WORKDIR", pwd);
        it.putExtra(PREFC + "BACKGROUND", background);
        it.putExtra(PREFC + "SESSION_ACTION", "0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            cont.startForegroundService(it);
        else
            cont.startService(it);
	}

	public static String escape(String str) {
        return str.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$").replace("\"", "\\\"");
    }

	public static boolean isBlob(File f) {
		int i = Math.min((int)f.length(), 2048);
		try {
			FileInputStream file = new FileInputStream(f);
			byte[] bArr = new byte[i];
			new DataInputStream(file).readFully(bArr);
			for (int i2 = 0; i2 < i; i2++) {
				if (bArr[i2] == 0) {
					file.close();
					return true;
				}
			}
			file.close();
		} catch (IOException e) {
            e.printStackTrace();
        }
		return false;
	}

    public static int readLine(BufferedInputStream inps, byte[] line) throws IOException {
        int l = line.length, c;
        inps.mark(l);
        if ((c = inps.read()) == -1) return -1;
        byte fr = (byte)c;
        line[0] = fr;
        for (int i=1; i<l; i++) {
            if ((c = inps.read()) == -1 || ('\r' == fr && '\n' == c && i == i--)) {
                l = i;
                break;
            }
            line[i] = fr = (byte)c;
        }
        inps.reset();
        inps.skip(l + 2);
        return l;
    }

    public static boolean arrNEquals(byte[] arr1, byte[] arr2, int n) {
        if (arr1.length < n || arr2.length < n)
            throw new IndexOutOfBoundsException();
        for (int i = 0; i < n; i++) {
            if (arr1[i] != arr2[i]) {
                return false;
            }
        }
        return true;
    }

	public static boolean removeFiles(File dir) {
		File[] fl = dir.listFiles();
		if (fl != null)
			for (File f:fl)
				removeFiles(f);
		return dir.delete();
	}

	public static void testApp(Activity ctx, boolean manually) {
		PackageManager pm = ctx.getPackageManager();
		try {
			pm.getPackageInfo(TERMUX, PackageManager.GET_GIDS);
			if (manually) HelperUtils.show(Toast.makeText(ctx, R.string.installed, Toast.LENGTH_SHORT));
		} catch (PackageManager.NameNotFoundException e) {
			Builder bd = new Builder(ctx);
			bd.setTitle(R.string.install_app);
			bd.setMessage(R.string.confirm_install);
			bd.setNegativeButton(android.R.string.cancel, null);
			Install oc = new Install(ctx);
			bd.setPositiveButton(android.R.string.ok, oc);
            if (!manually)
			    bd.setNeutralButton(R.string.no_remind, oc);
			bd.create().show();
		}
	}

	private static class Install
	implements DialogInterface.OnClickListener, SimpleAdapter.ViewBinder {
		Activity mApp;
		Install(Activity app) {
			mApp = app;
		}
		public boolean setViewValue(View v, Object o, String k) {
			if (v instanceof ImageView && o instanceof Drawable) {
				((ImageView)v).setImageDrawable((Drawable)o);
				return true;
			}
			return false;
		}
		public void onClick(DialogInterface d, int p) {
			Activity app = mApp;
			if (p == DialogInterface.BUTTON_NEUTRAL) {
				app.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean(MainActivity.TESTAPP, false).commit();
				return;
			}
			Uri uri = Uri.parse("market://details?id=" + TERMUX);
			Intent it = new Intent(Intent.ACTION_VIEW, uri);
            it = Intent.createChooser(it, app.getString(R.string.install_via));
            String pkgName = app.getPackageName();
            Intent[] extraIntents = {
                newLabeled(pkgName, "Github Release", "https://github.com/termux/termux-app/releases"),
                newLabeled(pkgName, "F-Droid", "https://f-droid.org/packages/com.termux")
            };
            it.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);
            app.startActivity(it);
		}
	}

	public static void initBack(Activity ctx, boolean manually) {
		try {
			ctx.getPackageManager().getPackageInfo(TERMUX, PackageManager.GET_GIDS);
			Builder bd = new Builder(ctx);
			bd.setTitle(R.string.init_termux);
			bd.setMessage(R.string.init_inform);
			Init it = new Init(ctx);
			bd.setPositiveButton(R.string.copy_jump, it);
			bd.setNegativeButton(android.R.string.cancel, null);
			if (!manually)
				bd.setNeutralButton(R.string.initialized, it);
			bd.create().show();
		} catch (PackageManager.NameNotFoundException nne) {
			if (manually)
                HelperUtils.show(Toast.makeText(ctx, R.string.no_install, Toast.LENGTH_SHORT));
		}
	}

	public static void copyJump(Context ctx) {
		ClipboardManager cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
		try {
			BufferedInputStream is = new BufferedInputStream(ctx.getAssets().open("init"));
			StringBuilder sb = new StringBuilder(ctx.getString(R.string.init_prefix));
			int i;
			byte[] bt = new byte[1024];
			while ((i = is.read(bt)) > 0)
				sb.append(new String(bt, 0, i));
			is.close();
			ClipData cd = ClipData.newPlainText("Label", sb.toString());
			cm.setPrimaryClip(cd);
            Intent it = ctx.getPackageManager().getLaunchIntentForPackage(TERMUX);
            if (it != null) {
			    ctx.startActivity(it);
            }
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private static class Init implements DialogInterface.OnClickListener {
		Activity mCtx;
		public Init(Activity ctx) {
			mCtx = ctx;
		}
		public void onClick(DialogInterface p1, int p2) {
			if (p2 == DialogInterface.BUTTON_POSITIVE) {
				copyJump(mCtx);
			} else {
				mCtx.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean(MainActivity.INITAPP, false).commit();
			}
		}
	}

    public static boolean extractTemplate(Context ctx, String type, File des) {
        if ("Clang".equals(type))
            return Project.create(des);
        if (!des.isDirectory())
            des.mkdir();
        AssetManager am = ctx.getAssets();
        try {
            String[] temps = am.list(type);
            if (temps == null)
                return false;
            for (String s:temps)
                dumpFile(am.open(new File(type, s).getPath()), new File(des, s));
            Project.load(new File(des, Project.PROJ), null);
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }

    public static void dumpFile(InputStream is, File f) throws IOException {
        FileChannel os = new FileOutputStream(f).getChannel();
        ReadableByteChannel rbc = Channels.newChannel(is);
        os.transferFrom(rbc, 0, is.available());
        os.close();
        is.close();
    }

    public static void setNightMode(Context ctx, String thm) {
        ctx.setTheme("s".equals(thm) ?R.style.AppThemeDayNight:
                     "l".equals(thm) ?R.style.AppTheme:
                     R.style.AppThemeDark);
    }

    private static Intent newLabeled(String srcPkg, CharSequence title, String url) {
        Intent it = Build.VERSION.SDK_INT < 33 ? new Intent() : new LabeledIntent(srcPkg, title, 0);
        it.setAction(Intent.ACTION_VIEW);
        it.setData(Uri.parse(url));
        return it;
    }
}
