package cn.rbc.codeeditor.lang;
import cn.rbc.codeeditor.util.*;
import cn.rbc.codeeditor.lang.c.*;
import java.util.*;

public class LanguageJson extends Language
{
    private static Language _theOne = null;

    private final static String[] keywords = {
        "true", "false", "null"
    };

    public static Language getInstance(){
        if(_theOne == null){
            _theOne = new LanguageJson();
        }
        return _theOne;
    }

    private LanguageJson(){
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
            }
            cx.keywords = keys;
            cx.types = Collections.emptySet();
            cx.trackPreprocessor = false;
            cx.backquoteString = false;
        } else lx.yyreset(reader);
        return lx;
	}
}
