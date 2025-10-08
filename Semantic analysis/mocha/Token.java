package mocha;

public class Token {

    public enum Kind {
        // boolean operators
        AND("and"),
        OR("or"),
        NOT("not"),

        // arithmetic operators
        POW("^"),

        MUL("*"),
        DIV("/"),
        MOD("%"),

        ADD("+"),
        SUB("-"),

        // relational operators
        EQUAL_TO("=="),
        NOT_EQUAL("!="),
        LESS_THAN("<"),
        LESS_EQUAL("<="),
        GREATER_EQUAL(">="),
        GREATER_THAN(">"),

        // assignment operators
        ASSIGN("="),
        ADD_ASSIGN("+="),
        SUB_ASSIGN("-="),
        MUL_ASSIGN("*="),
        DIV_ASSIGN("/="),
        MOD_ASSIGN("%="),
        POW_ASSIGN("^="),

        // unary increment/decrement
        UNI_INC("++"),
        UNI_DEC("--"),

        // primitive types
        VOID("void"),
        BOOL("bool"),
        INT("int"),
        FLOAT("float"),

        // boolean literals
        TRUE("true"),
        FALSE("false"),

        // region delimiters
        OPEN_PAREN("("),
        CLOSE_PAREN(")"),
        OPEN_BRACE("{"),
        CLOSE_BRACE("}"),
        OPEN_BRACKET("["),
        CLOSE_BRACKET("]"),

        // field/record delimiters
        COMMA(","),
        COLON(":"),
        SEMICOLON(";"),
        PERIOD("."),

        // control flow statements
        IF("if"),
        THEN("then"),
        ELSE("else"),
        FI("fi"),

        WHILE("while"),
        DO("do"),
        OD("od"),

        REPEAT("repeat"),
        UNTIL("until"),

        CALL("call"),
        RETURN("return"),

        // keywords
        MAIN("main"),
        FUNC("function"),

        // special cases
        INT_VAL(),
        FLOAT_VAL(),
        IDENT(),

        EOF(),

        ERROR();

        private String defaultLexeme;

        Kind () {
            defaultLexeme = "";
        }

        Kind (String lexeme) {
            defaultLexeme = lexeme;
        }

        public boolean hasStaticLexeme () 
        {
        	return defaultLexeme != null && !defaultLexeme.isEmpty();
        }

        public String defaultLexeme() 
        {
            return defaultLexeme;
        }
        // OPTIONAL: convenience function - boolean matches (String lexeme)
        //           to report whether a Token.Kind has the given lexeme
        //           may be useful
    }
    
    public String getLexeme() {
        return lexeme;
    }

    private int lineNum;
    private int charPos;
    Kind kind;  // package-private
    private String lexeme = "";


    // TODO: implement remaining factory functions for handling special cases (EOF below)

    public static Token EOF (int linePos, int charPos) 
    {
        Token tok = new Token(linePos, charPos);
        tok.kind = Kind.EOF;
        tok.lexeme = "";
        return tok;
    }

    private Token (int lineNum, int charPos) 
    {
        this.lineNum = lineNum;
        this.charPos = charPos;

        // no lexeme provide, signal error
        this.kind = Kind.ERROR;
        this.lexeme = "No Lexeme Given";
    }

    public Token (String lexeme, int lineNum, int charPos) {
        this.lineNum = lineNum;
        this.charPos = charPos;
        this.lexeme = lexeme;
        // TODO: based on the given lexeme determine and set the actual kind

        // if we don't match anything, signal error
        this.kind = Kind.ERROR;
        this.lexeme = "Unrecognized lexeme: " + lexeme;
    }

    public int lineNumber () {
        return lineNum;
    }

    public int charPosition () {
        return charPos;
    }

    public String lexeme () 
    {
        return this.lexeme;
    }

    public Kind kind () 
    {
        return this.kind;
    }

    public boolean is(Kind k) 
    { 
        return kind == k; 
    }
    // TODO: function to query a token about its kind - boolean is (Token.Kind kind)
    
    public Token(Kind kind, String lexeme, int lineNum, int charPos) 
    {
        this.kind = kind;
        this.lexeme = lexeme;
        this.lineNum = lineNum;
        this.charPos = charPos;
    }
    
    public static Kind lookupKind(String lexeme) 
    {
    	// 1) multi-lexeme operators/keywords first
        // logical
        if ("&&".equals(lexeme)) return Kind.AND;
        if ("||".equals(lexeme)) return Kind.OR;
        if ("!".equals(lexeme))  return Kind.NOT;

        // 2) direct matches against enum’s default lexemes
        for (Kind k : Kind.values()) {
            if (k.hasStaticLexeme() && k.defaultLexeme().equals(lexeme)) {
                return k;
            }
        }

        // 3) numeric literals
        if (lexeme.matches("\\d+"))            return Kind.INT_VAL;
        if (lexeme.matches("\\d+\\.\\d+"))     return Kind.FLOAT_VAL;

        // 4) identifiers (incl. keywords already caught above)
        if (lexeme.matches("[a-zA-Z_][a-zA-Z0-9_]*")) return Kind.IDENT;

        // 5) otherwise
        return Kind.ERROR;
    }

    // OPTIONAL: add any additional helper or convenience methods
    //           that you find make for a cleaner design

    @Override
    public String toString () {
        return "Line: " + lineNum + ", Char: " + charPos + ", Lexeme: " + lexeme;
    }
}
