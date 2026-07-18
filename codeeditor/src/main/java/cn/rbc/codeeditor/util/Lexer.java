package cn.rbc.codeeditor.util;
import java.io.*;

public interface Lexer
{
	int EOF = 0,
	OPERATOR=1, //运算符
	IDENTIFIER=2,//标识符
	INTEGER_LITERAL=3, //整数
	KEYWORD=4, //关键字
	TYPE=18, //类型
	FLOATING_POINT_LITERAL=5, //浮点
	COMMENT=6, //注释
	STRING_LITERAL=7,//字符串
	COMMA=8, //逗号
	SEMICOLON=9,//分号
	RBRACK=10, 
	LBRACK=11,
	LPAREN=12,//左括号
	RPAREN=13,//右括号
	RBRACE=14, //右大括号
	LBRACE=15, //左大括号
	DOT=16, //点
	CHARACTER_LITERAL=17,//字符
	PRETREATMENT_LINE=19, //预处理
	WHITE_SPACE=20,//空白符
	DEFINE_LINE=21,//define
	NEW_LINE=22,
	ERROR=23;

	int yylex() throws IOException;
	int yylength();
	String yytext();
	void yyreset(Reader rd);
}