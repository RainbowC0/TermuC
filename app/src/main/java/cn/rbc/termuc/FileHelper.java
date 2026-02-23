package cn.rbc.termuc;
import java.io.*;
import android.net.*;
import android.content.*;
import android.database.*;
import android.provider.*;
import java.util.*;

import static android.provider.DocumentsContract.Document.*;

public class FileHelper {
	
	public static boolean isTermuxFile(File f) {
		return f.getAbsolutePath().startsWith("/data/data/com.termux");
	}

	public final static List<FileItem> lists(File f, FileFilter filter) {
		if (!isTermuxFile(f)) {
			File[] fs = f.listFiles(filter);
			FileItem[] fis;
			if (fs == null || fs.length == 0)
				fis = new FileItem[0];
			else {
				fis = new FileItem[fs.length];
				for (int i=0,l=fs.length; i<l; i++) {
					File fi = fs[i];
					fis[i] = toFileItem(fi.isDirectory(), fi.getName());
				}
			}
			return Arrays.asList(fis);
		}
		Application app = Application.getInstance();
		ContentResolver sov = app.getContentResolver();
		Uri u = DocumentsContract.buildChildDocumentsUriUsingTree(app.treeUri, f.getAbsolutePath());
		Cursor cur = sov.query(u, new String[]{
			COLUMN_DOCUMENT_ID,
			COLUMN_MIME_TYPE
		},
		null, null, null);
		if (cur == null) return Arrays.asList(new FileItem[0]);
		ArrayList<FileItem> fis = new ArrayList<>(cur.getCount());
		if (cur.moveToFirst())
		do {
			File fi = new File(cur.getString(0));
			if (filter == null || filter.accept(fi))
				fis.add(toFileItem(MIME_TYPE_DIR.equals(cur.getString(1)), fi.getName()));
		} while (cur.moveToNext());
		cur.close();
		return fis;
	}

	private static FileItem toFileItem(boolean dir, String n) {
		int icon;
		if (dir)
			icon = R.drawable.ic_folder_24;
		else if (n.endsWith(".c")||isCpp(n)
			||n.endsWith(".h")||n.endsWith(".hpp"))
			icon = R.drawable.ic_code_24;
		else icon = R.drawable.ic_file_24;
		return new FileItem(icon, n);
	}

	public final static boolean isCpp(String name) {
		return name.endsWith(".cpp") || name.endsWith(".cxx") || name.endsWith(".cc");
	}

	public final static InputStream openInputStream(File f) throws IOException {
		if (!isTermuxFile(f))
			return new FileInputStream(f);
		Application app = Application.getInstance();
		Uri uri = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		ContentResolver crv = app.getContentResolver();
		if (null == crv.query(uri, null, null, null, null)) {
			uri = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getParent());
			uri = DocumentsContract.createDocument(crv, uri, "text/plain", f.getName());
		}
		return crv.openInputStream(uri);
	}

	public final static OutputStream openOutputStream(File f) throws IOException {
		if (!isTermuxFile(f))
			return new FileOutputStream(f);
		Application app = Application.getInstance();
		Uri uri = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		ContentResolver crv = app.getContentResolver();
		if (null == crv.query(uri, null, null, null, null)) {
			uri = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getParent());
			uri = DocumentsContract.createDocument(crv, uri, "text/plain", f.getName());
		}
		return crv.openOutputStream(uri);
	}

	public final static long lastModified(File f) {
		if (!isTermuxFile(f))
			return f.lastModified();
		Application app = Application.getInstance();
		Uri uri = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		Cursor cur = app.getContentResolver().query(uri, new String[]{
			COLUMN_LAST_MODIFIED
		}, null, null, null);
		if (cur == null || !cur.moveToFirst()) return 0;
		long l = cur.getLong(0);
		cur.close();
		return l;
	}

	public final static long length(File f) {
		if (!isTermuxFile(f)) {
			return f.length();
		}
		Application app = Application.getInstance();
		Uri u = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		Cursor cur = app.getContentResolver().query(u, new String[]{
			COLUMN_SIZE
		}, null, null, null);
		long l = 0;
		if (cur != null) {
			if (cur.moveToFirst()) {
				l = cur.getLong(0);
			}
			cur.close();
		}
		return l;
	}

	public final static boolean isDirectory(File f) {
		if (!isTermuxFile(f))
			return f.isDirectory();
		Application app = Application.getInstance();
		Uri u = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		Cursor cur = app.getContentResolver().query(u, new String[]{
														COLUMN_MIME_TYPE
													}, null, null, null);
		boolean isd = false;
		if (cur != null) {
			if (cur.moveToFirst())
				isd = MIME_TYPE_DIR.equals(cur.getString(0));
			cur.close();
		}
		return isd;
	}

	public final static boolean isFile(File f) {
		if (!isTermuxFile(f))
			return f.isFile();
		Application app = Application.getInstance();
		Uri u = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		Cursor cur = app.getContentResolver().query(u, new String[]{
			COLUMN_MIME_TYPE
		}, null, null, null);
		boolean isd = false;
		if (cur != null) {
			if (cur.moveToFirst())
				isd = !MIME_TYPE_DIR.equals(cur.getString(0));
			cur.close();
		}
		return isd;
	}

	public final static Object[] queryFile(File f, String[] projs) {
		if (isTermuxFile(f)) {
			Application app = Application.getInstance();
			Uri u = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
			Cursor cur = app.getContentResolver().query(u, projs, null, null, null);
			Object[] ret = null;
			if (cur == null) return ret;
			if (cur.moveToFirst()) {
				ret = new Object[projs.length];
				for (int i=0;i<projs.length;i++) {
					Object j;
					switch (cur.getType(i)) {
						case Cursor.FIELD_TYPE_INTEGER:
							j = cur.getLong(i);
							break;
						case Cursor.FIELD_TYPE_FLOAT:
							j = cur.getDouble(i);
							break;
						case Cursor.FIELD_TYPE_STRING:
							j = cur.getString(i);
							break;
						default:
							j = null;
							break;
					}
					ret[i] = j;
				}
			}
			cur.close();
			return ret;
		}
		Object[] ret = new Object[projs.length];
		for (int i=0;i<projs.length;i++) {
			switch (projs[i]) {
				case COLUMN_DISPLAY_NAME:
					ret[i] = f.getName();
					break;
				case COLUMN_SIZE:
					ret[i] = f.length();
					break;
				case COLUMN_LAST_MODIFIED:
					ret[i] = f.lastModified();
					break;
				case COLUMN_DOCUMENT_ID:
					ret[i] = f.getAbsolutePath();
					break;
				case COLUMN_MIME_TYPE:
					ret[i] = f.isDirectory() ? MIME_TYPE_DIR : "text/plain";
					break;
			}
		}
		return ret;
	}

	public final static boolean removeFiles(File f) {
		if (!isTermuxFile(f)) {
			return removeCommonFiles(f);
		}
		Application app = Application.getInstance();
		Uri u = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		ContentResolver crv = app.getContentResolver();
		Cursor cur = crv.query(u, new String[] {
			COLUMN_DOCUMENT_ID,
			COLUMN_MIME_TYPE
		}, null, null, null);
		if (cur != null) {
			try {
				if (cur.moveToFirst()) {
					if (MIME_TYPE_DIR.equals(cur.getString(1)))
						removeTermuxFiles(crv, app.treeUri, f.getAbsolutePath());
					return DocumentsContract.deleteDocument(crv, DocumentsContract.buildDocumentUriUsingTree(app.treeUri, cur.getString(0)));
				}
			} catch (FileNotFoundException fne) {
				fne.printStackTrace();
			} finally {
				cur.close();
			}
		}
		return false;
	}

	private static boolean removeCommonFiles(File dir) {
		File[] fl = dir.listFiles();
		if (fl != null)
			for (File f:fl)
				removeCommonFiles(f);
		return dir.delete();
	}

	private static boolean removeTermuxFiles(ContentResolver crv, Uri tree, String dir) throws FileNotFoundException {
		Uri u = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir);
		Cursor cur = crv.query(u, new String[]{
			COLUMN_DOCUMENT_ID,
			COLUMN_MIME_TYPE
		}, null, null, null);
		boolean ret = true;
		if (cur != null) {
			if(cur.moveToFirst())
				do {
					String docId = cur.getString(0);
					if (MIME_TYPE_DIR.equals(cur.getString(1)))
						removeTermuxFiles(crv, tree, docId);
					ret &= DocumentsContract.deleteDocument(crv, DocumentsContract.buildDocumentUriUsingTree(tree, docId));
				} while(cur.moveToNext());
			cur.close();
		}
		return ret;
	}

	public static boolean createFile(File f, boolean isDir) throws IOException {
		if (!isTermuxFile(f)) {
			return isDir ? f.mkdir() : f.createNewFile();
		}
		Application app = Application.getInstance();
		ContentResolver crv = app.getContentResolver();
		Uri parent = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getParent());
		return null != DocumentsContract.createDocument(crv, parent, isDir ? MIME_TYPE_DIR : "text/plain", f.getName());
	}

	public static boolean rename(File f, String n) {
		if (!isTermuxFile(f)) {
			return f.renameTo(new File(f.getParentFile(), n));
		}
		Application app = Application.getInstance();
		ContentResolver crv = app.getContentResolver();
		Uri file = DocumentsContract.buildDocumentUriUsingTree(app.treeUri, f.getAbsolutePath());
		try {
			return null != DocumentsContract.renameDocument(crv, file, n);
		} catch (FileNotFoundException fne) {
			return false;
		} catch (UnsupportedOperationException uo) {
			return false;
		}
	}
}
