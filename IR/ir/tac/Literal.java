package ir.tac;

public class Literal implements Value {

    private final Object val;

    public Literal(Object val) { this.val = val; }

    public Object value() { return val; }

    @Override
    public void accept(TACVisitor visitor) { visitor.visit(this); }

    @Override
    public String toString() {
        if (val == null) return "null";

        // Fast path for primitives/boxed/strings
        if (val instanceof Number || val instanceof Boolean || val instanceof CharSequence) {
            return val.toString();
        }

        // If it's an AST node, try common "value()" / "getValue()" / "text()" methods via reflection.
        try {
            for (String m : new String[]{"value", "getValue", "text", "getText"}) {
                try {
                    java.lang.reflect.Method mm = val.getClass().getMethod(m);
                    Object inner = mm.invoke(val);
                    if (inner != null) return inner.toString();
                } catch (NoSuchMethodException ignore) { /* try next name */ }
            }
        } catch (Exception ignore) { /* fall through to default */ }

        // Fallback
        return val.toString();
    }
}