package cn.rbc.termuc;
import android.widget.*;
import android.view.*;
import android.content.*;
import java.io.*;
import android.text.*;
import java.util.*;
import android.os.*;
import android.util.*;

public class HeaderAdapter extends ArrayAdapter<String>
implements SpinnerAdapter, Iterable<String>
{
    private final static String BITS = "bs";
    private ArrayList<Boolean> bs;

	public HeaderAdapter(Context context, int id) {
		super(context, id);
        bs = new ArrayList<>();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = super.getView(position, convertView, parent);
		if (v instanceof TextView) {
			TextView tv = (TextView)v;
            String s = new File(tv.getText().toString()).getName();
			tv.setText(getEdit(position)?"*".concat(s):s);
		}
		return v;
	}

    public void setEdit(int idx, boolean b) {
        int i = bs.size();
        if (i > idx)
            bs.set(idx, b);
        else {
            for (;i < idx;i++) {
                bs.add(false);
            }
            bs.add(true);
        }
    }

    public boolean getEdit(int idx) {
        return idx<bs.size() && bs.get(idx);
    }

    public void store(Bundle bd) {
        bd.putSerializable(BITS, bs);
    }

    public void load(Bundle bd) {
        bs = (ArrayList<Boolean>)bd.getSerializable(BITS);
    }

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return super.getView(position, convertView, parent);
	}

    @Override
    public void remove(String object) {
        final int pos = this.getPosition(object);
        if (bs.size() > pos)
            bs.remove(pos);
        super.remove(object);
    }

	@Override
	public Iterator<String> iterator() {
		return new Iterator<String>() {
			private int curr = 0;
			public boolean hasNext() {
				return curr < getCount();
			}
			public String next() {
				return getItem(curr++);
			}
			public void remove() {}
		};
	}

	@Override
	public Spliterator<String> spliterator() {
		return null;
	}
}
