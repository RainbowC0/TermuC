package cn.rbc.codeeditor.util;

public class Range implements Comparable<Range> {
	public int stl, stc, enl, enc;
	public String msg;

    @Override
    public int compareTo(Range p1)
    {
        int dif = stl - p1.stl;
        return dif != 0 ? dif : stc - p1.stc;
    }
}
