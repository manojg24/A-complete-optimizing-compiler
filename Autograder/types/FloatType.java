package types;

public class FloatType extends Type {
	
	@Override
    public boolean equivalent(Type that) {
        return that instanceof FloatType;
    }

    @Override
    public Type add(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return this; // Result is always a float
        }
        return super.add(that);
    }

    @Override
    public Type sub(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return this;
        }
        return super.sub(that);
    }

    @Override
    public Type mul(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return this;
        }
        return super.mul(that);
    }

    @Override
    public Type div(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return this;
        }
        return super.div(that);
    }

    @Override
    public Type compare(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return new BoolType(); // Comparisons always yield a boolean
        }
        return super.compare(that);
    }
    
    @Override
    public Type assign(Type source) {
        // You can only assign a float to a float
        if (source instanceof FloatType) {
            return this;
        }
        return super.assign(source);
    }

    @Override
    public String toString() {
        return "float";
    }

}
