package types;

public class IntType extends Type {
	
	@Override
    public boolean equivalent(Type that) {
        return that instanceof IntType;
    }

    @Override
    public Type add(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return that; // In mixed arithmetic, the result is the wider type (float)
        }
        return super.add(that);
    }

    @Override
    public Type sub(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return that;
        }
        return super.sub(that);
    }

    @Override
    public Type mul(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return that;
        }
        return super.mul(that);
    }

    @Override
    public Type div(Type that) {
        if (that instanceof IntType || that instanceof FloatType) {
            return that;
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
        // You can assign an int to an int or a float
        if (source instanceof IntType) {
            return this;
        }
        return super.assign(source);
    }

    @Override
    public String toString() {
        return "int";
    }

}
