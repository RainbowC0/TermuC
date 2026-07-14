/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package cn.rbc.codeeditor.lang;
import cn.rbc.codeeditor.util.*;
import cn.rbc.codeeditor.lang.c.*;
import java.util.*;

/**
 * Singleton class containing the symbols and operators of the Java language
 */
public class LanguageJava extends Language{
		private static Language _theOne = null;
	
	private final static String[] keywords = {
		"import", "package", "new", "class", "interface", "extends", "implements", "enum",
		"public", "private", "protected", "static", "abstract", "final", "native", "volatile",
		"assert", "try", "throw", "throws", "catch", "finally", "instanceof", "super", "this",
		"if", "else", "for", "do", "while", "switch", "case", "default",
		"continue", "break", "return", "synchronized", "transient", "strictfp",
        "void", "boolean", "byte", "char", "short", "int", "long", "float", "double",
	};
    private final static String[] keynames = {"null", "true", "false"};
    private final static char[] BASIC_C_OPERATORS = {
        '(', ')', '{', '}', '.', ',', ';', '=', '+', '-',
        '/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
        '?', '~', '%', '^'
	};

	public static Language getInstance(){
		if(_theOne == null){
			_theOne = new LanguageJava();
		}
		return _theOne;
	}
	
	private LanguageJava(){
		setKeywords(keywords);
        setOperators(BASIC_C_OPERATORS);
        addKeynames(keynames);
	}
	
	/**
	 * Java has no preprocessors. Override base class implementation
	 */
	public boolean isLineAStart(char c){
		return false;
	}

    @Override
    public boolean isProgLang()
    {
        return true;
    }

    private Lexer lx = null;
    @Override
    public Lexer newLexer(CharSeqReader reader) {
        if (lx==null) {
            CppLexer cx = new CppLexer(reader);
            lx = cx;
            int l = keywords.length - 8;
            Set<String> keys = new HashSet<>();
            for (int i=0;i<l;i++) {
                keys.add(keywords[i]);
            }
            for (int i=0;i<keynames.length;i++) {
                keys.add(keynames[i]);
            }
            cx.keywords = keys;
            Set<String> types = new HashSet<>();
            for (int i=l,j=keywords.length;i<j;i++) {
                types.add(keywords[i]);
            }
            cx.types = types;
            cx.trackPreprocessor = false;
        } else lx.yyreset(reader);
        return lx;
	}
}
