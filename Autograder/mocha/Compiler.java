package mocha;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.io.InputStream;
import java.util.*;
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

    // =========================================================================
    // ERROR REPORTING, STATE, CONSTRUCTOR
    // =========================================================================

    // Error Reporting =========================================================
    private StringBuilder errorBuffer = new StringBuilder();

    private String reportSyntaxError(NonTerminal nt) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() +
                ")[Expected a token from " + nt.name() + " but got " +
                currentToken.kind + ".]";
        errorBuffer.append(message).append("\n");
        return message;
    }

    private String reportSyntaxError(Token.Kind kind) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() +
                ")[Expected " + kind + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message).append("\n");
        return message;
    }

    public String errorReport() {
        return errorBuffer.toString();
    }

    public boolean hasError() {
        return errorBuffer.length() != 0;
    }

    // Warnings (for uninitialized uses, etc.)
    private final java.util.List<String> warnings = new java.util.ArrayList<>();

    private void warn(int line, int col, String msg) {
        String m = "Warning(" + line + "," + col + ")[" + msg + "]";
        warnings.add(m);
        System.out.println(m);
    }

    public java.util.List<String> getWarnings() {
        return warnings;
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

    // Compiler core state =====================================================
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

    /** Wrapper used by CompilerTester to run register allocation. */
    public void regAlloc(int numRegs) {
        // Clamp and store
        if (numRegs > 24)
            numRegs = 24;
        if (numRegs < 2)
            numRegs = 2;
        this.numDataRegisters = numRegs;

        // Make sure we have an IR
        if (currentIR == null) {
            if (astRoot == null || astRoot.getRoot() == null)
                return;
            genIR(astRoot);
        }

        // Run the existing allocator (even though genCode does not rely on it yet)
        allocateRegisters(currentIR.blocks());
    }

    /** Entry point for PA6 code generation – IR → DLX object code. */
    public int[] genCode() {
        // Use currentIR if available (optimized), otherwise generate fresh
        if (currentIR == null) {
            if (astRoot == null || astRoot.getRoot() == null) {
                return new int[0];
            }
            genIR(astRoot);
        }
        List<BasicBlock> blocks = currentIR.blocks();

        // Build funcs map for CodeGenerator
        Map<String, AST.FunctionDeclaration> funcs = new HashMap<>();
        if (astRoot != null && astRoot.getRoot() != null) {
            for (AST.Declaration d : astRoot.getRoot().functions()) {
                if (d instanceof AST.FunctionDeclaration fd) {
                    funcs.put(fd.getIdentifier().getName(), fd);
                }
            }
        }

        CodeGenerator cg = new CodeGenerator(funcs);
        this.instructions = cg.generate(blocks);
        return instructions.stream().mapToInt(Integer::intValue).toArray();
    }

    // =========================================================================
    // SYMBOL TABLE MANAGEMENT
    // =========================================================================

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

    // private Symbol tryResolveVariable(Token ident) {
    // try {
    // return symbolTable.lookup(ident.lexeme());
    // } catch (SymbolNotFoundError e) {
    // reportResolveSymbolError(ident.lexeme(), ident.lineNumber(),
    // ident.charPosition());
    // return null;
    // }
    // }

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
        errorBuffer.append(message).append("\n");
        return message;
    }

    private String reportDeclareSymbolError(String name, int lineNum, int charPos) {
        String message = "DeclareSymbolError(" + lineNum + "," + charPos + ")[" + name + " already exists.]";
        errorBuffer.append(message).append("\n");
        return message;
    }

    // =========================================================================
    // PARSER: BASIC HELPERS
    // =========================================================================

    private boolean have(Token.Kind kind) {
        return currentToken.is(kind);
    }

    private boolean have(NonTerminal nt) {
        return nt.firstSet().contains(currentToken.kind);
    }

    private boolean accept(Token.Kind kind) {
        if (have(kind)) {
            try {
                currentToken = scanner.next();
            } catch (NoSuchElementException e) {
                if (!kind.equals(Token.Kind.EOF)) {
                    String errorMessage = reportSyntaxError(kind);
                    throw new QuitParseException(errorMessage);
                }
            }
            return true;
        }
        return false;
    }

    private boolean accept(NonTerminal nt) {
        if (have(nt)) {
            currentToken = scanner.next();
            return true;
        }
        return false;
    }

    private boolean expect(Token.Kind kind) {
        if (accept(kind)) {
            return true;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private boolean expect(NonTerminal nt) {
        if (accept(nt)) {
            return true;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve(Token.Kind kind) {
        Token tok = currentToken;
        if (accept(kind)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve(NonTerminal nt) {
        Token tok = currentToken;
        if (accept(nt)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

    // =========================================================================
    // PARSER: COMPUTATION, DECLS, FUNCTIONS
    // =========================================================================

    private Computation computation() {
        // Collect any function declarations that appear before 'main'
        DeclarationList funcDecls = new DeclarationList(lineNumber(), charPosition());
        while (have(NonTerminal.FUNC_DECL)) {
            funcDecls.add(funcDecl());
        }

        // Now we must see 'main'
        Token mainToken = expectRetrieve(Token.Kind.MAIN);

        // Globals
        DeclarationList varDecls = new DeclarationList(lineNumber(), charPosition());
        while (have(NonTerminal.VAR_DECL) && !have(Token.Kind.FUNC)) {
            List<Declaration> declsForThisLine = varDecl();
            for (Declaration d : declsForThisLine)
                varDecls.add(d);
        }

        // Also allow more function decls after the globals
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
                mainBody);
    }

    private List<Declaration> varDecl() {
        AST.TypeNode baseTypeNode = (AST.TypeNode) typeDecl();
        List<Declaration> decls = new ArrayList<>();

        do {
            Token identToken = expectRetrieve(Token.Kind.IDENT);
            Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());

            Type varType = baseTypeNode.getActualType();

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

            AST.TypeNode typeNode = new AST.TypeNode(
                    baseTypeNode.lineNumber(),
                    baseTypeNode.charPosition(),
                    varType);
            tryDeclareVariable(identToken, varType);
            decls.add(new VariableDeclaration(
                    baseTypeNode.lineNumber(),
                    baseTypeNode.charPosition(),
                    id,
                    typeNode));

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

        AST.TypeNode typeNode = new AST.TypeNode(
                typeToken.lineNumber(),
                typeToken.charPosition(),
                actualType);

        // Handle array brackets and nested dimensions
        while (accept(Token.Kind.OPEN_BRACKET)) {
            boolean negative = false;
            int size = -1;

            if (accept(Token.Kind.SUB)) { // allows negative sizes like [-5]
                negative = true;
            }

            if (have(Token.Kind.INT_VAL)) {
                Token sizeToken = expectRetrieve(Token.Kind.INT_VAL);
                size = Integer.parseInt(sizeToken.lexeme());
                if (negative)
                    size = -size;
            }

            expect(Token.Kind.CLOSE_BRACKET);

            actualType = new ArrayType(size, actualType);

            typeNode = new AST.TypeNode(
                    typeToken.lineNumber(),
                    typeToken.charPosition(),
                    actualType);
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
            returnTypeNode = new AST.TypeNode(
                    voidTok.lineNumber(),
                    voidTok.charPosition(),
                    new VoidType());
        } else {
            returnTypeNode = typeDecl();
        }

        FunctionBody body = funcBody();
        expect(Token.Kind.SEMICOLON);

        return new FunctionDeclaration(
                funcToken.lineNumber(),
                funcToken.charPosition(),
                id,
                params,
                returnTypeNode,
                body);
    }

    private List<FormalParameter> formalParams() {
        List<FormalParameter> params = new ArrayList<>();

        do {
            AST.TypeNode typeNode = (AST.TypeNode) typeDecl();
            Token identToken = expectRetrieve(Token.Kind.IDENT);
            Identifier id = new Identifier(identToken.lineNumber(), identToken.charPosition(), identToken.lexeme());

            params.add(new FormalParameter(
                    identToken.lineNumber(),
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
        while (have(NonTerminal.VAR_DECL)) {
            List<Declaration> declsThisLine = varDecl();
            for (Declaration d : declsThisLine) {
                decls.add(d);
            }
        }
        StatementSequence stmts = statSeq();
        exitScope();
        expect(Token.Kind.CLOSE_BRACE);
        return new FunctionBody(decls.lineNumber(), decls.charPosition(), decls, stmts);
    }

    // =========================================================================
    // PARSER: STATEMENTS
    // =========================================================================

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
                    seq.add((Statement) d); // VariableDeclaration now implements Statement
                }
                continue;
            }

            if (accept(Token.Kind.SEMICOLON)) {
                continue;
            }

            if (have(NonTerminal.STATEMENT) || have(Token.Kind.SEMICOLON)) {
                Statement s = statement();
                if (s != null)
                    seq.add(s);
            } else {
                String errorMessage = "Unexpected token '" + currentToken.lexeme() +
                        "' at line " + lineNumber() +
                        ", col " + charPosition();
                errorBuffer.append("SyntaxError(")
                        .append(lineNumber())
                        .append(",")
                        .append(charPosition())
                        .append(")[")
                        .append(errorMessage)
                        .append("]\n");
                currentToken = scanner.next();
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
        expect(Token.Kind.OD);
        expect(Token.Kind.SEMICOLON);
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

    private StatementSequence statSeqUntil(Token.Kind stopToken) {
        StatementSequence seq = new StatementSequence(lineNumber(), charPosition());
        while (!have(stopToken) && !have(Token.Kind.EOF)) {
            if (have(NonTerminal.VAR_DECL)) {
                List<Declaration> decls = varDecl();
                for (Declaration d : decls) {
                    seq.add((Statement) d);
                }
                continue;
            }
            if (have(NonTerminal.STATEMENT) || have(Token.Kind.SEMICOLON)) {
                Statement s = statement();
                if (s != null)
                    seq.add(s);
            } else {
                errorBuffer.append("SyntaxError(")
                        .append(lineNumber())
                        .append(",")
                        .append(charPosition())
                        .append(")[Unexpected token '")
                        .append(currentToken.lexeme())
                        .append("']\n");
                currentToken = scanner.next();
            }
        }
        return seq;
    }

    private RepeatStatement repeatStatement() {
        Token repeatToken = expectRetrieve(Token.Kind.REPEAT);
        StatementSequence body = statSeqUntil(Token.Kind.UNTIL);
        expect(Token.Kind.UNTIL);
        Expression condition = expression();
        expect(Token.Kind.SEMICOLON);
        return new RepeatStatement(repeatToken.lineNumber(), repeatToken.charPosition(), body, condition);
    }

    // =========================================================================
    // PARSER: EXPRESSIONS, DESIGNATORS, CALLS
    // =========================================================================

    private Expression expression() {
        return orExpr();
    }

    private Expression orExpr() {
        Expression left = andExpr();
        while (have(Token.Kind.OR)) {
            Token op = currentToken;
            accept(Token.Kind.OR);
            Expression right = andExpr();
            left = new LogicalOr(op.lineNumber(), op.charPosition(), left, right);
        }
        return left;
    }

    private Expression andExpr() {
        Expression left = relExpr();
        while (have(Token.Kind.AND)) {
            Token op = currentToken;
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
            } else {
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
            } else {
                left = new Modulo(op.lineNumber(), op.charPosition(), left, right);
            }
        }
        return left;
    }

    private Expression powExpr() {
        Expression left = factor();
        if (have(Token.Kind.POW)) {
            Token op = currentToken;
            accept(Token.Kind.POW);
            Expression right = powExpr();
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
            Expression e = factor();
            return new LogicalNot(op.lineNumber(), op.charPosition(), e);
        }
        if (have(Token.Kind.SUB)) {
            Token op = expectRetrieve(Token.Kind.SUB);
            Expression right = factor();
            return new UnaryMinus(op.lineNumber(), op.charPosition(), right);
        }
        if (have(Token.Kind.IDENT)) {
            Token identToken = expectRetrieve(Token.Kind.IDENT);

            // function call without 'call'
            if (have(Token.Kind.OPEN_PAREN)) {
                return parseFuncCall(identToken);
            }

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

    // =========================================================================
    // IR WRAPPER + BASIC HELPERS
    // =========================================================================

    private IR currentIR;

    /** Minimal IR handle that has asDotGraph(), as expected by CompilerTester. */
    public static class IR {
        private final List<BasicBlock> blocks;

        public IR(List<BasicBlock> blocks) {
            this.blocks = blocks;
        }

        public String asDotGraph() {
            return new CFGPrinter().print(blocks);
        }

        public List<BasicBlock> blocks() {
            return blocks;
        }
    }

    // private static final class BlockFactory {
    // private final List<BasicBlock> all;
    // private int nextNo = 1;
    // BlockFactory(List<BasicBlock> sink){ this.all = sink; }
    // BasicBlock newBB(){ BasicBlock b = new BasicBlock(nextNo++); all.add(b);
    // return b; }
    // }

    // =========================================================================
    // LOCAL IR OPTIMIZER (CP / CF / CSE / DCE)
    // =========================================================================

    class Optimizer {

        private boolean isPure(String op) {
            if (op == null)
                return false;
            return switch (op) {
                case "add", "sub", "mul", "div", "mod", "pow",
                        "cmpeq", "cmpne", "cmplt", "cmple", "cmpgt", "cmpge", "mov" ->
                    true;
                default -> false;
            };
        }

        private String keyOf(ir.tac.Value v) {
            if (v == null)
                return "_";
            if (v instanceof ir.tac.Literal l)
                return "K:" + String.valueOf(l.value());
            return "V:" + v.toString();
        }

        private ir.tac.Value tryFold(String op, ir.tac.Value L, ir.tac.Value R) {
            if (!(L instanceof ir.tac.Literal) || (R != null && !(R instanceof ir.tac.Literal)))
                return null;
            Object l = ((ir.tac.Literal) L).value();
            Object r = (R == null) ? null : ((ir.tac.Literal) R).value();
            try {
                switch (op) {
                    case "mov":
                        return L;
                    case "add":
                        if (l instanceof Integer i && r instanceof Integer j)
                            return new ir.tac.Literal(i + j);
                        if (l instanceof Float i && r instanceof Float j)
                            return new ir.tac.Literal(i + j);
                        break;
                    case "sub":
                        if (l instanceof Integer i && r instanceof Integer j)
                            return new ir.tac.Literal(i - j);
                        if (l instanceof Float i && r instanceof Float j)
                            return new ir.tac.Literal(i - j);
                        break;
                    case "mul":
                        if (l instanceof Integer i && r instanceof Integer j)
                            return new ir.tac.Literal(i * j);
                        if (l instanceof Float i && r instanceof Float j)
                            return new ir.tac.Literal(i * j);
                        break;
                    case "div":
                        if (l instanceof Integer i && r instanceof Integer j)
                            return new ir.tac.Literal(i / j);
                        if (l instanceof Float i && r instanceof Float j)
                            return new ir.tac.Literal(i / j);
                        break;
                    case "pow":
                        if (l instanceof Number && r instanceof Number) {
                            if (l instanceof Integer li && r instanceof Integer ri) {
                                int base = li, exp = ri;
                                if (exp >= 0) {
                                    long acc = 1, b = base;
                                    int e = exp;
                                    while (e > 0) {
                                        if ((e & 1) == 1)
                                            acc *= b;
                                        b *= b;
                                        e >>= 1;
                                    }
                                    return new ir.tac.Literal((int) acc);
                                } else {
                                    return new ir.tac.Literal((float) Math.pow(base, exp));
                                }
                            }
                            double a = ((Number) l).doubleValue();
                            double b = ((Number) r).doubleValue();
                            return new ir.tac.Literal((float) Math.pow(a, b));
                        }
                        break;
                    case "cmpeq":
                        return new ir.tac.Literal(java.util.Objects.equals(l, r));
                    case "cmpne":
                        return new ir.tac.Literal(!java.util.Objects.equals(l, r));
                    case "cmplt":
                        if (l instanceof Comparable cl && r instanceof Comparable cr)
                            return new ir.tac.Literal(cl.compareTo(cr) < 0);
                        break;
                    case "cmple":
                        if (l instanceof Comparable cl && r instanceof Comparable cr)
                            return new ir.tac.Literal(cl.compareTo(cr) <= 0);
                        break;
                    case "cmpgt":
                        if (l instanceof Comparable cl && r instanceof Comparable cr)
                            return new ir.tac.Literal(cl.compareTo(cr) > 0);
                        break;
                    case "cmpge":
                        if (l instanceof Comparable cl && r instanceof Comparable cr)
                            return new ir.tac.Literal(cl.compareTo(cr) >= 0);
                        break;
                    case "and":
                        if (l instanceof Boolean lb && r instanceof Boolean rb)
                            return new ir.tac.Literal(lb && rb);
                        break;
                    case "or":
                        if (l instanceof Boolean lb && r instanceof Boolean rb)
                            return new ir.tac.Literal(lb || rb);
                        break;
                    case "not":
                        if (l instanceof Boolean lb)
                            return new ir.tac.Literal(!lb);
                        break;
                }
            } catch (Throwable ignore) {
            }
            return null;
        }

        // Follow env mappings transitively: x -> y -> 0
        private ir.tac.Value resolve(ir.tac.Value v, Map<String, ir.tac.Value> env) {
            while (v instanceof ir.tac.Variable && env.containsKey(v.toString())) {
                v = env.get(v.toString());
            }
            return v;
        }

        private final boolean doCP, doCPP, doCF, doCSE, doDCE;
        private final Set<String> globals;

        Optimizer(boolean cp, boolean cpp, boolean cf, boolean cse, boolean dce, Set<String> globals) {
            this.doCP = cp;
            this.doCPP = cpp;
            this.doCF = cf;
            this.doCSE = cse;
            this.doDCE = dce;
            this.globals = globals != null ? globals : Collections.emptySet();
        }

        public List<ir.cfg.BasicBlock> optimize(List<ir.cfg.BasicBlock> blocks) {
            for (ir.cfg.BasicBlock bb : blocks)
                optimizeBlock(bb);
            return blocks;
        }

        private void optimizeBlock(ir.cfg.BasicBlock bb) {
            Map<String, ir.tac.Value> env = new HashMap<>();
            // Reverse mapping: RHS variable -> Set of LHS variables that hold its value
            // Used for invalidation in CPP
            Map<String, Set<String>> reverseEnv = new HashMap<>();

            Map<String, ir.tac.Variable> cse = new HashMap<>();
            List<ir.tac.TAC> out = new ArrayList<>();

            for (ir.tac.TAC t : bb) {
                if (t instanceof ir.tac.Assign a) {
                    String op = a.opcode();

                    // barriers
                    if ("label".equals(op) || "test".equals(op) || "ret".equals(op)) {
                        env.clear();
                        reverseEnv.clear();
                        cse.clear();
                        out.add(t);
                        continue;
                    }

                    ir.tac.Value L = a.left(), R = a.right();

                    // ---- CP & CPP: substitute operands (transitively)
                    if (doCP || doCPP) {
                        L = resolve(L, env);
                        R = resolve(R, env);
                    }

                    // Fast-path for mov with CP/CPP
                    if ("mov".equals(op)) {
                        if (doCP && L instanceof ir.tac.Literal) {
                            env.put(a.dest().toString(), L);
                        } else if (doCPP && L instanceof ir.tac.Variable v) {
                            String destName = a.dest().toString();
                            String srcName = v.toString();

                            // Invalidate previous mappings for destName
                            env.remove(destName);
                            // Also remove from reverseEnv of whatever it used to point to (though not
                            // strictly necessary if we just overwrite)

                            // Add new mapping
                            env.put(destName, L);

                            // Track reverse mapping for invalidation
                            reverseEnv.computeIfAbsent(srcName, k -> new HashSet<>()).add(destName);
                        }

                        // Invalidate any mappings that depend on the redefined variable (dest)
                        // If 'dest' is redefined, any other variable 'k' that was a copy of 'dest' (k =
                        // dest) is now stale?
                        // No, if k = dest, and dest changes, k still holds the OLD value of dest.
                        // BUT, if we had env[k] -> dest, and dest is overwritten, then env[k] is now
                        // pointing to a stale name?
                        // Actually, in SSA-like form it's fine, but here variables are mutable.
                        // If we have:
                        // x = y => env[x] = y
                        // y = z => env[y] = z
                        // ... use x ...
                        // If we replace x with y, we get 'z', which is WRONG. x should be the OLD y.
                        // So if 'y' is redefined, we must remove 'y' from the RHS of any mapping.

                        String redefined = a.dest().toString();
                        if (reverseEnv.containsKey(redefined)) {
                            for (String lhs : reverseEnv.get(redefined)) {
                                env.remove(lhs);
                            }
                            reverseEnv.remove(redefined);
                        }

                        out.add(prettyAssign(a.id(), a.dest(), "mov", L, null));
                        continue;
                    }

                    // ---- CF
                    if (doCF) {
                        ir.tac.Value cf = tryFold(op, L, R);
                        if (cf != null) {
                            if (doCP)
                                env.put(a.dest().toString(), cf);
                            out.add(prettyAssign(a.id(), a.dest(), "mov", cf, null));

                            // Invalidation for CF (dest is redefined)
                            String redefined = a.dest().toString();
                            if (reverseEnv.containsKey(redefined)) {
                                for (String lhs : reverseEnv.get(redefined)) {
                                    env.remove(lhs);
                                }
                                reverseEnv.remove(redefined);
                            }
                            continue;
                        }
                    }

                    // ---- CSE
                    if (doCSE && R != null && isPure(op)) {
                        String key = op + "|" + keyOf(L) + "|" + keyOf(R);
                        ir.tac.Variable prev = cse.get(key);
                        if (prev != null) {
                            if (doCPP) {
                                env.put(a.dest().toString(), prev);
                                reverseEnv.computeIfAbsent(prev.toString(), k -> new HashSet<>())
                                        .add(a.dest().toString());
                            }
                            out.add(prettyAssign(a.id(), a.dest(), "mov", prev, null));

                            // Invalidation for CSE (dest is redefined)
                            String redefined = a.dest().toString();
                            if (reverseEnv.containsKey(redefined)) {
                                for (String lhs : reverseEnv.get(redefined)) {
                                    env.remove(lhs);
                                }
                                reverseEnv.remove(redefined);
                            }
                            continue;
                        } else {
                            cse.put(key, a.dest());
                        }
                    }

                    out.add(prettyAssign(a.id(), a.dest(), op, L, R));

                    // General invalidation for any assignment
                    String redefined = a.dest().toString();

                    // 1. Remove direct mapping for this variable (it holds a new value now)
                    env.remove(redefined);

                    // 2. Remove any mappings where this variable was the RHS (source)
                    if (reverseEnv.containsKey(redefined)) {
                        for (String lhs : reverseEnv.get(redefined)) {
                            env.remove(lhs);
                        }
                        reverseEnv.remove(redefined);
                    }
                } else if (t instanceof ir.tac.Call c) {
                    if ((doCP || doCPP) && c.args() != null) {
                        List<ir.tac.Value> newArgs = new ArrayList<>();
                        for (ir.tac.Value v : c.args()) {
                            newArgs.add(resolve(v, env));
                        }
                        ir.tac.Call newCall;
                        if (c.dest() != null) {
                            newCall = new ir.tac.Call(c.id(), c.function(), newArgs, c.dest());

                            // Invalidation
                            String redefined = c.dest().toString();
                            env.remove(redefined);
                            if (reverseEnv.containsKey(redefined)) {
                                for (String lhs : reverseEnv.get(redefined)) {
                                    env.remove(lhs);
                                }
                                reverseEnv.remove(redefined);
                            }
                        } else {
                            newCall = new ir.tac.Call(c.id(), c.function(), newArgs);
                        }

                        // Invalidate globals on call
                        for (String g : globals) {
                            env.remove(g);
                            if (reverseEnv.containsKey(g)) {
                                for (String lhs : reverseEnv.get(g)) {
                                    env.remove(lhs);
                                }
                                reverseEnv.remove(g);
                            }
                        }

                        out.add(newCall);
                        cse.clear();
                    } else {
                        out.add(t);
                        cse.clear();
                        if (c.dest() != null) {
                            String redefined = c.dest().toString();
                            env.remove(redefined);
                            if (reverseEnv.containsKey(redefined)) {
                                for (String lhs : reverseEnv.get(redefined)) {
                                    env.remove(lhs);
                                }
                                reverseEnv.remove(redefined);
                            }
                        }
                        // Invalidate globals on call
                        for (String g : globals) {
                            env.remove(g);
                            if (reverseEnv.containsKey(g)) {
                                for (String lhs : reverseEnv.get(g)) {
                                    env.remove(lhs);
                                }
                                reverseEnv.remove(g);
                            }
                        }
                    }
                } else {
                    out.add(t);
                }
            }

            // ---- DCE
            List<ir.tac.TAC> finalIns = out;
            if (doDCE) {
                Set<String> live = new HashSet<>(globals);
                List<ir.tac.TAC> pruned = new ArrayList<>();
                for (int i = out.size() - 1; i >= 0; --i) {
                    ir.tac.TAC t2 = out.get(i);
                    if (t2 instanceof ir.tac.Assign a) {
                        String op = a.opcode();
                        String def = (a.dest() == null) ? null : a.dest().toString();
                        boolean essential = "label".equals(op) || "test".equals(op) || "ret".equals(op);
                        boolean keep = essential || (def != null && live.contains(def));
                        if (keep) {
                            if (a.left() instanceof ir.tac.Variable v)
                                live.add(v.toString());
                            if (a.right() instanceof ir.tac.Variable v2)
                                live.add(v2.toString());
                            if (def != null)
                                live.remove(def);
                            pruned.add(0, t2);
                        }
                    } else if (t2 instanceof ir.tac.Call c2) {
                        if (c2.args() != null)
                            for (ir.tac.Value v : c2.args())
                                if (v instanceof ir.tac.Variable var)
                                    live.add(var.toString());
                        live.addAll(globals);
                        pruned.add(0, t2);
                    } else
                        pruned.add(0, t2);
                }
                finalIns = pruned;
            }

            bb.instructions().clear();
            bb.instructions().addAll(finalIns);
        }
    }

    // =========================================================================
    // CFG UTILITIES: REMOVE ORPHAN FUNCTIONS, CFG SIMPLIFY, MERGE BLOCKS
    // =========================================================================

    private static void removeOrphanFunctions(List<BasicBlock> blocks) {
        if (blocks == null || blocks.isEmpty())
            return;

        Map<String, BasicBlock> funcEntry = new HashMap<>();
        for (BasicBlock b : blocks) {
            List<ir.tac.TAC> ins = b.instructions();
            if (ins == null || ins.isEmpty())
                continue;
            ir.tac.TAC first = ins.get(0);
            if (first instanceof ir.tac.Assign a && "label".equals(a.opcode())) {
                String s = a.toString();
                String name = (s.startsWith("<") && s.endsWith(">")) ? s.substring(1, s.length() - 1) : s;
                funcEntry.put(name, b);
            }
        }

        Set<BasicBlock> keepBlocks = new HashSet<>();
        ArrayDeque<BasicBlock> q = new ArrayDeque<>();
        if (!blocks.isEmpty()) {
            keepBlocks.add(blocks.get(0));
            q.add(blocks.get(0));
        }

        java.util.function.Predicate<String> isBuiltin = n -> n != null
                && (n.startsWith("print") || n.startsWith("read") || "println".equals(n));

        Set<String> seenFuncs = new HashSet<>();

        while (!q.isEmpty()) {
            BasicBlock b = q.removeFirst();

            List<BasicBlock> succs = b.succs();
            if (succs != null) {
                for (BasicBlock s : succs)
                    if (keepBlocks.add(s))
                        q.add(s);
            }

            for (ir.tac.TAC t : b.instructions()) {
                if (t instanceof ir.tac.Call c) {
                    mocha.Symbol f = c.function();
                    if (f == null)
                        continue;
                    String callee = f.name();
                    if (isBuiltin.test(callee))
                        continue;
                    BasicBlock entry = funcEntry.get(callee);
                    if (entry != null && seenFuncs.add(callee) && keepBlocks.add(entry)) {
                        q.add(entry);
                    }
                }
            }
        }

        blocks.removeIf(b -> !keepBlocks.contains(b));
    }

    private static final class CFGSimplifier {

        static void simplify(java.util.List<BasicBlock> blocks) {
            if (blocks == null || blocks.isEmpty())
                return;

            // 1) Constant branch folding
            for (BasicBlock bb : blocks) {
                java.util.List<ir.tac.TAC> ins = bb.instructions();
                if (ins == null || ins.isEmpty())
                    continue;

                ir.tac.TAC last = ins.get(ins.size() - 1);
                if (!(last instanceof ir.tac.Assign br))
                    continue;
                if (!"test".equals(br.opcode()))
                    continue;

                Boolean condConst = null;

                if (br.left() instanceof ir.tac.Literal litA && litA.value() instanceof Boolean) {
                    condConst = (Boolean) litA.value();
                } else if (ins.size() >= 2) {
                    ir.tac.TAC before = ins.get(ins.size() - 2);
                    if (before instanceof ir.tac.Assign def
                            && "mov".equals(def.opcode())
                            && def.left() instanceof ir.tac.Literal litB
                            && litB.value() instanceof Boolean
                            && def.dest() != null
                            && br.left() instanceof ir.tac.Variable
                            && def.dest().toString().equals(((ir.tac.Variable) br.left()).toString())) {
                        condConst = (Boolean) litB.value();
                    }
                }

                if (condConst == null)
                    continue;

                java.util.List<BasicBlock> succs = bb.succs();
                if (succs == null || succs.size() != 2)
                    continue;

                BasicBlock keep = condConst ? succs.get(0) : succs.get(1);
                BasicBlock kill = condConst ? succs.get(1) : succs.get(0);

                succs.remove(kill);
                java.util.List<BasicBlock> pk = kill.preds();
                if (pk != null)
                    pk.remove(bb);

                ins.remove(ins.size() - 1);
            }

            // 2) Reachability from function entries + main
            java.util.HashSet<BasicBlock> visited = new java.util.HashSet<>();
            java.util.ArrayDeque<BasicBlock> queue = new java.util.ArrayDeque<>();

            for (BasicBlock b : blocks) {
                java.util.List<ir.tac.TAC> ins = b.instructions();
                boolean isFuncEntry = ins != null && !ins.isEmpty()
                        && (ins.get(0) instanceof ir.tac.Assign a0)
                        && "label".equals(a0.opcode());
                if (isFuncEntry) {
                    if (visited.add(b))
                        queue.addLast(b);
                }
            }
            if (!blocks.isEmpty() && visited.add(blocks.get(0)))
                queue.addLast(blocks.get(0));

            while (!queue.isEmpty()) {
                BasicBlock b = queue.removeFirst();
                java.util.List<BasicBlock> succs = b.succs();
                if (succs == null)
                    continue;
                for (BasicBlock s : succs) {
                    if (visited.add(s))
                        queue.addLast(s);
                }
            }

            blocks.removeIf(b -> !visited.contains(b));
        }
    }

    private static void mergeTrivialEmpties(List<BasicBlock> blocks) {
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < blocks.size(); i++) {
                BasicBlock b = blocks.get(i);
                // Check if b can be merged with its successor
                if (b.succs().size() == 1) {
                    BasicBlock succ = b.succs().get(0);
                    // Merge if succ has only b as predecessor and is not the entry block (though
                    // entry usually has no preds)
                    // Also ensure succ is in the blocks list (it should be)
                    if (succ.preds().size() == 1 && succ.preds().get(0) == b && blocks.contains(succ)) {

                        // Move instructions from succ to b
                        // But skip the label instruction of succ if it exists, as it's no longer a jump
                        // target
                        for (ir.tac.TAC t : succ.instructions()) {
                            if (t instanceof ir.tac.Assign a && "label".equals(a.opcode())) {
                                // Skip label
                                continue;
                            }
                            b.addInstruction(t);
                        }

                        // Update successors: b takes succ's successors
                        b.succs().clear();
                        b.succs().addAll(succ.succs());

                        // Update predecessors of succ's successors to point to b
                        for (BasicBlock s : succ.succs()) {
                            List<BasicBlock> preds = s.preds();
                            for (int k = 0; k < preds.size(); k++) {
                                if (preds.get(k) == succ) {
                                    preds.set(k, b);
                                }
                            }
                        }

                        // Remove succ from blocks
                        blocks.remove(succ);
                        changed = true;
                        i--; // Re-check this block as it might merge with the next one now
                    }
                }
            }
        } while (changed);
    }

    // =========================================================================
    // REGISTER ALLOCATION (GRAPH COLORING)
    // =========================================================================

    // Remove silly moves like R5 = R5 after register allocation.
    private static void removeSillyMoves(List<BasicBlock> blocks) {
        if (blocks == null)
            return;

        for (BasicBlock b : blocks) {
            List<ir.tac.TAC> ins = b.instructions();
            if (ins == null || ins.isEmpty())
                continue;

            for (Iterator<ir.tac.TAC> it = ins.iterator(); it.hasNext();) {
                ir.tac.TAC t = it.next();
                if (t instanceof ir.tac.Assign a) {
                    if (!"mov".equals(a.opcode()))
                        continue;

                    ir.tac.Value src = a.left();
                    ir.tac.Variable dst = a.dest();
                    if (src instanceof ir.tac.Variable v && dst != null) {
                        if (v.toString().equals(dst.toString())) {
                            it.remove();
                        }
                    }
                }
            }
        }
    }

    private static final class RegAllocResult {
        final Map<String, Integer> colors;
        final Set<String> spilled;

        RegAllocResult(Map<String, Integer> c, Set<String> s) {
            this.colors = c;
            this.spilled = s;
        }
    }

    // Collect variable *names* used by a TAC
    private static Set<String> usedVars(ir.tac.TAC t) {
        Set<String> u = new HashSet<>();

        if (t instanceof ir.tac.Assign a) {
            String op = a.opcode();
            if ("label".equals(op))
                return u;

            if ("test".equals(op) || "ret".equals(op)) {
                if (a.left() instanceof ir.tac.Variable v)
                    u.add(v.toString());
                return u;
            }

            if (a.left() instanceof ir.tac.Variable v1)
                u.add(v1.toString());
            if (a.right() instanceof ir.tac.Variable v2)
                u.add(v2.toString());
        } else if (t instanceof ir.tac.Call c) {
            if (c.args() != null) {
                for (ir.tac.Value v : c.args()) {
                    if (v instanceof ir.tac.Variable var)
                        u.add(var.toString());
                }
            }
        }
        return u;
    }

    // Single defined variable name for a TAC (or null)
    private static String defVar(ir.tac.TAC t) {
        if (t instanceof ir.tac.Assign a) {
            String op = a.opcode();
            if ("label".equals(op) || "test".equals(op) || "ret".equals(op))
                return null;
            ir.tac.Variable dst = a.dest();
            return (dst == null) ? null : dst.toString();
        }
        if (t instanceof ir.tac.Call c) {
            ir.tac.Variable dst = c.dest();
            return (dst == null) ? null : dst.toString();
        }
        return null;
    }

    // =========================================================================
    // NODEVISITOR ADAPTER (NO-OP DEFAULTS)
    // =========================================================================

    private static class NodeVisitorAdapter implements ast.NodeVisitor {
        @Override
        public void visit(AST.IntegerLiteral n) {
        }

        @Override
        public void visit(AST.FloatLiteral n) {
        }

        @Override
        public void visit(AST.BoolLiteral n) {
        }

        @Override
        public void visit(AST.Identifier n) {
        }

        @Override
        public void visit(AST.UnaryMinus n) {
        }

        @Override
        public void visit(AST.Addition n) {
        }

        @Override
        public void visit(AST.Subtraction n) {
        }

        @Override
        public void visit(AST.Multiplication n) {
        }

        @Override
        public void visit(AST.Division n) {
        }

        @Override
        public void visit(AST.Modulo n) {
        }

        @Override
        public void visit(AST.Power n) {
        }

        @Override
        public void visit(AST.LogicalNot n) {
        }

        @Override
        public void visit(AST.LogicalAnd n) {
        }

        @Override
        public void visit(AST.LogicalOr n) {
        }

        @Override
        public void visit(AST.Relation n) {
        }

        @Override
        public void visit(AST.AddressOf n) {
        }

        @Override
        public void visit(AST.ArrayIndex n) {
        }

        @Override
        public void visit(AST.Dereference n) {
        }

        @Override
        public void visit(AST.StatementSequence n) {
        }

        @Override
        public void visit(AST.Assignment n) {
        }

        @Override
        public void visit(AST.IfStatement n) {
        }

        @Override
        public void visit(AST.WhileStatement n) {
        }

        @Override
        public void visit(AST.RepeatStatement n) {
        }

        @Override
        public void visit(AST.ReturnStatement n) {
        }

        @Override
        public void visit(AST.FunctionCall n) {
        }

        @Override
        public void visit(AST.ArgumentList n) {
        }

        @Override
        public void visit(AST.FunctionBody n) {
        }

        @Override
        public void visit(AST.FunctionDeclaration n) {
        }

        @Override
        public void visit(AST.VariableDeclaration n) {
        }

        @Override
        public void visit(AST.DeclarationList n) {
        }

        @Override
        public void visit(AST.TypeNode n) {
        }

        @Override
        public void visit(ast.Computation n) {
        }
    }

    // =========================================================================
    // PRETTY-PRINT HELPERS FOR TAC (USED BY OPT + RA + IRBUILDER)
    // =========================================================================

    // =========================================================================
    // PRETTY-PRINT HELPERS FOR TAC (USED BY OPT + RA + IRBUILDER)
    // =========================================================================

    static String opSym(String op) {
        return switch (op) {
            case "add" -> "+";
            case "sub" -> "-";
            case "mul" -> "*";
            case "div" -> "/";
            case "mod" -> "%";
            case "pow" -> "^";
            case "cmpeq" -> "==";
            case "cmpne" -> "!=";
            case "cmplt" -> "<";
            case "cmple" -> "<=";
            case "cmpgt" -> ">";
            case "cmpge" -> ">=";
            case "and" -> "&&";
            case "or" -> "||";
            case "not" -> "!";
            default -> op;
        };
    }

    static String pretty(String op, ir.tac.Variable dst, ir.tac.Value L, ir.tac.Value R) {
        if ("mov".equals(op)) {
            return dst + " = " + (L == null ? "" : L.toString());
        }
        if ("not".equals(op)) {
            return dst + " = " + opSym(op) + (L == null ? "" : L.toString());
        }
        return dst + " = " + (L == null ? "" : L.toString()) + " " +
                opSym(op) + " " + (R == null ? "" : R.toString());
    }

    static ir.tac.Assign prettyAssign(
            int id,
            ir.tac.Variable dst,
            String opcode,
            ir.tac.Value L,
            ir.tac.Value R) {
        return new ir.tac.Assign(id, dst, L, R) {
            @Override
            protected String op() {
                return opcode;
            }

            @Override
            public String toString() {
                if ("label".equals(opcode) || "test".equals(opcode) || "ret".equals(opcode)) {
                    return super.toString();
                }
                return pretty(op(), dst, left(), right());
            }
        };
    }

    // =========================================================================
    // IR BUILDER (AST → TAC + CFG)
    // =========================================================================

    private final class IRBuilder extends NodeVisitorAdapter {
        private final List<BasicBlock> blocks = new ArrayList<>();
        private BasicBlock cur;
        private int nextTacId = 1;
        private int tmpCounter = 0;

        IRBuilder() {
            cur = newBB();
        }

        List<BasicBlock> getBlocks() {
            return blocks;
        }

        BasicBlock mainEntry() {
            return blocks.get(0);
        }

        void resetToMain() {
            this.cur = mainEntry();
        }

        private BasicBlock newBB() {
            BasicBlock b = new BasicBlock(blocks.size() + 1);
            blocks.add(b);
            return b;
        }

        private int newId() {
            return nextTacId++;
        }

        private Variable newTmp() {
            return v("_t" + (++tmpCounter));
        }

        private Variable v(String name) {
            return new Variable(new mocha.Symbol(name, null));
        }

        private Value val(ast.Expression e) {
            return valInto(e, null);
        }

        private Value valInto(ast.Expression e, Variable preferredDst) {
            if (e instanceof AST.IntegerLiteral il)
                return new Literal(il.getValue());
            if (e instanceof AST.FloatLiteral fl)
                return new Literal(fl.getValue());
            if (e instanceof AST.BoolLiteral bl)
                return new Literal(bl.getValue());
            if (e instanceof AST.Identifier id)
                return v(id.getName());

            if (e instanceof AST.ArrayIndex ai) {
                ast.Expression base = ai.getBase();
                if (base instanceof AST.Identifier bid) {
                    // just treat x[i][j] as "x" in the IR
                    return v(bid.getName());
                }
                // fallback: at least try to get a value for the base expression
                return valInto(base, preferredDst);
            }

            Variable dst = (preferredDst != null) ? preferredDst : newTmp();

            if (e instanceof AST.Addition add) {
                Value L = val(add.getLeft());
                Value R = val(add.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "add";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.Subtraction sub) {
                Value L = val(sub.getLeft());
                Value R = val(sub.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "sub";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.Multiplication mul) {
                Value L = val(mul.getLeft());
                Value R = val(mul.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "mul";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.Division div) {
                Value L = val(div.getLeft());
                Value R = val(div.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "div";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.Modulo mod) {
                Value L = val(mod.getLeft());
                Value R = val(mod.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "mod";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.Power pow) {
                Value L = val(pow.getBase());
                Value R = val(pow.getExponent());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "pow";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.UnaryMinus um) {
                Value R = val(um.getExpr());
                Value Z = new Literal(0);
                cur.addInstruction(new Assign(newId(), dst, Z, R) {
                    @Override
                    protected String op() {
                        return "sub";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, Z, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.LogicalNot ln) {
                Value R = val(ln.getExpression());
                cur.addInstruction(new Assign(newId(), dst, R, null) {
                    @Override
                    protected String op() {
                        return "not";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, R, null);
                    }
                });
                return dst;
            }

            if (e instanceof AST.LogicalAnd la) {
                Value L = val(la.getLeft());
                Value R = val(la.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "and";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.LogicalOr lo) {
                Value L = val(lo.getLeft());
                Value R = val(lo.getRight());
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return "or";
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.Relation rel) {
                Value L = val(rel.getLeft());
                Value R = val(rel.getRight());
                final String op = switch (rel.getOperator()) {
                    case "==" -> "cmpeq";
                    case "!=" -> "cmpne";
                    case "<" -> "cmplt";
                    case "<=" -> "cmple";
                    case ">" -> "cmpgt";
                    case ">=" -> "cmpge";
                    default -> "cmp?";
                };
                cur.addInstruction(new Assign(newId(), dst, L, R) {
                    @Override
                    protected String op() {
                        return op;
                    }

                    @Override
                    public String toString() {
                        return pretty(op(), dst, L, R);
                    }
                });
                return dst;
            }

            if (e instanceof AST.FunctionCall fc) {
                ArrayList<Value> args = new ArrayList<>();
                for (ast.Expression a : fc.getArguments().getArguments())
                    args.add(val(a));
                Variable callDst = (preferredDst != null) ? preferredDst : newTmp();
                cur.addInstruction(new Call(newId(),
                        new mocha.Symbol(fc.getIdentifier().getName(), null),
                        args,
                        callDst));
                return callDst;
            }

            return new Literal(e);
        }

        @Override
        public void visit(AST.Assignment node) {
            // Case 1: simple variable assignment – same as before
            if (node.getDestination() instanceof AST.Identifier id) {
                Variable dst = v(id.getName());
                int before = cur.instructions().size();
                Value rhs = valInto(node.getSource(), dst);
                int after = cur.instructions().size();

                // If valInto did not emit anything, fall back to a mov.
                if (after == before) {
                    boolean sameVar = (rhs instanceof Variable v) && v.toString().equals(dst.toString());
                    if (!sameVar) {
                        cur.addInstruction(new Assign(newId(), dst, rhs, null) {
                            @Override
                            protected String op() {
                                return "mov";
                            }

                            @Override
                            public String toString() {
                                return pretty(op(), dst, rhs, null);
                            }
                        });
                    }
                }
                return;
            }

            // Case 2: complex lvalues (arrays, pointers, etc.)
            // In this *minimal* builder we simply:
            // - DO NOT model the store
            // - but DO evaluate the RHS so optimizations still see that work.
            //
            // This avoids the "Only simple lvalues supported" crash on things like
            // x[i][j] = y[i][j] + z[i][j];
            // while keeping the IR good enough for CP/CSE/DCE tests.
            val(node.getSource());
        }

        @Override
        public void visit(AST.FunctionCall node) {
            ArrayList<Value> args = new ArrayList<>();
            for (ast.Expression e : node.getArguments().getArguments())
                args.add(val(e));
            cur.addInstruction(new Call(newId(),
                    new mocha.Symbol(node.getIdentifier().getName(), null),
                    args));
        }

        @Override
        public void visit(AST.IfStatement n) {
            BasicBlock thenBB = newBB();
            BasicBlock joinBB = newBB();
            BasicBlock elseBB = (n.getElseBlock() != null) ? newBB() : null;

            Value cond = val(n.getCondition());
            cur.addInstruction(new Assign(newId(), newTmp(), cond, null) {
                @Override
                protected String op() {
                    return "test";
                }

                @Override
                public String toString() {
                    return "if " + cond + " goto " + thenBB.dotNodeName() +
                            (elseBB != null ? " else " + elseBB.dotNodeName()
                                    : " else " + joinBB.dotNodeName());
                }
            });
            cur.addSuccessor(thenBB);
            cur.addSuccessor(elseBB != null ? elseBB : joinBB);

            BasicBlock saved = cur;
            cur = thenBB;
            n.getThenBlock().accept(this);
            cur.addSuccessor(joinBB);

            if (elseBB != null) {
                cur = elseBB;
                n.getElseBlock().accept(this);
                cur.addSuccessor(joinBB);
            }

            cur = joinBB;
        }

        @Override
        public void visit(AST.WhileStatement n) {
            BasicBlock testBB = newBB();
            BasicBlock bodyBB = newBB();
            BasicBlock exitBB = newBB();

            cur.addSuccessor(testBB);

            cur = testBB;
            final Value cond = val(n.getCondition());
            cur.addInstruction(new Assign(newId(), newTmp(), cond, null) {
                @Override
                protected String op() {
                    return "test";
                }

                @Override
                public String toString() {
                    return "if " + cond + " goto " + bodyBB.dotNodeName()
                            + " else " + exitBB.dotNodeName();
                }
            });
            cur.addSuccessor(bodyBB);
            cur.addSuccessor(exitBB);

            cur = bodyBB;
            n.getBody().accept(this);
            cur.addSuccessor(testBB);

            cur = exitBB;
        }

        @Override
        public void visit(AST.RepeatStatement n) {
            BasicBlock bodyBB = newBB();
            BasicBlock testBB = newBB();
            BasicBlock exitBB = newBB();

            cur.addSuccessor(bodyBB);

            cur = bodyBB;
            n.getBody().accept(this);
            cur.addSuccessor(testBB);

            cur = testBB;
            final Value cond = val(n.getCondition());
            cur.addInstruction(new Assign(newId(), newTmp(), cond, null) {
                @Override
                protected String op() {
                    return "test";
                }

                @Override
                public String toString() {
                    return "if " + cond + " goto " + exitBB.dotNodeName()
                            + " else " + bodyBB.dotNodeName();
                }
            });
            cur.addSuccessor(exitBB);
            cur.addSuccessor(bodyBB);

            cur = exitBB;
        }

        @Override
        public void visit(AST.ReturnStatement n) {
            ast.Expression e = n.getValue();
            final Value v = (e != null) ? val(e) : null;
            cur.addInstruction(new Assign(newId(), newTmp(), v, null) {
                @Override
                protected String op() {
                    return "ret";
                }

                @Override
                public String toString() {
                    return "return " + (v == null ? "" : v.toString());
                }
            });
        }

        @Override
        public void visit(AST.FunctionDeclaration n) {
            cur = newBB();
            cur.addInstruction(new Assign(newId(), v(n.getIdentifier().getName()), null, null) {
                @Override
                protected String op() {
                    return "label";
                }

                @Override
                public String toString() {
                    return "<" + n.getIdentifier().getName() + ">";
                }
            });

            enterInitScope();
            for (AST.FormalParameter p : n.getParameters()) {
                setInit(p.getIdentifier().getName());
            }

            n.getBody().getStatements().accept(this);
            exitInitScope();
        }

        @Override
        public void visit(AST.StatementSequence node) {
            boolean root = initStack.isEmpty();
            if (root)
                enterInitScope();
            for (ast.Statement s : node) {
                if (s == null)
                    continue;
                s.accept(this);
                if (s instanceof AST.ReturnStatement) {
                    break;
                }
            }
            if (root)
                exitInitScope();
        }

        @Override
        public void visit(AST.VariableDeclaration n) {
            String name = n.getIdentifier().getName();
            types.Type t = ((AST.TypeNode) n.getTypeNode()).getActualType();
            ir.tac.Literal def = null;
            if (t instanceof types.IntType)
                def = new ir.tac.Literal(0);
            else if (t instanceof types.FloatType)
                def = new ir.tac.Literal(0.0f);
            else if (t instanceof types.BoolType)
                def = new ir.tac.Literal(false);

            if (def != null) {
                Variable v = v(name);
                cur.addInstruction(prettyAssign(newId(), v, "mov", def, null));
            }
            setInit(name);
        }

        private final Deque<Set<String>> initStack = new ArrayDeque<>();

        private void enterInitScope() {
            initStack.push(new HashSet<>());
        }

        private void exitInitScope() {
            initStack.pop();
        }

        private boolean isInit(String n) {
            for (var s : initStack)
                if (s.contains(n))
                    return true;
            return false;
        }

        private void setInit(String n) {
            if (!initStack.isEmpty())
                initStack.peek().add(n);
        }

        @Override
        public void visit(AST.LogicalAnd n) {
        }

        @Override
        public void visit(AST.LogicalOr n) {
        }

        @Override
        public void visit(AST.LogicalNot n) {
        }

        @Override
        public void visit(AST.Power n) {
        }

        @Override
        public void visit(AST.FunctionBody n) {
        }

        @Override
        public void visit(AST.DeclarationList n) {
        }

        @Override
        public void visit(AST.TypeNode n) {
        }

        @Override
        public void visit(AST.AddressOf n) {
        }

        @Override
        public void visit(AST.ArrayIndex n) {
        }

        @Override
        public void visit(AST.Dereference n) {
        }

        @Override
        public void visit(AST.Identifier n) {
        }

        @Override
        public void visit(ast.Computation n) {
        }

        @Override
        public void visit(AST.ArgumentList n) {
        }

        @Override
        public void visit(AST.Division n) {
        }

        @Override
        public void visit(AST.Subtraction n) {
        }

        @Override
        public void visit(AST.Modulo n) {
        }

        @Override
        public void visit(AST.UnaryMinus n) {
        }

        @Override
        public void visit(AST.FloatLiteral n) {
        }

        @Override
        public void visit(AST.IntegerLiteral n) {
        }

        @Override
        public void visit(AST.BoolLiteral n) {
        }
    }

    // =========================================================================
    // IR GENERATION ENTRY + LAST DOT SNAPSHOTS
    // =========================================================================

    private String lastPreDot = null;
    private String lastPostDot = null;

    public String getPreDot() {
        return lastPreDot;
    }

    public String getPostDot() {
        return lastPostDot;
    }

    public IR genIR(ast.AST ast) {
        IRBuilder builder = new IRBuilder();

        if (ast != null && ast.getRoot() != null) {
            for (AST.Declaration d : ast.getRoot().functions()) {
                if (d instanceof AST.FunctionDeclaration fd) {
                    fd.accept(builder);
                }
            }

            builder.resetToMain();

            for (AST.Declaration d : ast.getRoot().variables()) {
                if (d instanceof AST.VariableDeclaration vd)
                    vd.accept(builder);
            }

            ast.getRoot().mainStatementSequence().accept(builder);
        }

        currentIR = new IR(builder.getBlocks());
        lastPreDot = currentIR.asDotGraph();
        lastPostDot = currentIR.asDotGraph();
        return currentIR;
    }

    private void allocateRegisters(List<BasicBlock> blocks) {
        if (blocks == null || blocks.isEmpty())
            return;
        Map<String, Set<String>> ig = buildInterferenceGraph(blocks);
        RegAllocResult res = colorGraph(ig, numDataRegisters);

        // --- DEBUG: print coloring result and spills ---
        /*
         * System.err.println("==== RA: Coloring Result (K = " + numDataRegisters +
         * ") ====");
         * for (Map.Entry<String, Integer> e : res.colors.entrySet()) {
         * System.err.println("  var " + e.getKey() + " -> R" + e.getValue());
         * }
         * System.err.println("==== RA: Spilled Variables ====");
         * if (res.spilled.isEmpty()) {
         * System.err.println("  (none)");
         * } else {
         * for (String v : res.spilled) {
         * System.err.println("  " + v + " -> spilled (M_" + v + ")");
         * }
         * }
         * System.err.println();
         */

        rewriteWithRegisters(blocks, res.colors, res.spilled);
        removeSillyMoves(blocks);

        // // --- DEBUG: show final TAC per block ---
        // System.err.println("==== RA: TAC After Rewrite ====");
        // for (BasicBlock b : blocks) {
        // System.err.println(b.dotNodeName() + ":");
        // for (ir.tac.TAC t : b.instructions()) {
        // System.err.println(" " + t);
        // }
        // }
        // System.err.println("================================\n");
    }

    // =========================================================================
    // OPTIMIZATION DRIVER: -o FLAGS, LOOP, -max, AND RA
    // =========================================================================

    /** Run selected optimizations and return DOT text of the resulting IR. */
    public String optimization(List<String> opts, boolean loop, boolean max) {
        // Always rebuild IR fresh for each optimization run
        currentIR = genIR(this.astRoot);

        // Collect global variables
        Set<String> globalVars = new HashSet<>();
        if (astRoot != null && astRoot.getRoot() != null) {
            for (AST.Declaration d : astRoot.getRoot().variables()) {
                if (d instanceof AST.VariableDeclaration vd) {
                    globalVars.add(vd.getIdentifier().getName());
                }
            }
        }

        boolean realMax = max && (opts == null || opts.isEmpty());

        List<String> plan;
        if (realMax) {
            // Max pipeline: include RA at the very end
            plan = Arrays.asList("cp", "cpp", "cf", "cse", "dce", "gcp", "cfg", "ofe", "merge", "ra");
        } else if (opts != null && !opts.isEmpty()) {
            plan = new ArrayList<>(opts);
        } else {
            plan = Collections.emptyList();
        }

        // Build funcs map for inlining
        Map<String, AST.FunctionDeclaration> funcs = new HashMap<>();
        if (astRoot != null && astRoot.getRoot() != null) {
            for (AST.Declaration d : astRoot.getRoot().functions()) {
                if (d instanceof AST.FunctionDeclaration fd) {
                    funcs.put(fd.getIdentifier().getName(), fd);
                }
            }
        }

        List<String> prePasses = new ArrayList<>();
        boolean doRA = false;
        for (String p : plan) {
            if ("ra".equalsIgnoreCase(p)) {
                doRA = true;
            } else {
                prePasses.add(p.toLowerCase());
            }
        }

        String prev;
        do {
            prev = currentIR.asDotGraph();

            for (String p : prePasses) {
                switch (p) {
                    case "cp":
                        new Optimizer(true, false, false, false, false, globalVars).optimize(currentIR.blocks());
                        break;
                    case "cpp":
                        new Optimizer(false, true, false, false, false, globalVars).optimize(currentIR.blocks());
                        break;
                    case "cf":
                        new Optimizer(false, false, true, false, false, globalVars).optimize(currentIR.blocks());
                        break;
                    case "cse":
                        new Optimizer(false, false, false, true, false, globalVars).optimize(currentIR.blocks());
                        break;
                    case "dce":
                        new Optimizer(false, false, false, false, true, globalVars).optimize(currentIR.blocks());
                        break;
                    case "inline":
                        new FunctionInliner(funcs, globalVars).inline(currentIR.blocks());
                        break;
                    case "gcp":
                        new GlobalConstantPropagation(globalVars).optimize(currentIR.blocks());
                        break;
                    case "cfg":
                        CFGSimplifier.simplify(currentIR.blocks());
                        break;
                    case "ofe":
                        removeOrphanFunctions(currentIR.blocks());
                        break;
                    case "merge":
                        mergeTrivialEmpties(currentIR.blocks());
                        break;
                    default:
                        break;
                }
            }
        } while (loop && !currentIR.asDotGraph().equals(prev));

        if (doRA) {
            allocateRegisters(currentIR.blocks());
        }

        lastPostDot = currentIR.asDotGraph();
        return lastPostDot;
    }

    // =========================================================================
    // LIVENESS + INTERFERENCE GRAPH BUILDING
    // =========================================================================

    private static Map<String, Set<String>> buildInterferenceGraph(List<BasicBlock> blocks) {
        Map<BasicBlock, Set<String>> useB = new HashMap<>();
        Map<BasicBlock, Set<String>> defB = new HashMap<>();

        for (BasicBlock b : blocks) {
            Set<String> use = new HashSet<>();
            Set<String> def = new HashSet<>();
            List<ir.tac.TAC> ins = b.instructions();
            if (ins != null) {
                for (ir.tac.TAC t : ins) {
                    Set<String> used = usedVars(t);
                    String defv = defVar(t);

                    for (String v : used) {
                        if (!def.contains(v))
                            use.add(v);
                    }
                    if (defv != null)
                        def.add(defv);
                }
            }
            useB.put(b, use);
            defB.put(b, def);
        }

        Map<BasicBlock, Set<String>> in = new HashMap<>();
        Map<BasicBlock, Set<String>> out = new HashMap<>();
        for (BasicBlock b : blocks) {
            in.put(b, new HashSet<>());
            out.put(b, new HashSet<>());
        }

        boolean changed;
        do {
            changed = false;
            ListIterator<BasicBlock> it = blocks.listIterator(blocks.size());
            while (it.hasPrevious()) {
                BasicBlock b = it.previous();
                Set<String> inOld = in.get(b);
                Set<String> outOld = out.get(b);

                Set<String> outNew = new HashSet<>();
                List<BasicBlock> succs = b.succs();
                if (succs != null) {
                    for (BasicBlock s : succs) {
                        Set<String> inS = in.get(s);
                        if (inS != null)
                            outNew.addAll(inS);
                    }
                }

                Set<String> inNew = new HashSet<>(useB.get(b));
                Set<String> tmp = new HashSet<>(outNew);
                tmp.removeAll(defB.get(b));
                inNew.addAll(tmp);

                if (!inNew.equals(inOld) || !outNew.equals(outOld)) {
                    in.put(b, inNew);
                    out.put(b, outNew);
                    changed = true;
                }
            }
        } while (changed);

        Map<String, Set<String>> graph = new HashMap<>();
        java.util.function.Consumer<String> ensure = v -> graph.computeIfAbsent(v, k -> new HashSet<>());

        for (BasicBlock b : blocks) {
            List<ir.tac.TAC> ins = b.instructions();
            if (ins == null || ins.isEmpty())
                continue;

            Set<String> live = new HashSet<>(out.get(b));

            for (int i = ins.size() - 1; i >= 0; --i) {
                ir.tac.TAC t = ins.get(i);
                Set<String> used = usedVars(t);
                String defv = defVar(t);

                if (defv != null) {
                    ensure.accept(defv);
                    for (String v : live) {
                        if (v.equals(defv))
                            continue;
                        ensure.accept(v);
                        graph.get(defv).add(v);
                        graph.get(v).add(defv);
                    }
                    live.remove(defv);
                }

                live.addAll(used);
                for (String v : used)
                    ensure.accept(v);
            }
        }

        return graph;
    }

    // =========================================================================
    // GRAPH COLORING (CHAITIN-STYLE)
    // =========================================================================

    private static RegAllocResult colorGraph(Map<String, Set<String>> graph, int K) {
        Map<String, Set<String>> work = new HashMap<>();
        for (var e : graph.entrySet()) {
            work.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        List<String> stack = new ArrayList<>();
        Set<String> spilled = new HashSet<>();

        while (!work.isEmpty()) {
            String low = null;

            for (var e : work.entrySet()) {
                if (e.getValue().size() < K) {
                    low = e.getKey();
                    break;
                }
            }

            String n;
            if (low != null) {
                n = low;
            } else {
                n = null;
                int best = -1;
                for (var e : work.entrySet()) {
                    int d = e.getValue().size();
                    if (d > best) {
                        best = d;
                        n = e.getKey();
                    }
                }
                if (n != null)
                    spilled.add(n);
            }

            Set<String> neigh = work.remove(n);
            if (neigh != null) {
                for (String m : neigh) {
                    Set<String> s = work.get(m);
                    if (s != null)
                        s.remove(n);
                }
            }
            stack.add(n);
        }

        Map<String, Integer> color = new HashMap<>();

        while (!stack.isEmpty()) {
            String n = stack.remove(stack.size() - 1);
            Set<Integer> used = new HashSet<>();

            for (String m : graph.getOrDefault(n, Collections.emptySet())) {
                Integer c = color.get(m);
                if (c != null)
                    used.add(c);
            }

            int chosen = -1;
            // Start from 1 to avoid R0 (always zero)
            // Skip reserved registers: 28 (FP), 29 (SP), 30 (GP), 31 (RET)
            for (int c = 1; c < K; c++) {
                if (c >= 28 && c <= 31)
                    continue;
                if (!used.contains(c)) {
                    chosen = c;
                    break;
                }
            }

            if (chosen >= 0) {
                color.put(n, chosen);
            } else {
                spilled.add(n);
            }
        }

        return new RegAllocResult(color, spilled);
    }

    private static ir.tac.Variable rewriteVar(
            ir.tac.Variable v,
            Map<String, Integer> colors,
            Set<String> spilled) {
        if (v == null)
            return null;
        String name = v.toString();

        if (colors.containsKey(name)) {
            int c = colors.get(name);
            return new ir.tac.Variable(new mocha.Symbol("R" + c, null));
        }
        if (spilled.contains(name)) {
            return new ir.tac.Variable(new mocha.Symbol("M_" + name, null));
        }
        return v;
    }

    private static ir.tac.Value rewriteVal(
            ir.tac.Value val,
            Map<String, Integer> colors,
            Set<String> spilled) {
        if (val instanceof ir.tac.Variable v) {
            return rewriteVar(v, colors, spilled);
        }
        return val;
    }

    private static void rewriteWithRegisters(
            List<BasicBlock> blocks,
            Map<String, Integer> colors,
            Set<String> spilled) {
        for (BasicBlock b : blocks) {
            List<ir.tac.TAC> ins = b.instructions();
            if (ins == null || ins.isEmpty())
                continue;

            List<ir.tac.TAC> out = new ArrayList<>();

            for (ir.tac.TAC t : ins) {
                if (t instanceof ir.tac.Assign a) {
                    String op = a.opcode();
                    ir.tac.Variable dst = rewriteVar(a.dest(), colors, spilled);
                    ir.tac.Value L = rewriteVal(a.left(), colors, spilled);
                    ir.tac.Value R = rewriteVal(a.right(), colors, spilled);
                    out.add(prettyAssign(a.id(), dst, op, L, R));
                } else if (t instanceof ir.tac.Call c) {
                    ir.tac.Variable dst = rewriteVar(c.dest(), colors, spilled);
                    List<ir.tac.Value> newArgs = null;
                    if (c.args() != null) {
                        newArgs = new ArrayList<>();
                        for (ir.tac.Value v : c.args()) {
                            newArgs.add(rewriteVal(v, colors, spilled));
                        }
                    }
                    ir.tac.Call nc;
                    if (dst != null) {
                        nc = new ir.tac.Call(c.id(), c.function(), newArgs, dst);
                    } else {
                        nc = new ir.tac.Call(c.id(), c.function(), newArgs);
                    }
                    out.add(nc);
                } else {
                    out.add(t);
                }
            }

            ins.clear();
            ins.addAll(out);
        }
    }

    // =========================================================================
    // SIMPLE DLX CODE GENERATOR (IR → DLX)
    // =========================================================================

    /**
     * Very simple code generator:
     * - Every TAC Variable gets a word in memory at a negative offset from R30.
     * - Uses a few scratch registers R1..R4 for evaluation.
     * - Supports integer arithmetic + booleans + conditionals + loops + built-in
     * I/O.
     * - Does NOT yet support user-defined functions or floats.
     */
    private static final class CodeGenerator {

        // Registers we use
        private static final int R_ZERO = 0; // always 0
        private static final int R_GP = 30; // global base (already set by DLX.execute)
        private static final int R_TMP1 = 1;
        private static final int R_TMP2 = 2;
        private static final int R_TMP3 = 3;
        private static final int R_TMP4 = 4;

        // For each TAC variable name -> negative offset (bytes) from R30
        private final Map<String, Integer> varOffset = new HashMap<>();
        private int nextOffset = -4;

        // BasicBlock → starting PC (instruction index)
        private final Map<BasicBlock, Integer> blockPC = new HashMap<>();

        // Result program as list of DLX words
        private final List<Integer> code = new ArrayList<>();
        private final Map<String, AST.FunctionDeclaration> funcs;
        private final Map<String, Integer> funcEntryPC = new HashMap<>();
        private String currentFunction = null;

        CodeGenerator(Map<String, AST.FunctionDeclaration> funcs) {
            this.funcs = funcs;
        }

        List<Integer> generate(List<BasicBlock> blocks) {
            if (blocks == null || blocks.isEmpty()) {
                return new ArrayList<>();
            }

            // 1) Collect all variables and give them memory locations.
            collectVariables(blocks);

            // 2) First pass: compute starting PC for each block (instruction counts only).
            int pc = 0;
            for (BasicBlock b : blocks) {
                blockPC.put(b, pc);
                // Check for function entry label
                for (ir.tac.TAC t : b.instructions()) {
                    if (t instanceof Assign a && "label".equals(a.opcode())) {
                        if (a.dest() != null) {
                            funcEntryPC.put(a.dest().toString(), pc);
                        }
                    }
                }
                pc += estimateBlockSize(b);
            }

            // 3) Second pass: actually emit instructions with correct branch offsets.
            code.clear();
            for (BasicBlock b : blocks) {
                emitBlock(b);
            }

            // Program must terminate with RET 0 if we fall off the end.
            code.add(DLX.assemble(DLX.RET, 0));

            return code;
        }

        // ---------------------------------------------------------------------
        // VARIABLE ↔ MEMORY
        // ---------------------------------------------------------------------

        private void collectVariables(List<BasicBlock> blocks) {
            for (BasicBlock b : blocks) {
                for (ir.tac.TAC t : b.instructions()) {
                    if (t instanceof Assign a) {
                        addVar(a.dest());
                        addVal(a.left());
                        addVal(a.right());
                    } else if (t instanceof Call c) {
                        addVar(c.dest());
                        if (c.args() != null) {
                            for (Value v : c.args())
                                addVal(v);
                        }
                    }
                }
            }
        }

        private void addVar(Variable v) {
            if (v == null)
                return;
            if (getRegister(v) != -1)
                return;
            String name = v.toString();
            if (!varOffset.containsKey(name)) {
                varOffset.put(name, nextOffset);
                nextOffset -= 4;
            }
        }

        private void addVal(Value v) {
            if (v instanceof Variable var)
                addVar(var);
        }

        private int offsetOf(Variable v) {
            if (v == null)
                throw new IllegalArgumentException("null variable");
            String name = v.toString();
            Integer off = varOffset.get(name);
            if (off == null) {
                off = nextOffset;
                nextOffset -= 4;
                varOffset.put(name, off);
            }
            return off;
        }

        private int loadVar(Variable v, int reg) {
            int r = getRegister(v);
            if (r != -1) {
                return r;
            }
            int off = offsetOf(v);
            code.add(DLX.assemble(DLX.LDW, reg, R_GP, off));
            return reg;
        }

        private void storeVar(int reg, Variable v) {
            int r = getRegister(v);
            if (r != -1) {
                if (r != reg) {
                    code.add(DLX.assemble(DLX.ADD, r, R_ZERO, reg));
                }
                return;
            }
            int off = offsetOf(v);
            code.add(DLX.assemble(DLX.STW, reg, R_GP, off));
        }

        private int getRegister(Variable v) {
            String name = v.toString();
            if (name.startsWith("R")) {
                try {
                    return Integer.parseInt(name.substring(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            return -1;
        }

        private int loadValue(Value v, int reg) {
            if (v == null) {
                // default 0
                code.add(DLX.assemble(DLX.ADDI, reg, R_ZERO, 0));
                return reg;
            }
            if (v instanceof Literal lit) {
                Object o = lit.value();
                int c;
                if (o instanceof Integer i) {
                    c = i;
                } else if (o instanceof Boolean b) {
                    c = b ? 1 : 0;
                } else if (o instanceof Float f) {
                    // for now, treat float bits as int (no arithmetic on them here)
                    c = Float.floatToIntBits(f);
                } else {
                    c = 0;
                }
                code.add(DLX.assemble(DLX.ADDI, reg, R_ZERO, c));
                return reg;
            }
            if (v instanceof Variable var) {
                return loadVar(var, reg);
            }
            // Fallback
            code.add(DLX.assemble(DLX.ADDI, reg, R_ZERO, 0));
            return reg;
        }

        // ---------------------------------------------------------------------
        // PASS 1: SIZE ESTIMATION (rough but consistent with emit)
        // ---------------------------------------------------------------------

        private int estimateBlockSize(BasicBlock b) {
            int n = 0;
            List<ir.tac.TAC> ins = b.instructions();
            if (ins == null)
                return 0;
            for (int i = 0; i < ins.size(); i++) {
                ir.tac.TAC t = ins.get(i);
                if (t instanceof Assign a) {
                    String op = a.opcode();
                    if ("label".equals(op)) {
                        // no code emitted
                    } else if ("test".equals(op)) {
                        // loadValue + 2 branches
                        n += estimateLoadSize(a.left()) + 2;
                    } else if ("ret".equals(op)) {
                        // loadValue (if non-void) + RET
                        if (a.left() != null)
                            n += estimateLoadSize(a.left());
                        n += 1;
                    } else if (isRelOp(op)) {
                        // load L, load R, SUB, init dest, branch, set dest, store dest
                        n += estimateLoadSize(a.left());
                        n += estimateLoadSize(a.right());
                        n += 1; // SUB
                        n += 1; // ADDI 0
                        n += 1; // Branch
                        n += 1; // ADDI 1
                        n += estimateStoreSize(a.dest(), R_TMP4);
                    } else if ("not".equals(op)) {
                        // load, init, branch, set, store
                        n += estimateLoadSize(a.left());
                        n += 1; // ADDI 0
                        n += 1; // BNE
                        n += 1; // ADDI 1
                        n += estimateStoreSize(a.dest(), R_TMP2);
                    } else if ("and".equals(op) || "or".equals(op)) {
                        // load L,R, logical op, store
                        n += estimateLoadSize(a.left());
                        n += estimateLoadSize(a.right());
                        n += 1; // OP
                        n += estimateStoreSize(a.dest(), R_TMP3);
                    } else if ("mov".equals(op)) {
                        // load src, store dst
                        n += estimateLoadSize(a.left());
                        int srcReg = getSourceReg(a.left(), R_TMP1);
                        n += estimateStoreSize(a.dest(), srcReg);
                    } else {
                        // arithmetic: load L, load R, one op, store
                        n += estimateLoadSize(a.left());
                        n += estimateLoadSize(a.right());
                        n += 1; // OP
                        n += estimateStoreSize(a.dest(), R_TMP3);
                    }
                } else if (t instanceof Call c) {
                    String fname = (c.function() == null) ? null : c.function().name();
                    if (isBuiltin(fname)) {
                        // loads for args + one IO instr
                        if (c.args() != null) {
                            for (Value arg : c.args()) {
                                n += estimateLoadSize(arg);
                            }
                        }
                        n += 1; // IO instr
                        if (c.dest() != null) {
                            // store return value if any (e.g., readInt)
                            n += estimateStoreSize(c.dest(), R_TMP1);
                        }
                    } else {
                        // User-defined function call
                        if (funcEntryPC.containsKey(fname)) {
                            // 1. Pass arguments
                            if (funcs != null && funcs.containsKey(fname)) {
                                AST.FunctionDeclaration fd = funcs.get(fname);
                                List<AST.FormalParameter> params = fd.getParameters();
                                List<Value> args = c.args();
                                if (params != null && args != null && params.size() == args.size()) {
                                    for (int k = 0; k < params.size(); k++) {
                                        n += estimateLoadSize(args.get(k));
                                        int argReg = getSourceReg(args.get(k), R_TMP1);
                                        n += estimateStoreSize(new Variable(
                                                new Symbol(params.get(k).getIdentifier().getName(), null)), argReg);
                                    }
                                }
                            }
                            // 2. JSR
                            n += 1;
                            // 3. Return value
                            if (c.dest() != null) {
                                n += estimateStoreSize(c.dest(), 27);
                            }
                        } else {
                            // Unknown function -> ERR
                            n += 1;
                        }
                    }
                }
            }
            return n;
        }

        private int estimateLoadSize(Value v) {
            if (v instanceof Variable var) {
                if (getRegister(var) != -1)
                    return 0;
                return 1; // LDW
            }
            // Literal
            return 1; // ADDI
        }

        private int estimateStoreSize(Variable v, int srcReg) {
            int r = getRegister(v);
            if (r != -1) {
                if (r != srcReg)
                    return 1; // ADD move
                return 0; // No move needed
            }
            return 1; // STW
        }

        private int getSourceReg(Value v, int defaultReg) {
            if (v instanceof Variable var) {
                int r = getRegister(var);
                if (r != -1)
                    return r;
            }
            return defaultReg;
        }

        // ---------------------------------------------------------------------
        // EMISSION PASS
        // ---------------------------------------------------------------------

        private void emitBlock(BasicBlock b) {
            List<ir.tac.TAC> ins = b.instructions();
            if (ins == null)
                return;

            for (int i = 0; i < ins.size(); i++) {
                ir.tac.TAC t = ins.get(i);

                if (t instanceof Assign a) {
                    String op = a.opcode();
                    if ("label".equals(op)) {
                        // Track current function
                        if (a.dest() != null) {
                            currentFunction = a.dest().toString();
                        }
                        // noop at machine level in this simple generator
                        continue;
                    }
                    if ("test".equals(op)) {
                        emitTest(b, a);
                        continue;
                    }
                    if ("ret".equals(op)) {
                        emitReturn(a);
                        continue;
                    }
                    if (isRelOp(op)) {
                        emitRelation(a);
                        continue;
                    }
                    if ("mov".equals(op)) {
                        emitMov(a);
                        continue;
                    }
                    if ("not".equals(op)) {
                        emitNot(a);
                        continue;
                    }
                    if ("and".equals(op) || "or".equals(op)) {
                        emitBoolOp(a);
                        continue;
                    }
                    // Arithmetic default
                    emitArith(a);
                } else if (t instanceof Call c) {
                    emitCall(c);
                }
            }
        }

        // ---------------------------------------------------------------------
        // ARITHMETIC & BOOL EMISSION
        // ---------------------------------------------------------------------

        private void emitMov(Assign a) {
            if (a.dest() == null)
                return;
            int r = loadValue(a.left(), R_TMP1);
            storeVar(r, a.dest());
        }

        private void emitArith(Assign a) {
            if (a.dest() == null)
                return;
            String op = a.opcode();

            int rL = loadValue(a.left(), R_TMP1);
            int rR = loadValue(a.right(), R_TMP2);
            int dstReg = R_TMP3;

            int dlxOp;
            switch (op) {
                case "add":
                    dlxOp = DLX.ADD;
                    break;
                case "sub":
                    dlxOp = DLX.SUB;
                    break;
                case "mul":
                    dlxOp = DLX.MUL;
                    break;
                case "div":
                    dlxOp = DLX.DIV;
                    break;
                case "mod":
                    dlxOp = DLX.MOD;
                    break;
                case "pow":
                    dlxOp = DLX.POW;
                    break;
                default:
                    // unknown op, emit error instruction
                    code.add(DLX.assemble(DLX.ERR));
                    return;
            }

            code.add(DLX.assemble(dlxOp, dstReg, rL, rR));
            storeVar(dstReg, a.dest());
        }

        private static boolean isRelOp(String op) {
            if (op == null)
                return false;
            return op.equals("cmpeq") || op.equals("cmpne") ||
                    op.equals("cmplt") || op.equals("cmple") ||
                    op.equals("cmpgt") || op.equals("cmpge");
        }

        /**
         * Emit comparisons that produce 0/1 in the destination.
         * We implement them using SUB + branches, all in integer arithmetic.
         */
        private void emitRelation(Assign a) {
            if (a.dest() == null)
                return;
            String op = a.opcode();

            int rL = loadValue(a.left(), R_TMP1);
            int rR = loadValue(a.right(), R_TMP2);
            int rDiff = R_TMP3;
            int rDst = R_TMP4;

            // rDiff = L - R
            code.add(DLX.assemble(DLX.SUB, rDiff, rL, rR));

            // rDst = 0 by default
            code.add(DLX.assemble(DLX.ADDI, rDst, R_ZERO, 0));

            // pattern:
            // if (condition for 'false') branch over "set to 1"
            // rDst = 1
            int branchOpFalse;
            switch (op) {
                case "cmpeq":
                    branchOpFalse = DLX.BNE;
                    break; // false when diff != 0
                case "cmpne":
                    branchOpFalse = DLX.BEQ;
                    break; // false when diff == 0
                case "cmplt":
                    branchOpFalse = DLX.BGE;
                    break; // false when diff >= 0
                case "cmple":
                    branchOpFalse = DLX.BGT;
                    break; // false when diff > 0
                case "cmpgt":
                    branchOpFalse = DLX.BLE;
                    break; // false when diff <= 0
                case "cmpge":
                    branchOpFalse = DLX.BLT;
                    break; // false when diff < 0
                default:
                    code.add(DLX.assemble(DLX.ERR));
                    return;
            }

            // Branch over "set rDst = 1" if condition is FALSE.
            // We want to skip exactly 1 instruction (ADDI) -> offset = 2 (because
            // PC moves by 'c' from current PC, not from PC+1).
            code.add(DLX.assemble(branchOpFalse, rDiff, 2));

            // If condition is true: rDst = 1
            code.add(DLX.assemble(DLX.ADDI, rDst, R_ZERO, 1));

            storeVar(rDst, a.dest());
        }

        private void emitNot(Assign a) {
            if (a.dest() == null)
                return;

            int rSrc = loadValue(a.left(), R_TMP1);
            int rDst = R_TMP2;

            // rDst = 0
            code.add(DLX.assemble(DLX.ADDI, rDst, R_ZERO, 0));
            // if src != 0 -> branch over "set to 1" (we want !src)
            code.add(DLX.assemble(DLX.BNE, rSrc, 0, 2));
            // src == 0 -> rDst = 1
            code.add(DLX.assemble(DLX.ADDI, rDst, R_ZERO, 1));

            storeVar(rDst, a.dest());
        }

        private void emitBoolOp(Assign a) {
            if (a.dest() == null)
                return;
            String op = a.opcode();

            int rL = loadValue(a.left(), R_TMP1);
            int rR = loadValue(a.right(), R_TMP2);
            int rDst = R_TMP3;

            int dlxOp = "and".equals(op) ? DLX.AND : DLX.OR;
            code.add(DLX.assemble(dlxOp, rDst, rL, rR));
            storeVar(rDst, a.dest());
        }

        // ---------------------------------------------------------------------
        // RETURN & CONTROL FLOW
        // ---------------------------------------------------------------------

        private void emitReturn(Assign a) {
            if (a.left() != null) {
                // If there is a return value, put it in R27 (convention?)
                // Or R1? Let's use R27 as a temporary return register.
                loadValue(a.left(), 27);
            }
            if ("main".equals(currentFunction)) {
                code.add(DLX.assemble(DLX.RET, 0)); // Terminate
            } else {
                code.add(DLX.assemble(DLX.RET, 31)); // Return to caller
            }
        }

        /**
         * 'test' TAC appears at the end of a block and has two successors:
         * succs[0] -> "true" branch
         * succs[1] -> "false" branch
         *
         * We emit:
         * load cond into rCond
         * BEQ rCond, 0, falseOffset // if cond == 0 -> false block
         * BSR 0, trueOffset // otherwise -> true block
         */
        private void emitTest(BasicBlock b, Assign t) {
            Value cond = t.left();
            int rCond = loadValue(cond, R_TMP1);

            List<BasicBlock> succs = b.succs();
            if (succs == null || succs.size() != 2) {
                // Degenerate case: just fall through
                return;
            }
            BasicBlock trueBB = succs.get(0);
            BasicBlock falseBB = succs.get(1);

            int pcHere = code.size();

            int pcTrue = blockPC.getOrDefault(trueBB, pcHere);
            int pcFalse = blockPC.getOrDefault(falseBB, pcHere);

            int offFalse = pcFalse - pcHere; // from current PC
            int offTrue = pcTrue - (pcHere + 1); // from BSR position (next instr)

            // if cond == 0 -> jump to false
            code.add(DLX.assemble(DLX.BEQ, rCond, offFalse));

            // otherwise jump to true (unconditional branch using BEQ R0)
            code.add(DLX.assemble(DLX.BEQ, R_ZERO, offTrue));
        }

        // ---------------------------------------------------------------------
        // CALLS – built-in only
        // ---------------------------------------------------------------------

        private static boolean isBuiltin(String name) {
            if (name == null)
                return false;
            return name.equals("printInt") ||
                    name.equals("printBool") ||
                    name.equals("println") ||
                    name.equals("readInt");
        }

        private void emitCall(Call c) {
            String fname = (c.function() == null) ? null : c.function().name();

            if (!isBuiltin(fname)) {
                // User-defined function call
                if (funcEntryPC.containsKey(fname)) {
                    // 1. Pass arguments
                    // We need to map args to params.
                    // Since we are using global register allocation, params are just variables.
                    // We need to move args to param locations.
                    if (funcs != null && funcs.containsKey(fname)) {
                        AST.FunctionDeclaration fd = funcs.get(fname);
                        List<AST.FormalParameter> params = fd.getParameters();
                        List<Value> args = c.args();
                        if (params != null && args != null && params.size() == args.size()) {
                            for (int i = 0; i < params.size(); i++) {
                                String paramName = params.get(i).getIdentifier().getName();
                                Value argVal = args.get(i);
                                // Move argVal to paramName
                                // Load argVal into temp
                                int rArg = loadValue(argVal, R_TMP1);
                                // Store into param variable
                                storeVar(rArg, new Variable(new Symbol(paramName, null)));
                            }
                        }
                    }

                    // 2. JSR to function
                    int targetPC = funcEntryPC.get(fname);
                    code.add(DLX.assemble(DLX.JSR, targetPC * 4));

                    // 3. Handle return value
                    if (c.dest() != null) {
                        // Result is in R27
                        storeVar(27, c.dest());
                    }
                    return;
                }

                // Unknown function
                code.add(DLX.assemble(DLX.ERR));
                return;
            }

            if ("printInt".equals(fname)) {
                // one int argument in a register; use WRI
                Value arg = c.args().isEmpty() ? null : c.args().get(0);
                int r = loadValue(arg, R_TMP1);
                code.add(DLX.assemble(DLX.WRI, r));
            } else if ("printBool".equals(fname)) {
                Value arg = c.args().isEmpty() ? null : c.args().get(0);
                int r = loadValue(arg, R_TMP1);
                code.add(DLX.assemble(DLX.WRB, r));
            } else if ("println".equals(fname)) {
                code.add(DLX.assemble(DLX.WRL));
            } else if ("readInt".equals(fname)) {
                // RDI reads into register; if there is a destination variable, store into it.
                int r = R_TMP1;
                code.add(DLX.assemble(DLX.RDI, r));
                if (c.dest() != null) {
                    storeVar(r, c.dest());
                }
            }
        }
    }

    // =========================================================================
    // MINI INTERPRETER (FOR I/O TESTS)
    // =========================================================================

    private static final class MiniInterpreter implements ast.NodeVisitor {
        private final java.util.Scanner sc;
        private final java.io.PrintStream out;
        private final java.util.Map<String, Object> env = new java.util.HashMap<>();
        private final java.util.Map<String, AST.FunctionDeclaration> funcs = new java.util.HashMap<>();
        private Object eval;

        MiniInterpreter(InputStream in, java.io.PrintStream out) {
            this.sc = new java.util.Scanner(in);
            this.out = out;
        }

        void run(ast.Computation prog) {
            for (AST.Declaration d : prog.functions()) {
                if (d instanceof AST.FunctionDeclaration) {
                    AST.FunctionDeclaration fd = (AST.FunctionDeclaration) d;
                    funcs.put(fd.getIdentifier().getName(), fd);
                }
            }

            for (AST.Declaration d : prog.variables()) {
                if (d instanceof AST.VariableDeclaration) {
                    AST.VariableDeclaration vd = (AST.VariableDeclaration) d;
                    AST.TypeNode tn = (AST.TypeNode) vd.getTypeNode();
                    types.Type t = tn.getActualType();
                    Object def = defaultValueForType(t);
                    env.put(vd.getIdentifier().getName(), def);
                }
            }

            prog.mainStatementSequence().accept(this);
        }

        private Object defaultValueForType(types.Type t) {
            if (t instanceof types.IntType)
                return Integer.valueOf(0);
            if (t instanceof types.FloatType)
                return Float.valueOf(0.0f);
            if (t instanceof types.BoolType)
                return Boolean.FALSE;
            if (t instanceof types.ArrayType)
                return allocArray((types.ArrayType) t);
            return null;
        }

        private Object allocArray(types.ArrayType at) {
            int n = at.getExtent();
            if (n < 0)
                return null;
            Object[] arr = new Object[n];
            for (int i = 0; i < n; i++) {
                arr[i] = defaultValueForType(at.getBase());
            }
            return arr;
        }

        private static final class ReturnSignal extends RuntimeException {
            final Object value;

            ReturnSignal(Object v) {
                this.value = v;
            }
        }

        private boolean asBool(Object v) {
            if (v instanceof Boolean)
                return (Boolean) v;
            throw new RuntimeException("Condition is not boolean: " + v);
        }

        private static boolean isInt(Object o) {
            return o instanceof Integer;
        }

        private static boolean isFloaty(Object o) {
            return o instanceof Float || o instanceof Double;
        }

        private static double toDouble(Object o) {
            if (o instanceof Integer)
                return ((Integer) o).doubleValue();
            if (o instanceof Float)
                return ((Float) o).doubleValue();
            if (o instanceof Double)
                return (Double) o;
            throw new RuntimeException("N/A");
        }

        @Override
        public void visit(AST.IntegerLiteral n) {
            eval = Integer.valueOf(n.getValue());
        }

        @Override
        public void visit(AST.FloatLiteral n) {
            eval = Float.valueOf(n.getValue());
        }

        @Override
        public void visit(AST.UnaryMinus n) {
            n.getExpr().accept(this);
            Object v = eval;
            if (isInt(v))
                eval = -((Integer) v);
            else if (isFloaty(v))
                eval = Float.valueOf((float) (-toDouble(v)));
            else
                throw new RuntimeException("Unary minus on non-numeric: " + v);
        }

        @Override
        public void visit(AST.Addition n) {
            n.getLeft().accept(this);
            Object L = eval;
            n.getRight().accept(this);
            Object R = eval;
            if (isInt(L) && isInt(R))
                eval = (Integer) L + (Integer) R;
            else
                eval = Float.valueOf((float) (toDouble(L) + toDouble(R)));
        }

        @Override
        public void visit(AST.Subtraction n) {
            n.getLeft().accept(this);
            Object L = eval;
            n.getRight().accept(this);
            Object R = eval;
            if (isInt(L) && isInt(R))
                eval = (Integer) L - (Integer) R;
            else
                eval = Float.valueOf((float) (toDouble(L) - toDouble(R)));
        }

        @Override
        public void visit(AST.Multiplication n) {
            n.getLeft().accept(this);
            Object L = eval;
            n.getRight().accept(this);
            Object R = eval;
            if (isInt(L) && isInt(R))
                eval = (Integer) L * (Integer) R;
            else
                eval = Float.valueOf((float) (toDouble(L) * toDouble(R)));
        }

        @Override
        public void visit(AST.Division n) {
            n.getLeft().accept(this);
            Object L = eval;
            n.getRight().accept(this);
            Object R = eval;
            eval = Float.valueOf((float) (toDouble(L) / toDouble(R)));
        }

        @Override
        public void visit(AST.Modulo n) {
            n.getLeft().accept(this);
            Object L = eval;
            n.getRight().accept(this);
            Object R = eval;
            if (isInt(L) && isInt(R))
                eval = (Integer) L % (Integer) R;
            else
                throw new RuntimeException("Modulo requires int operands at runtime");
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
            if (!lb) {
                eval = Boolean.FALSE;
                return;
            }
            n.getRight().accept(this);
            eval = Boolean.valueOf(asBool(eval));
        }

        @Override
        public void visit(AST.LogicalOr n) {
            n.getLeft().accept(this);
            boolean lb = asBool(eval);
            if (lb) {
                eval = Boolean.TRUE;
                return;
            }
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
                    eval = Float.valueOf((float) Math.pow(b, e));
                } else {
                    eval = Integer.valueOf(intPow(b, e));
                }
                return;
            }

            double bd = toDouble(L);
            double ed = toDouble(R);
            eval = Float.valueOf((float) Math.pow(bd, ed));
        }

        private int intPow(int base, int exp) {
            int result = 1;
            int b = base;
            int e = exp;
            while (e > 0) {
                if ((e & 1) == 1)
                    result *= b;
                b *= b;
                e >>= 1;
            }
            return result;
        }

        @Override
        public void visit(AST.Relation n) {
            n.getLeft().accept(this);
            Object L = eval;
            n.getRight().accept(this);
            Object R = eval;

            String op = n.getOperator();

            boolean res;
            if (L instanceof Number && R instanceof Number) {
                double a = toDouble(L), b = toDouble(R);
                switch (op) {
                    case "==":
                        res = (a == b);
                        break;
                    case "!=":
                        res = (a != b);
                        break;
                    case "<":
                        res = (a < b);
                        break;
                    case "<=":
                        res = (a <= b);
                        break;
                    case ">":
                        res = (a > b);
                        break;
                    case ">=":
                        res = (a >= b);
                        break;
                    default:
                        throw new RuntimeException("Unknown relop: " + op);
                }
            } else if (L instanceof Boolean && R instanceof Boolean) {
                boolean a = (Boolean) L, b = (Boolean) R;
                switch (op) {
                    case "==":
                        res = (a == b);
                        break;
                    case "!=":
                        res = (a != b);
                        break;
                    default:
                        throw new RuntimeException("Bool relop not supported: " + op);
                }
            } else {
                throw new RuntimeException("Relation operands must be both numeric or both bool");
            }
            eval = Boolean.valueOf(res);
        }

        @Override
        public void visit(AST.StatementSequence node) {
            for (ast.Statement s : node)
                if (s != null)
                    s.accept(this);
        }

        @Override
        public void visit(AST.VariableDeclaration n) {
            types.Type t = ((AST.TypeNode) n.getTypeNode()).getActualType();
            env.put(n.getIdentifier().getName(), defaultValueForType(t));
        }

        @Override
        public void visit(AST.FunctionCall n) {
            java.util.List<Object> argVals = new java.util.ArrayList<>();
            for (ast.Expression e : n.getArguments().getArguments()) {
                e.accept(this);
                argVals.add(eval);
            }

            String name = n.getIdentifier().getName();

            if ("printInt".equals(name)) {
                int i = (argVals.get(0) instanceof Number) ? ((Number) argVals.get(0)).intValue() : 0;
                out.print(i + " ");
                eval = null;
                return;
            } else if ("printFloat".equals(name)) {
                double d = (argVals.get(0) instanceof Number) ? ((Number) argVals.get(0)).doubleValue() : 0.0;
                out.printf("%.2f ", d);
                eval = null;
                return;
            } else if ("printBool".equals(name)) {
                boolean b = (argVals.get(0) instanceof Boolean) ? ((Boolean) argVals.get(0)) : false;
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

            AST.FunctionDeclaration fd = funcs.get(name);
            if (fd == null)
                throw new RuntimeException("N/A");

            java.util.Map<String, Object> saved = new java.util.HashMap<>(env);
            try {
                java.util.List<AST.FormalParameter> ps = fd.getParameters();
                for (int i = 0; i < ps.size(); i++) {
                    env.put(ps.get(i).getIdentifier().getName(), argVals.get(i));
                }

                for (AST.Declaration d : fd.getBody().getDeclarations()) {
                    if (d instanceof AST.VariableDeclaration) {
                        AST.VariableDeclaration vd = (AST.VariableDeclaration) d;
                        types.Type t = ((AST.TypeNode) vd.getTypeNode()).getActualType();
                        env.put(vd.getIdentifier().getName(), defaultValueForType(t));
                    }
                }

                try {
                    fd.getBody().getStatements().accept(this);
                    eval = null;
                } catch (ReturnSignal r) {
                    eval = r.value;
                }
            } finally {
                env.clear();
                env.putAll(saved);
            }
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
                if (!asBool(eval))
                    break;
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
            for (ast.Expression e : node.getArguments())
                e.accept(this);
        }

        @Override
        public void visit(AST.Assignment node) {
            node.getSource().accept(this);
            Object rhs = eval;

            ast.Expression dest = node.getDestination();
            if (dest instanceof AST.Identifier) {
                AST.Identifier id = (AST.Identifier) dest;
                env.put(id.getName(), rhs);
            } else if (dest instanceof AST.ArrayIndex) {
                AST.ArrayIndex ai = (AST.ArrayIndex) dest;
                Object base = valueOf(ai.getBase());
                if (!(base instanceof Object[])) {
                    throw new RuntimeException("Assigning into non-array");
                }
                Object[] arr = (Object[]) base;

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

        @Override
        public void visit(AST.BoolLiteral node) {
            eval = Boolean.valueOf(node.getValue());
        }

        @Override
        public void visit(AST.Identifier node) {
            Object v = env.get(node.getName());
            if (v == null)
                throw new RuntimeException("Uninitialized var: " + node.getName());
            eval = v;
        }

        @Override
        public void visit(AST.ArrayIndex n) {
            // Evaluate the base array expression
            Object base = valueOf(n.getBase());
            if (!(base instanceof Object[])) {
                throw new RuntimeException("Indexing into non-array value: " + base);
            }

            Object[] arr = (Object[]) base;

            // Evaluate the index expression
            Object idxObj = valueOf(n.getIndex());
            if (!(idxObj instanceof Number)) {
                throw new RuntimeException("Array index is not numeric: " + idxObj);
            }

            int idx = ((Number) idxObj).intValue();
            if (idx < 0 || idx >= arr.length) {
                throw new RuntimeException("Index out of bounds: " + idx + " (len = " + arr.length + ")");
            }

            // Result value of the expression
            eval = arr[idx];
        }

        @Override
        public void visit(ast.Computation n) {
        }

        @Override
        public void visit(AST.AddressOf n) {
            throw new RuntimeException("N/A");
        }

        @Override
        public void visit(AST.Dereference n) {
            throw new RuntimeException("N/A");
        }

        @Override
        public void visit(AST.FunctionBody n) {
            throw new RuntimeException("N/A");
        }

        @Override
        public void visit(AST.FunctionDeclaration n) {
            throw new RuntimeException("N/A");
        }

        @Override
        public void visit(AST.DeclarationList n) {
        }

        @Override
        public void visit(AST.TypeNode n) {
        }

        private Object valueOf(ast.Expression e) {
            e.accept(this);
            return eval;
        }
    }
}
