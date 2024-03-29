import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PShape;
import processing.core.PVector;

/**
 * Handles "hand-drawing" lines and some simple shapes.  
 */
public abstract class Sketcher extends PApplet {

    public static final float RSW_DEF_DIV = 500f;
    public static final float RSV_MIN = 0.15f;
    public static final float RSV_MAX = 0.6f;
    public static final int RSS_MIN = 1;
    public static final int RSS_MAX = 5;
    public static final int WAVE = 69;

    public float RSW_DEF; // default rough stroke weight 
    public float roughStrokeWeight; // the average weight of hand drawn lines
    public float roughStrokeVariabilityRate = RSV_MIN; // the max deviation from a smooth line
    public int roughStrokeShakiness = RSS_MIN; // the rate at which the rough line deviates

    /* Generates a sin wave as a set of points, for use in Wave token generation */
    private PVector[] sinWave(float waveWidth, int nSections, float sectionDepth, int verticesPerSection,
            int startOffset) {
        PVector[] vertices = new PVector[nSections * verticesPerSection];
        float xIncr = waveWidth / (vertices.length - 1);
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new PVector(i * xIncr,
                    sectionDepth
                            * sin(TAU * (((i + startOffset) % (2f * verticesPerSection)) / (2f * verticesPerSection))));
        }
        return vertices;
    }

    /* Draws a line that evenly thickens/thins from one end to the other  */
    private PShape drawDualWeightedLine(PVector start, PVector end, float startWeight, float endWeight) {
        float heading = (start.copy().sub(end)).heading();
        heading += HALF_PI;
        PVector startPadding = PVector.fromAngle(heading).mult(startWeight);
        PVector endPadding = PVector.fromAngle(heading).mult(endWeight);

        PVector topLeft = start.copy().sub(startPadding);
        PVector topRight = end.copy().sub(endPadding);
        PVector bottomRight = end.copy();
        PVector bottomLeft = start.copy();

        return createShape(PConstants.QUAD,
                topLeft.x, topLeft.y,
                topRight.x, topRight.y,
                bottomRight.x, bottomRight.y,
                bottomLeft.x, bottomLeft.y);
    }

    /* Wrapper function that sets the line colour */
    public PShape handDrawLine(int strokeColour, PVector start, PVector end) {
        PShape line = handDrawLine(start, end);
        line.setFill(strokeColour);
        return line;
    }

    /*
     * Essentially creates a jagged quasi-quadrilteral to act as a hand-drawn line
     */
    private PShape handDrawLine(PVector start, PVector end) {
        PVector smoothLine = start.copy().sub(end);
        float smoothLineLength = smoothLine.mag();
        float heading = smoothLine.heading();
        heading += HALF_PI;
        PVector padding = PVector.fromAngle(heading).mult(roughStrokeWeight);
        float roughStrokeVariability = roughStrokeWeight * roughStrokeVariabilityRate;

        // topLeft is not necessarily the top left corner, but it's easier to keep track
        // of than just numbering the corners
        PVector topLeft = start.copy().sub(padding);
        PVector topRight = end.copy().sub(padding);
        PVector bottomRight = topRight.copy().add(padding);
        PVector bottomLeft = topLeft.copy().add(padding);

        PShape roughLine = createShape();
        roughLine.beginShape();
        roughLine.vertex(topLeft.x, topLeft.y); // top left corner

        int sections = (int) random(0, roughStrokeShakiness);
        PVector section = topLeft.copy();
        PVector direction = (topRight.copy().sub(topLeft)).normalize();
        for (int i = 0; i < sections; i++) {
            section = section.add(direction.copy().mult(random(0, smoothLineLength / sections)));
            roughLine.vertex(section.x + random(-roughStrokeVariability, roughStrokeVariability),
                    section.y + random(-roughStrokeVariability, roughStrokeVariability));
        }

        roughLine.vertex(topRight.x, topRight.y);
        roughLine.vertex(bottomRight.x, bottomRight.y);

        sections = (int) random(0, roughStrokeShakiness);
        section = bottomRight.copy();
        direction = (bottomLeft.copy().sub(bottomRight)).normalize();
        for (int i = 0; i < sections; i++) {
            section = section.add(direction.copy().mult(random(0, smoothLineLength / sections)));
            roughLine.vertex(section.x + random(-roughStrokeVariability, roughStrokeVariability),
                    section.y + random(-roughStrokeVariability, roughStrokeVariability));
        }

        roughLine.vertex(bottomLeft.x, bottomLeft.y);
        roughLine.endShape(CLOSE);
        return roughLine;
    }

    /* black stroke and empty fill */
    public PShape handDraw(int type, float... params) {
        return handDraw(type, 0xFF000000, 0xFFFFFFFF, params);
    }

    /**
     * Hand draws a primitive shape. 
     * @param type PConstants.QUAD, PConstants.RECT, PConstants.ELLIPSE, or Sketcher.WAVE 
     * @param strokeColour 
     * @param fillColour
     * @param params for a QUAD: x1, y1, x2, y2, x3, y3, x4, y4;
     *               for a RECT: x, y, width, height;
     *               for an ELLIPSE: vertices, x, y, width, height;
     *               for a WAVE: width, height, nSections, sectionDepth, verticesPerSection, startOffset;
     * @return
     */
    public PShape handDraw(int type, int strokeColour, int fillColour, float... params) {
        switch (type) {
            case PConstants.QUAD:
                // params : x1, y1, x2, y2, x3, y3, x4, y4
                if (params.length != 8) {
                    throw new IndexOutOfBoundsException(
                            "handDraw(QUAD) requires 8 floats in params, got " + params.length);
                } else {
                    PShape quad = createShape(GROUP);

                    // add the fill as a normal shape
                    PShape fill = createShape(type, params);
                    fill.setFill(fillColour);
                    quad.addChild(fill);

                    PShape line = handDrawLine(new PVector(params[0], params[1]), new PVector(params[2], params[3]));
                    line.setFill(strokeColour);
                    quad.addChild(line);

                    line = handDrawLine(new PVector(params[2], params[3]), new PVector(params[4], params[5]));
                    line.setFill(strokeColour);
                    quad.addChild(line);

                    line = handDrawLine(new PVector(params[4], params[5]), new PVector(params[6], params[7]));
                    line.setFill(strokeColour);
                    quad.addChild(line);

                    line = handDrawLine(new PVector(params[6], params[7]), new PVector(params[0], params[1]));
                    line.setFill(strokeColour);
                    quad.addChild(line);

                    return quad;
                }
            case PConstants.RECT:
                // params : x, y, width, height
                if (params.length != 4) {
                    throw new IndexOutOfBoundsException(
                            "handDraw(RECT) requires 4 floats in params, got " + params.length);
                } else {
                    return handDraw(PConstants.QUAD, strokeColour, fillColour,
                            params[0], params[1],
                            params[0] + params[2], params[1],
                            params[0] + params[2], params[1] + params[3],
                            params[0], params[1] + params[3]);
                }
            case PConstants.ELLIPSE:
                // params : vertices, x, y, width, height
                if (params.length != 5) {
                    throw new IndexOutOfBoundsException(
                            "handDraw(ELLIPSE) requires 5 floats in params, got " + params.length);
                } else {
                    int vertices = (int) params[0];
                    float centreX = params[1];
                    float centreY = params[2];
                    float width = params[3] / 2;
                    float height = params[4] / 2;

                    PShape ellipse = createShape(GROUP);

                    // add the fill as a normal shape
                    PShape fill = createShape(type, centreX, centreY, width * 2, height * 2);
                    fill.setFill(fillColour);
                    ellipse.addChild(fill);

                    // generate the lines around the circumference of the ellipse
                    PVector start = new PVector(centreX + width * cos(0), centreY + height * sin(0));
                    PVector end = new PVector();
                    float startWeight = roughStrokeWeight;
                    float roughStrokeVariability = roughStrokeWeight * roughStrokeVariabilityRate;
                    float endWeight = roughStrokeWeight + random(-roughStrokeVariability, roughStrokeVariability);

                    for (int i = 1; i <= vertices; i++) {
                        float angle = i * TAU / vertices;
                        end.x = (centreX + width * cos(angle));
                        end.y = (centreY + height * sin(angle));

                        PShape line = drawDualWeightedLine(start, end, startWeight, endWeight);
                        line.setFill(strokeColour);
                        ellipse.addChild(line);

                        start.x = end.x;
                        start.y = end.y;
                        startWeight = endWeight;
                        endWeight = roughStrokeWeight + random(-roughStrokeVariability, roughStrokeVariability);
                    }

                    return ellipse;
                }
            case WAVE:
                // width, height, nSections, sectionDepth, verticesPerSection, startOffset

                PShape wave = createShape(GROUP);
                PVector[] vertices = sinWave(params[0], (int) params[2], params[3], (int) params[4], (int) params[5]);

                // add the fill as a normal shape
                PShape fill = createShape();
                fill.beginShape();
                for (PVector vertex : vertices) {
                    fill.vertex(vertex.x, vertex.y);
                }
                fill.vertex(params[0], params[1]);
                fill.vertex(0, params[1]);
                fill.fill(fillColour);
                fill.endShape(CLOSE);
                wave.addChild(fill);

                // generate the weighted line at the top of the wave
                float startWeight = roughStrokeWeight;
                float roughStrokeVariability = roughStrokeWeight * roughStrokeVariabilityRate;
                float endWeight = roughStrokeWeight + random(-roughStrokeVariability, roughStrokeVariability);
                for (int i = 0; i < vertices.length - 1; i++) {
                    PShape line = drawDualWeightedLine(vertices[i], vertices[i + 1], startWeight, endWeight);
                    line.setFill(strokeColour);
                    wave.addChild(line);

                    startWeight = endWeight;
                    endWeight = roughStrokeWeight + random(-roughStrokeVariability, roughStrokeVariability);
                }

                return wave;
            default:
                System.err.println("handDraw() only works with QUAD, RECT, ELLIPSE and WAVE objects");
                return createShape(type, params);
        }
    }
}
