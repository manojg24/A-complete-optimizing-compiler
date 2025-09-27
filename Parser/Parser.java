package mocha;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class Parser {

    // Error Reporting ============================================================
    private StringBuilder errorBuffer = new StringBuilder();

    private String reportSyntaxError (NonTerminal nt) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected a token from " + nt.name() + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    private String reportSyntaxError (Token.Kind kind) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected " + kind + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    public String errorReport () {
        return errorBuffer.toString();
    }

    public boolean hasError () {
        return errorBuffer.length() != 0;
    }

    private class QuitParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public QuitParseException (String errorMessage) {
            super(errorMessage);
        }
    }

    private int lineNumber () {
        return currentToken.lineNumber();
    }

    private int charPosition () {
        return currentToken.charPosition();
    }

// Parser ============================================================
    private Scanner scanner;
    private Token currentToken;

    private BufferedReader reader;
    private StringTokenizer st;

    // TODO: add maps from Token IDENT to int/float/bool

    public Parser (Scanner scanner, InputStream in) {
        this.scanner = scanner;
        currentToken = this.scanner.next();

        reader = new BufferedReader(new InputStreamReader(in));
        st = null;
    }

    public void parse ()
    {
        try {
            computation();
            expect(Token.Kind.EOF);
        }
        catch (QuitParseException q)
        {
            // too verbose
            // errorBuffer.append("SyntaxError(" + lineNumber() + "," + charPosition() + ")");
            // errorBuffer.append("[Could not complete parsing.]");
        }
    }

// Helper Methods =============================================================
    private boolean have (Token.Kind kind) {
        return currentToken.is(kind);
    }

    private boolean have (NonTerminal nt) {
        return nt.firstSet().contains(currentToken.kind);
    }

    private boolean accept (Token.Kind kind) {
        if (have(kind)) {
            try {
                currentToken = scanner.next();
            }
            catch (NoSuchElementException e) {
                if (!kind.equals(Token.Kind.EOF)) {
                    String errorMessage = reportSyntaxError(kind);
                    throw new QuitParseException(errorMessage);
                }
            }
            return true;
        }
        return false;
    }

    private boolean accept (NonTerminal nt) {
        if (have(nt)) {
            currentToken = scanner.next();
            return true;
        }
        return false;
    }

    private boolean expect (Token.Kind kind) {
        if (accept(kind)) {
            return true;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private boolean expect (NonTerminal nt) {
        if (accept(nt)) {
            return true;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve (Token.Kind kind) {
        Token tok = currentToken;
        if (accept(kind)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve (NonTerminal nt) {
        Token tok = currentToken;
        if (accept(nt)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

// Grammar Rules ==============================================================

    // function for matching rule that only expects nonterminal's FIRST set
    private Token matchNonTerminal (NonTerminal nt) {
        return expectRetrieve(nt);
    }

    // TODO: implement operators and type grammar rules
    private void varDecl () 
    {
        typeDecl();
        expect(Token.Kind.IDENT);
        while (accept(Token.Kind.COMMA))
        {
            expect(Token.Kind.IDENT);
        }
        expect(Token.Kind.SEMICOLON);
    }
    
    private void typeDecl ()
    {
        expect(NonTerminal.TYPE_DECL);
        while (accept(Token.Kind.OPEN_BRACKET))
        {
            // Assuming array size must be an integer literal
            accept(Token.Kind.INT_VAL);
            expect(Token.Kind.CLOSE_BRACKET);
        }
    }
    
    private void funcDecl ()
    {
        expect(Token.Kind.FUNC);
        expect(Token.Kind.IDENT);
        expect(Token.Kind.OPEN_PAREN);
        if (have(NonTerminal.FORMAL_PARAM))
        {
            formalParam();
            while (accept(Token.Kind.COMMA))
            {
                formalParam();
            }
        }
        expect(Token.Kind.CLOSE_PAREN);
        if (accept(Token.Kind.COLON))
        {
            typeDecl();
        }
        funcBody();
        expect(Token.Kind.SEMICOLON);
    }
    
    private void formalParam ()
    {
        typeDecl();
        expect(Token.Kind.IDENT);
    }
    
    private void funcBody ()
    {
        expect(Token.Kind.OPEN_BRACE);
        while (have(NonTerminal.VAR_DECL))
        {
            varDecl();
        }
        statSeq();
        expect(Token.Kind.CLOSE_BRACE);
    }
    
    private void statSeq ()
    {
    	if (!have(NonTerminal.STATEMENT))
    	{
            String errorMessage = reportSyntaxError(NonTerminal.STATEMENT);
            throw new QuitParseException(errorMessage);
        }

        statement();
        expect(Token.Kind.SEMICOLON);

        while (have(NonTerminal.STATEMENT))
        {
            statement();
            expect(Token.Kind.SEMICOLON);
        }
    }
    
    private void statement ()
    {
    	if (have(Token.Kind.IF)) {
            ifStat();
        } else if (have(Token.Kind.WHILE))
        {
            whileStat();
        } else if (have(Token.Kind.REPEAT))
        {
            repeatStat();
        } else if (have(Token.Kind.CALL))
        {
            funcCall();
        } else if (have(Token.Kind.RETURN))
        {
            returnStat();
        } else if (have(Token.Kind.IDENT))
        {
        	designator();

        	if (have(NonTerminal.ASSIGN_OP))
        	{
                expect(NonTerminal.ASSIGN_OP);
                relExpr();
            } else if (accept(Token.Kind.UNI_INC) || accept(Token.Kind.UNI_DEC))
            {
                return;
            } else if (have(Token.Kind.OPEN_PAREN))
            {
                expect(Token.Kind.OPEN_PAREN);
                if (have(NonTerminal.EXPR))
                {
                    relExpr();
                    while (accept(Token.Kind.COMMA))
                    {
                        relExpr();
                    }
                }
                expect(Token.Kind.CLOSE_PAREN);
            }else
            {
                String errorMessage = reportSyntaxError(NonTerminal.STATEMENT);
                throw new QuitParseException(errorMessage);
            }
        } else
        {
            String errorMessage = reportSyntaxError(NonTerminal.STATEMENT);
            throw new QuitParseException(errorMessage);
        }
    }
    
    private void assign ()
    {
        designator();
        expect(NonTerminal.ASSIGN_OP);
        relExpr();
    }
    
    private void funcCall ()
    {
    	if (accept(Token.Kind.CALL))
    	{
            expect(Token.Kind.IDENT);
        } else
        {
            expect(Token.Kind.IDENT);
        }

        expect(Token.Kind.OPEN_PAREN);
        if (have(NonTerminal.EXPR))
        {
            relExpr();
            while (accept(Token.Kind.COMMA))
            {
                relExpr();
            }
        }
        expect(Token.Kind.CLOSE_PAREN);
    }
    
    private void ifStat ()
    {
    	expect(Token.Kind.IF);
        expect(Token.Kind.OPEN_PAREN);
        relExpr();
        expect(Token.Kind.CLOSE_PAREN);

        if (accept(Token.Kind.OPEN_BRACE))
        {	// block form
            statSeq();
            expect(Token.Kind.CLOSE_BRACE);

            if (accept(Token.Kind.ELSE))
            {
                expect(Token.Kind.OPEN_BRACE);
                statSeq();
                expect(Token.Kind.CLOSE_BRACE);
            }

        } else if (accept(Token.Kind.THEN))
        {   // then/fi form
            statSeq();

            if (accept(Token.Kind.ELSE))
            {
                statSeq();
            }

            expect(Token.Kind.FI);

        } else
        {
            // if neither form matched
            String errorMessage = reportSyntaxError(Token.Kind.OPEN_BRACE);
            throw new QuitParseException(errorMessage);
        }
    }
    
    private void whileStat ()
    {
    	expect(Token.Kind.WHILE);
        expect(Token.Kind.OPEN_PAREN);
        relExpr();
        expect(Token.Kind.CLOSE_PAREN);

        if (accept(Token.Kind.OPEN_BRACE))
        {   // block form
            statSeq();
            expect(Token.Kind.CLOSE_BRACE);
        } else if (accept(Token.Kind.DO))
        {   // do/od form
            statSeq();
            expect(Token.Kind.OD);
        } else 
        {   // neither form matched
            String errorMessage = reportSyntaxError(Token.Kind.OPEN_BRACE);
            throw new QuitParseException(errorMessage);
        }
    }
    
    private void repeatStat ()
    {
        expect(Token.Kind.REPEAT);
        statSeq();
        expect(Token.Kind.UNTIL);
        relExpr();
    }
    
    private void returnStat ()
    {
        expect(Token.Kind.RETURN);
        if (have(NonTerminal.EXPR))
        {
        	relExpr();
        }
    }

    // literal = integerLit | floatLit
    private Token literal () {
        return matchNonTerminal(NonTerminal.LITERAL);
    }

    // designator = ident { "[" relExpr "]" }
    private void designator () {
        int lineNum = lineNumber();
        int charPos = charPosition();

        Token ident = expectRetrieve(Token.Kind.IDENT);

        // TODO: get designated value from appropriate map from IDENT to value
        while (accept(Token.Kind.OPEN_BRACKET))
        {
            relExpr();
            expect(Token.Kind.CLOSE_BRACKET);
        }
        
    }

    // TODO: implement remaining grammar rules
    private void expr () 
    {
        term();
        while (have(Token.Kind.ADD) || have(Token.Kind.SUB) || have(Token.Kind.OR)) {
            accept(currentToken.kind);
            term();
        }
    }
    
    private void term ()
    {
        power();
        while (have(Token.Kind.MUL) || have(Token.Kind.DIV) || have(Token.Kind.MOD) || have(Token.Kind.AND))
        {
        	Token.Kind op = currentToken.kind;
            accept(op);
            power();
        }
    }
    
    private void factor ()
    {
        if (have(Token.Kind.IDENT))
        {
            designator();
        } else if(have(Token.Kind.CALL))
        {
        	funcCall();
        } else if (have(NonTerminal.LITERAL))
        {
            literal();
        } else if (accept(Token.Kind.OPEN_PAREN))
        {
        	relExpr();
            expect(Token.Kind.CLOSE_PAREN);
        } else if (have(NonTerminal.UNARY_OP))
        {
            accept(currentToken.kind);
            factor();
        } else
        {
            String errorMessage = reportSyntaxError(NonTerminal.FACTOR);
            throw new QuitParseException(errorMessage);
        }
    }
    
    private void relExpr ()
    {
        expr();
        while (have(NonTerminal.REL_OP))
        {
        	accept(NonTerminal.REL_OP);
        	expr();
        }
    }
    
    private void power ()
    {
        factor();
        while (have(Token.Kind.POW))
        {
            accept(Token.Kind.POW);
            factor();
        }
    }
    
//    private void postfixStat()
//    {
//        designator();
//        
//        // It must be followed by either UNI_INC or UNI_DEC
//        if (!accept(Token.Kind.UNI_INC))
//        {
//            expect(Token.Kind.UNI_DEC);
//        }
//    }

    // computation	= "main" {varDecl} {funcDecl} "{" statSeq "}" "."
    private void computation () {
        
        expect(Token.Kind.MAIN);

        while (have(NonTerminal.VAR_DECL))
        {
            varDecl();
        }

        while (have(NonTerminal.FUNC_DECL))
        {
            funcDecl();
        }

        expect(Token.Kind.OPEN_BRACE);
        statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.PERIOD);
    }
    
}
