package cn.rbc.termuc;
import android.util.*;
import java.io.*;
import java.util.*;

public class Project
{
	private static long lastModified;
	private static final String 
	KEY_BUILDCMD = "buildCmd",
	KEY_COMPILE = "compileCmd",
	KEY_RUNCMD = "runCmd",
	KEY_OUTPUTDIR = "outputDir",
	KEY_OPENS = "opens";
	public final static String PROJ = "termuc.json";
	public static String rootPath, outputDir;
	public static String buildCmd, compileCmd, runCmd;

	private static void setDefault() {
		buildCmd = "clang $d/$f -o $o/$e -lm -Wall";
		compileCmd = "clang -c $d/$f -o $o/$e.o -lm -Wall";
		runCmd = "$x/$e";
		outputDir = "build";
	}

	public static void load(File conf, List<String> opens) throws IOException {
		JsonReader p = new JsonReader((new BufferedReader(new FileReader(conf))));
		p.beginObject();
		setDefault();
		while (p.hasNext()) {
			switch (p.nextName()) {
				case KEY_BUILDCMD: buildCmd = p.nextString(); break;
				case KEY_COMPILE: compileCmd = p.nextString(); break;
				case KEY_RUNCMD: runCmd = p.nextString(); break;
				case KEY_OUTPUTDIR: outputDir = p.nextString(); break;
				case KEY_OPENS:
					if (opens == null) {
						p.skipValue();
						break;
					}
					opens.clear();
					p.beginArray();
					while (p.hasNext())
						opens.add(p.nextString());
					p.endArray();
					break;
				default: p.skipValue();
			}
		}
		p.endObject();
		p.close();
		rootPath = conf.getParent();
		lastModified = conf.lastModified();
	}

	public static boolean save(Iterable<String> opens) {
		if (rootPath == null)
			return false;
		File f = new File(rootPath, PROJ);
        try {
			JsonWriter w = new JsonWriter(new BufferedWriter(new FileWriter(f)));
			w.setIndent("  ");
			w.beginObject();
			w.name(KEY_BUILDCMD);w.value(buildCmd);
			w.name(KEY_COMPILE);w.value(compileCmd);
			w.name(KEY_RUNCMD);w.value(runCmd);
			w.name(KEY_OUTPUTDIR);w.value(outputDir);
			w.name(KEY_OPENS);
			w.beginArray();
			if (opens != null)
				for (String s:opens)
					w.value(s);
			w.endArray();
			w.endObject();
			w.close();
			lastModified = f.lastModified();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean create(File f) {
		rootPath = f.getAbsolutePath();
		setDefault();
		new File(f, outputDir).mkdirs();
		return save(null);
	}

	public static void close() {
		rootPath = null;
	}

	public static long lastModified() {
		return lastModified;
	}

	public static void reload() throws IOException {
		File f = new File(rootPath, PROJ);
		if (f.lastModified()>lastModified)
			load(f, null);
	}
	/**
	 * Local environment variables
	 * $f  file name
	 * $e  file name without suffix
	 * $d  file directory path
	 * $p  project root path (if in project)
	 * $o  outputDir
	 */
	public static StringBuilder buildEnvironment(File file) {
		StringBuilder sb = new StringBuilder();
		if (rootPath!=null) {
			sb.append("p=\"");
			sb.append(Utils.escape(rootPath));
			sb.append("\";o=\"");
			sb.append(Utils.escape(outputDir));
			sb.append("\";");
		}
		if (file!=null) {
			sb.append("f=\"");
			String name = file.getName();
			sb.append(Utils.escape(name));
			sb.append("\";e=\"");
			// remove suffix
			int i = name.lastIndexOf('.');
			if (i>=0)
				name = name.substring(0, i);
			sb.append(Utils.escape(name));
			sb.append("\";d=\"");
			sb.append(Utils.escape(file.getParent()));
			sb.append("\";");
		}
		return sb;
	}
}
