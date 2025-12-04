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
        if (!(that instanceof ArrayType)) return false;
        ArrayType other = (ArrayType) that;

        boolean extentOk =
            this.extent == other.extent ||
            this.extent == -1 ||              // formal/this unknown matches any actual
            other.extent == -1;               // (symmetry helps in general comparisons)

        return extentOk && this.base.equivalent(other.base);
    }

    /**
     * arr[i] : i must be int, result is base type.
     */
    @Override
    public Type index(Type that) {
        if (that.equivalent(new IntType())) {
            return this.base;
        }
        return super.index(that);
    }

    /**
     * Allow assigning actual arrays to formal arrays with unspecified extent.
     * (Used for param matching when FuncType.call calls formal.assign(actual).)
     */
    @Override
    public Type assign(Type source) {
        if (!(source instanceof ArrayType)) {
            return super.assign(source);
        }
        ArrayType r = (ArrayType) source;

        // extent must match OR this (formal) may be -1 (wildcard)
        boolean extentOk = (this.extent == r.extent) || (this.extent == -1);
        if (!extentOk) {
            return new ErrorType("Cannot assign " + r + " to " + this + ".");
        }

        // element type must be equivalent
        if (!this.base.equivalent(r.base)) {
            return new ErrorType("Cannot assign " + r + " to " + this + ".");
        }

        return this;
    }

    @Override
    public String toString() {
        return base.toString() + "[" + extent + "]";
    }
}
