package cn.rbc.codeeditor.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import android.widget.*;
import android.annotation.*;

public class HelperUtils {

	private static Toast _t;

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
		if (_t != null)
			_t.cancel();
		_t = t;
        if (t != null)
            t.show();
	}
/*
    public static int codePointAt(CharSequence seq, int pos, int limit) {
        // 1. 参数校验
        if (pos < 0 || pos >= limit || limit > seq.length()) {
            throw new IndexOutOfBoundsException(
                "pos=" + pos + ", limit=" + limit + ", length=" + seq.length()
            );
        }

        // 2. 获取当前字符
        char high = seq.charAt(pos);

        // 3. 检查是否为高代理项（High Surrogate）
        if (Character.isHighSurrogate(high)) {
            // 检查下一个字符是否在有效范围内
            if (pos + 1 < limit) {
                char low = seq.charAt(pos + 1);
                if (Character.isLowSurrogate(low)) {
                    // 4. 合法代理对：返回组合后的代码点
                    return Character.toCodePoint(high, low);
                }
            }
        }

        // 5. 单字符或无效代理：返回原始值
        return high;
    }*/
}
