package cn.rbc.termuc;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class FileAdapter extends BaseAdapter implements Comparator<File>, FileFilter
{
	private Context mCont;
	private static FileItem parent;
	private FileItem[] mData;
	private boolean mNRoot;
	private File mPath;
	private LayoutInflater mInflater;
	private FileFilter mFilter;

	public FileAdapter(Context context, File path) {
		this(context, path, null);
	}

	public FileAdapter(Context context, File path, FileFilter filter) {
		super();
		mCont = context;
		mInflater = LayoutInflater.from(context);
		mFilter = filter;
		setPath(path);
	}

	@Override
	public long getItemId(int p1) {
		return p1;
	}

	@Override
	public FileItem getItem(int p1) {
		if (mNRoot) {
			if (p1==0) return parent;
			else p1--;
		}
		return mData[p1];
	}

	@Override
	public int getCount() {
		return mNRoot ? mData.length+1 : mData.length;
	}

	@Override
	public View getView(int pos, View convert, ViewGroup parent) {
        ImageView img;
        TextView txv;
        ViewHolder vh;
		if (convert == null) {
			convert = mInflater.inflate(R.layout.file_item, parent, false);
		    img = convert.findViewById(R.id.file_icon);
            txv = convert.findViewById(R.id.file_name);
            vh = new ViewHolder();
            vh.img = img;
            vh.txv = txv;
            convert.setTag(vh);
        } else {
            vh = (ViewHolder)convert.getTag();
            img = vh.img;
            txv = vh.txv;
        }
		FileItem fitm = getItem(pos);
		img.setImageResource(fitm.icon);
		txv.setText(fitm.name);
		return convert;
	}

	public void setPath(File path) {
		mNRoot = !Utils.ROOT.equals(path);
		if (parent==null && mNRoot)
			parent = new FileItem(R.drawable.ic_folder_24, "..");
		mPath = path;
		File[] lst = path.listFiles(this);
		if (lst==null)
			lst = new File[0];
		Arrays.sort(lst, this);
		mData = new FileItem[lst.length];
		for (int i=0,l=lst.length;i<l;i++) {
			File f = lst[i];
			mData[i] = new FileItem(computeIcon(f), f.getName());
		}
	}

	public int compare(File a, File b) {
		boolean ad=a.isDirectory(), bd=b.isDirectory();
		return ad==bd?
			a.getName().compareToIgnoreCase(b.getName())
			:ad?-1:1;
	}

	public boolean accept(File p1) {
		FileFilter ff;
		return (Application.show_hidden || p1.getName().charAt(0) != '.') && ((ff=mFilter)==null || ff.accept(p1));
	}

	private static int computeIcon(File f) {
		if (f.isDirectory())
			return R.drawable.ic_folder_24;
		else {
			String n = f.getName();
			if (n.endsWith(".c")||isCpp(n)
				||n.endsWith(".h")||n.endsWith(".hpp"))
				return R.drawable.ic_code_24;
		}
		return R.drawable.ic_file_24;
	}

	public final static boolean isCpp(String name) {
		return name.endsWith(".cpp") || name.endsWith(".cxx") || name.endsWith(".cc");
	}

    static class ViewHolder {
        ImageView img;
        TextView txv;
    }
}
