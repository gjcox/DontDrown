import java.util.ArrayList;
import java.util.Comparator;

import processing.core.PVector;

public class CollisionDetector {

    private final DontDrown sketch;

    public CollisionDetector(DontDrown sketch) {
        this.sketch = sketch;
    }

    private float getXAtYOverlap(PlayerCharacter pc, PVector dir, float otherY) {
        if (dir.y != 0) {
            if (dir.x < 0) {
                return Math.min(pc.pos.x,
                        pc.oldPos.x + (dir.x * ((otherY - (pc.oldPos.y + pc.diameter / 2)) / dir.y)));
            } else {
                return Math.max(pc.pos.x,
                        pc.oldPos.x + (dir.x * ((otherY - (pc.oldPos.y + pc.diameter / 2)) / dir.y)));
            }
        } else {
            return -1;
            // ignore x
        }
    }

    private void detectPlatformCollisions(PlayerCharacter pc, PVector dir) {
        if (pc.fallState.equals(PlayerCharacter.FallState.FALLING)) {
            ArrayList<Platform> sortedPlatforms = new ArrayList<>(sketch.level.platforms);
            sortedPlatforms.sort(new Comparator<Platform>() {

                @Override
                public int compare(Platform o1, Platform o2) {
                    return Math.round(o2.pos.y - o1.pos.y);
                }

            });

            for (Platform platform : sortedPlatforms) {
                if (platform.pos.y > sketch.height && platform.pos.y < platform.height) {
                    // platform not on screen
                    // continue search
                } else if (platform.pos.y < pc.oldPos.y) {
                    // platform too high
                    // cut off search
                    break;
                } else if (platform.pos.y > (pc.pos.y + pc.radius)) {
                    // platform too low
                    // continue search
                } else {
                    // platform vertically between oldPos and pos + pc.radius
                    // check horizontal overlap to confirm collision
                    float xAtYOverlap = getXAtYOverlap(pc, dir, platform.pos.y);
                    if (xAtYOverlap >= platform.pos.x && xAtYOverlap <= platform.pos.x + platform.width
                            || pc.pos.x >= platform.pos.x && pc.pos.x <= platform.pos.x + platform.width) {
                        pc.land(platform);
                        break;
                    }
                }

            }
        } else if (pc.fallState.equals(PlayerCharacter.FallState.ON_SURFACE)
                && (pc.pos.x < pc.surface.pos.x
                        || pc.pos.x > pc.surface.pos.x + pc.surface.width)) {
            pc.fall();
        }
    }

    /* See report for a diagram of what this is doing */
    private boolean basicRayTracing(PlayerCharacter pc, Token token) {
        Line[] tokenLines = new Line[4];
        PVector top = new PVector(token.pos.x, token.pos.y - token.height / 2);
        PVector right = new PVector(token.pos.x + token.width / 2, token.pos.y);
        PVector bottom = new PVector(token.pos.x, token.pos.y + token.height / 2);
        PVector left = new PVector(token.pos.x - token.width / 2, token.pos.y);
        tokenLines[0] = new Line(top, right);
        tokenLines[1] = new Line(right, bottom);
        tokenLines[2] = new Line(bottom, left);
        tokenLines[3] = new Line(left, top);

        Line[] pcLines = new Line[12];
        pcLines[0] = new Line(new PVector(pc.oldPos.x, pc.oldPos.y - pc.radius),
                new PVector(pc.pos.x, pc.pos.y - pc.radius)); // top to top
        pcLines[1] = new Line(new PVector(pc.oldPos.x + pc.radius, pc.oldPos.y),
                new PVector(pc.pos.x + pc.radius, pc.pos.y)); // right to right
        pcLines[2] = new Line(new PVector(pc.oldPos.x, pc.oldPos.y + pc.radius),
                new PVector(pc.pos.x, pc.pos.y + pc.radius)); // bottom to bottom
        pcLines[3] = new Line(new PVector(pc.oldPos.x - pc.radius, pc.oldPos.y),
                new PVector(pc.pos.x - pc.radius, pc.pos.y)); // left to left

        top = new PVector(pc.pos.x, pc.pos.y - pc.radius);
        right = new PVector(pc.pos.x + pc.radius, pc.pos.y);
        bottom = new PVector(pc.pos.x, pc.pos.y + pc.radius);
        left = new PVector(pc.pos.x - pc.radius, pc.pos.y);
        pcLines[4] = new Line(top, right);
        pcLines[5] = new Line(right, bottom);
        pcLines[6] = new Line(bottom, left);
        pcLines[7] = new Line(left, top);

        top = new PVector(pc.oldPos.x, pc.oldPos.y - pc.radius);
        right = new PVector(pc.oldPos.x + pc.radius, pc.oldPos.y);
        bottom = new PVector(pc.oldPos.x, pc.oldPos.y + pc.radius);
        left = new PVector(pc.oldPos.x - pc.radius, pc.oldPos.y);
        pcLines[8] = new Line(top, right);
        pcLines[9] = new Line(right, bottom);
        pcLines[10] = new Line(bottom, left);
        pcLines[11] = new Line(left, top);

        for (Line tLine : tokenLines) {
            for (Line pcLine : pcLines) {
                if (Line.overlap(tLine, pcLine)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void detectTokenCollisions(PlayerCharacter pc, PVector dir) {
        ArrayList<Token> sortedTokens = new ArrayList<>(sketch.level.tokens);
        sortedTokens.sort(new Comparator<Token>() {

            @Override
            public int compare(Token o1, Token o2) {
                return Math.round(o2.pos.y - o1.pos.y);
            }

        });

        for (Token token : sortedTokens) {
            if (token.pos.y - token.height / 2 > sketch.height && token.pos.y < token.height / 2) {
                // token not on screen
                // continue search
            } else if (token.pos.y + token.height / 2 < Math.min(pc.oldPos.y, pc.pos.y) - pc.radius) {
                // token too high
                // cut off search
                break;
            } else if (token.pos.y - token.height / 2 > Math.max(pc.oldPos.y, pc.pos.y) + pc.radius) {
                // token too low
                // continue search
            } else {
                // token vertically between oldPos and pos + pc.radius
                // check horizontal overlap to confirm collision
                if (dir.y == 0) {
                    // pc was moving horizontally
                    // check if token between start and end of movement
                    float left = Math.min(pc.pos.x, pc.oldPos.x) - pc.radius;
                    float right = Math.max(pc.pos.x, pc.oldPos.x) + pc.radius;
                    if (token.pos.x + token.width / 2 >= left && token.pos.x - token.width / 2 <= right) {
                        sketch.levelState.collectToken(token);
                        break;
                    }
                } else if (dir.x == 0) {
                    // pc was moving vertically
                    // check if token horizontally overlaps with pc('s vertical path)
                    if (token.pos.x + token.width / 2 >= pc.pos.x - pc.radius
                            && token.pos.x - token.width / 2 <= pc.pos.x + pc.radius) {
                        sketch.levelState.collectToken(token);
                        break;
                    }
                } else {
                    // pc was moving diagonally
                    // check line equations for overlap
                    if (basicRayTracing(pc, token)) {
                        sketch.levelState.collectToken(token);
                        break;
                    }
                }  
            }
        }
    }

    public void detectCollisions() {
        PlayerCharacter pc = sketch.pc;
        PVector dir = (pc.pos.copy().sub(pc.oldPos)).normalize(); // i.e. pc.vel from last frame + panning
        detectPlatformCollisions(pc, dir);
        detectTokenCollisions(pc, dir);
    }

}
