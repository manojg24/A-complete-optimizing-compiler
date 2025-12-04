package mocha;
import types.Type;

public class Symbol {

    private String name;
    private Type type;

    // TODO: Add other parameters like type

    public Symbol (String name, Type type) {
        this.name = name;
        this.type = type;
    }
    public String name () {
        return name;
    }
    public Type type () {
        return type;
    }

    @Override
    public String toString()
    {
        // Helpful for debugging
        return name + ": " + type;
    }
}
