package cn.rbc.codeeditor.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import android.widget.*;

import java.lang.ref.WeakReference;
import java.util.List;

public class HelperUtils {

	private static WeakReference<Toast> _t;

    public static float getDpi(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    // create bitmap from vector drawable
    public static Bitmap getBitmap(Context context, int res) {
        Bitmap bitmap = null;
		/* ContextCompat.getDrawable */
        Drawable vectorDrawable = context.getDrawable(res);
        if (vectorDrawable != null) {
            vectorDrawable.setAlpha(210);
            //vectorDrawable.setTint(fetchAccentColor(context));
            bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            vectorDrawable.draw(canvas);
            return bitmap;
        }
        return bitmap;
    }

	public static void show(Toast t) {
        Toast lastT;
        if (_t != null && (lastT = _t.get()) != null) {
            lastT.cancel();
        }
        if (t != null) {
            t.show();
            _t = new WeakReference<>(t);
        }
	}

    public static int lowerBound(List<Pair> list, int left, int right, int val) {
        while (left < right) {
            int mid = left + ((right - left) >> 1);
            if (list.get(mid).first >= val) {
                right = mid;
                continue;
            }
            left = mid + 1;
        }
        return left;
    }

    public static int lowerBound(List<Pair> list, int val) {
        return lowerBound(list, 0, list.size()-1, val);
    }
}