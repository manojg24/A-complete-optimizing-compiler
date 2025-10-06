package mocha;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.io.InputStream;

import ast.AST;
import ast.AST.*;
import ast.Computation;
import ast.Expression;
import ast.NodeVisitor;
import ast.Visitable;
import ast.Statement;

// Import all the necessary type classes
import types.Type;
import types.FuncType;
import types.TypeList;
import types.VoidType;
import types.IntType;
import types.FloatType;
import types.BoolType;
import types.ArrayType;

// mocha imports
import mocha.Scanner;
import mocha.Symbol;
import mocha.Token;
import mocha.NonTerminal;

public class Compiler {

    // Error Reporting ============================================================
    private StringBuilder errorBuffer = new StringBuilder();

    private String reportSyntaxError(NonTerminal nt) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected a token from " + nt.name() + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    private String reportSyntaxError(Token.Kind kind) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected " + kind + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    public String errorReport() {
        return errorBuffer.toString();
    }

    public boolean hasError() {
        return errorBuffer.length() != 0;
    }

    private class QuitParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public QuitParseException(String errorMessage) {
            super(errorMessage);
        }
    }

    private int lineNumber() {
        return currentToken.lineNumber();
    }

    private int charPosition() {
        return currentToken.charPosition();
    }

    // Compiler ===================================================================
    private Scanner scanner;
    private Token currentToken;
    private int numDataRegisters;
    private List<Integer> instructions;

    public Compiler(Scanner scanner, int numRegs) {
        this.scanner = scanner;
        currentToken = this.scanner.next();
        numDataRegisters = numRegs;
        instructions = new ArrayList<>();
    }

    public ast.AST genAST() {
        initSymbolTable();
        try {
            Computation root = computation();
            return new AST(root);
        } catch (QuitParseException q) {
            return new ast.AST(null);
        }
    }
    
    public void interpret(InputStream in) {
        System.out.println("Interpreter not implemented for this assignment.");
    }

    public int[] compile() {
        System.out.println("Compiler not implemented for this assignment.");
        return new int[0];
    }

    // SymbolTable Management =====================================================
    private SymbolTable symbolTable;

    private void initSymbolTable() {
        symbolTable = new SymbolTable();
    }

    private void enterScope() {
        symbolTable.enterScope();
    }

    private void exitScope() {
        symbolTable.exitScope();
    }

    private Symbol tryResolveVariable(Token ident) {
        try {
            return symbolTable.lookup(ident.lexeme());
        } catch (SymbolNotFoundError e) {
            reportResolveSymbolError(ident.lexeme(), ident.lineNumber(), ident.charPosition());
            return null;
        }
    }

    private Symbol tryDeclareVariable(Token ident, Type type) {
        try {
            return symbolTable.insert(ident.lexeme(), type);
        } catch (RedeclarationError e) {
            reportDeclareSymbolError(ident.lexeme(), ident.lineNumber(), ident.charPosition());
            return null;
        }
    }

    private String reportResolveSymbolError(String name, int lineNum, int charPos) {
        String message = "ResolveSymbolError(" + lineNum + "," + charPos + ")[Could not find " + name + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    private String reportDeclareSymbolError(String name, int lineNum, int charPos) {
        String message = "DeclareSymbolError(" + lineNum + "," + charPos + ")[" + name + " already exists.]";
        errorBuffer.append(message + "\n");
        return message;
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

    private Computation computation() {
        // Collect any function declarations that appear before 'main'
        DeclarationList funcDecls = new DeclarationList(lineNumber(), charPosition());
        while (have(NonTerminal.FUNC_DECL)) {
            funcDecls.add(funcDecl());
        }

        // Now we must see 'main'
        Token mainToken = expectRetrieve(Token.Kind.MAIN);

        // Globals (after 'main' per your original grammar)
        DeclarationList varDecls = new DeclarationList(lineNumber(), charPosition());
        while (have(NonTerminal.VAR_DECL) && !have(Token.Kind.FUNC)) {
            List<Declaration> declsForThisLine = varDecl();
            for (Declaration d : declsForThisLine) varDecls.add(d);
        }

        // Also allow more function decls after the globals (merge into same list)
        while (have(NonTerminal.FUNC_DECL)) {
            funcDecls.add(funcDecl());
        }

        // Main block
        expect(Token.Kind.OPEN_BRACE);
        StatementSequence mainBody = statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.PERIOD);
        expect(Token.Kind.EOF);

        return new Computation(
            mainToken.lineNumber(),
            mainToken.charPosition(),
            new Symbol("main", new FuncType(new TypeList(), new VoidType())),
            varDecls,
            funcDecls,
            mainBody
        );
    }

    private List<Declaration> varDecl() {
        // get the concrete (syntactic) type directly
        AST.TypeNode baseTypeNode = (AST.TypeNode) typeDecl();
        List<Declaration> decls = new ArrayList<>();

        do {
            Token identToken = expectRetrieve(Token.Kind.IDENT);
            Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());

            Type varType = baseTypeNode.getActualType(); // ✅ not null

            // Per-variable brackets: int a[5], b[], c;
            while (accept(Token.Kind.OPEN_BRACKET)) {
                int size = -1;
                if (have(Token.Kind.INT_VAL)) {
                    Token sizeToken = expectRetrieve(Token.Kind.INT_VAL);
                    size = Integer.parseInt(sizeToken.lexeme());
                }
                expect(Token.Kind.CLOSE_BRACKET);
                varType = new ArrayType(size, varType);
            }

            AST.TypeNode typeNode = new AST.TypeNode(baseTypeNode.lineNumber(), baseTypeNode.charPosition(), varType);
            tryDeclareVariable(identToken, varType);
            decls.add(new VariableDeclaration(baseTypeNode.lineNumber(), baseTypeNode.charPosition(), id, typeNode));

        } while (accept(Token.Kind.COMMA));

        expect(Token.Kind.SEMICOLON);
        return decls;
    }
    
    private Node typeDecl() {
        Token typeToken = currentToken;
        Type actualType;

        // Base types
        if (accept(Token.Kind.INT)) {
            actualType = new IntType();
        } else if (accept(Token.Kind.FLOAT)) {
            actualType = new FloatType();
        } else if (accept(Token.Kind.BOOL)) {
            actualType = new BoolType();
        } else {
            throw new QuitParseException(reportSyntaxError(NonTerminal.TYPE_DECL));
        }

        // Start with a base TypeNode
        AST.TypeNode typeNode = new AST.TypeNode(
                typeToken.lineNumber(),
                typeToken.charPosition(),
                actualType
        );

        // Handle array brackets and nested dimensions
        while (accept(Token.Kind.OPEN_BRACKET)) {
            boolean negative = false;
            int size = -1; // -1 means unspecified array size (like int[])

            if (accept(Token.Kind.SUB)) {  // allows negative sizes like [-5]
                negative = true;
            }

            if (have(Token.Kind.INT_VAL)) {
                Token sizeToken = expectRetrieve(Token.Kind.INT_VAL);
                size = Integer.parseInt(sizeToken.lexeme());
                if (negative) size = -size;
            }

            expect(Token.Kind.CLOSE_BRACKET);

            // Wrap existing type inside a new ArrayType
            actualType = new ArrayType(size, actualType);

            // Update typeNode to wrap the most recent ArrayType
            typeNode = new AST.TypeNode(
                    typeToken.lineNumber(),
                    typeToken.charPosition(),
                    actualType
            );
        }

        return typeNode;
    }

    private Declaration funcDecl() {
        Token funcToken = expectRetrieve(Token.Kind.FUNC);
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());
        
        expect(Token.Kind.OPEN_PAREN);
        List<FormalParameter> params = new ArrayList<>();
        if (!have(Token.Kind.CLOSE_PAREN)) {
            params = formalParams();
        }
        expect(Token.Kind.CLOSE_PAREN);

        expect(Token.Kind.COLON);
        Node returnTypeNode;
        if (have(Token.Kind.VOID)) {
            Token voidTok = expectRetrieve(Token.Kind.VOID);
            returnTypeNode = new AST.TypeNode(voidTok.lineNumber(), voidTok.charPosition(), new VoidType());
        } else {
            returnTypeNode = typeDecl(); // int/float/bool and optional []s
        }
        
        FunctionBody body = funcBody();
        expect(Token.Kind.SEMICOLON);
        
        return new FunctionDeclaration(funcToken.lineNumber(), funcToken.charPosition(), id, params, returnTypeNode, body);
    }

    private List<FormalParameter> formalParams() {
        List<FormalParameter> params = new ArrayList<>();

        // Caller guarantees we're not at CLOSE_PAREN yet; parse one or more: TYPE IDENT ( , TYPE IDENT )*
        do {
            AST.TypeNode typeNode = (AST.TypeNode) typeDecl();      // type only (int/float/bool + [] groups)
            Token identToken = expectRetrieve(Token.Kind.IDENT);     // then IDENT
            Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());

            params.add(new FormalParameter(identToken.lineNumber(),
                                           identToken.charPosition(),
                                           id,
                                           typeNode));
        } while (accept(Token.Kind.COMMA));

        return params;
    }
    
    private FunctionBody funcBody() {
        expect(Token.Kind.OPEN_BRACE);
        enterScope();
        DeclarationList decls = new DeclarationList(lineNumber(), charPosition());
        while(have(NonTerminal.VAR_DECL)) {
            List<Declaration> declsThisLine = varDecl();
            for (Declaration d : declsThisLine)
                decls.add(d);
        }
        StatementSequence stmts = statSeq();
        exitScope();
        expect(Token.Kind.CLOSE_BRACE);
        return new FunctionBody(decls.lineNumber(), decls.charPosition(), decls, stmts);
    }

    private StatementSequence statSeq() {
        StatementSequence seq = new StatementSequence(lineNumber(), charPosition());
        
        while (!have(Token.Kind.CLOSE_BRACE) &&
               !have(Token.Kind.OD) &&
               !have(Token.Kind.FI) &&
               !have(Token.Kind.ELSE) &&
               !have(Token.Kind.EOF)) {
        	if (accept(Token.Kind.SEMICOLON)) {
                continue;
            }
            if (have(NonTerminal.STATEMENT) || have(Token.Kind.SEMICOLON)) {
            	Statement s = statement(); 
                if (s != null) seq.add(s); 
            } else {
                // Skip invalid token and report error instead of crashing
                String errorMessage = "Unexpected token '" + currentToken.lexeme() +
                                      "' at line " + lineNumber() +
                                      ", col " + charPosition();
                errorBuffer.append("SyntaxError(" + lineNumber() + "," + charPosition() + ")["
                                   + errorMessage + "]\n");
                currentToken = scanner.next(); // try to continue parsing
            }
        }
        
        return seq;
    }

    private Statement statement() {
        if (have(NonTerminal.DESIGNATOR)) {
            return assignmentOrUnary();
        }
        if (have(Token.Kind.IF)) {
            return ifStatement();
        }
        if (have(Token.Kind.WHILE)) {
            return whileStatement();
        }
        if (have(Token.Kind.RETURN)) {
            return returnStatement();
        }
        if (have(Token.Kind.REPEAT)) {
            return repeatStatement();
        }
        if (have(Token.Kind.CALL)) {
            Statement call = funcCall();
            expect(Token.Kind.SEMICOLON);
            return call;
        }
        if (accept(Token.Kind.SEMICOLON)) {
            // empty statement, skip safely
            return null;
        }

        throw new QuitParseException(reportSyntaxError(NonTerminal.STATEMENT));
    }

    private Statement assignmentOrUnary() {
        Expression dest = designator();
        Token op = currentToken;

        // Simple assignment
        if (accept(Token.Kind.ASSIGN)) {
            Expression src = expression();
            expect(Token.Kind.SEMICOLON);
            return new Assignment(op.lineNumber(), op.charPosition(), dest, src);
        }

        // Compound assignments: +=, -=, *=, /=, %=
        if (accept(Token.Kind.ADD_ASSIGN) || accept(Token.Kind.SUB_ASSIGN) ||
            accept(Token.Kind.MUL_ASSIGN) || accept(Token.Kind.DIV_ASSIGN) ||
            accept(Token.Kind.MOD_ASSIGN)) {

            Expression src = expression();
            Expression result;

            if (op.is(Token.Kind.ADD_ASSIGN)) {
                result = new Addition(dest.lineNumber(), dest.charPosition(), dest, src);
            } else if (op.is(Token.Kind.SUB_ASSIGN)) {
                result = new Subtraction(dest.lineNumber(), dest.charPosition(), dest, src);
            } else if (op.is(Token.Kind.MUL_ASSIGN)) {
                result = new Multiplication(dest.lineNumber(), dest.charPosition(), dest, src);
            } else if (op.is(Token.Kind.DIV_ASSIGN)) {
                result = new Division(dest.lineNumber(), dest.charPosition(), dest, src);
            } else { // MOD_ASSIGN
                result = new Modulo(dest.lineNumber(), dest.charPosition(), dest, src);
            }

            expect(Token.Kind.SEMICOLON);
            return new Assignment(dest.lineNumber(), dest.charPosition(), dest, result);
        }

        // Unary increment/decrement: ++a / --a
        if (accept(Token.Kind.UNI_INC) || accept(Token.Kind.UNI_DEC)) {
            boolean isInc = op.is(Token.Kind.UNI_INC);
            expect(Token.Kind.SEMICOLON);
            Expression one = new IntegerLiteral(dest.lineNumber(), dest.charPosition(), 1);
            Expression result = isInc
                                ? new Addition(dest.lineNumber(), dest.charPosition(), dest, one)
                                : new Subtraction(dest.lineNumber(), dest.charPosition(), dest, one);
            return new Assignment(dest.lineNumber(), dest.charPosition(), dest, result);
        }

        throw new QuitParseException(reportSyntaxError(NonTerminal.STATEMENT));
    }

    private IfStatement ifStatement() {
        Token ifToken = expectRetrieve(Token.Kind.IF);
        Expression condition = expression();
        expect(Token.Kind.THEN);
        StatementSequence thenBlock = statSeq();
        StatementSequence elseBlock = null;
        if (accept(Token.Kind.ELSE)) {
            elseBlock = statSeq();
        }
        expect(Token.Kind.FI);
        accept(Token.Kind.SEMICOLON);
        return new IfStatement(ifToken.lineNumber(), ifToken.charPosition(), condition, thenBlock, elseBlock);
    }

    private WhileStatement whileStatement() {
        Token whileToken = expectRetrieve(Token.Kind.WHILE);
        Expression condition = expression();
        expect(Token.Kind.DO);
        StatementSequence body = statSeq();
        expect(Token.Kind.OD);  // OD ends the block, no semicolon
        expect(Token.Kind.SEMICOLON);
        return new WhileStatement(whileToken.lineNumber(), whileToken.charPosition(), condition, body);
    }

    private ReturnStatement returnStatement() {
        Token retToken = expectRetrieve(Token.Kind.RETURN);
        Expression value = null;
        if (!have(Token.Kind.SEMICOLON)) {
            value = expression();
        }
        expect(Token.Kind.SEMICOLON);  // semicolon required at the end of return
        return new ReturnStatement(retToken.lineNumber(), retToken.charPosition(), value);
    }

    private StatementSequence statSeqUntil(Token.Kind stopToken) {
        StatementSequence seq = new StatementSequence(lineNumber(), charPosition());
        while (!have(stopToken) && !have(Token.Kind.EOF)) {
            if (have(NonTerminal.STATEMENT) || have(Token.Kind.SEMICOLON)) {
            	Statement s = statement();
                if (s != null) seq.add(s);
            } else {
                // skip invalid tokens
                errorBuffer.append("SyntaxError(" + lineNumber() + "," + charPosition() + ")[Unexpected token '" + currentToken.lexeme() + "']\n");
                currentToken = scanner.next();
            }
        }
        return seq;
    }
    
    private RepeatStatement repeatStatement() {
        Token repeatToken = expectRetrieve(Token.Kind.REPEAT);
        StatementSequence body = statSeqUntil(Token.Kind.UNTIL);
        expect(Token.Kind.UNTIL);
        Expression condition = expression();  // UNTIL condition ends statement naturally
        expect(Token.Kind.SEMICOLON);
        return new RepeatStatement(repeatToken.lineNumber(), repeatToken.charPosition(), body, condition);
    }

    private Expression expression() {
        // CHANGED: Your grammar has OR and AND, so we start there.
        return orExpr();
    }

    private Expression orExpr() {
        Expression left = andExpr();
        while(have(Token.Kind.OR)) {
            Token op = currentToken;          // capture before consuming
            accept(Token.Kind.OR);
            Expression right = andExpr();
            left = new LogicalOr(op.lineNumber(), op.charPosition(), left, right);
        }
        return left;
    }

    private Expression andExpr() {
        Expression left = relExpr();
        while(have(Token.Kind.AND)) {
            Token op = currentToken;          // capture before consuming
            accept(Token.Kind.AND);
            Expression right = relExpr();
            left = new LogicalAnd(op.lineNumber(), op.charPosition(), left, right);
        }
        return left;
    }
    
    private Expression relExpr() {
        Expression left = addExpr();
        if (have(NonTerminal.REL_OP)) {
            Token op = currentToken;
            accept(NonTerminal.REL_OP);
            Expression right = addExpr();
            left = new Relation(op.lineNumber(), op.charPosition(), left, right, op.lexeme());
        }
        return left;
    }
    
    private Expression addExpr() {
        Expression left = mulExpr();
        while (have(NonTerminal.ADD_OP)) {
            Token op = currentToken;
            accept(NonTerminal.ADD_OP);
            Expression right = mulExpr();
            if (op.is(Token.Kind.ADD)) {
                left = new Addition(op.lineNumber(), op.charPosition(), left, right);
            } else { // SUB
                left = new Subtraction(op.lineNumber(), op.charPosition(), left, right);
            }
        }
        return left;
    }

    private Expression mulExpr() {
        Expression left = powExpr();
        while (have(NonTerminal.MUL_OP)) {
            Token op = currentToken;
            accept(NonTerminal.MUL_OP);
            Expression right = powExpr();
            if (op.is(Token.Kind.MUL)) {
                left = new Multiplication(op.lineNumber(), op.charPosition(), left, right);
            } else if (op.is(Token.Kind.DIV)) {
                left = new Division(op.lineNumber(), op.charPosition(), left, right);
            } else { // MOD
                left = new Modulo(op.lineNumber(), op.charPosition(), left, right);
            }
        }
        return left;
    }

    // New method for right-associative power operator
    private Expression powExpr() {
        Expression left = factor();
        if (have(Token.Kind.POW)) {
            Token op = currentToken;          // capture before consuming
            accept(Token.Kind.POW);
            Expression right = powExpr(); // Recursive call for right-associativity
            return new Power(op.lineNumber(), op.charPosition(), left, right);
        }
        return left;
    }

    private Expression factor() {
    	if (have(Token.Kind.CALL)) {
            return funcCall();
        }
    	if (have(Token.Kind.SUB)) {  // handle unary minus
            Token op = expectRetrieve(Token.Kind.SUB);
            Expression right = factor(); // recursive call for unary
            return new UnaryMinus(op.lineNumber(), op.charPosition(), right);
        }
    	if (have(Token.Kind.IDENT)) {
    	    Token identToken = expectRetrieve(Token.Kind.IDENT);

    	    // function call without 'call'
    	    if (have(Token.Kind.OPEN_PAREN)) {
    	        return parseFuncCall(identToken);
    	    }

    	    // r-value designator: IDENT [expr]...
    	    Expression d = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());
    	    while (accept(Token.Kind.OPEN_BRACKET)) {
    	        Expression index = expression();
    	        expect(Token.Kind.CLOSE_BRACKET);
    	        d = new ArrayIndex(d.lineNumber(), d.charPosition(), d, index);
    	    }
    	    return d;
    	}
        if (have(Token.Kind.INT_VAL)) {
            Token tok = expectRetrieve(Token.Kind.INT_VAL);
            return new IntegerLiteral(tok.lineNumber(), tok.charPosition(), Integer.parseInt(tok.lexeme()));
        }
        if (have(Token.Kind.FLOAT_VAL)) {
            Token tok = expectRetrieve(Token.Kind.FLOAT_VAL);
            return new FloatLiteral(tok.lineNumber(), tok.charPosition(), Float.parseFloat(tok.lexeme()));
        }
        if (have(Token.Kind.TRUE)) {
            Token tok = expectRetrieve(Token.Kind.TRUE);
            return new BoolLiteral(tok.lineNumber(), tok.charPosition(), true);
        }
        if (have(Token.Kind.FALSE)) {
            Token tok = expectRetrieve(Token.Kind.FALSE);
            return new BoolLiteral(tok.lineNumber(), tok.charPosition(), false);
        }
        if (accept(Token.Kind.OPEN_PAREN)) {
            Expression expr = expression();
            expect(Token.Kind.CLOSE_PAREN);
            return expr;
        }
        throw new QuitParseException(reportSyntaxError(NonTerminal.FACTOR));
    }

    private Expression designator() {
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        Expression designator = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());

        while (accept(Token.Kind.OPEN_BRACKET)) {
            Expression index = expression();
            expect(Token.Kind.CLOSE_BRACKET);
            designator = new ArrayIndex(designator.lineNumber(), designator.charPosition(), designator, index);
        }

        // Only wrap in AddressOf if it is used as an L-value in assignment
        return designator;
    }
    
    private Expression literal() {
        Token tok = currentToken;
        if (accept(Token.Kind.INT_VAL)) {
            return new IntegerLiteral(tok.lineNumber(), tok.charPosition(), Integer.parseInt(tok.lexeme()));
        }
        if (accept(Token.Kind.FLOAT_VAL)) {
            return new FloatLiteral(tok.lineNumber(), tok.charPosition(), Float.parseFloat(tok.lexeme()));
        }
        if (accept(Token.Kind.TRUE)) {
            return new BoolLiteral(tok.lineNumber(), tok.charPosition(), true);
        }
        if (accept(Token.Kind.FALSE)) {
            return new BoolLiteral(tok.lineNumber(), tok.charPosition(), false);
        }
        throw new QuitParseException(reportSyntaxError(NonTerminal.LITERAL));
    }
    
    private FunctionCall funcCall() {
        Token callToken = expectRetrieve(Token.Kind.CALL);
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());

        expect(Token.Kind.OPEN_PAREN);
        ArgumentList args = new ArgumentList(lineNumber(), charPosition());
        if (!have(Token.Kind.CLOSE_PAREN)) {
            args.add(expression());
            while (accept(Token.Kind.COMMA)) {
                args.add(expression());
            }
        }
        expect(Token.Kind.CLOSE_PAREN);

        return new FunctionCall(callToken.lineNumber(), callToken.charPosition(), id, args);
    }
    
    private FunctionCall parseFuncCall(Token identToken) {
        Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());
        expect(Token.Kind.OPEN_PAREN);
        ArgumentList args = new ArgumentList(lineNumber(), charPosition());
        if (!have(Token.Kind.CLOSE_PAREN)) {
            args.add(expression());
            while (accept(Token.Kind.COMMA)) {
                args.add(expression());
            }
        }
        expect(Token.Kind.CLOSE_PAREN);
        return new FunctionCall(identToken.lineNumber(), identToken.charPosition(), id, args);
    }
}
