/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package cn.rbc.codeeditor.view;

import cn.rbc.codeeditor.util.Tokenizer;
import cn.rbc.codeeditor.util.TextWarriorException;

import java.util.HashMap;

import android.graphics.*;

public abstract class ColorScheme {
    public enum Colorable {
        FOREGROUND, BACKGROUND, BACKGROUND_PURE, SELECTION_FOREGROUND,
        SELECTION_BACKGROUND, CARET_FOREGROUND, CARET_BACKGROUND, CARET_DISABLED,
        LINE_HIGHLIGHT, NON_PRINTING_GLYPH, COMMENT, KEYWORD, NAME, NUMBER, STRING,
        SECONDARY, TYPE, NOTE, OPERATOR
    }

    protected HashMap<Colorable, Integer> _colors = generateDefaultColors();

    public void setColor(Colorable colorable, int color) {
        _colors.put(colorable, color);
    }

    public int getColor(Colorable colorable) {
        Integer color = _colors.get(colorable);
        if (color == null) {
            TextWarriorException.fail("Color not specified for " + colorable);
            return 0;
        }
        return color;
    }

    // Currently, color scheme is tightly coupled with semantics of the token types
    public int getTokenColor(int tokenType) {
        Colorable element;
        switch (tokenType) {
            case Tokenizer.NORMAL:
                element = Colorable.FOREGROUND;
                break;
            case Tokenizer.KEYWORD:
                element = Colorable.KEYWORD;
                break;
            case Tokenizer.TYPE:
                element = Colorable.TYPE;
                break;
            case Tokenizer.NAME:
                element = Colorable.NAME;
                break;
            case Tokenizer.DOUBLE_SYMBOL_LINE: //fall-through
            case Tokenizer.DOUBLE_SYMBOL_DELIMITED_MULTILINE:
                element = Colorable.COMMENT;
                break;
            case Tokenizer.SINGLE_SYMBOL_DELIMITED_A: //fall-through
            case Tokenizer.SINGLE_SYMBOL_DELIMITED_B:
            case Tokenizer.KEYNAME:
                element = Colorable.STRING;
                break;
            case Tokenizer.NUMBER:
                element = Colorable.NUMBER;
                break;
            case Tokenizer.NOTE:
                element = Colorable.NOTE;
                break;
            case Tokenizer.OPERATOR:
                element = Colorable.OPERATOR;
                break;
            case Tokenizer.SINGLE_SYMBOL_LINE_A: //fall-through
            case Tokenizer.SINGLE_SYMBOL_WORD:
                element = Colorable.SECONDARY;
                break;
            case Tokenizer.SINGLE_SYMBOL_LINE_B: //类型
                element = Colorable.NAME;
                break;
            default:
                TextWarriorException.fail("Invalid token type");
                element = Colorable.FOREGROUND;
                break;
        }
        return getColor(element);
    }

    /**
     * Whether this color scheme uses a dark background, like black or dark grey.
     */
    public abstract boolean isDark();

    private HashMap<Colorable, Integer> generateDefaultColors() {
        // High-contrast, black-on-white color scheme
        HashMap<Colorable, Integer> colors = new HashMap<Colorable, Integer>(Colorable.values().length);
        colors.put(Colorable.FOREGROUND, BLACK);//前景色
        colors.put(Colorable.BACKGROUND, WHITE);
        colors.put(Colorable.SELECTION_FOREGROUND, WHITE);//选择文本的前景色
        colors.put(Colorable.SELECTION_BACKGROUND, 0xFF97C024);//选择文本的背景色
        colors.put(Colorable.CARET_FOREGROUND, WHITE);
        colors.put(Colorable.CARET_BACKGROUND, BLACK);
        colors.put(Colorable.CARET_DISABLED, GREY);
        colors.put(Colorable.LINE_HIGHLIGHT, 0x20888888);

        colors.put(Colorable.NON_PRINTING_GLYPH, 0xFFBBBBBB);//行号
        colors.put(Colorable.COMMENT, OLIVE_GREEN); //注释
        colors.put(Colorable.KEYWORD, BLUE); //关键字
        colors.put(Colorable.NAME, GREY); // Eclipse default color
        colors.put(Colorable.NUMBER, PINK); // 数字
        colors.put(Colorable.STRING, DARK_RED); //字符串
        colors.put(Colorable.SECONDARY, 0xff6f008a);//宏定义
        return colors;
    }

    // In ARGB format: 0xAARRGGBB
    private static final int BLACK = 0xFF000000;
    private static final int BLUE = 0xFF0000FF;
    private static final int DARK_RED = 0xFFA31515;
    private static final int PINK = 0xFFD040DD;
    private static final int GREY = 0xFF808080;
    private static final int OLIVE_GREEN = 0xFF3F7F5F;
    private static final int WHITE = 0xFFFFFFE0;
    private static final int LIGHT_BLUE2 = 0xFF40B0FF;

    static final int[] DIAG = {Color.RED, Color.MAGENTA, 0xFF00BB00, Color.CYAN};
}
