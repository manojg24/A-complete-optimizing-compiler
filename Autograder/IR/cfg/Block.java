package ir.cfg;

public abstract class Block implements Visitable {

    boolean visited;

    protected Block () {
        visited = false;
    }

    public boolean visited () {
        return visited;
    }

    public void markVisited () {
        visited = true;
    }

    public abstract void resetVisited ();
}
