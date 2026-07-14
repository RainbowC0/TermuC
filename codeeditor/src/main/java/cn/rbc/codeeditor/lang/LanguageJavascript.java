/*
 * Copyright (c) 2011 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package cn.rbc.codeeditor.lang;

import cn.rbc.codeeditor.lang.c.CppLexer;
import cn.rbc.codeeditor.util.*;
import java.util.*;

/**
 * Singleton class containing the symbols and operators of the Javascript language
 */
public class LanguageJavascript extends Language {
	private static Language _theOne = null;
	
	private final static String[] keywords = {
		"as", "async", "await", "break", "case", "catch",
		"class", "const", "continue", "debugger", "default", "delete", "do",
		"else", "enum", "export", "extends", "false",
		"finally", "for", "from", "function", "get", "if", "implements",
		"import", "in", "instanceof", "interface", "let",
		"new", "null", "of", "package", "private", "protected", "public", "return",
		"set", "static", "super", "switch", "synchronized", "this", "throw",
	    "true", "try", "typeof", "var", "void", "while", "with", "yield"
	};

	public static Language getInstance(){
		if(_theOne == null){
			_theOne = new LanguageJavascript();
		}
		return _theOne;
	}
	
	private LanguageJavascript(){
		setKeywords(keywords);
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
            int l = keywords.length;
            Set<String> keys = new HashSet<>();
            for (int i=0;i<l;i++) {
                keys.add(keywords[i]);
            }/*
            for (int i=0;i<keynames.length;i++) {
                keys.add(keynames[i]);
            }*/
            cx.keywords = keys;
            Set<String> types = new HashSet<>();
            for (int i=l,j=keywords.length;i<j;i++) {
                types.add(keywords[i]);
            }
            cx.types = types;
            cx.trackPreprocessor = false;
            cx.backquoteString = true;
        } else lx.yyreset(reader);
        return lx;
	}
}
