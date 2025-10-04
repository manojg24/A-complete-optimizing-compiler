package types;

public class ArrayType extends Type {
	
	private int extent;
    private Type base;

    public ArrayType(int extent, Type base) {
        this.extent = extent;
        this.base = base;
    }

    public int getExtent() {
        return extent;
    }

    public Type getBase() {
        return base;
    }

    /**
     * An array type is equivalent to another if they are both arrays,
     * have the same extent, and have equivalent base types.
     */
    @Override
    public boolean equivalent(Type that) {
        if (that instanceof ArrayType) {
            ArrayType other = (ArrayType) that;
            return this.extent == other.extent && this.base.equivalent(other.base);
        }
        return false;
    }

    /**
     * This defines the array indexing operation (e.g., arr[i]).
     * The index must be an integer, and the result is the base type of the array.
     */
    @Override
    public Type index(Type that) {
        if (that.equivalent(new IntType())) {
            return this.base; // Accessing an array gives you its base type
        }
        return super.index(that); // Fallback to the error-producing method
    }

    @Override
    public Type assign(Type source) {
        // An array can be assigned a value from another, equivalent array
        if (this.equivalent(source)) {
            return this;
        }
        return super.assign(source);
    }

    @Override
    public String toString() {
        return base.toString() + "[" + extent + "]";
    }
}
