import processing.core.PShape;
import processing.core.PVector;

public abstract class AbstractDrawable {
    // TODO make all objects semi-transparent 
    // TODO statically define my two colour modes 

    protected final DontDrown sketch;
    protected final LevelState state;

    public PVector oldPos; // movement in the last frame
    public PVector pos; // position

    protected int frameCounter;
    protected PShape token;

    protected AbstractDrawable(DontDrown sketch) {
        this.sketch = sketch;
        this.state = sketch.levelState;
        frameCounter = (int) (sketch.random(0,5)); 
    }

    protected abstract void generateToken();

    public abstract void render();
}