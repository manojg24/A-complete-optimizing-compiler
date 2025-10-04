package mocha;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.io.InputStream;

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
            return new ast.AST(root);
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
        Token mainToken = expectRetrieve(Token.Kind.MAIN);

        DeclarationList varDecls = new DeclarationList(lineNumber(), charPosition());
        while (have(NonTerminal.VAR_DECL) && !have(Token.Kind.FUNC)) {
            varDecls.add(varDecl());
        }

        DeclarationList funcDecls = new DeclarationList(lineNumber(), charPosition());
        while (have(NonTerminal.FUNC_DECL)) {
            funcDecls.add(funcDecl());
        }

        expect(Token.Kind.OPEN_BRACE);
        StatementSequence mainBody = statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.PERIOD);
        expect(Token.Kind.EOF);

        return new Computation(mainToken.lineNumber(), mainToken.charPosition(), new Symbol("main", new FuncType(new TypeList(), new VoidType())), varDecls, funcDecls, mainBody);
    }

    private Declaration varDecl() {
        Node typeNode = typeDecl();
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        expect(Token.Kind.SEMICOLON);
        Identifier id = new Identifier(identToken.lexeme());
        
        tryDeclareVariable(identToken, typeNode.getType());
        
        return new VariableDeclaration(typeNode.lineNumber(), typeNode.charPosition(), id, typeNode);
    }
    
    private Node typeDecl() {
        Token typeToken = currentToken;
        Node typeNode;
        Type actualType;

        if (accept(Token.Kind.INT)) {
            actualType = new IntType();
            typeNode = new IntegerLiteral(typeToken.lineNumber(), typeToken.charPosition(), 0);
        } else if (accept(Token.Kind.FLOAT)) {
            actualType = new FloatType();
            typeNode = new FloatLiteral(typeToken.lineNumber(), typeToken.charPosition(), 0);
        } else if (accept(Token.Kind.BOOL)) {
            actualType = new BoolType();
            typeNode = new BoolLiteral(typeToken.lineNumber(), typeToken.charPosition(), false);
        } else {
            throw new QuitParseException(reportSyntaxError(NonTerminal.TYPE_DECL));
        }

        while (accept(Token.Kind.OPEN_BRACKET)) {
            Token extentToken = expectRetrieve(Token.Kind.INT_VAL);
            int extent = Integer.parseInt(extentToken.lexeme());
            expect(Token.Kind.CLOSE_BRACKET);
            actualType = new ArrayType(extent, actualType);
        }

        typeNode.setType(actualType);
        return typeNode;
    }

    private Declaration funcDecl() {
        Token funcToken = expectRetrieve(Token.Kind.FUNC);
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        Identifier id = new Identifier(identToken.lexeme());
        
        expect(Token.Kind.OPEN_PAREN);
        List<FormalParameter> params = new ArrayList<>();
        if (!have(Token.Kind.CLOSE_PAREN)) {
            params = formalParams();
        }
        expect(Token.Kind.CLOSE_PAREN);

        expect(Token.Kind.COLON);
        Node returnTypeNode = typeDecl();
        
        FunctionBody body = funcBody();
        
        return new FunctionDeclaration(funcToken.lineNumber(), funcToken.charPosition(), id, params, returnTypeNode, body);
    }

    private List<FormalParameter> formalParams() {
        List<FormalParameter> params = new ArrayList<>();
        
        Node typeNode = typeDecl();
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        params.add(new FormalParameter(identToken.lineNumber(), identToken.charPosition(), new Identifier(identToken.lexeme()), typeNode));
        
        while(accept(Token.Kind.COMMA)) {
            typeNode = typeDecl();
            identToken = expectRetrieve(Token.Kind.IDENT);
            params.add(new FormalParameter(identToken.lineNumber(), identToken.charPosition(), new Identifier(identToken.lexeme()), typeNode));
        }
        return params;
    }
    
    private FunctionBody funcBody() {
        expect(Token.Kind.OPEN_BRACE);
        DeclarationList decls = new DeclarationList(lineNumber(), charPosition());
        while(have(NonTerminal.VAR_DECL)) {
            decls.add(varDecl());
        }
        StatementSequence stmts = statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        return new FunctionBody(decls.lineNumber(), decls.charPosition(), decls, stmts);
    }

    private StatementSequence statSeq() {
        StatementSequence seq = new StatementSequence(lineNumber(), charPosition());
        while (have(NonTerminal.STATEMENT)) {
            seq.add(statement());
        }
        return seq;
    }

    private Statement statement() {
        // CHANGED: Assignment no longer starts with LET. It starts with a variable name (DESIGNATOR).
        if (have(NonTerminal.DESIGNATOR)) {
            return assignment();
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
        if (have(Token.Kind.CALL)) {
            Statement call = funcCall();
            expect(Token.Kind.SEMICOLON);
            return call;
        }
        throw new QuitParseException(reportSyntaxError(NonTerminal.STATEMENT));
    }
    
    private Assignment assignment() {
        // CHANGED: Removed "expectRetrieve(Token.Kind.LET)"
        Expression dest = designator();
        Token assignOp = currentToken;
        // Your Token.Kind has many assignment operators, but a simple parser handles just ASSIGN.
        // A full parser would check for ADD_ASSIGN, etc., and build a different AST node.
        expect(Token.Kind.ASSIGN);
        Expression src = expression();
        expect(Token.Kind.SEMICOLON);
        return new Assignment(assignOp.lineNumber(), assignOp.charPosition(), dest, src);
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
        return new IfStatement(ifToken.lineNumber(), ifToken.charPosition(), condition, thenBlock, elseBlock);
    }
    
    private WhileStatement whileStatement() {
        Token whileToken = expectRetrieve(Token.Kind.WHILE);
        Expression condition = expression();
        expect(Token.Kind.DO);
        StatementSequence body = statSeq();
        expect(Token.Kind.OD);
        return new WhileStatement(whileToken.lineNumber(), whileToken.charPosition(), condition, body);
    }

    private ReturnStatement returnStatement() {
        Token retToken = expectRetrieve(Token.Kind.RETURN);
        Expression value = null;
        if (!have(Token.Kind.SEMICOLON)) {
            value = expression();
        }
        expect(Token.Kind.SEMICOLON);
        return new ReturnStatement(retToken.lineNumber(), retToken.charPosition(), value);
    }

    private Expression expression() {
        // CHANGED: Your grammar has OR and AND, so we start there.
        return orExpr();
    }

    private Expression orExpr() {
        Expression left = andExpr();
        while(accept(Token.Kind.OR)) {
            Token op = currentToken;
            Expression right = andExpr();
            left = new LogicalOr(op.lineNumber(), op.charPosition(), left, right);
        }
        return left;
    }

    private Expression andExpr() {
        Expression left = relExpr();
        while(accept(Token.Kind.AND)) {
            Token op = currentToken;
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
        if (accept(Token.Kind.POW)) {
            Token op = currentToken;
            Expression right = powExpr(); // Recursive call for right-associativity
            return new Power(op.lineNumber(), op.charPosition(), left, right);
        }
        return left;
    }

    private Expression factor() {
        if (have(Token.Kind.NOT)) {
            Token op = currentToken;
            accept(Token.Kind.NOT);
            Expression expr = factor();
            return new LogicalNot(op.lineNumber(), op.charPosition(), expr);
        }
        if (have(NonTerminal.DESIGNATOR)) {
            return designator();
        } else if (have(NonTerminal.LITERAL)) {
            return literal();
        } else if (have(Token.Kind.CALL)) {
            return funcCall();
        } else if (accept(Token.Kind.OPEN_PAREN)) {
            Expression expr = expression();
            expect(Token.Kind.CLOSE_PAREN);
            return expr;
        }
        throw new QuitParseException(reportSyntaxError(NonTerminal.FACTOR));
    }

    private Expression designator() {
        Token identToken = expectRetrieve(Token.Kind.IDENT);
        Expression designator = new AddressOf(identToken.lineNumber(), identToken.charPosition(), new Identifier(identToken.lexeme()));
        while (accept(Token.Kind.OPEN_BRACKET)) {
            Expression index = expression();
            expect(Token.Kind.CLOSE_BRACKET);
            designator = new ArrayIndex(designator.lineNumber(), designator.charPosition(), designator, index);
        }
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
        Identifier id = new Identifier(identToken.lexeme());
        
        expect(Token.Kind.OPEN_PAREN);
        ArgumentList args = new ArgumentList(lineNumber(), charPosition());
        if (!have(Token.Kind.CLOSE_PAREN)) {
            args.add(expression());
            while(accept(Token.Kind.COMMA)) {
                args.add(expression());
            }
        }
        expect(Token.Kind.CLOSE_PAREN);
        
        return new FunctionCall(callToken.lineNumber(), callToken.charPosition(), id, args);
    }
}