import processing.core.PShape;
import processing.core.PVector;

/**
 * Handles the rendering of objects based on current stress. 
 * 
 * N.B. Implementing subclasses must have a method of token generation.
 * Token generation should use a static method, hence not being an abstract to inherit.
 */
public abstract class AbstractDrawable {

    public static final int VARIANT_TOKENS = 15;
    public static final int FRAMES_PER_STRESS_BAR_RESKETCH = 2;

    public static int stressIndex = 0;

    protected final DontDrown sketch;
    protected final StressAndTokenState state;
    protected final int redrawOffset;

    public PVector pos; // position

    private final PShape[][] tokens;
    private PShape token; // i.e. sprite or drawing (not to be confused with collectable Tokens)
    private int tokenIndex = 0;
    private int lastStressIndex = 0;

    protected abstract boolean onScreen();

    protected AbstractDrawable(DontDrown sketch, PShape[][] tokens) {
        this.sketch = sketch;
        state = sketch.levelState;
        redrawOffset = (int) Math.floor(sketch.random(StressAndTokenState.FRAMES_PER_RESKETCH_MIN));
        this.tokens = tokens;
    }

    /**
     * (Redraws and) renders an object as dictated by stress.  
     */
    protected void renderAD() {
        if (onScreen()) {
            if (token == null
                    || (sketch.frameCount + redrawOffset) % state.framesPerResketch == 0) {
                tokenIndex = (tokenIndex + 1) % VARIANT_TOKENS;
                token = tokens[stressIndex][tokenIndex];
                lastStressIndex = stressIndex;
            } else if (Math.abs(state.stress - lastStressIndex) > 5) {
                token = tokens[stressIndex][tokenIndex];
                lastStressIndex = stressIndex;
            }

            sketch.shape(token, pos.x, pos.y);
        }
    }

    /**
     * Intended for the stress bar's fill, which redraws itself more frequently so that the bar fills smoothly. 
     */
    protected void renderADStress() {
        if (onScreen()) {
            if (token == null
                    || (sketch.frameCount + redrawOffset) % FRAMES_PER_STRESS_BAR_RESKETCH == 0) {
                tokenIndex = (tokenIndex + 1) % VARIANT_TOKENS;
                int stressIndexAlt = (int) Math.max(0, (state.stress * ScoreOverlay.StressBar.STRESS_BAR_RESOLUTION));
                token = tokens[stressIndexAlt][tokenIndex];
            }

            sketch.shape(token, pos.x, pos.y);
        }
    }

    /**
     * Usually a wrapper for either renderAD() or renderADStress(). 
     */
    public abstract void render();
}
