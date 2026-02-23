package cn.rbc.termuc;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import android.net.*;
import android.provider.*;
import android.database.*;

public class FileAdapter extends BaseAdapter implements Comparator<FileItem>, FileFilter
{
	private Context mCont;
	private static FileItem parent;
	private List<FileItem> mData;
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
		return mData.get(p1);
	}

	@Override
	public int getCount() {
		return mNRoot ? mData.size()+1 : mData.size();
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
		if (FileHelper.isTermuxFile(path)) {
			Uri uri = Application.getInstance().treeUri;
			String root = DocumentsContract.getTreeDocumentId(uri);
			mNRoot = !root.equals(path.getAbsolutePath());
		} else {
			mNRoot = !Utils.ROOT.equals(path);
		}
		if (parent==null && mNRoot)
			parent = new FileItem(R.drawable.ic_folder_24, "..");
		mPath = path;
		mData = FileHelper.lists(path, this);
		Collections.sort(mData, this);
	}

	public int compare(FileItem a, FileItem b) {
		boolean ad=(a.icon==R.drawable.ic_folder_24), bd=(b.icon==R.drawable.ic_folder_24);
		return ad==bd?
			a.name.compareToIgnoreCase(b.name)
			:ad?-1:1;
	}

	public boolean accept(File p1) {
		FileFilter ff;
		return (Application.show_hidden || !p1.isHidden()) && ((ff=mFilter)==null || ff.accept(p1));
	}

	
    static class ViewHolder {
        ImageView img;
        TextView txv;
    }
}
