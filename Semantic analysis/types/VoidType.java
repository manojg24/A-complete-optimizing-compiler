package types;

public class VoidType extends Type {
	
	@Override
    public boolean equivalent(Type that) {
        return that instanceof VoidType;
    }
    
    @Override
    public String toString() {
        return "void";
    }
}
