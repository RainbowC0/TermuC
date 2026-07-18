package cn.rbc.codeeditor.util;

import java.util.Arrays;

import static java.lang.System.*;

public class GapIntSetTest {
    public static void main(String[] args) {
        GapIntSet data = new GapIntSet();
        final int[] src = {6,9,10};
        out.print("test on ");
        out.println(Arrays.toString(src));
        final int[] tests = {
                7,2,    // 6,11,12
                7,-1,   // 6,8,9
                7,-3,   // 6,7
                10,1,   // 6,9,11
                10,-1,  // 6,9
                11,-1,  // 6,9,10
                10,-5   // 5
        };
        for (int i=0;i<tests.length;i+=2) {
            data.setData(Arrays.copyOf(src, src.length));
            data.shift(tests[i], tests[i+1]);
            out.println(data);
        }
        data.setData(new int[]{0,6,9,10});
        data.shift(10,-5);
        out.println(data);  // 0,5
    }
}