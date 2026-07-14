package cn.rbc.codeeditor.lang.c;

import cn.rbc.codeeditor.util.Lexer;

%%

%public
%class CppLexer
%implements Lexer

%unicode

%line
%column
%char
%type int

%{
public boolean trackPreprocessor = true;
public boolean backquoteString = false;
private String rquote;
public java.util.Set<String> keywords, types;
%}

/* main character classes */
WhiteCharacter = [ \t\f]

/* 注释 */
Comment = {TraditionalComment} | {EndOfLineComment}
TraditionalComment = "/*"([^*] | \*+[^*/])*(\*+"/")?
EndOfLineComment = "//" (.|\\\R)*

/* 标识符 */
Identifier = [:jletter:][:jletterdigit:]*

/* 整数 */
DecIntegerLiteral = {DecDigit} [uU]?
DecLongLiteral    = {DecDigit} ([lL]|[lL][uU]|[uU][lL])
DecDigit          = (0 | [1-9][0-9]*)

HexIntegerLiteral = 0 [xX] 0* {HexDigit} {1,8} [uU]?
HexLongLiteral    = 0 [xX] 0* {HexDigit} {1,16} ([lL]|[lL][uU]|[uU][lL])
HexDigit          = [0-9a-fA-F]

OctIntegerLiteral = 0+ [1-3]? {OctDigit} {1,15} [uU]?
OctLongLiteral    = 0+ 1? {OctDigit} {1,21} ([lL]|[lL][uU]|[uU][lL])
OctDigit          = [0-7]
    
/* 浮点数 */        
FloatLiteral  = ({FLit1}|{FLit2}|{FLit3}) {Exponent}? [fF]
DoubleLiteral = ({FLit1}|{FLit2}|{FLit3}) {Exponent}?

FLit1    = [0-9]+ \. [0-9]* 
FLit2    = \. [0-9]+ 
FLit3    = [0-9]+ 
Exponent = [eEpP] [+-]? [0-9]+

/* 字符串和字符 */
StringCharacter = [^\r\n\"\\]
SingleCharacter = [^\r\n\'\\]
DCharSeq = [^\s\(\)\\\t\f\v\R]*

/*预处理*/
Preprocess = #{WhiteCharacter}*(define|error|else|elif|endif|if|ifdef|ifndef|line|pragma|undef|warning)
IncludeLine = #{WhiteCharacter}*include

Escape = \\

%state STRING,CHARLITERAL,PRETREATMENT,INCLUDE,RAW_STRING,BQ_STRING

%%

<YYINITIAL> {

  /*Include*/
  {IncludeLine}			 { if (trackPreprocessor) {yybegin(INCLUDE);return PRETREATMENT_LINE; } else {yypushback(yylength()-1);return ERROR;}}
  
   /*预处理*/
  {Preprocess}			 { if (trackPreprocessor) {yybegin(PRETREATMENT);return PRETREATMENT_LINE; } else {yypushback(yylength()-1);return ERROR;}}
  
  /* 分隔符 */
  "("                            { return LPAREN; }
  ")"                            { return RPAREN; }
  "{"                            { return LBRACE; }
  "}"                            { return RBRACE; }
  "["                            { return LBRACK; }
  "]"                            { return RBRACK; }
  ";"                            { return SEMICOLON; }
  ","                            { return COMMA; }
  "."                            { return DOT; }
  
  /* 运算符 */
  "="                            { return OPERATOR; }
  ">"                            { return OPERATOR; }
  "<"                            { return OPERATOR; }
  "!"                            { return OPERATOR; }
  "~"                            { return OPERATOR; }
  "?"                            { return OPERATOR; }
  ":"                            { return OPERATOR; }
 /* "=="                           { return OPERATOR; }
  "<="                           { return OPERATOR; }
  ">="                           { return OPERATOR; }
  "!="                           { return OPERATOR; }
  "&&"                           { return OPERATOR; }
  "||"                           { return OPERATOR; }
  "++"                           { return OPERATOR; }
  "--"                           { return OPERATOR; }*/
  "+"                            { return OPERATOR; }
  "-"                            { return OPERATOR; }
  "*"                            { return OPERATOR; }
  "/"                            { return OPERATOR; }
  "&"                            { return OPERATOR; }
  "|"                            { return OPERATOR; }
  "^"                            { return OPERATOR; }
  "%"                            { return OPERATOR; }
 /* "<<"                           { return OPERATOR; }
  ">>"                           { return OPERATOR; }
  "+="                           { return OPERATOR; }
  "-="                           { return OPERATOR; }
  "*="                           { return OPERATOR; }
  "/="                           { return OPERATOR; }
  "&="                           { return OPERATOR; }
  "|="                           { return OPERATOR; }
  "^="                           { return OPERATOR; }
  "%="                           { return OPERATOR; }
  "<<="                          { return OPERATOR; }
  ">>="                          { return OPERATOR; }*/
  
  /* 字符串开始 */
  (u8|u|U|L)?\"                  { yybegin(STRING); return STRING_LITERAL;}

  /* 字符 */
  (u8|u|U|L)?\'                  { yybegin(CHARLITERAL);return CHARACTER_LITERAL; }

  /* 原始字符串 */
  R\"{DCharSeq}\(                { rquote=new String(zzBuffer,zzStartRead+2,zzMarkedPos-zzStartRead-3);yybegin(RAW_STRING);return STRING_LITERAL; }

  /* 反括号字符串 */
  `                              { if (backquoteString) {yybegin(BQ_STRING); return STTING_LITERAL;} return ERROR;}

  /* numeric literals */

  /* This is matched together with the minus, because the number is too big to 
     be represented by a positive integer. */
  
  {DecIntegerLiteral}            { return INTEGER_LITERAL; }
  {DecLongLiteral}               { return INTEGER_LITERAL; }
  
  {HexIntegerLiteral}            { return INTEGER_LITERAL; }
  {HexLongLiteral}               { return INTEGER_LITERAL; }
 
  {OctIntegerLiteral}            { return INTEGER_LITERAL; }  
  {OctLongLiteral}               { return INTEGER_LITERAL; }
  
  {FloatLiteral}                 { return FLOATING_POINT_LITERAL; }
  {DoubleLiteral}                { return FLOATING_POINT_LITERAL; }
  {DoubleLiteral}[dD]            { return FLOATING_POINT_LITERAL; }
  
  /* comments */
  {Comment}                      { return COMMENT; }

  /* 换行符 */
  \R                   { return NEW_LINE; }

    /* 空白符 */
  \s                  { return WHITE_SPACE; }
  
  /* 标识符 */ 
  {Identifier}                   { String idt = yytext(); if (keywords.contains(idt)) return KEYWORD; else if (types.contains(idt)) return TYPE; else return IDENTIFIER; } 
  
}
/*单行宏*/
<INCLUDE>{
	[a-zA-Z0-9_-]			{ return PRETREATMENT_LINE; }
	\" .* \"             { return STRING_LITERAL; }
    "<" .* ">"            { return STRING_LITERAL; }
   // ">"             { return PRETREATMENT_LINE; }
    "."             { return PRETREATMENT_LINE; }
	{Comment}		{ return COMMENT; }
	\R				{ yybegin(YYINITIAL);return NEW_LINE; }
	\s				{ return PRETREATMENT_LINE; }
	"/"             { return PRETREATMENT_LINE; }/*空格*/
}
/*多行宏*/
<PRETREATMENT>{
  .				{ return PRETREATMENT_LINE; }
  {Escape}\R    { return PRETREATMENT_LINE; }
  \R							{ yybegin(YYINITIAL);return NEW_LINE; }
  {Comment}						{ return COMMENT; }
}

<STRING> {
  \"                             { yybegin(YYINITIAL);return STRING_LITERAL;}
  {StringCharacter}+             	{  return STRING_LITERAL;}
  {Escape}\R				{ return STRING_LITERAL;}
  \\.				            { return STRING_LITERAL;}
  \R		             	    {yybegin(YYINITIAL);return NEW_LINE;}
}

<CHARLITERAL> {
   \'							{yybegin(YYINITIAL);return CHARACTER_LITERAL;}
  {SingleCharacter}          	{  return CHARACTER_LITERAL; }
  {Escape}\R				{ return CHARACTER_LITERAL;}
   \\.				            { return CHARACTER_LITERAL;}
  \R               {yybegin(YYINITIAL);return NEW_LINE;}
}

<RAW_STRING> {
  \){DCharSeq}\"   {if(new String(zzBuffer,zzStartRead+1,zzMarkedPos-zzStartRead-2).equals(rquote))yybegin(YYINITIAL); return STRING_LITERAL;}
  [^\)]+|\)         {return STRING_LITERAL;}
}

<BQ_STRING> {
  {Escape}`       { return STRING_LITERAL; }
  `               { yybegin(YYINITIAL);return STRING_LITERAL; }
  [^]             { return STRING_LITERAL; }
}
/* error fallback */
[^]                              { return ERROR; }
<<EOF>>                          { rquote=null;return EOF; }
