package cn.rbc.codeeditor.util;
import java.util.*;

public class GapIntSet {
	private int[] mData;
	private int size, gapSt, gapSz;

	public GapIntSet() {
		this(4);
	}

	public GapIntSet(int cap) {
		if (cap<1)
			throw new IllegalArgumentException("Illegal Capacity: "+cap);
		mData = new int[cap];
		size = 0;
	}

	/* copied from ArrayList */
	public void ensureCapacity(int cap) {
		int[] dt = mData;
		int oldCap = dt.length;
		if (cap>oldCap) {
			int newCap = (oldCap*3)/2+1;
			if (newCap < cap)
				newCap = cap;
			mData = Arrays.copyOf(dt, newCap);
		}
	}

	/**
	 * Toggle the int in mData
	 * @param l the target int
	 */
	public void toggle(int l) {
		int idx = set(l);
		if (idx >= 0)
			remove(idx);
	}

	/**
	 * Try to add an int to mData
	 * @param l the target int
	 * @return the index of int l if already exists, or its one's complement after added
	 */
	public int set(int l) {
		if (l>=gapSt+gapSz)
			l -= gapSz;
		else if (l>=gapSt) {
			refresh();
			gapSz = 0;
		}
		int idx = Arrays.binarySearch(mData, 0, size, l);
		if (idx<0)
			add(~idx, l);
		return idx;
	}

	private void add(int i, int l) {
		ensureCapacity(1+size);
		System.arraycopy(mData, i, mData, i+1, size - i);
		mData[i] = l;
		size++;
	}

	public void remove(int i) {
		int numMoved = size - i - 1;
		if (numMoved > 0)
			System.arraycopy(mData, i+1, mData, i, numMoved);
		size--;
	}

	private void refresh() {
		int i, j, t;
		if (size>0 && gapSz!=0) {
			int[] ls = mData;
			for (i=size-1; i>=0&&(j=ls[i])>=gapSt; i--)
				ls[i] = j+gapSz;
			j=i+1;
			if (j<size) {
				for (t=ls[j]; i>=0&&ls[i]>=t; i--);
				if (j!=(t=i+1)) {
					System.arraycopy(mData, j, mData, t, size - j);
					size += t-j;
				}
			}
		}
	}

	public int[] getData() {
		refresh();
		gapSz = 0;
		return Arrays.copyOf(mData, size);
	}

	public void clear() {
		size = 0;
	}

	public void setData(int[] d) {
		size = d.length;
		if (d.length==0)
			return;
		mData = d;
		gapSz = 0;
	}

	public int size() {
		return size;
	}

	public int get(int i) {
		if (i>=size||i<0)
			throw new IndexOutOfBoundsException(String.format(
				"Index %d out of bounds for length %d", i, size));
		int r = mData[i];
		return r>=gapSt?r+gapSz:r;
	}

	public void shift(int pivot, int off) {
		if (off==0||size==0||pivot>get(size-1)) {
            return;
        }
		if (gapSt<=Math.min(pivot,pivot+off)&&(gapSz>=0&&pivot<=gapSt+gapSz)) {
			gapSz += off;
            return;
		}
        refresh();
        int idx = Arrays.binarySearch(mData, 0, size, pivot-1);
        if (idx<0)
            idx = (~idx)-1;
        if (off>0) {
            gapSt = idx<0?0:mData[idx]+1;
            gapSz = ++idx==size?off:gapSt-mData[idx];
        } else {
            gapSt = (++idx==size?pivot:mData[idx]);
            idx = Arrays.binarySearch(mData, 0, idx, gapSt+off);
            if (idx<0) idx=~idx;
            gapSz = (idx==0?0:mData[idx-1]+1) - gapSt;
        }
        refresh();
        gapSz = off-gapSz;
        if (off<0) {
            gapSt = idx <= 0 ? 0 : mData[idx-1]+1;
        }
	}

	public int find(int l) {
		if (l>=gapSt+gapSz)
			l -= gapSz;
		else if (l>gapSt)
			l = gapSt;
		return Arrays.binarySearch(mData, 0, size, l);
	}

	public boolean isInGap(int l) {
		return gapSt<=l&&l<gapSt+gapSz;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		int s = size;
		if (s>0) {
			sb.append(get(0));
			for (int i=1; i<s; i++) {
				sb.append(", ");
				sb.append(get(i));
			}
		}
		sb.append(']');
		return sb.toString();
	}
}