package ast;
import types.Type;

public interface Visitable {

    public void accept (NodeVisitor visitor);
    Type getType();
    void setType(Type type);
    int lineNumber();
    int charPosition();
}
