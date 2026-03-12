/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package cn.rbc.codeeditor.util;

import cn.rbc.codeeditor.lang.Language;
import cn.rbc.codeeditor.lang.LanguageNonProg;

import java.util.ArrayList;
import java.util.List;

import android.util.*;
import cn.rbc.codeeditor.view.*;

/**
 * Does lexical analysis of a text for C-like languages.
 * The programming language syntax used is set as a static class variable.
 */
public class Tokenizer {
    public final static int
	UNKNOWN = -1,
    NORMAL = 0,
    KEYWORD = 1,
    OPERATOR = 2,
	NOTE = 3,
    NAME = 4,
    NUMBER = 5,
	KEYNAME = 6,
	TYPE = 7;
	final static int NUM_HEX = 1;
	final static int NUM_FLOAT = 2;
	final static int NUM_EXP = 4;
    /**
     * A word that starts with a special symbol, inclusive.
     * Examples:
     * :ruby_symbol
     */
    public final static int SINGLE_SYMBOL_WORD = 10;
    /**
     * Tokens that extend from a single start symbol, inclusive, until the end of line.
     * Up to 2 types of symbols are supported per language, denoted by A and B
     * Examples:
     * #include "myCppFile"
     * #this is a comment in Python
     * %this is a comment in Prolog
     */
    public final static int SINGLE_SYMBOL_LINE_A = 20;
    public final static int SINGLE_SYMBOL_LINE_B = 21;
    /**
     * Tokens that extend from a two start symbols, inclusive, until the end of line.
     * Examples:
     * //this is a comment in C
     */
    public final static int DOUBLE_SYMBOL_LINE = 30;
    /**
     * Tokens that are enclosed between a start and end sequence, inclusive,
     * that can span multiple lines. The start and end sequences contain exactly
     * 2 symbols.
     * Examples:
     * {- this is a...
     * ...multi-line comment in Haskell -}
     */
    public final static int DOUBLE_SYMBOL_DELIMITED_MULTILINE = 40;
    /**
     * Tokens that are enclosed by the same single symbol, inclusive, and
     * do not span over more than one line.
     * Examples: 'c', "hello world"
     */
    public final static int SINGLE_SYMBOL_DELIMITED_A = 50;
    public final static int SINGLE_SYMBOL_DELIMITED_B = 51;
   // public final static int MAX_KEYWORD_LENGTH = 63;
    private static Language _globalLanguage = LanguageNonProg.getInstance();

    LexCallback _callback = null;
    private Document _hDoc;
    private LexThread _workerThread = null;

    public Tokenizer(LexCallback callback) {
        _callback = callback;
    }

    synchronized public static Language getLanguage() {
        return _globalLanguage;
    }

    synchronized public static void setLanguage(Language lang) {
        _globalLanguage = lang;
    }

    public void tokenize(Document hDoc) {
        if (!Tokenizer.getLanguage().isProgLang()) {
			List<Pair> tokens = new ArrayList<>();
			tokens.add(new Pair(0, Tokenizer.NORMAL));
			tokenizeDone(tokens);
            return;
		}

        //tokenize will modify the state of hDoc; make a copy
        setDocument(hDoc);
        if (_workerThread == null) {
            _workerThread = new LexThread();
            _workerThread.start();
        } else
            _workerThread.restart();
    }

    void tokenizeDone(List<Pair> result) {
        if (_callback != null)
            _callback.lexDone(result);
        _workerThread = null;
    }

    public void cancelTokenize() {
        if (_workerThread != null) {
            _workerThread.abort();
            _workerThread = null;
        }
    }

    public synchronized Document getDocument() {
        return _hDoc;
    }

    public synchronized void setDocument(Document hDoc) {
        _hDoc = hDoc;
    }

    public interface LexCallback {
        public void lexDone(List<Pair> results);
    }

    private class LexThread extends Thread {
        /**
         * can be set by another thread to stop the scan immediately
         */
        private final Flag _abort;
        private boolean rescan = false;
        /**
         * A collection of Pairs, where Pair.first is the start
         * position of the token, and Pair.second is the type of the token.
         */
        private ArrayList<Pair> _tokens;

        public LexThread() {
            _abort = new Flag();
        }

        @Override
        public void run() {
            do {
                rescan = false;
                _abort.clear();
                tokenize();
            } while (rescan);

            if (!_abort.isSet())
                // lex complete
                tokenizeDone(_tokens);
        }

        public void restart() {
            rescan = true;
            _abort.set();
        }

        public void abort() {
            _abort.set();
        }

        /**
         * Scans the document referenced by _lexManager for tokens.
         * The result is stored internally.
         */
        private void tokenize() {
			Language language = Tokenizer.getLanguage();
			ArrayList<Pair> tokens = new ArrayList<>();
			Lexer lexer=language.newLexer(new CharSeqReader(_hDoc));
			int type=-1, ltype=-1, ttype=-1, ltp=-1;
			int idx=0;
			String identifier=null;//存储标识符
			language.clearUserWord();
			while (type!=Lexer.EOF && !_abort.isSet()){
				try {
					type=lexer.yylex();
					if (type!=ltp) {
						switch (type)
						{
							case Lexer.KEYWORD:
                                ltype = KEYWORD;
								break;
							case Lexer.TYPE:
                                ltype = TYPE;
								break;
							case Lexer.COMMENT:
                                ltype = DOUBLE_SYMBOL_DELIMITED_MULTILINE;
								break;
								// macro
							case Lexer.PRETREATMENT_LINE:
							case Lexer.DEFINE_LINE:
                                ltype = SINGLE_SYMBOL_LINE_A;
								break;
								// string, char
							case Lexer.STRING_LITERAL:
							case Lexer.CHARACTER_LITERAL:
                                ltype = SINGLE_SYMBOL_DELIMITED_A;
								break;
								// number
							case Lexer.INTEGER_LITERAL:
							case Lexer.FLOATING_POINT_LITERAL:
                                ltype = NUMBER;
								break;
							case Lexer.IDENTIFIER:
								identifier=lexer.yytext();
                                if (language.isType(identifier)) {
                                    ltype = TYPE;
                                    identifier = null;
                                    break;
                                }
                                ltype = NORMAL;
								break;
								// symbols
							case Lexer.LPAREN:// (
							case Lexer.RPAREN:// )
							case Lexer.LBRACK:// [
							case Lexer.RBRACK:// ]
							case Lexer.LBRACE:// {
							case Lexer.RBRACE:// }
							case Lexer.DOT: // .
							case Lexer.COMMA:// ,
							case Lexer.WHITE_SPACE:// ' '
							case Lexer.SEMICOLON:// ;
							case Lexer.OPERATOR:
								if (identifier!=null) {
									language.addUserWord(identifier);
									language.updateUserWord();
									identifier=null;
							    }
                            case Lexer.NEW_LINE:// '\n'
                                ltype = type==Lexer.OPERATOR ? OPERATOR : NOTE;
								break;
							default:
                                ltype = NORMAL;
						}
                        if (ltype != ttype) {
                            tokens.add(new Pair(_hDoc.logicalToRealIndex(idx), ltype));
                            ttype = ltype;
                        }
                        ltp = type;
                    }
					idx += lexer.yylength();
				} catch (Exception e) {
					e.printStackTrace();
					idx++;//错误了，索引也要往后挪
				}
			}

			if (tokens.isEmpty()){
				// return value cannot be empty
				tokens.add(new Pair(0, Tokenizer.NORMAL));
			}
			//printList(tokens);
			_tokens = tokens;
        }

    }//end inner class
}
