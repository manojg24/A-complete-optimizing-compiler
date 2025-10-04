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
