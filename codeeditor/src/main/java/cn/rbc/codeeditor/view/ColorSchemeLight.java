/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */

package cn.rbc.codeeditor.view;


/**
 * Off-black on off-white background color scheme
 */
public class ColorSchemeLight extends ColorScheme {

    private static ColorScheme mColorScheme;

	private ColorSchemeLight(){
		//文字
		setColor(Colorable.FOREGROUND, OFF_BLACK);
		//背景
		setColor(Colorable.BACKGROUND, OFF_WHITE);
        setColor(Colorable.BACKGROUND_PURE, 0xFFFFFFFF);
		//选取文字
		setColor(Colorable.SELECTION_FOREGROUND, OFF_WHITE);
		//选取背景
		setColor(Colorable.SELECTION_BACKGROUND, 0xFF999999);
		//关键字
		setColor(Colorable.KEYWORD, BLUE_DARK);
		//函数名
		//setColor(Colorable.LITERAL, BLUE_LIGHT);
		//字符串、数字
		setColor(Colorable.STRING, 0xFFAA2200);
		setColor(Colorable.NUMBER, 0xFFAA2200);
		//类型
		setColor(Colorable.TYPE, BLUE_LIGHT); //0xFF2A40FF);
		//操作符
		setColor(Colorable.OPERATOR, 0xFF007C1F);
		//标点
		setColor(Colorable.NOTE, 0xFF0096FF);
		//宏
		setColor(Colorable.SECONDARY, GREY);
		//光标
		setColor(Colorable.CARET_DISABLED, 0xFF000000);
		//yoyo？
		setColor(Colorable.CARET_FOREGROUND, OFF_WHITE);
		//yoyo背景
		setColor(Colorable.CARET_BACKGROUND, 0xFF29B6F6);
		//当前行
		setColor(Colorable.LINE_HIGHLIGHT, 0x1E888888);

		//注释
		setColor(Colorable.COMMENT, GREEN_LIGHT);
		setColor(Colorable.HIGHLIGHT, 0x30aaaaaaaa);
		/*
		setColor(Colorable.FOREGROUND, OFF_BLACK);
		setColor(Colorable.BACKGROUND, OFF_WHITE);
		setColor(Colorable.SELECTION_FOREGROUND, OFF_WHITE);
		setColor(Colorable.CARET_FOREGROUND, OFF_WHITE);*/
	}

	private static final int OFF_WHITE = 0xFFF0F0ED;
	private static final int OFF_BLACK = 0xFF333333;

	private static final int GREY = 0xFF808080;
	private static final int GREEN_LIGHT = 0xFF009B00;
	//private static final int GREEN_DARK = 0xFF3F7F5F;
	private static final int BLUE_LIGHT = 0xFF0F9CFF;
	private static final int BLUE_DARK = 0xFF2C82C8;

    public static ColorScheme getInstance() {
        if (mColorScheme==null)
            mColorScheme = new ColorSchemeLight();
        return mColorScheme;
	}

	@Override
	public boolean isDark() {
		return false;
	}
}
