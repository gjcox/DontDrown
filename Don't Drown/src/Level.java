import java.util.ArrayList;

import processing.core.PVector;

public class Level {

    public static final float PAN_RATE_DIV = PlayerCharacter.PC_DIAMETER_DIV * 10f;
    public static final float H_MIN_JUMP_RANGE_MULT = 0.5f;
    public static final float H_MAX_JUMP_RANGE_MULT = 1f;
    public static final float V_MIN_JUMP_RANGE_MULT = 0f;
    public static final float V_MAX_JUMP_RANGE_MULT = 0.25f;
    public static final float H_MIN_JUMP_HEIGHT_MULT = 0.25f;
    public static final float H_MAX_JUMP_HEIGHT_MULT = 0.5f;
    public static final float V_MIN_JUMP_HEIGHT_MULT = 0.75f;
    public static final float V_MAX_JUMP_HEIGHT_MULT = 1f;

    private final DontDrown sketch;

    // meta information
    public final Debuff debuff;
    public final Difficulty difficulty;
    public final int height;
    public final Page page;
    public final float panRate;
    public final float topLimit; // used to stop over-panning
    private final float tokenElevation; // height above platforms for tokens to hover
    public final float waveTime; // time taken for the wave to reach the top platform

    // level generation values
    public final float lowestPlatformHeight;
    public final float highestPlatformHeight; // upper limit; may not actually be reached
    public final float playableWidth; // width - margin
    public final int betweenRedHerrings; // minimum layers between red herring platforms
    private final float jumpRange; // cached value from PC
    private final float jumpHeight; // cached value from PC
    private final float verticality; // affects the ratio of vertical jumps to horizontal ones

    // wave speed
    public final float defaultWaveRiseRate;
    public float waveRiseRate;

    public enum PanningState {
        UP,
        DOWN,
        NEITHER;
    }

    // variable values
    public PanningState panningState = PanningState.NEITHER;
    public float top; // the top of the level relative to the viewport
    public ArrayList<Token> tokens = new ArrayList<>();
    public ArrayList<Platform> platforms = new ArrayList<>();
    public Platform highestPlatform;

    // high score
    public int highScore = 0;
    public float timeLeft = -123; // value for which the level selector menu does not show seconds to spare

    public Level(DontDrown sketch, Debuff debuff, Difficulty difficulty) {
        this.sketch = sketch;

        // meta information
        this.debuff = debuff;
        this.difficulty = difficulty;
        this.height = (int) (sketch.height * difficulty.heightMult);
        page = new Page(sketch, height, false);
        panRate = height / PAN_RATE_DIV;
        topLimit = (float) sketch.height - height;
        tokenElevation = 0.75f * sketch.width / PlayerCharacter.PC_DIAMETER_DIV;

        // level generation values
        lowestPlatformHeight = .75f * sketch.height;
        highestPlatformHeight = page.topLineY + sketch.height / 10f;
        playableWidth = sketch.width - Page.marginX;
        this.verticality = difficulty.verticality;
        this.betweenRedHerrings = difficulty.betweenRedHerrings;
        jumpRange = sketch.pc.jumpRange;
        jumpHeight = sketch.pc.jumpHeight;

        // wave speed
        defaultWaveRiseRate = sketch.height / (60f * difficulty.waveRiseTime);
        waveRiseRate = defaultWaveRiseRate;

        top = topLimit;

        generatePlatformsAndTokens(difficulty.hasGround);

        float heightRatio = (Wave.waveInitHeight - highestPlatform.pos.y) / sketch.height;
        waveTime = difficulty.waveRiseTime * heightRatio;
    }

    /* Wrapper function */
    private void addToken(float x, float y) {
        tokens.add(new Token(sketch, x, y));
    }

    /*
     * Calculates the position vector of the next platform based on the current
     * platform and the generated X and Y distances, ensuring that the new platform
     * will be within the bounds of the level.
     */
    private PVector placePlatform(Platform currentPlatform, Platform nextPlatform, float diffX, float diffY) {
        float x = Math.max(Page.marginX, currentPlatform.pos.x + diffX);
        x = Math.min(x, sketch.width - nextPlatform.width);
        float y = Math.min(height - nextPlatform.height, currentPlatform.pos.y - diffY);
        y = Math.max(highestPlatformHeight, y);
        return new PVector(x, y);
    }

    /*
     * Corrects the proposed position of a new platform to be within the bounds of
     * the level.
     */
    private PVector placePlatform(Platform toPlace, float proposedX, float proposedY) {
        float x = Math.max(Page.marginX, proposedX);
        x = Math.min(x, sketch.width - toPlace.width);
        float y = Math.min(height - toPlace.height, proposedY);
        y = Math.max(highestPlatformHeight, y);
        return new PVector(x, y);
    }

    /**
     * Randomly generates the platforms and tokens of the level.
     * 
     * @param hasGround determines if the first platform should span the playable
     *                  area
     */
    private void generatePlatformsAndTokens(boolean hasGround) {
        Platform prevPlatform = null;
        Platform currentPlatform;

        if (hasGround) {
            currentPlatform = new Platform(sketch, Page.marginX, lowestPlatformHeight, playableWidth);
            platforms.add(currentPlatform);
        } else {
            currentPlatform = new Platform(sketch,
                    Page.marginX + sketch.random(playableWidth - sketch.width / Platform.PF_WIDTH_DIV),
                    lowestPlatformHeight);
            platforms.add(currentPlatform);
        }
        highestPlatform = currentPlatform;

        float diffX = 0, diffY = 0; // displacements between current and next platform
        boolean goingLeft = false;
        boolean redHerring = false; // whether or not to add an extra platform with a token
        int sinceRedHerring = betweenRedHerrings; // the minimum number of platforms between tokens

        while (currentPlatform.pos.y > highestPlatformHeight + (jumpHeight * V_MIN_JUMP_HEIGHT_MULT)) {

            Platform nextPlatform = new Platform(sketch, 0, 0);

            if (debuff.equals(Debuff.OVERWORKED) && platforms.size() > 1) {
                // every platform has a token
                addToken(currentPlatform.pos.x + currentPlatform.width / 2, currentPlatform.pos.y - tokenElevation);
            }

            if (redHerring && prevPlatform != null) {
                // place a token on a new platform off the optimal path
                Platform redHerringP = new Platform(sketch, 0, 0);
                redHerringP.initPos = placePlatform(redHerringP,
                        prevPlatform.pos.x - diffX,
                        currentPlatform.pos.y);
                redHerringP.pos = redHerringP.initPos.copy();
                addToken(redHerringP.pos.x + redHerringP.width / 2, redHerringP.pos.y - tokenElevation);
                platforms.add(redHerringP);
                sinceRedHerring = 0;
            } else {
                sinceRedHerring++;
            }

            if (currentPlatform.width == playableWidth) {
                // first platform after the ground is a special case
                diffY = jumpHeight * sketch.random(V_MIN_JUMP_HEIGHT_MULT, V_MAX_JUMP_HEIGHT_MULT);
                diffX = sketch.random(0, playableWidth - nextPlatform.width);
            } else {
                boolean wentUp = diffY >= jumpHeight * V_MIN_JUMP_HEIGHT_MULT;
                boolean edgeReached = currentPlatform.pos.x < Page.marginX + nextPlatform.width
                        || currentPlatform.pos.x > sketch.width - currentPlatform.width - nextPlatform.width;

                if (edgeReached) {
                    redHerring = false;

                    // turn around
                    goingLeft = !goingLeft;

                    // reflection jump
                    diffY = jumpHeight * sketch.random(V_MIN_JUMP_HEIGHT_MULT, V_MAX_JUMP_HEIGHT_MULT);
                    diffX = Math.max(currentPlatform.width,
                            jumpRange * sketch.random(H_MIN_JUMP_RANGE_MULT, H_MAX_JUMP_RANGE_MULT));
                } else {
                    // random chance to change horizontal direction
                    if (!redHerring && sketch.random(0f, 1f) < 0.1) {
                        goingLeft = !goingLeft;
                        redHerring = false;
                    } else {
                        redHerring = /* !redHerring && */sinceRedHerring >= betweenRedHerrings && wentUp;
                    }

                    if (!wentUp && sketch.random(0f, 1f) < verticality) {
                        // vertical jump (can't have two in a row)
                        diffY = jumpHeight * sketch.random(V_MIN_JUMP_HEIGHT_MULT, V_MAX_JUMP_HEIGHT_MULT);
                        diffX = jumpRange * sketch.random(V_MIN_JUMP_RANGE_MULT, V_MAX_JUMP_RANGE_MULT);
                    } else {
                        // horizontal jump
                        diffY = jumpHeight * sketch.random(H_MIN_JUMP_HEIGHT_MULT, H_MAX_JUMP_HEIGHT_MULT);
                        diffX = Math.max(currentPlatform.width,
                                jumpRange * sketch.random(H_MIN_JUMP_RANGE_MULT, H_MAX_JUMP_RANGE_MULT));
                    }

                }

                if (goingLeft) {
                    diffX = -(diffX);
                }
            }

            nextPlatform.initPos = placePlatform(currentPlatform, nextPlatform, diffX, diffY);
            nextPlatform.pos = nextPlatform.initPos.copy();
            prevPlatform = currentPlatform;
            currentPlatform = nextPlatform;
            platforms.add(currentPlatform);

            if (currentPlatform.pos.y < highestPlatform.pos.y) {
                highestPlatform = currentPlatform;
            }
        }

        // replace the highest platform with a specially coloured one
        highestPlatform = new Platform(highestPlatform);
        platforms.remove(platforms.size() - 1);
        platforms.add(highestPlatform);
    }

    /**
     * Undo panning and the collection of tokens
     */
    public void reset() {
        panningState = PanningState.NEITHER;
        top = topLimit;
        waveRiseRate = defaultWaveRiseRate;

        if (page.lines != null) {
            page.lines.resetMatrix();
        }
        for (Platform platform : platforms) {
            platform.pos = platform.initPos.copy();
        }
        for (Token token : tokens) {
            token.reset();
        }
    }

    /**
     * Make tokens bob up and down. Pan level if needed. 
     */
    public void integrate() {
        for (Token token : tokens) {
            token.integrate();
        }

        if (panningState.equals(PanningState.UP)) {
            if (top + panRate >= 0f) {
                pan(0f - top);
                panningState = PanningState.NEITHER;
            } else {
                pan(Math.max(panRate, Math.abs(sketch.pc.vel.y)));
            }
        } else if (panningState.equals(PanningState.DOWN)) {
            if (top - panRate <= topLimit) {
                pan(topLimit - top);
                panningState = PanningState.NEITHER;
            } else {
                pan(-Math.max(panRate, Math.abs(sketch.pc.vel.y)));
            }
        }
    }

    /* Move all level elements up or down (incl. PC and wave) */
    private void pan(float y) {
        top += y;
        page.lines.translate(0, y);
        for (Platform platform : platforms) {
            platform.pos.y += y;
        }
        for (Token token : tokens) {
            token.pos.y += y;
        }
        sketch.pc.pos.y += y;
        sketch.risingWave.pos.y += y;
    }


    public void render() {
        page.render();

        int i = 0;
        for (Platform platform : platforms) {
            platform.render();
            if (sketch.debugging)
                sketch.text(i++, platform.pos.x, platform.pos.y);
        }

        for (Token token : tokens) {
            token.render();
        }
    }

}
