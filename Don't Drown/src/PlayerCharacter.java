import processing.core.PConstants;
import processing.core.PShape;
import processing.core.PVector;

public class PlayerCharacter extends AbstractDrawable {

    public static final float PC_DIAMETER_DIV = 40f;
    private static final float PC_INCR_DIV = 100f;

    // horizontal movement
    private static final int PC_MIN_ACC_TIME = 10; // min frames to max speed from stop
    private static final int PC_MAX_ACC_TIME = 25; // max frames to max speed from stop
    private static final int PC_MIN_DEC_TIME = 5; // min frames to stop from max speed
    private static final int PC_MAX_DEC_TIME = 20; // max frames to stop from max speed
    private static final float PC_MAX_SPEED_MULT = 0.6f; // incr per frame
    private static final float PC_AIR_THRUST_MULT = 0.4f; // horizontal thrust multiplier when mid-air
    private static final float PC_AIR_FRICTION_FACTOR = 0.05f; // horizontal friction multiplier when mid-air

    // vertical movement
    private static final float PC_JUMP_IMPULSE = 12.5f;
    private static final float PC_RISING_GRAVITY = 0.5f;
    private static final float PC_FALLING_GRAVITY = 0.7f;
    private static final float PC_FALLING_DRAG_FACTOR = 0.025f; // vertical drag multiplier when falling
    private static final float PC_BOUNCE_MULT = 0.75f; // coefficient of restitution for horizontal collision
    private static final int PC_HANG_TIME_DEF = 3; // frames of (default) hang time
    private static final int PC_BOUNCE_REMEMBER = 5; // frames before landing on a platform for a jump to work
    private static final int PC_COYOTE_TIME = 2; // frames to jump after falling off the end of a platform

    // rendering
    private static PShape[][] staticTokens = null;
    public static final float PC_MIN_HUE = 280f;
    public static final float PC_MAX_HUE = 360f;
    public static final float PC_MIN_SAT = 0.2f;
    public static final float PC_MAX_SAT = 1f;
    public static final float PC_MIN_LIGHT = 0.9f;
    public static final float PC_MAX_LIGHT = 0.9f;
    public static final float PC_STROKE_ALPHA = 1f;
    public static final float PC_FILL_ALPHA = 0.85f;
    public static float diameter;
    public static float radius;

    // general movement
    public final float incr; // movement increment
    public final float maxSpeed; // max horizontal speed, in incr per frame
    public PVector vel; // current velocity

    // vertical movement
    public Platform surface = null; // platform that the PC is on/falling through
    public FallState fallState = FallState.FALLING;
    public final int riseFrames; // time taken to reach peak of jump
    public final int fallFrames; // time taken to return to ground after peak
    public final int jumpFrames; // total time taken to return to ground after jump
    private final float jumpHeightIncr; // vertical jump height at peak, in incr
    public final float jumpRange; // max horizontal distance travelled in a jump, in pixels
    public final float jumpHeight; // vertical jump height at peak, in pixels

    // horizontal movement
    public final float minHorizontalThrust; // thrust at 0 stress
    public final float maxHorizontalThrust; // thrust at max stress
    public final float minHorizontalFriction; // friction force at max stress
    public final float maxHorizontalFriction; // friction force at 0 stress
    private SteerState steerState = SteerState.NEITHER;
    private MoveState moveState = MoveState.AT_REST;
    public boolean steerSinceLand = false;

    // force resolution
    public PVector resultant = new PVector();
    public static final float I_MASS = 1 / 15f; // inverse mass

    // frame counters
    private int jumpMemoryCounter = 0; // trying to jump just before hitting the ground
    private int hangCounter = 0; // peak of jump
    private int coyoteCounter = 0; // jumping just after leaving the edge of a platform

    public enum FallState {
        ON_SURFACE(0f),
        COYOTE_TIME(0f),
        RISING(PC_RISING_GRAVITY),
        HANG_TIME(0f),
        FALLING(PC_FALLING_GRAVITY),
        DROPPING(PC_FALLING_GRAVITY); // through a platform

        final float gravity;

        FallState(Float gravity) {
            this.gravity = gravity;
        }
    }

    public enum SteerState {
        LEFT(-1),
        RIGHT(1),
        NEITHER(0);

        public final int directionMult;

        SteerState(int directionMult) {
            this.directionMult = directionMult;
        }
    }

    public enum MoveState {
        AT_REST,
        ACCELERATING, // deceleration is acceleration against current velocity
        MAX_SPEED;
    }

    public SteerState getSteerState() {
        return steerState;
    }

    public MoveState getMoveState() {
        return moveState;
    }

    private int riseFrames() {
        // v = 0 = u + at -> t = - u/a
        // underestimating / ignoring drag
        float u = (PC_JUMP_IMPULSE - FallState.RISING.gravity) * I_MASS;
        float a = FallState.RISING.gravity * I_MASS;
        return (int) Math.floor(u / a);
    }

    private float jumpHeight() {
        // s = ut + 1/2at^2
        // u = initial vertical speed after jump
        // u = jumpImpulse * iWeight (incr per frame)
        // t = riseFrames (frames)
        // a = - (rising.gravity * iWeight) (underestimating / ignoring drag)
        // ... (incr per frame^2)
        float u = (PC_JUMP_IMPULSE - FallState.RISING.gravity) * I_MASS; // incr per frame
        return (float) ((u * riseFrames) +
                (0.5f * -(FallState.RISING.gravity * I_MASS) *
                        Math.pow(riseFrames, 2)));
    }

    private int fallFrames() {
        // s = ut + 1/2at^2
        // s = jumpHieght (incr)
        // u = 0 (incr per frame)
        // t = ? (frames)
        // a = falling.gravity * iWeight (underestimating / ignoring drag) (incr per
        // ... frame^2)
        // 0 = 1/2at^2 + ut - s
        return (int) Math.floor(Utils.solveQuadratic(-FallState.FALLING.gravity * I_MASS / 2, 0, jumpHeightIncr));
    }

    private float jumpRange() {
        // s = ut
        // constant horizontal velocity
        // 1.1f accounts for rounding/approximation errors (based on experimentation)
        return maxSpeed * jumpFrames * 1.1f;
    }

    public PlayerCharacter(DontDrown sketch) {
        super(sketch, (staticTokens == null ? generateTokens(sketch) : staticTokens));

        this.pos = new PVector(sketch.width / 2f, sketch.height / 2f);
        this.vel = new PVector();

        this.incr = sketch.width / PC_INCR_DIV;
        this.maxSpeed = incr * PC_MAX_SPEED_MULT;
        this.minHorizontalFriction = (PC_MAX_SPEED_MULT / PC_MAX_DEC_TIME) / I_MASS;
        this.maxHorizontalFriction = (PC_MAX_SPEED_MULT / PC_MIN_DEC_TIME) / I_MASS;
        this.minHorizontalThrust = (PC_MAX_SPEED_MULT / PC_MAX_ACC_TIME) / I_MASS;
        this.maxHorizontalThrust = (PC_MAX_SPEED_MULT / PC_MIN_ACC_TIME) / I_MASS;

        this.riseFrames = riseFrames();
        this.jumpHeightIncr = jumpHeight();
        this.jumpHeight = incr * jumpHeightIncr;
        this.fallFrames = fallFrames();
        this.jumpFrames = riseFrames + PC_HANG_TIME_DEF + fallFrames;
        this.jumpRange = jumpRange();
    }

    private void applyHorizontalDrag() {
        int direction;

        if (vel.x == 0) {
            direction = 0;
        } else {
            direction = vel.x < 0 ? 1 : -1;
        }

        if (fallState.equals(FallState.ON_SURFACE)) {
            resultant.x += state.pcFriction * direction;
        } else {
            resultant.x += state.pcFriction * direction * PC_AIR_FRICTION_FACTOR;
        }
    }

    private void applyHorizontalThrust() {
        if (Math.abs(vel.x) < maxSpeed || steerState.equals(vel.x < 0 ? SteerState.RIGHT : SteerState.LEFT)) {
            if (fallState.equals(FallState.ON_SURFACE) || fallState.equals(FallState.COYOTE_TIME)) {
                resultant.x += state.pcThrust * steerState.directionMult;
            } else {
                resultant.x += state.pcThrust * steerState.directionMult * PC_AIR_THRUST_MULT;
            }
        } else if (Math.abs(vel.x) >= maxSpeed && !steerState.equals(SteerState.NEITHER)) {
            // transitition to max speed
            vel.x = maxSpeed * steerState.directionMult;
            moveState = MoveState.MAX_SPEED;
        }
    }

    private void updateVelocity() {
        // apply gravity
        resultant.y += fallState.gravity;

        // jump if landed within 5 frames of pressing the jump button
        if (jumpMemoryCounter-- >= 0 && fallState.equals(FallState.ON_SURFACE)) {
            jump();
        }

        // check if at horizontal rest
        if (!moveState.equals(MoveState.AT_REST) && steerState.equals(SteerState.NEITHER)
                && ((fallState.equals(FallState.ON_SURFACE) && Math.abs(vel.x) < state.pcMinSpeed)
                        || (!fallState.equals(FallState.ON_SURFACE)
                                && Math.abs(vel.x) < state.pcMinSpeed * PC_AIR_FRICTION_FACTOR))) {
            vel.x = 0;
            moveState = MoveState.AT_REST;
        }

        // if at max horizontal speed, check if max speed should be maintained
        if (moveState.equals(MoveState.MAX_SPEED)) {
            if (vel.x > 0 && steerState.equals(SteerState.RIGHT)
                    || vel.x < 0 && steerState.equals(SteerState.LEFT)) {
                // do nothing
            } else {
                moveState = MoveState.ACCELERATING;
            }
        }

        // if not at rest or max speed, apply thrust from steering
        if (moveState.equals(MoveState.ACCELERATING)) {
            applyHorizontalThrust();
        }

        // if not at rest, max speed, nor steering in the same direction as velocity,
        // apply friction force
        if (moveState.equals(MoveState.ACCELERATING) && (steerState.equals(SteerState.NEITHER)
                || steerState.equals(vel.x < 0 ? SteerState.RIGHT : SteerState.LEFT))) {
            applyHorizontalDrag();
        }

        // calulate acceleration and velocity from resultant force
        PVector acc = resultant.mult(I_MASS).mult(incr);
        vel.add(acc);

        if (fallState.equals(FallState.RISING) && vel.y >= 0) {
            // if at peak of jump (i.e. start of hang time)
            fallState = FallState.HANG_TIME;
            hangCounter = 0;
            vel.y = 0;
        } else if (fallState.equals(FallState.HANG_TIME) && hangCounter++ >= PC_HANG_TIME_DEF) {
            // if end of hang time reached
            fallState = FallState.FALLING;
        } else if (fallState.equals(FallState.FALLING)) {
            // apply drag if falling
            vel.y -= vel.y * PC_FALLING_DRAG_FACTOR;
        } else if (fallState.equals(FallState.COYOTE_TIME) && coyoteCounter-- == 0) {
            // if end of coyote time
            fallState = FallState.FALLING;
        }

        // reset resultant force
        resultant = new PVector();
    }

    /** Move the PC, bouncing them off the edge of the playable area if needed. */
    public void integrate() {
        updateVelocity();
        pos.add(vel);
        if (sketch.level != null) {
            if (pos.x - diameter / 2 <= Page.marginX) {
                pos.x = Page.marginX + diameter / 2;
                vel.x = Math.abs(vel.x) * PC_BOUNCE_MULT;
            } else if (pos.x + diameter / 2 >= sketch.width) {
                pos.x = sketch.width - diameter / 2;
                vel.x = -Math.abs(vel.x) * PC_BOUNCE_MULT;
            }
        }
    }

    public void jump() {
        if (fallState.equals(PlayerCharacter.FallState.ON_SURFACE)
                || fallState.equals(PlayerCharacter.FallState.COYOTE_TIME)) {
            fallState = FallState.RISING;
            resultant.y = -PC_JUMP_IMPULSE;
            jumpMemoryCounter = 0;
            surface = null;
        } else {
            jumpMemoryCounter = PC_BOUNCE_REMEMBER;
        }
    }

    public void land(Platform upon) {
        if (upon != null) {
            fallState = FallState.ON_SURFACE;
            surface = upon;
            vel.y = 0f;
            pos.y = upon.pos.y - diameter / 2f;
            if (steerState.equals(SteerState.NEITHER)) {
                steerSinceLand = false;
            }
        }
    }

    private void fall(boolean fall) {
        if (!fall) {
            // stop the player from sliding off the edge of a platform
            this.steer(SteerState.NEITHER);
            if (surface != null) {
                // turn away from nearest edge
                vel.x = Math.abs(vel.x * PC_BOUNCE_MULT) * (pos.x < surface.pos.x + surface.width / 2 ? 1 : -1);
            } else {
                // turn around
                vel.x = -vel.x * PC_BOUNCE_MULT;
            }
        } else {
            surface = null;
            fallState = FallState.COYOTE_TIME;
            coyoteCounter = PC_COYOTE_TIME;
        }
    }

    public void fall() {
        if (steerState.equals(SteerState.NEITHER) ||
                vel.x < 0 && steerState.equals(SteerState.RIGHT)
                || vel.x > 0 && steerState.equals(SteerState.LEFT)) {
            fall(false);
        } else {
            fall(true);
        }
    }

    /* Drop through the current platform */
    public void drop() {
        if (fallState.equals(FallState.ON_SURFACE)) {
            fallState = FallState.DROPPING;
        }
    }

    public void steer(SteerState direction) {
        if (!direction.equals(steerState)) {
            moveState = MoveState.ACCELERATING;
            this.steerState = direction;
        }
        if (!direction.equals(SteerState.NEITHER))
            steerSinceLand = true;
    }

    public void reset(float x, float y) {
        this.pos.x = x;
        this.pos.y = y;
        this.vel.x = 0;
        this.vel.y = 0;
        this.steer(SteerState.NEITHER);
        moveState = MoveState.AT_REST;
        this.steerSinceLand = true;
        this.fall(true);
    }

    protected static PShape[][] generateTokens(DontDrown sketch) {
        staticTokens = new PShape[StressAndTokenState.ABS_MAX_STRESS + 1][VARIANT_TOKENS];
        diameter = sketch.width / PC_DIAMETER_DIV;
        radius = diameter / 2f;

        sketch.colorModeHSB();
        sketch.roughStrokeWeight = sketch.RSW_DEF;
        StressAndTokenState state = sketch.levelState;

        for (int i = 0; i <= StressAndTokenState.ABS_MAX_STRESS; i++) {
            state.stress = i;
            state.sketchiness();
            state.calcStressHSBColour();

            int fillColour = sketch.color(state.stressHSBColour[0], state.stressHSBColour[1], state.stressHSBColour[2],
                    PC_FILL_ALPHA);
            int strokeColour = sketch.color(state.stressHSBColour[0], state.stressHSBColour[1],
                    state.stressHSBColour[2] - PC_MIN_LIGHT / 2, PC_STROKE_ALPHA);
            for (int j = 0; j < VARIANT_TOKENS; j++) {
                staticTokens[i][j] = sketch.handDraw(PConstants.ELLIPSE, strokeColour, fillColour, 20, 0, 0, diameter,
                        diameter);
            }
        }

        return staticTokens;
    }

    protected boolean onScreen() {
        return true;
    }

    public void render() {
        renderAD();
    }
}
