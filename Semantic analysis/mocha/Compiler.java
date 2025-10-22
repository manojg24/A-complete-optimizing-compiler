package mocha;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.io.InputStream;
import java.util.*;
import ir.cfg.BasicBlock;
import ir.cfg.CFGPrinter;
import ir.cfg.BasicBlock;
import ir.cfg.CFGPrinter;
import ir.tac.*;

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
    private ast.AST astRoot;

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
            this.astRoot = new AST(root); 
            return new AST(root);
        } catch (QuitParseException q) {
        	this.astRoot = new AST(null);
            return this.astRoot;
        }
    }
    
    public void interpret(InputStream in) {
    	if (astRoot == null || astRoot.getRoot() == null) {
            System.out.println("Interpreter: no program to run.");
            return;
        }
        new MiniInterpreter(in, System.out).run(astRoot.getRoot());
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
            if (accept(Token.Kind.ASSIGN)) {
                expression();
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
        	
        	if (have(NonTerminal.VAR_DECL)) {
                List<Declaration> decls = varDecl();
                for (Declaration d : decls) {
                    seq.add((Statement) d);  // VariableDeclaration now implements Statement
                }
                continue;
            }
        	
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
        	if (have(NonTerminal.VAR_DECL)) {
                List<Declaration> decls = varDecl();
                for (Declaration d : decls) {
                    seq.add((Statement) d);  // VariableDeclaration now implements Statement
                }
                continue;
            }
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
    	if (have(Token.Kind.NOT)) {
            Token op = expectRetrieve(Token.Kind.NOT);
            Expression e = factor(); // right-associative unary
            return new LogicalNot(op.lineNumber(), op.charPosition(), e);
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
    
    private IR currentIR;

    /** Minimal IR handle that has asDotGraph(), as expected by CompilerTester. */
    public static class IR {
        private final List<BasicBlock> blocks;
        public IR(List<BasicBlock> blocks) { this.blocks = blocks; }
        public String asDotGraph() { return new CFGPrinter().print(blocks); }
        public List<BasicBlock> blocks() { return blocks; }
    }
    
    public IR genIR(ast.AST ast) {
        // Create a trivial CFG with a single empty basic block so things run.
    	BasicBlock bb = new BasicBlock(1);
        IRBuilder b = new IRBuilder(bb);
        // walk the main statement sequence and emit TAC
        if (ast != null && ast.getRoot() != null) {
            ast.getRoot().mainStatementSequence().accept(b);
        }
        List<BasicBlock> blocks = new ArrayList<>();
        blocks.add(bb);
        this.currentIR = new IR(blocks);
        return this.currentIR;
    }
    
    private static class NodeVisitorAdapter implements ast.NodeVisitor {
        // literals / ids
        @Override public void visit(AST.IntegerLiteral n) {}
        @Override public void visit(AST.FloatLiteral n) {}
        @Override public void visit(AST.BoolLiteral n) {}
        @Override public void visit(AST.Identifier n) {}

        // unary / binary ops
        @Override public void visit(AST.UnaryMinus n) {}
        @Override public void visit(AST.Addition n) {}
        @Override public void visit(AST.Subtraction n) {}
        @Override public void visit(AST.Multiplication n) {}
        @Override public void visit(AST.Division n) {}
        @Override public void visit(AST.Modulo n) {}
        @Override public void visit(AST.Power n) {}
        @Override public void visit(AST.LogicalNot n) {}
        @Override public void visit(AST.LogicalAnd n) {}
        @Override public void visit(AST.LogicalOr n) {}
        @Override public void visit(AST.Relation n) {}

        // lvalues / misc
        @Override public void visit(AST.AddressOf n) {}
        @Override public void visit(AST.ArrayIndex n) {}
        @Override public void visit(AST.Dereference n) {}

        // statements
        @Override public void visit(AST.StatementSequence n) {}
        @Override public void visit(AST.Assignment n) {}
        @Override public void visit(AST.IfStatement n) {}
        @Override public void visit(AST.WhileStatement n) {}
        @Override public void visit(AST.RepeatStatement n) {}
        @Override public void visit(AST.ReturnStatement n) {}

        // functions / declarations / types
        @Override public void visit(AST.FunctionCall n) {}
        @Override public void visit(AST.ArgumentList n) {}
        @Override public void visit(AST.FunctionBody n) {}
        @Override public void visit(AST.FunctionDeclaration n) {}
        @Override public void visit(AST.VariableDeclaration n) {}
        @Override public void visit(AST.DeclarationList n) {}
        @Override public void visit(AST.TypeNode n) {}

        // root
        @Override public void visit(ast.Computation n) {}
    }
    
    private final class IRBuilder extends NodeVisitorAdapter {
        private final BasicBlock bb;
        private int nextId = 1;      // TAC ids
        private int tmpIdx = 0;      // temp variable counter

        IRBuilder(BasicBlock bb){ this.bb = bb; }

        // ---- helpers ----
        private Variable v(String name) {
            // Symbol(Type) might be needed in your Symbol; using null type is OK for printing.
            return new Variable(new mocha.Symbol(name, null));
        }
        private Variable newTmp() { return v("_t" + (++tmpIdx)); }
        private int newId() { return nextId++; }

        private Value toValue(ast.Expression e) {
            if (e instanceof AST.IntegerLiteral il) return new Literal(il.getValue());
            if (e instanceof AST.FloatLiteral   fl) return new Literal(fl.getValue());
            if (e instanceof AST.BoolLiteral    bl) return new Literal(bl.getValue());
            if (e instanceof AST.Identifier     id) return v(id.getName());

            // handle binary expressions by materializing into a tmp
            if (e instanceof AST.Addition add) {
                Value l = toValue(add.getLeft());
                Value r = toValue(add.getRight());
                Variable t = newTmp();
                bb.addInstruction(new Add(newId(), t, l, r));
                return t;
            }
            if (e instanceof AST.Multiplication mul) {
                Value l = toValue(mul.getLeft());
                Value r = toValue(mul.getRight());
                Variable t = newTmp();
                // You only defined Add.java in ir.tac; reuse Assign with op="mul" quickly:
                bb.addInstruction(new Assign(newId(), t, l, r) {
                    @Override protected String op() { return "mul"; }
                });
                return t;
            }

            // fallback: literal wrapper (will print something reasonable)
            return new Literal(e);
        }

        // ---- visitors we actually need for your sample ----
        @Override
        public void visit(AST.StatementSequence node) {
            for (ast.Statement s : node) if (s != null) s.accept(this);
        }

        @Override
        public void visit(AST.Assignment node) {
            // dest must be an Identifier in your sample
            if (!(node.getDestination() instanceof AST.Identifier id))
                throw new RuntimeException("Only simple assignments supported in this minimal builder.");
            Variable dst = v(id.getName());
            Value rhs = toValue(node.getSource());

            // Emit "dst = add/mul/..." or simple "dst = literal"
            if (rhs instanceof Literal || rhs instanceof Variable) {
                // represent plain copy as an "add x 0" or define a dedicated Move op
                bb.addInstruction(new Assign(newId(), dst, rhs, null) {
                    @Override protected String op() { return "mov"; }
                    @Override public String toString() { return dst + " = " + (rhs instanceof Literal ? rhs.toString() : ((Variable)rhs).toString()); }
                });
            } else {
                // rhs already materialized into a temp by toValue()
                bb.addInstruction(new Assign(newId(), dst, rhs, null) {
                    @Override protected String op() { return "mov"; }
                    @Override public String toString() { return dst + " = " + rhs; }
                });
            }
        }

        @Override
        public void visit(AST.FunctionCall n) {
            // only need "call printInt(y)" for your input
            java.util.List<Value> args = new java.util.ArrayList<>();
            for (ast.Expression e : n.getArguments().getArguments()) args.add(toValue(e));
            bb.addInstruction(new Call(newId(), new mocha.Symbol(n.getIdentifier().getName(), null), args));
        }

        // Unused visitors (no-ops)
        @Override public void visit(AST.VariableDeclaration n) {}
        @Override public void visit(AST.IfStatement n) {}
        @Override public void visit(AST.WhileStatement n) {}
        @Override public void visit(AST.RepeatStatement n) {}
        @Override public void visit(AST.ReturnStatement n) {}
        @Override public void visit(AST.Relation n) {}
        @Override public void visit(AST.LogicalAnd n) {}
        @Override public void visit(AST.LogicalOr n) {}
        @Override public void visit(AST.LogicalNot n) {}
        @Override public void visit(AST.Power n) {}
        @Override public void visit(AST.FunctionBody n) {}
        @Override public void visit(AST.DeclarationList n) {}
        @Override public void visit(AST.TypeNode n) {}
        @Override public void visit(AST.AddressOf n) {}
        @Override public void visit(AST.ArrayIndex n) {}
        @Override public void visit(AST.Dereference n) {}
        @Override public void visit(AST.Identifier n) {}
        @Override public void visit(ast.Computation n) {}
        @Override public void visit(AST.ArgumentList n) {}
        @Override public void visit(AST.Division n) {}
        @Override public void visit(AST.Subtraction n) {}
        @Override public void visit(AST.Modulo n) {}
        @Override public void visit(AST.UnaryMinus n) {}
        @Override public void visit(AST.FloatLiteral n) {}
        @Override public void visit(AST.IntegerLiteral n) {}
        @Override public void visit(AST.BoolLiteral n) {}
    }

    /** Run selected optimizations and return DOT text of the resulting IR. */
    public String optimization(List<String> opts, boolean loop, boolean max) {
        if (currentIR == null) currentIR = genIR(this.astRoot); // safety

        // TODO: apply passes in `opts` (e.g., "dce"), and loop to convergence if requested.
        // For now, no-ops — just return the graph of currentIR.
        return currentIR.asDotGraph();
    }
    
 // ---------- Minimal interpreter for the I/O test ----------
    	private static final class MiniInterpreter implements ast.NodeVisitor {
        private final java.util.Scanner sc;
        private final java.io.PrintStream out;
        private final java.util.Map<String,Object> env = new java.util.HashMap<>();
        private final java.util.Map<String, AST.FunctionDeclaration> funcs = new java.util.HashMap<>();
        private Object eval; // holds last evaluated expression result

        MiniInterpreter(InputStream in, java.io.PrintStream out) {
            this.sc = new java.util.Scanner(in);
            this.out = out;
        }

        void run(ast.Computation prog) {
            // 1) Index function declarations (handy for user-defined calls later)
            for (AST.Declaration d : prog.functions()) {
                if (d instanceof AST.FunctionDeclaration) {
                	AST.FunctionDeclaration fd = (AST.FunctionDeclaration) d;
                    funcs.put(fd.getIdentifier().getName(), fd);
                }
            }

            // 2) Allocate/initialize globals with sensible defaults
            for (AST.Declaration d : prog.variables()) {
                if (d instanceof AST.VariableDeclaration) {
                	AST.VariableDeclaration vd = (AST.VariableDeclaration) d;
                    AST.TypeNode tn = (AST.TypeNode) vd.getTypeNode();
                    types.Type t = tn.getActualType();
                    Object def = defaultValueForType(t);
                    env.put(vd.getIdentifier().getName(), def);
                }
            }

            // 3) Execute main body
            prog.mainStatementSequence().accept(this);
        }

        /** Default value for a type (ints 0, floats 0.0f, bool false, arrays allocated and filled). */
        private Object defaultValueForType(types.Type t) {
            if (t instanceof types.IntType)   return Integer.valueOf(0);
            if (t instanceof types.FloatType) return Float.valueOf(0.0f); // use Float, not Double
            if (t instanceof types.BoolType)  return Boolean.FALSE;
            if (t instanceof types.ArrayType) return allocArray((types.ArrayType) t);
            return null; // for void or unknown, nothing to store
        }

        /** Recursively allocates Java arrays for Mocha array types (only when extent >= 0). */
        private Object allocArray(types.ArrayType at) {
            int n = at.getExtent();
            if (n < 0) return null; // unspecified size: don’t allocate
            Object[] arr = new Object[n];
            for (int i = 0; i < n; i++) {
                arr[i] = defaultValueForType(at.getBase());
            }
            return arr;
        }
        
        private static final class ReturnSignal extends RuntimeException {
            final Object value;
            ReturnSignal(Object v) { this.value = v; }
        }
        
        private boolean asBool(Object v) {
            if (v instanceof Boolean) return (Boolean) v;
            throw new RuntimeException("Condition is not boolean: " + v);
        }
        
        @Override
        public void visit(AST.ReturnStatement node) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
                throw new ReturnSignal(eval);
            } else {
                throw new ReturnSignal(null);
            }
        }
        
        private static boolean isInt(Object o)    { return o instanceof Integer; }
        private static boolean isFloaty(Object o) { return o instanceof Float || o instanceof Double; }
        private static double  toDouble(Object o) {
            if (o instanceof Integer) return ((Integer)o).doubleValue();
            if (o instanceof Float)   return ((Float)o).doubleValue();
            if (o instanceof Double)  return (Double)o;
            throw new RuntimeException("N/A");
        }
        
        @Override
        public void visit(AST.IntegerLiteral n) { eval = Integer.valueOf(n.getValue()); }

        @Override
        public void visit(AST.FloatLiteral n)   { eval = Float.valueOf(n.getValue()); }

        @Override
        public void visit(AST.UnaryMinus n) {
            n.getExpr().accept(this);
            Object v = eval;
            if (isInt(v))        eval = -((Integer) v);
            else if (isFloaty(v)) eval = Float.valueOf((float)(-toDouble(v)));
            else throw new RuntimeException("Unary minus on non-numeric: " + v);
        }

        @Override
        public void visit(AST.Addition n) {
            n.getLeft().accept(this);  Object L = eval;
            n.getRight().accept(this); Object R = eval;
            if (isInt(L) && isInt(R)) eval = (Integer)L + (Integer)R;
            else eval = Float.valueOf((float)(toDouble(L) + toDouble(R)));
        }

        @Override
        public void visit(AST.Subtraction n) {
            n.getLeft().accept(this);  Object L = eval;
            n.getRight().accept(this); Object R = eval;
            if (isInt(L) && isInt(R)) eval = (Integer)L - (Integer)R;
            else eval = Float.valueOf((float)(toDouble(L) - toDouble(R)));
        }

        @Override
        public void visit(AST.Multiplication n) {
            n.getLeft().accept(this);  Object L = eval;
            n.getRight().accept(this); Object R = eval;
            if (isInt(L) && isInt(R)) eval = (Integer)L * (Integer)R;
            else eval = Float.valueOf((float)(toDouble(L) * toDouble(R)));
        }

        @Override
        public void visit(AST.Division n) {
            n.getLeft().accept(this);  Object L = eval;
            n.getRight().accept(this); Object R = eval;
            eval = Float.valueOf((float)(toDouble(L) / toDouble(R))); // numeric division
        }

        @Override
        public void visit(AST.Modulo n) {
            n.getLeft().accept(this);  Object L = eval;
            n.getRight().accept(this); Object R = eval;
            if (isInt(L) && isInt(R)) eval = (Integer)L % (Integer)R;
            else throw new RuntimeException("Modulo requires int operands at runtime");
        }
        
        @Override
        public void visit(AST.LogicalNot n) {
            n.getExpression().accept(this);
            eval = Boolean.valueOf(!asBool(eval));
        }

        @Override
        public void visit(AST.LogicalAnd n) {
            n.getLeft().accept(this);
            boolean lb = asBool(eval);
            if (!lb) { eval = Boolean.FALSE; return; } // short-circuit
            n.getRight().accept(this);
            eval = Boolean.valueOf(asBool(eval));
        }

        @Override
        public void visit(AST.LogicalOr n) {
            n.getLeft().accept(this);
            boolean lb = asBool(eval);
            if (lb) { eval = Boolean.TRUE; return; } // short-circuit
            n.getRight().accept(this);
            eval = Boolean.valueOf(asBool(eval));
        }
        
        @Override
        public void visit(AST.Power n) {
            n.getBase().accept(this);
            Object L = eval;
            n.getExponent().accept(this);
            Object R = eval;

            if (isInt(L) && isInt(R)) {
                int b = (Integer) L;
                int e = (Integer) R;
                if (e < 0) {
                    // negative int exponent -> float result
                    eval = Float.valueOf((float)Math.pow(b, e));
                } else {
                    eval = Integer.valueOf(intPow(b, e));
                }
                return;
            }

            // any float involved -> float result
            double bd = toDouble(L);
            double ed = toDouble(R);
            eval = Float.valueOf((float)Math.pow(bd, ed));
        }

        // fast integer power (non-negative exponent)
        private int intPow(int base, int exp) {
            int result = 1;
            int b = base;
            int e = exp;
            while (e > 0) {
                if ((e & 1) == 1) result *= b;
                b *= b;
                e >>= 1;
            }
            return result;
        }

        @Override
        public void visit(AST.Relation n) {
            n.getLeft().accept(this);  Object L = eval;
            n.getRight().accept(this); Object R = eval;

            // Adjust the getter to match your AST: getOp() / getOperator() / getRelop()
            String op = n.getOperator();

            boolean res;
            if (L instanceof Number && R instanceof Number) {
                double a = toDouble(L), b = toDouble(R);
                switch (op) {
                    case "==": res = (a == b); break;
                    case "!=": res = (a != b); break;
                    case "<":  res = (a <  b); break;
                    case "<=": res = (a <= b); break;
                    case ">":  res = (a >  b); break;
                    case ">=": res = (a >= b); break;
                    default: throw new RuntimeException("Unknown relop: " + op);
                }
            } else if (L instanceof Boolean && R instanceof Boolean) {
                boolean a = (Boolean)L, b = (Boolean)R;
                switch (op) {
                    case "==": res = (a == b); break;
                    case "!=": res = (a != b); break;
                    default: throw new RuntimeException("Bool relop not supported: " + op);
                }
            } else {
                throw new RuntimeException("Relation operands must be both numeric or both bool");
            }
            eval = Boolean.valueOf(res);
        }

        // ---------- statements ----------
        @Override
        public void visit(AST.StatementSequence node) {
            for (ast.Statement s : node) if (s != null) s.accept(this);
        }
        
        @Override
        public void visit(AST.VariableDeclaration n) {
            types.Type t = ((AST.TypeNode) n.getTypeNode()).getActualType();
            env.put(n.getIdentifier().getName(), defaultValueForType(t));
        }

//        @Override
//        public void visit(AST.Assignment node) {
//            node.getSource().accept(this);
//            Object rhs = eval;
//            // only simple identifiers needed for the first test
//            if (node.getDestination() instanceof AST.Identifier id) {
//                env.put(id.getName(), rhs);
//            } else {
//                throw new RuntimeException("Only simple assignments supported in MiniInterpreter.");
//            }
//            eval = null;
//        }

        @Override
        public void visit(AST.FunctionCall n) {
            // 1) Evaluate argument expressions to values
            java.util.List<Object> argVals = new java.util.ArrayList<>();
            for (ast.Expression e : n.getArguments().getArguments()) {
                e.accept(this);
                argVals.add(eval);
            }

            String name = n.getIdentifier().getName();

            // 2) Built-ins (no default that throws!)
            if ("printInt".equals(name)) {
                int i = (argVals.get(0) instanceof Number) ? ((Number)argVals.get(0)).intValue() : 0;
                out.print(i + " ");
                eval = null; 
                return;
            } else if ("printFloat".equals(name)) {
                double d = (argVals.get(0) instanceof Number) ? ((Number)argVals.get(0)).doubleValue() : 0.0;
                out.printf("%.2f ", d);
                eval = null; 
                return;
            } else if ("printBool".equals(name)) {
                boolean b = (argVals.get(0) instanceof Boolean) ? ((Boolean)argVals.get(0)) : false;
                out.print(b ? "true " : "false ");
                eval = null; 
                return;
            } else if ("println".equals(name)) {
                out.println();
                eval = null; 
                return;
            } else if ("readInt".equals(name)) {
                out.print("int? ");
                eval = Integer.valueOf(sc.nextInt());
                return;
            } else if ("readFloat".equals(name)) {
                out.print("float? ");
                eval = Double.valueOf(sc.nextDouble());
                return;
            } else if ("readBool".equals(name)) {
                out.print("true or false? ");
                String tok = sc.next();
                eval = Boolean.valueOf("true".equalsIgnoreCase(tok.trim()));
                return;
            }

            // 3) User-defined function
            AST.FunctionDeclaration fd = funcs.get(name);
            if (fd == null) throw new RuntimeException("N/A");

            // Save current env (simple single-frame model)
            java.util.Map<String,Object> saved = new java.util.HashMap<>(env);
            try {
                // Bind parameters (values, not AST nodes!)
                java.util.List<AST.FormalParameter> ps = fd.getParameters();
                for (int i = 0; i < ps.size(); i++) {
                    env.put(ps.get(i).getIdentifier().getName(), argVals.get(i));
                }

                // Allocate locals with defaults
                for (AST.Declaration d : fd.getBody().getDeclarations()) {
                    if (d instanceof AST.VariableDeclaration) {
                    	AST.VariableDeclaration vd = (AST.VariableDeclaration) d;
                        types.Type t = ((AST.TypeNode)vd.getTypeNode()).getActualType();
                        env.put(vd.getIdentifier().getName(), defaultValueForType(t));
                    }
                }

                // Execute body and catch return
                try {
                    fd.getBody().getStatements().accept(this);
                    eval = null; // no explicit return => void
                } catch (ReturnSignal r) {
                    eval = r.value;
                }
            } finally {
                env.clear();
                env.putAll(saved);
            }
        }
        
        @Override
        public void visit(AST.IfStatement n) {
            n.getCondition().accept(this);
            if (asBool(eval)) {
                n.getThenBlock().accept(this);
            } else if (n.getElseBlock() != null) {
                n.getElseBlock().accept(this);
            }
            eval = null;
        }

        @Override
        public void visit(AST.WhileStatement n) {
            for (;;) {
                n.getCondition().accept(this);
                if (!asBool(eval)) break;
                n.getBody().accept(this);
            }
            eval = null;
        }

        @Override
        public void visit(AST.RepeatStatement n) {
            do {
                n.getBody().accept(this);
                n.getCondition().accept(this);
            } while (!asBool(eval));
            eval = null;
        }

        @Override
        public void visit(AST.ArgumentList node) {
            // Evaluate args left-to-right; keep last in eval for convenience
            for (ast.Expression e : node.getArguments()) e.accept(this);
        }
        
        @Override
        public void visit(AST.Assignment node) {
            // evaluate RHS normally
            node.getSource().accept(this);
            Object rhs = eval;

            ast.Expression dest = node.getDestination();
            if (dest instanceof AST.Identifier) {
            	AST.Identifier id = (AST.Identifier) dest;
                env.put(id.getName(), rhs);
            } else if (dest instanceof AST.ArrayIndex ) {
            	AST.ArrayIndex ai = (AST.ArrayIndex) dest;
                // evaluate base and index explicitly (we need the container)
            	Object base = valueOf(ai.getBase());
            	if (!(base instanceof Object[])) {
            	    throw new RuntimeException("Assigning into non-array");
            	}
            	Object[] arr = (Object[]) base;

            	// evaluate index once and validate it's numeric
            	Object idxObj = valueOf(ai.getIndex());
            	if (!(idxObj instanceof Number)) {
            	    throw new RuntimeException("Array index is not an int");
            	}
            	int idx = ((Number) idxObj).intValue();

            	if (idx < 0 || idx >= arr.length) {
            	    throw new RuntimeException("Index out of bounds: " + idx);
            	}

            	arr[idx] = rhs;
            } else {
                throw new RuntimeException("Unsupported lvalue: " + dest.getClass().getSimpleName());
            }
            eval = null;
        }

        // ---------- expressions ----------
        @Override public void visit(AST.BoolLiteral node)   { eval = Boolean.valueOf(node.getValue()); }

        @Override
        public void visit(AST.Identifier node) {
            Object v = env.get(node.getName());
            if (v == null) throw new RuntimeException("Uninitialized var: " + node.getName());
            eval = v;
        }

        // The rest of NodeVisitor methods (not used in the I/O test) can be no-ops:
        @Override public void visit(ast.Computation n) {}
        @Override public void visit(AST.AddressOf n)   { throw new RuntimeException("N/A"); }
        @Override public void visit(AST.ArrayIndex n)  { throw new RuntimeException("N/A"); }
        @Override public void visit(AST.Dereference n) { throw new RuntimeException("N/A"); }
        @Override public void visit(AST.FunctionBody n){ throw new RuntimeException("N/A"); }
        @Override public void visit(AST.FunctionDeclaration n){ throw new RuntimeException("N/A"); }
        @Override public void visit(AST.DeclarationList n){ /* globals handled in run() */ }
        @Override public void visit(AST.TypeNode n) {}

        // helper to evaluate an expression node to a Java value
        private Object valueOf(ast.Expression e) {
            e.accept(this);
            return eval;
        }
    }
}
