package org.pepsoft.worldpainter.util;

import javax.swing.*;
import java.awt.*;

import static javax.swing.SwingConstants.HORIZONTAL;
import static javax.swing.SwingConstants.VERTICAL;

/**
 * A {@link JLabel} which can draw its text rotated a quarter turn, for labelling narrow columns.
 *
 * <p>Replaces {@code com.jidesoft.swing.JideLabel}, which was the only remaining reason to install the JIDE look and
 * feel extension. That extension cannot be installed on Java 9 or later, because it probes for
 * {@code com.sun.java.swing.plaf.windows.WindowsLookAndFeel}, which module encapsulation has made inaccessible.
 *
 * <p>The orientation and rotation direction have the same meaning as they did on {@code JideLabel}: an orientation of
 * {@link SwingConstants#VERTICAL} rotates the text, and {@code clockwise} chooses the direction, with
 * {@code false} (the default) making the text read from bottom to top.
 */
public class VerticalLabel extends JLabel {
    public VerticalLabel() {
        // Do nothing
    }

    public VerticalLabel(String text) {
        super(text);
    }

    public int getOrientation() {
        return orientation;
    }

    /**
     * @param orientation {@link SwingConstants#VERTICAL} to rotate the text, {@link SwingConstants#HORIZONTAL} to draw
     *                    it normally.
     */
    public void setOrientation(int orientation) {
        if ((orientation != VERTICAL) && (orientation != HORIZONTAL)) {
            throw new IllegalArgumentException("orientation " + orientation);
        }
        if (orientation != this.orientation) {
            this.orientation = orientation;
            revalidate();
            repaint();
        }
    }

    public boolean isClockwise() {
        return clockwise;
    }

    /**
     * @param clockwise {@code true} to have the text read from top to bottom, {@code false} (the default) for bottom to
     *                  top. Only has an effect when the orientation is {@link SwingConstants#VERTICAL}.
     */
    public void setClockwise(boolean clockwise) {
        if (clockwise != this.clockwise) {
            this.clockwise = clockwise;
            repaint();
        }
    }

    // JLabel

    @Override
    public Dimension getPreferredSize() {
        return rotate(super.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
        return rotate(super.getMinimumSize());
    }

    @Override
    public Dimension getMaximumSize() {
        return rotate(super.getMaximumSize());
    }

    /**
     * While the label is painting itself rotated, the UI delegate has to see the width and height the other way round,
     * because it lays the text out in the rotated coordinate system.
     */
    @Override
    public int getWidth() {
        return (painting && (orientation == VERTICAL)) ? super.getHeight() : super.getWidth();
    }

    @Override
    public int getHeight() {
        return (painting && (orientation == VERTICAL)) ? super.getWidth() : super.getHeight();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (orientation != VERTICAL) {
            super.paintComponent(g);
            return;
        }
        final Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (clockwise) {
                g2.translate(super.getWidth(), 0);
                g2.rotate(Math.PI / 2);
            } else {
                g2.translate(0, super.getHeight());
                g2.rotate(-Math.PI / 2);
            }
            painting = true;
            try {
                super.paintComponent(g2);
            } finally {
                painting = false;
            }
        } finally {
            g2.dispose();
        }
    }

    private Dimension rotate(Dimension size) {
        return (orientation == VERTICAL) ? new Dimension(size.height, size.width) : size;
    }

    private int orientation = VERTICAL;
    private boolean clockwise;
    private transient boolean painting;

    private static final long serialVersionUID = 1L;
}
