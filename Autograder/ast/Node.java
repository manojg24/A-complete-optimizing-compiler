package ast;

import mocha.Token;
import types.Type;

public abstract class Node implements Visitable {

    private int lineNum;
    private int charPos;
    protected Type type;

    protected Node (int lineNum, int charPos) {
        this.lineNum = lineNum;
        this.charPos = charPos;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public Type getType()
    {
        return this.type;
    }
    
    public int lineNumber () {
        return lineNum;
    }

    public int charPosition () {
        return charPos;
    }

    public String getClassInfo () {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString () {
        return this.getClass().getSimpleName();
    }
    
    public static class TypeNode extends Node {
        private Type type;

        public TypeNode(int line, int col, Type type) {
            super(line, col);
            this.type = type;
        }

        @Override
        public void accept(NodeVisitor v) {
            // Can be empty if visitors don't process TypeNode
        }

        public Type getType() {
            return type;
        }
            
            public void setType(Type t) {
                this.type = t;
        }
    }

    // Some factory methods for convenience
    public static Statement newAssignment (int lineNum, int charPos, Expression dest, Token assignOp, Expression src) {
        throw new RuntimeException("implement newAssignment factory method");
    }

    public static Expression newExpression (Expression leftSide, Token op, Expression rightSide) {
        throw new RuntimeException("implement newExpression factory method");
    }

    public static Expression newLiteral (Token tok) {
       throw new RuntimeException("implement newLiteral factory method");     
    }
}
