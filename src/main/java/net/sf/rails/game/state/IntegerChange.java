package net.sf.rails.game.state;

/**
 * Change associated with IntegerState
 */
public final class IntegerChange extends Change {

    private final IntegerState state;
    private final int newValue;
    private final int oldValue;
    
    IntegerChange(IntegerState state, int newValue) {
        this.state = state;
        this.newValue = newValue;
        this.oldValue = state.value();
        super.init(state);
    }

    @Override void execute() {
        state.change(newValue);
    }

    @Override void undo() {
        state.change(oldValue);
    }

    @Override
    public IntegerState getState() {
        return state;
    }

    @Override
    public String toString() {
        return "Change for " + state + ": From " + oldValue + " to " + newValue; 
    }

}
