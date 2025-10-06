package ast;

import ast.AST.Node;
import mocha.Symbol;
import mocha.Token;
import types.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Container class for AST and all nested node classes.
 */
public class AST implements Visitable {

    private final Computation root;

    public AST(Computation root) {
        this.root = root;
    }

    public String printPreOrder() {
        if (root == null) return "AST root is null (parsing likely failed).";
        PrettyPrinter printer = new PrettyPrinter();
        return printer.print(this.root);
    }

    public Computation getRoot() {
        return this.root;
    }

    @Override
    public void accept(NodeVisitor visitor) {
        if (root != null) root.accept(visitor);
    }

    @Override
    public Type getType() {
        return (root != null) ? root.getType() : null;
    }

    @Override
    public void setType(Type type) {
        if (root != null) root.setType(type);
    }

    @Override
    public int lineNumber() {
        return (root != null) ? root.lineNumber() : 1;
    }

    @Override
    public int charPosition() {
        return (root != null) ? root.charPosition() : 1;
    }

    // ========================== BASE CLASSES ==========================
    public static abstract class Node implements Visitable {
        private final int lineNum, charPos;
        protected Type type;

        protected Node(int lineNum, int charPos) {
            this.lineNum = lineNum;
            this.charPos = charPos;
        }

        public void setType(Type type) { this.type = type; }
        public Type getType() { return this.type; }
        public int lineNumber() { return lineNum; }
        public int charPosition() { return charPos; }
        public abstract void accept(NodeVisitor visitor);
    }

    public static abstract class Declaration extends Node {
        protected Declaration(int lineNum, int charPos) { super(lineNum, charPos); }
    }

    // ========================== LITERALS ==========================
    public static class BoolLiteral extends Node implements Expression {
        private final boolean value;
        public BoolLiteral(int l, int c, boolean v) { super(l, c); this.value = v; }
        public boolean getValue() { return value; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class IntegerLiteral extends Node implements Expression {
        private final int value;
        public IntegerLiteral(int l, int c, int v) { super(l, c); this.value = v; }
        public int getValue() { return value; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class FloatLiteral extends Node implements Expression {
        private final float value;
        public FloatLiteral(int l, int c, float v) { super(l, c); this.value = v; }
        public float getValue() { return value; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    // ========================== DESIGNATORS ==========================
    public static class Identifier extends Node implements Expression {
        private final String name;
        private Symbol symbol;

        public Identifier(int line, int col, String name) { super(line, col); this.name = name; }
        public String getName() { return name; }
        public void setSymbol(Symbol s) { this.symbol = s; }
        public Symbol getSymbol() { return symbol; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class AddressOf extends Node implements Expression {
        private final Identifier identifier;
        public AddressOf(int l, int c, Identifier id) { super(l, c); this.identifier = id; }
        public Identifier getIdentifier() { return identifier; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class ArrayIndex extends Node implements Expression {
        private final Expression base, index;
        public ArrayIndex(int l, int c, Expression base, Expression index) { super(l, c); this.base = base; this.index = index; }
        public Expression getBase() { return base; }
        public Expression getIndex() { return index; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Dereference extends Node implements Expression {
        private final Expression expression;
        public Dereference(int l, int c, Expression expr) { super(l, c); this.expression = expr; }
        public Expression getExpression() { return expression; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    // ========================== EXPRESSIONS ==========================
    public static class LogicalNot extends Node implements Expression {
        private final Expression expression;
        public LogicalNot(int l, int c, Expression expr) { super(l, c); this.expression = expr; }
        public Expression getExpression() { return expression; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Power extends Node implements Expression {
        private final Expression base, exponent;
        public Power(int l, int c, Expression base, Expression exponent) { super(l, c); this.base = base; this.exponent = exponent; }
        public Expression getBase() { return base; }
        public Expression getExponent() { return exponent; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Multiplication extends Node implements Expression {
        private final Expression left, right;
        public Multiplication(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Division extends Node implements Expression {
        private final Expression left, right;
        public Division(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Modulo extends Node implements Expression {
        private final Expression left, right;
        public Modulo(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class LogicalAnd extends Node implements Expression {
        private final Expression left, right;
        public LogicalAnd(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Addition extends Node implements Expression {
        private final Expression left, right;
        public Addition(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Subtraction extends Node implements Expression {
        private final Expression left, right;
        public Subtraction(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class LogicalOr extends Node implements Expression {
        private final Expression left, right;
        public LogicalOr(int l, int c, Expression left, Expression right) { super(l, c); this.left = left; this.right = right; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class Relation extends Node implements Expression {
        private final Expression left, right;
        private final String operator;
        public Relation(int l, int c, Expression left, Expression right, String op) { super(l, c); this.left = left; this.right = right; this.operator = op; }
        public Expression getLeft() { return left; }
        public Expression getRight() { return right; }
        public String getOperator() { return operator; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    // ========================== STATEMENTS ==========================
    public static class Assignment extends Node implements Statement {
        private final Expression destination, source;
        public Assignment(int l, int c, Expression dest, Expression src) { super(l, c); this.destination = dest; this.source = src; }
        public Expression getDestination() { return destination; }
        public Expression getSource() { return source; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class IfStatement extends Node implements Statement {
        private final Expression condition;
        private final StatementSequence thenBlock, elseBlock;
        public IfStatement(int l, int c, Expression cond, StatementSequence thenB, StatementSequence elseB) { super(l,c); this.condition = cond; this.thenBlock = thenB; this.elseBlock = elseB; }
        public Expression getCondition() { return condition; }
        public StatementSequence getThenBlock() { return thenBlock; }
        public StatementSequence getElseBlock() { return elseBlock; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class WhileStatement extends Node implements Statement {
        private final Expression condition;
        private final StatementSequence body;
        public WhileStatement(int l, int c, Expression cond, StatementSequence body) { super(l,c); this.condition = cond; this.body = body; }
        public Expression getCondition() { return condition; }
        public StatementSequence getBody() { return body; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class RepeatStatement extends Node implements Statement {
        private final Expression condition;
        private final StatementSequence body;
        public RepeatStatement(int l, int c, StatementSequence body, Expression cond) { super(l,c); this.condition = cond; this.body = body; }
        public Expression getCondition() { return condition; }
        public StatementSequence getBody() { return body; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class ReturnStatement extends Node implements Statement {
        private final Expression value; // can be null
        public ReturnStatement(int l, int c, Expression value) { super(l,c); this.value = value; }
        public Expression getValue() { return value; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    // ========================== FUNCTION NODES ==========================
    public static class ArgumentList extends Node {
        private final List<Expression> arguments;
        public ArgumentList(int l, int c) { super(l,c); arguments = new ArrayList<>(); }
        public void add(Expression arg) { arguments.add(arg); }
        public List<Expression> getArguments() { return arguments; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class FunctionCall extends Node implements Expression, Statement {
        private final Identifier identifier;
        private final ArgumentList arguments;
        public FunctionCall(int l, int c, Identifier id, ArgumentList args) { super(l,c); this.identifier = id; this.arguments = args; }
        public Identifier getIdentifier() { return identifier; }
        public ArgumentList getArguments() { return arguments; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class FunctionBody extends Node {
        private final DeclarationList declarations;
        private final StatementSequence statements;
        public FunctionBody(int l, int c, DeclarationList decls, StatementSequence stmts) { super(l,c); this.declarations = decls; this.statements = stmts; }
        public DeclarationList getDeclarations() { return declarations; }
        public StatementSequence getStatements() { return statements; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class FunctionDeclaration extends Declaration {
        private final Identifier identifier;
        private final List<FormalParameter> parameters;
        private final Node returnType;
        private final FunctionBody body;
        public FunctionDeclaration(int l, int c, Identifier id, List<FormalParameter> params, Node retType, FunctionBody body) { super(l,c); this.identifier=id; this.parameters=params; this.returnType=retType; this.body=body; }
        public Identifier getIdentifier() { return identifier; }
        public List<FormalParameter> getParameters() { return parameters; }
        public Node getReturnType() { return returnType; }
        public FunctionBody getBody() { return body; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    public static class VariableDeclaration extends Declaration {
        private final Identifier identifier;
        private final Node typeNode;
        public VariableDeclaration(int l, int c, Identifier id, Node type) { super(l,c); this.identifier=id; this.typeNode=type; }
        public Identifier getIdentifier() { return identifier; }
        public Node getTypeNode() { return typeNode; }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
    }

    // ========================== CONTAINER NODES ==========================
    public static class DeclarationList extends Node implements List<Declaration> {
        private final List<Declaration> list;
        public DeclarationList(int l, int c) { super(l,c); list = new ArrayList<>(); }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
        // delegate all List methods
        @Override public int size() { return list.size(); }
        @Override public boolean isEmpty() { return list.isEmpty(); }
        @Override public boolean contains(Object o) { return list.contains(o); }
        @Override public java.util.Iterator<Declaration> iterator() { return list.iterator(); }
        @Override public Object[] toArray() { return list.toArray(); }
        @Override public <T> T[] toArray(T[] a) { return list.toArray(a); }
        @Override public boolean add(Declaration e) { return list.add(e); }
        @Override public boolean remove(Object o) { return list.remove(o); }
        @Override public boolean containsAll(java.util.Collection<?> c) { return list.containsAll(c); }
        @Override public boolean addAll(java.util.Collection<? extends Declaration> c) { return list.addAll(c); }
        @Override public boolean addAll(int i, java.util.Collection<? extends Declaration> c) { return list.addAll(i,c); }
        @Override public boolean removeAll(java.util.Collection<?> c) { return list.removeAll(c); }
        @Override public boolean retainAll(java.util.Collection<?> c) { return list.retainAll(c); }
        @Override public void clear() { list.clear(); }
        @Override public Declaration get(int i) { return list.get(i); }
        @Override public Declaration set(int i, Declaration e) { return list.set(i,e); }
        @Override public void add(int i, Declaration e) { list.add(i,e); }
        @Override public Declaration remove(int i) { return list.remove(i); }
        @Override public int indexOf(Object o) { return list.indexOf(o); }
        @Override public int lastIndexOf(Object o) { return list.lastIndexOf(o); }
        @Override public java.util.ListIterator<Declaration> listIterator() { return list.listIterator(); }
        @Override public java.util.ListIterator<Declaration> listIterator(int i) { return list.listIterator(i); }
        @Override public List<Declaration> subList(int f, int t) { return list.subList(f,t); }
    }

    public static class StatementSequence extends Node implements List<Statement> {
        private final List<Statement> list;
        public StatementSequence(int l, int c) { super(l,c); list = new ArrayList<>(); }
        @Override public void accept(NodeVisitor v) { v.visit(this); }
        // delegate all List methods
        @Override public int size() { return list.size(); }
        @Override public boolean isEmpty() { return list.isEmpty(); }
        @Override public boolean contains(Object o) { return list.contains(o); }
        @Override public java.util.Iterator<Statement> iterator() { return list.iterator(); }
        @Override public Object[] toArray() { return list.toArray(); }
        @Override public <T> T[] toArray(T[] a) { return list.toArray(a); }
        @Override public boolean add(Statement e) { return list.add(e); }
        @Override public boolean remove(Object o) { return list.remove(o); }
        @Override public boolean containsAll(java.util.Collection<?> c) { return list.containsAll(c); }
        @Override public boolean addAll(java.util.Collection<? extends Statement> c) { return list.addAll(c); }
        @Override public boolean addAll(int i, java.util.Collection<? extends Statement> c) { return list.addAll(i,c); }
        @Override public boolean removeAll(java.util.Collection<?> c) { return list.removeAll(c); }
        @Override public boolean retainAll(java.util.Collection<?> c) { return list.retainAll(c); }
        @Override public void clear() { list.clear(); }
        @Override public Statement get(int i) { return list.get(i); }
        @Override public Statement set(int i, Statement e) { return list.set(i,e); }
        @Override public void add(int i, Statement e) { list.add(i,e); }
        @Override public Statement remove(int i) { return list.remove(i); }
        @Override public int indexOf(Object o) { return list.indexOf(o); }
        @Override public int lastIndexOf(Object o) { return list.lastIndexOf(o); }
        @Override public java.util.ListIterator<Statement> listIterator() { return list.listIterator(); }
        @Override public java.util.ListIterator<Statement> listIterator(int i) { return list.listIterator(i); }
        @Override public List<Statement> subList(int f, int t) { return list.subList(f,t); }
    }

    public static class FormalParameter extends Node {
        private final Identifier identifier;
        private final Node typeNode;
        public FormalParameter(int l, int c, Identifier id, Node type) { super(l,c); this.identifier=id; this.typeNode=type; }
        public Identifier getIdentifier() { return identifier; }
        public Node getTypeNode() { return typeNode; }
        @Override public void accept(NodeVisitor v) { /* Handled by FunctionDeclaration */ }
    }
    
    public static class TypeNode extends Node {
        private final Type actualType;

        public TypeNode(int l, int c, Type actualType) {
            super(l, c);
            this.actualType = actualType;
        }

        public Type getActualType() { return actualType; }

        @Override
        public void accept(NodeVisitor visitor) {
            visitor.visit(this);
        }
    }
    
    public static class UnaryMinus extends Node implements Expression {
        private Expression expr;
        public UnaryMinus(int line, int col, Expression expr) {
            super(line, col);
            this.expr = expr;
        }
        public Expression getExpr() { return expr; }
        @Override
        public void accept(NodeVisitor visitor) { 
        	visitor.visit(this); 
        	}
    }
}
