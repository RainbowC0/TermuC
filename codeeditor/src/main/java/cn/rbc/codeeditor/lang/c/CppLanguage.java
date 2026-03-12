package cn.rbc.codeeditor.lang.c;

import cn.rbc.codeeditor.lang.*;
import cn.rbc.codeeditor.util.*;
import java.util.*;

public class CppLanguage extends Language{
	private static Language _theOne = null;
	private final static String[] keywords = {
		"auto", "const", "extern", "mutable", "register", "static", "thread_local", "volatile",
		"new", "delete", "sizeof", "typedef", "typeid", "typename", "constexpr", "decltype",
		"const_cast", "dynamic_cast", "reinterpret_cast", "static_cast",
		"class", "enum", "explicit", "operator", "struct", "template", "union", "virtual",
		"private", "protected", "public", "friend", "this",
		"break", "case", "catch", "continue", "default", "do", "else", "for",
		"goto", "if", "return", "switch", "throw", "try", "while",
		"export", "namespace", "using", "asm", "inline","restrict",
	/*};
	private final static String[] types = {*/
		"bool", "char", "double", "float", "int", "long", "short", "void", "wchar_t",
		"signed", "unsigned", "char16_t", "char32_t"
	};
	private  final  static  String[] cFunctions={
		"abort()","abs(int num):int","acos(double arg):double","asctime(const struct tm *ptr):char *","asin(double arg):double","assert(int exp)","atan(double arg):double","atan2(double y,double x):double","atexit(void (*func)(void)):int","atof(const char *str):double","atoi(const char *str):int","atol(const char *str):long"
		,"bsearch(const void *key,const void *buf,size_t num,size_t size,int (*compare)(const void *,const void *))","calloc(size_t num, size_t size)","ceil(double num):double","clearerr(FILE *stream)","clock():clock_t","cos(double arg):double","cosh(double arg):double","ctime(const time_t *time):char *","difftime(time_t time2,time_t time1):double","div(int numerator,int denominator):div_t"
		,"exit(int exit_code)","exp(double arg):double","fabs(double arg):double","fclose(FILE *stream):int","feof(FILE *stream):int","ferror(FILE *stream):int","fflush(FILE *stream):int","fgetc(FILE *stream):int","fgetpos(FILE *stream,fpos_t *position):int","fgets(char *str, int num,FILE *stream):char *","floor(double arg):double"
		,"fmod(double x,double y):double","fopen(const char *fname, const char *mode):FILE *","fprintf(FILE *stream,const char *format,...):int","fputc(int ch,FILE *stream):int","fputs(const char *str,FILE *stream):int","fread(void *buffer, size_t size, size_t num, FILE *stream):int","free(void *ptr)","freopen(const char *fname,const char *mode,FILE *stream):FILE *","frexp(double num,int *exp):double","fscanf(FILE *stream,const char *format,...):double","fseek(FILE *stream,long offset,int origin):int","fsetpos(FILE *stream,const fpos_t *position):int","ftell(FILE *stream):long","fwrite(const void *buffer,size_t size,size_t count,FILE *stream):int"
		,"getc(FILE *stream):int","getchar():int","getenv(const char *name):char *","gets(char *str):char *","gmtime(const time_t *time):struct tm *","isalnum(int ch):int","isalpha(int ch):int","iscntrl(int ch):int","isdigit(int ch):int","isgraph(int ch):int","islower(int ch):int","isprint(int ch):int","ispunct(int ch):int","isspace(int ch):int","isupper(int ch):int","isxdigit(int ch):int","labs(long num):long","ldexp(double num,int exp):double","ldiv(long numerator,long denominator):ldiv_t","localtime(const time_t *time):struct tm *","log(double num):double","log10(double num):double","longjmp(jmp_buf envbuf,int status)"
		,"malloc(size_t size):void *","memchr(const void *buffer,int ch,size_t count):void *","memcmp(const void *buffer1,const void *buffer2,size_t count):int","memcpy(void *to,const void *from,size_t count):void *","memmove(void *to,const void *from,size_t count):void *","memset(void *buffer,int ch,size_t count):void *","mktime(struct tm *time):time_t","modf(double num,double *i):double","perror(const char *str)","pow(double base,double exp):double","printf(const char *format,...):int"
		,"putc(int ch,FILE *stream):int","putchar(int ch):int","puts(char *str):int","qsort(void *buf,size_t num,size_t size,int (*compare)(const void *,const void *))","raise(int signal):int","rand():int","realloc(void *ptr,size_t size):void *","remove(const char *fname):int","rename(const char *oldfname,const char *newfname):int","rewind(FILE *stream)"
		,"scanf(const char *format,...):int","setbuf(FILE *stream,char *buffer)","setjmp(jmp_buf envbuf):int","setvbuf(FILE *stream,char *buffer,int mode,size_t size):int","signal(int signal,void (*func)(int))","sin(double arg):double","sinh(double arg):double","sprintf(char *buffer,const char *format,...):int","sqrt(double num):double","srand(unsigned seed)","sscanf(const char *buffer,const char *format,...):int","strcat(char *str1,const char *str2):char *","strchr(const char *str, int ch):char *","strcmp(const char *str1,const char *str2):int","strcoll(const char *str1,const char *str2):int","strcpy(char *to,const char *from):char *","strcspn(const char *str1,const char *str2):size_t","strerror(int num):char *","strftime(char *str,size_t maxsize,const char *fmt,struct tm *time):size_t","strlen(char *str):size_t","strncat(char *str1,const char *str2,size_t count):char *","strncmp(const char *str1,const char *str2,size_t count):int","strncpy(char *to,const char *from,size_t count):char *","strpbrk(const char *str1,const char *str2):char *","strrchr(const char *str,int ch):char *","strspn(const char *str1, const char *str2):size_t","strstr(const char *str1, const char *str2):char *","strtod(const char *start,char **end):double","strtok(char *str1,const char *str2):char *","strtol(const char *start,char **end,int base):long","strtoul(const char *start,char **end,int base):unsigned long","strxfrm(char *str1,const char *str2,size_t num):size_t","system(const char *command):int"
		,"tan(double arg):double","tanh(double arg):double","time(time_t *time):time_t","tmpfile():FILE *","tmpnam(char *name):char *","tolower(int ch):int","toupper(int ch):int","ungetc(int ch, FILE *stream):int","va_arg(va_list arg_ptr,type):type","vprintf(char *format,va_list arg_ptr):int","vfprintf(FILE *stream,const char *format,va_list arg_ptr):int","vsprintf(char *buffer,char *format,va_list arg_ptr):int"
	};

	private  final  static  String[]  preDefineField={
		"__LINE__","__FILE__","__DATE__","__TIME__","__cplusplus","__STDC__","__func__","__VA_ARGS__","__attribute__"
	};
	private  final  static  String[] cppNamespace ={
		"std"
	};
	private final static String[] cppNames = {
		"NULL", "EOF", "nullptr", "true", "false"
	};
	private  final  static  String[] cppClasses={
		//io
		"fstream","ifstream","ofstream","cout","cin","cerr","endl"
		//模板库
		,"bitset","string","list","deque","map","multimap","multiset","set","priority_queue","queue","stack","vector"
		//c++11 模板库
		,"array","forward_list","unordered_map","unordered_set"
	};

	private  final  static  String[] cppFunctions={
		"any()","append()","assign()","at()","back()","bad()","begin()","c_str()","capacity()","clear()","compare()","copy()","count()","data()","empty()","end()","eof()","equal_range()","erase()","fail()","fill()","find()","find_first_not_of()","find_first_of()","find_last_not_of()","find_last_of()","flags()","flip()","flush()","front()","fstream"
		,"gcount()","get()","get_allocator()","getline()","good()","ignore()","insert()","iterator()","key_comp()","length()","lower_bound()","max_size()","merge()","none()","open()","peek()","pop()","pop_back()","pop_front()","precision()","push()","push_back()","push_front()","put()","putback"
		,"rbegin()","rdstate()","read()","remove()","remove_if()","rend()","replace()","reserve()","reset()","resize()","reverse()","rfind()","seekg()","seekp()","set()","setf()","size()","sort()","splice()","substr()","swap()","sync_with_stdio()","tellg()","tellp()","test()","to_string()","to_ulong()","top"
		,"unique()","unsetf()","upper_bound()","value_comp()","width()","write()"
	};

	private  final  static  String[] extraWord={
		"define","include","ifdef","endif","ifndef","error","elif","line","pragma","undef","main"
	};
	private final static char[] BASIC_C_OPERATORS = {
		'(', ')', '{', '}', '.', ',', ';', '=', '+', '-',
		'/', '*', '&', '!', '|', ':', '[', ']', '<', '>',
		'?', '~', '%', '^'
	};
	public static Language getInstance(){
		if(_theOne == null){
			_theOne = new CppLanguage();
		}
		return _theOne;
	}

	private CppLanguage() {
		setOperators(BASIC_C_OPERATORS);
		setKeywords(keywords);
		addKeynames(cppNames);
		//addTypes(types);
		addNames(cFunctions);
		addNames(preDefineField);
		addNames(cppNamespace);
		addNames(cppClasses);
		addNames(cppFunctions);
		addNames(extraWord);
	}

	@Override
	public boolean isProgLang() {
		return true;
	}

    private static Lexer lx = null;
	@Override
	public Lexer newLexer(CharSeqReader reader) {
        if (lx==null) {
            CppLexer cx = new CppLexer(reader);
            lx = cx;
            int l = keywords.length - 13;
            Set<String> keys = new HashSet<>();
            for (int i=0;i<l;i++) {
                keys.add(keywords[i]);
            }
            for (int i=0;i<cppNames.length;i++) {
                keys.add(cppNames[i]);
            }
            cx.keywords = keys;
        } else lx.yyreset(reader);
		return lx;
	}
}

