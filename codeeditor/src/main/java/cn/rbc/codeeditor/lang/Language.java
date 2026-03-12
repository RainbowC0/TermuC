/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package cn.rbc.codeeditor.lang;

import cn.rbc.codeeditor.util.Tokenizer;

import java.util.HashMap;
import java.util.*;
import cn.rbc.codeeditor.util.*;

/**
 * Base class for programming language syntax.
 * By default, C-like symbols and operators are included, but not keywords.
 */
public abstract class Language
{
	public final static char EOF = '\uFFFF';
	public final static char NULL_CHAR = '\0';
	public final static char NEWLINE = '\n';
	public final static char BACKSPACE = '\b';
	public final static char TAB = '\t';
    public final static char DELETE = '\177';
	public final static char GLYPH_NEWLINE = '\u21b5';
	public final static char GLYPH_SPACE = '\u00b7';
	public final static char GLYPH_TAB = '\u00bb';


	private final static char[] BASIC_C_OPERATORS = {
		'(', ')', '{', '}', '.', ',', ';', '=', '+', '-',
		'/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
		'?', '~', '%', '^'
	};


	protected Set<String> _keywordsMap = new HashSet<String>(0);
    protected Set<String> _typeSet = new HashSet<>(0);
	protected HashMap<String, Integer> _namesMap = new HashMap<String, Integer>();
	// protected HashMap<String, Integer> _keynamesMap = new HashMap<String, Integer>(0);
	protected HashMap<String, String[]> _basesMap = new HashMap<String, String[]>(0);
	protected HashMap<String, Integer> _usersMap = new HashMap<String, Integer>(0);//userWord是那种用户自己定义的标识符
	protected HashSet<Character> _operatorsMap = generateOperators(BASIC_C_OPERATORS);
	
	private ArrayList<String> _userCache = new ArrayList<String>();
	private String[] _userWords=new String[0];
	private String[] _keyword;
	private String[] _name = new String[0];

	public void updateUserWord()
	{
		String[] uw = new String[_userCache.size()];
		_userWords = _userCache.toArray(uw);
	}

	public String[] getUserWord()
	{
		return  _userWords;
	}

	public String[] getNames()
	{
		return _name;
	}
	
	public String[] getBasePackage(String name)
	{
		return _basesMap.get(name);
	}
	
	public String[] getKeywords()
	{
		return _keyword;
	}

	public Lexer newLexer(CharSeqReader reader) {
		return null;
	}

	/*//子类需要实现该方法
	public LexerTokenizer getTokenizer() {
		return LexerTokenizer.getInstance();
	}*/

	//子类需要实现该方法
	public Formatter getFormatter() {
		return DefFormatter.getInstance();
	}

	public void setKeywords(String[] keywords)
	{
		_keywordsMap.clear();
        for (int i=0;i<keywords.length;i++) {
            _keywordsMap.add("[K]"+keywords[i]);
        }
        _keyword = new String[_keywordsMap.size()];
        _keywordsMap.toArray(_keyword);
	}
/*
	public String[] getKeynames() {
		return _keynames;
	}*/

	public void addKeynames(String[] keynames) {
		//_namesMap.clear();
		for (String keynam:keynames) {
			_namesMap.put(keynam, Tokenizer.KEYNAME);
		}
		_name = new String[_namesMap.size()];
		_namesMap.keySet().toArray(_name);
	}

	public void addNames(String[] names)
	{
		for (String keynam:names) {
			_namesMap.put(keynam, Tokenizer.NAME);
		}
		_name = new String[_namesMap.size()];
		_namesMap.keySet().toArray(_name);
	}

	public void setTypes(Set<String> types) {
        _typeSet = types;
	}

    public Set<String> getTypes() {
        return _typeSet;
    }

	public void addBasePackage(String name, String[] names)
	{
		_basesMap.put(name, names);
	}

	public void clearUserWord()
	{
		_userCache.clear();
		_usersMap.clear();
	}

	public void addUserWord(String name)
	{
		if(!_userCache.contains(name) && !_namesMap.containsKey(name))
			_userCache.add(name);
		_usersMap.put(name, Tokenizer.NAME);
	}

	protected void setOperators(char[] operators)
	{
		_operatorsMap = generateOperators(operators);
	}

	private HashSet<Character> generateOperators(char[] operators)
	{
		HashSet<Character> operatorsMap = new HashSet<Character>(operators.length);
		for (int i = 0; i < operators.length; ++i)
		{
			operatorsMap.add(operators[i]);
		}
		return operatorsMap;
	}

	public final boolean isOperator(char c)
	{
		return _operatorsMap.contains(c);
	}

	public final boolean isKeyword(String s)
	{
		return _keywordsMap.contains(s);
	}

	public final boolean isName(String s)
	{
		return _namesMap.containsKey(s);
	}

	public final Integer type(String s) {
		return _namesMap.get(s);
	}

	public final boolean isKeyname(String s) {
		Integer b = _namesMap.get(s);
		return b!=null && b.intValue() == Tokenizer.KEYNAME;
	}

	public final boolean isType(String s) {
		return _typeSet.contains(s);
	}

	public final boolean isBasePackage(String s)
	{
		return _basesMap.containsKey(s);
	}

	public final boolean isBaseWord(String p, String s)
	{
		String[] pkg= _basesMap.get(p);
		for (String n:pkg)
		{
			if (n.equals(s))
				return true;
		}
		return false;
	}

	public final boolean isUserWord(String s)
	{
		return _usersMap.containsKey(s);
	}

	/**
	 * 空白符
	 * @param c
	 * @return
     */
	public boolean isWhitespace(char c)
	{
		return (c == ' ' || c == '\n' || c == '\t' ||
			c == '\r' || c == '\f' || c == EOF);
	}

	/**
	 * 点运算符
	 * @param c
	 * @return
     */
	public boolean isSentenceTerminator(char c)
	{
		return (c == '.');
	}

	/**
	 * 斜杠
	 * @param c
     * @return
     */
	public boolean isEscapeChar(char c)
	{
		return (c == '\\');
	}

	/**
	 * Derived classes that do not do represent C-like programming languages
	 * should return false; otherwise return true
	 */
	public boolean isProgLang()
	{
		return false;
	}

	/**
	 * Whether the word after c is a token
	 */
	public boolean isWordStart(char c)
	{
		return false;
	}

	/**
	 * Whether cSc is a token, where S is a sequence of characters that are on the same line
	 * 字符串引号
	 */
	public boolean isDelimiterA(char c)
	{
		return (c == '"');
	}

	/**
	 * Same concept as isDelimiterA(char), but Language and its subclasses can
	 * specify a second type of symbol to use here
	 * 单个字符引号
	 */
	public boolean isDelimiterB(char c)
	{
		return (c == '\'');
	}

	/**
	 * Whether cL is a token, where L is a sequence of characters until the end of the line
	 * 宏定义
	 */
	public boolean isLineAStart(char c)
	{
		return (c == '#');
	}

	/**
	 * Same concept as isLineAStart(char), but Language and its subclasses can
	 * specify a second type of symbol to use here
	 */
	public boolean isLineBStart(char c)
	{
		return false;
	}

	/**
	 * Whether c0c1L is a token, where L is a sequence of characters until the end of the line
	 * 单行注释
	 */
	public boolean isLineStart(char c0, char c1)
	{
		return (c0 == '/' && c1 == '/');
	}

	/**
	 * Whether c0c1 signifies the start of a multi-line token
	 * 多行注释开始
	 */
	public boolean isMultilineStartDelimiter(char c0, char c1)
	{
		return (c0 == '/' && c1 == '*');
	}

	/**
	 * Whether c0c1 signifies the end of a multi-line token
	 * 多行注释结束
	 */
	public boolean isMultilineEndDelimiter(char c0, char c1)
	{
		return (c0 == '*' && c1 == '/');
	}
}
