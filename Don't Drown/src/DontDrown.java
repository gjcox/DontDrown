import processing.core.PApplet;
import processing.event.MouseEvent;

public class DontDrown extends Sketcher {

    private static final float LOADING_TEXT_DIV = 5f;
    private static final int SCROLL_DIV = 20;
    private static final int EXTENSION_TIME_MULT = 3; // the number of jumps for which the extension lasts
    private static final int REPERCUSSION_TIME_MULT = 3; // the number of jumps for which the repercussion lasts

    public static final String FONT_PATH = "sf-grunge-sans.bold.ttf";
    public static final char BULLET_POINT = 149;

    public enum GameState {
        PRE_STARTUP,
        STARTUP,
        MID_LEVEL,
        IN_MENU,
        ;
    }

    public GameState gameState = GameState.PRE_STARTUP;
    public boolean arcadeMode = false;
    public GameMenu gameMenu;
    public MusicPlayer musicPlayer;
    public boolean playingMusic = true;    
    public StressAndTokenState levelState;
    public PlayerCharacter pc;
    public Wave risingWave;
    public Wave staticWave;
    public DebugOverlay debugOverlay;
    public ScoreOverlay scoreOverlay;
    public Level[][] levels;
    public Level level;
    public CollisionDetector collisionDetector;
    public long levelStartTimeMillis;
    public int extensionFrames; // the number of frames per extensions
    public int repercussionFrames; // the number of frames per repercussion
    public float repercussionMult; // the wave speed multiplier for repercussions 
    public int endOfExtension = -1; // a value of frameCount
    public int endOfRepercussion = -1; // a value of frameCount 
    public boolean extensionUsed = false;

    public boolean debugging = false; // toggles debug overlay and cheat commands 
    public boolean staticStress = false; // prevents stress-based calculations; used for debugging 

    private int scrollIncr;  // the rate at which menus scroll 

    public void colorModeHSB() {
        colorMode(HSB, 360f, 1f, 1f, 1f);
    }

    public void colorModeRGB() {
        colorMode(ARGB, 255, 255, 255, 255);
    }

    @Override
    public void settings() {
        float displayRatio = 0.9f;
        size((int) (displayWidth * displayRatio), (int) (displayWidth * displayRatio * 0.5625));
        this.RSW_DEF = width / RSW_DEF_DIV;
        this.scrollIncr = height / SCROLL_DIV;
    }

    public void generateLevels() {
        levels = new Level[Debuff.values().length][];
        int deb = 0;
        for (Debuff debuff : Debuff.values()) {
            levels[deb] = new Level[Difficulty.values().length];

            int dif = 0;
            for (Difficulty difficulty : Difficulty.values()) {
                levels[deb][dif++] = new Level(this, debuff, difficulty);
            }

            deb++;
        }
        gameMenu.updateLevelSelector();
    }

    public void startLevel(Level levelToStart) {
        if (levelToStart == null) {
            level = new Level(this, Debuff.random(), Difficulty.random()); // arcade mode/debugging levels 
        } else {
            level = levelToStart;
        }

        // reset values 
        extensionUsed = false;
        endOfExtension = -1;
        endOfRepercussion = -1;
        levelState.reset(level);
        collisionDetector.sortLists();
        risingWave.pos.y = Wave.waveInitHeight;
        Platform ground = level.platforms.get(0);
        pc.reset(ground.pos.x + ground.width / 2, ground.pos.y - PlayerCharacter.diameter);
        collisionDetector.pcOldPos = pc.pos.copy();
        gameState = DontDrown.GameState.MID_LEVEL;
        gameMenu.midLevel = true;
        levelStartTimeMillis = System.currentTimeMillis();
    }

    public void endLevel(boolean completed) {
        if (arcadeMode) {
            if (completed) {
                startLevel(null);
            } else {
                startLevel(level);
            }
        } else {
            if (completed) {
                gameState = GameState.IN_MENU;
                gameMenu.setMenuState(GameMenu.MenuState.LEVEL_SELECTION);
                gameMenu.midLevel = false;
                float secondsLeft = (level.waveTime * (60 / frameRate))
                        - (System.currentTimeMillis() - levelStartTimeMillis) / 1000f;

                if (level.highScore < levelState.tokensCollected
                        || level.highScore == levelState.tokensCollected && secondsLeft > level.timeLeft) {
                    level.highScore = levelState.tokensCollected;
                    level.timeLeft = secondsLeft;
                    gameMenu.updateLevelSelector();
                }

                levelState.reset();
            } else {
                // restart level upon death 
                startLevel(level);
            }
        }

    }

    @Override
    public void draw() {
        switch (gameState) {
            case PRE_STARTUP:
                textAlign(CENTER, CENTER);
                textSize(height / LOADING_TEXT_DIV);
                fill(0xFF000000);
                text("Loading...", width / 2f, height / 2f);

                gameState = GameState.STARTUP;
                break;
            case STARTUP:
                noStroke();
                musicPlayer = new MusicPlayer(this);
                levelState = new StressAndTokenState(this);
                pc = new PlayerCharacter(this);
                extensionFrames = pc.jumpFrames * EXTENSION_TIME_MULT;
                repercussionFrames = pc.jumpFrames * REPERCUSSION_TIME_MULT;
                repercussionMult = 1 + (extensionFrames / (float) repercussionFrames);
                risingWave = new Wave(this);
                staticWave = new Wave(this);
                levelState.pcCalcs();
                debugOverlay = new DebugOverlay(this);
                scoreOverlay = new ScoreOverlay(this);
                collisionDetector = new CollisionDetector(this);
                gameMenu = new GameMenu(this);
                generateLevels();

                levelState.stress = 0;
                levelState.sketchiness();
                gameState = GameState.IN_MENU;
                break;
            case IN_MENU:
                cursor();
                if (playingMusic)
                    musicPlayer.playMusic();

                gameMenu.render();

                if (debugging)
                    debugOverlay.render();
                break;
            case MID_LEVEL:
                noCursor();
                if (playingMusic)
                    musicPlayer.playMusic();

                // update positions
                levelState.update();
                pc.integrate();
                level.integrate();
                integrateWave();

                // check if panning needed
                if (pc.pos.y < scoreOverlay.endOfPadding + 2 * pc.jumpHeight) {
                    level.panningState = Level.PanningState.UP;
                } else if (pc.pos.y > height - (scoreOverlay.endOfPadding + pc.jumpHeight)) {
                    level.panningState = Level.PanningState.DOWN;
                } else {
                    level.panningState = Level.PanningState.NEITHER;
                }

                // detect collisions
                collisionDetector.detectCollisions();

                // draw
                level.render();
                pc.render();
                risingWave.render();

                if (levelState.debuff.equals(Debuff.TUNNEL_VISION)) {
                    fill(0xFF000000);
                    rect(0f, 0f, width, pc.pos.y - pc.jumpHeight * 1.2f);
                    rect(0f, pc.pos.y + pc.jumpHeight, width, height);
                }

                scoreOverlay.render();
                if (debugging)
                    debugOverlay.render();
                break;
        }
    }

    private void integrateWave() {
        if (frameCount <= endOfExtension) {
            // don't change the wave
        } else if (frameCount <= endOfRepercussion) {
            risingWave.pos.y -= level.waveRiseRate * repercussionMult;
        } else {
            risingWave.pos.y -= level.waveRiseRate;
        }
    }

    @Override
    public void keyPressed() {
        if (key == 'D') {
            debugging = !debugging;
            return;
        } else if (key == ESC) {
            key = 'p';
        }

        switch (gameState) {
            case PRE_STARTUP:
            case STARTUP:
                // ignore inputs
                break;
            case IN_MENU:
                // unpause
                if (gameMenu.midLevel && (key == 'p' || key == 'P')) {
                    gameState = DontDrown.GameState.MID_LEVEL;
                }
                break;
            case MID_LEVEL:
                if (key == CODED) {
                    switch (keyCode) {
                        case LEFT:
                            pc.steer(PlayerCharacter.SteerState.LEFT);
                            break;
                        case RIGHT:
                            pc.steer(PlayerCharacter.SteerState.RIGHT);
                            break;
                        case UP:
                            pc.jump();
                            break;
                        case DOWN:
                            pc.drop();
                            break;
                        default:
                            // do nothing
                    }
                } else if (key == 'p' || key == 'P') {
                    gameMenu.midLevel = true;
                    gameMenu.setMenuState(GameMenu.MenuState.PAUSE_MENU);
                    gameState = GameState.IN_MENU;
                } else if (key == ' ' && !extensionUsed) {
                    endOfExtension = frameCount + extensionFrames;
                    endOfRepercussion = endOfExtension + repercussionFrames;
                    extensionUsed = false;
                } else if (debugging) {
                    switch (key) {
                        case '`':
                            levelState.stress = 100;
                            break;
                        case 'f':
                        case 'F':
                            if (frameRate > 40) {
                                frameRate(30);
                            } else if (frameRate > 20) {
                                frameRate(10);
                            } else {
                                frameRate(60);
                            }
                            break;
                        case ',':
                            level.panningState = Level.PanningState.DOWN;
                            break;
                        case '.':
                            level.panningState = Level.PanningState.NEITHER;
                            break;
                        case '/':
                            level.panningState = Level.PanningState.UP;
                            break;
                        case '+':
                            levelState.stress++;
                            break;
                        case '-':
                            levelState.stress--;
                            break;
                        case 'w':
                        case 'W':
                            if (level.waveRiseRate == level.defaultWaveRiseRate) {
                                level.waveRiseRate = 0;
                            } else {
                                level.waveRiseRate = level.defaultWaveRiseRate;
                            }
                            break;
                        case 's':
                        case 'S':
                            staticStress = !staticStress;
                            break;
                        default:
                            if (Character.isDigit(key)) {
                                levelState.stress = Integer.parseInt("" + key) * 10f;
                            } else {
                                // do nothing
                            }
                    }
                }
                break;
        }
    }

    @Override
    public void keyReleased() {
        if (key == CODED) {
            switch (keyCode) {
                case LEFT:
                    if (pc.getSteerState().equals(PlayerCharacter.SteerState.LEFT)) {
                        pc.steer(PlayerCharacter.SteerState.NEITHER);
                    }
                    break;
                case RIGHT:
                    if (pc.getSteerState().equals(PlayerCharacter.SteerState.RIGHT)) {
                        pc.steer(PlayerCharacter.SteerState.NEITHER);
                    }
                    break;
                default:
                    // do nothing
            }
        }
    }

    @Override
    public void mouseClicked() {
        if (gameState.equals(GameState.IN_MENU)) {
            gameMenu.resolveClick();
        }
    }

    @Override
    public void mouseWheel(MouseEvent event) {
        if (gameState.equals(GameState.IN_MENU)) {
            gameMenu.scrollWrapper(-event.getCount() * scrollIncr);
        }
    }

    public static void main(String[] args) {
        String[] processingArgs = { "DontDrown" };
        DontDrown sketch = new DontDrown();
        PApplet.runSketch(processingArgs, sketch);
    }

}
