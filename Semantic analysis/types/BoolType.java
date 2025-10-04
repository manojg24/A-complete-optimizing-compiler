package types;

public class BoolType extends Type {
    
	@Override
    public boolean equivalent(Type that) {
        return that instanceof BoolType;
    }

    @Override
    public Type and(Type that) {
        if (that instanceof BoolType) {
            return this;
        }
        return super.and(that);
    }

    @Override
    public Type or(Type that) {
        if (that instanceof BoolType) {
            return this;
        }
        return super.or(that);
    }

    @Override
    public Type not() {
        return this;
    }

    @Override
    public Type assign(Type source) {
        if (source instanceof BoolType) {
            return this;
        }
        return super.assign(source);
    }

    @Override
    public String toString() {
        return "bool";
    }
}
