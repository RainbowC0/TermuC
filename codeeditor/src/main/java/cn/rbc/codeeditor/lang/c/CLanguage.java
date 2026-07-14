package cn.rbc.codeeditor.lang.c;

import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.util.*;
import java.util.*;

public class CLanguage extends Language{
	private static Language _theOne = null;

	private final static String[] keywords = {
		"auto", "const", "extern", "register", "static", "volatile",
		"sizeof", "typedef",
		"enum", "struct", "union",
		"break", "case", "continue", "default", "do", "else", "for",
		"goto", "if", "return", "switch", "while",
	/*};
	private final static String[] types = {*/
		"char", "double", "float", "int", "long", "short", "void",
		"signed", "unsigned", "_Bool", "_Complex", "_Imaginary"
	};
	private  final  static  String[] functions={
		"abort","abs","acos","asctime","asin","assert","atan","atan2","atexit","atof","atoi","atol"
		,"bsearch","calloc","ceil","clearerr","clock","cos","cosh","ctime","difftime","div"
		,"exit","exp","fabs","fclose","feof","ferror","fflush","fgetc","fgetpos","fgets","floor"
		,"fmod","fopen","fprintf","fputc","fputs","fread","free","freopen","frexp","fscanf","fseek","fsetpos","ftell","fwrite"
		,"getc","getchar","getenv","gets","gmtime","isalnum","isalpha","iscntrl","isdigit","isgraph","islower","isprint","ispunct","isspace","isupper","isxdigit","labs","ldexp","ldiv","localtime","log","log10","longjmp"
		,"main","malloc","memchr","memcmp","memcpy","memmove","memset","mktime","modf","perror","pow","printf"
		,"putc","putchar","puts","qsort","raise","rand","realloc","remove","rename","rewind"
		,"scanf","setbuf","setjmp","setvbuf","signal","sin","sinh","sprintf","sqrt","srand","sscanf","strcat","strchr","strcmp","strcoll","strcpy","strcspn","strerror","strftime","strlen","strncat","strncmp","strncpy","strpbrk","strrchr","strspn","strstr","strtod","strtok","strtol","strtoul","strxfrm","system"
		,"tan","tanh","time","tmpfile","tmpnam","tolower","toupper","ungetc","va_arg","vprintf","vfprintf"
		,"__LINE__","__FILE__","__DATE__","__TIME__","_cplusplus","__STDC__"
	};
	private  final  static  String[] header={
		"math.h","stdio.h","stdlib.h","string.h","time.h","errno.h","ctype.h","local.h"
	};
	private final static String[] keynames = {
		"EOF", "NULL"
	};
	private final static String[] extraWord = {
		"define","include","ifdef","endif","ifndef","error","elif","line","pragma","undef","main"
	};
	private final static char[] BASIC_C_OPERATORS = {
		'(', ')', '{', '}', '.', ',', ';', '=', '+', '-',
		'/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
		'?', '~', '%', '^'
	};
	public static Language getInstance(){
		if(_theOne == null){
			_theOne = new CLanguage();
		}
		return _theOne;
	}

	private CLanguage() {
		setKeywords(keywords);
		addNames(header);
		addNames(functions);
		addNames(extraWord);
		addKeynames(keynames);
		setOperators(BASIC_C_OPERATORS);
	}

	@Override
	public boolean isProgLang() {
		return true;
	}

    private CppLexer lx = null;

	@Override
	public Lexer newLexer(CharSeqReader reader) {
        if (lx == null) {
		    CppLexer cx = new CppLexer(reader);
		    lx = cx;
		    int l = keywords.length - 12;
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
        } else lx.yyreset(reader);
        return lx;
	}
}

