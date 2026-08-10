//  ------------------------------------------------------------------
//
//  Copyright (c) 2006-2007 James Foulds and the University of Waikato
//
//  ------------------------------------------------------------------
//  This file is part of Tuatara Turing Machine Simulator.
//
//  Tuatara Turing Machine Simulator is free software: you can redistribute
//  it and/or modify it under the terms of the GNU General Public License as
//  published by the Free Software Foundation, either version 3 of the License,
//  or (at your option) any later version.
//
//  Tuatara Turing Machine Simulator is distributed in the hope that it will be
//  useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with Tuatara Turing Machine Simulator.  If not, see
//  <http://www.gnu.org/licenses/>.
//
//  author email: jf47 (at) waikato (dot) ac (dot) nz
//
//  ------------------------------------------------------------------

package tuataraTMSim;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/**
 * A factory for the program's iconography. Icons are stroked vector paths rather than bitmaps, so
 * that they stay crisp at any size and can be recoloured to match the current palette.
 *
 * All paths are authored on a nominal 24x24 grid with a 2 unit stroke; {@link VectorIcon} scales
 * that grid to the requested size, which scales the stroke with it.
 */
public final class Icons
{
    // Undocumented intentionally. This class should not be instantiated.
    private Icons() { }

    /**
     * The nominal grid the paths are authored on.
     */
    private static final double GRID = 24.0;

    /**
     * Get an icon which follows the current palette's primary text colour.
     * @param name The name of the icon.
     * @param size The width and height of the icon, in pixels.
     * @return The requested icon.
     */
    public static Icon get(String name, int size)
    {
        return new VectorIcon(name, size, null);
    }

    /**
     * Get an icon in a fixed colour.
     * @param name The name of the icon.
     * @param size The width and height of the icon, in pixels.
     * @param color The colour to draw the icon in, or null to follow the palette.
     * @return The requested icon.
     */
    public static Icon get(String name, int size, Color color)
    {
        return new VectorIcon(name, size, color);
    }

    /**
     * Get one of the large badged icons used by JOptionPane, drawn as a filled disc in the relevant
     * status colour with a glyph knocked out of it.
     * @param name One of "info", "warning", "error", "question".
     * @param size The width and height of the icon, in pixels.
     * @return The requested icon.
     */
    public static Icon dialog(String name, int size)
    {
        return new DialogIcon(name, size);
    }

    /**
     * An icon which renders a stroked vector path.
     */
    private static class VectorIcon implements Icon
    {
        /**
         * Creates a new instance of VectorIcon.
         * @param name The name of the icon.
         * @param size The width and height of the icon, in pixels.
         * @param color The colour to draw in, or null to follow the current palette.
         */
        VectorIcon(String name, int size, Color color)
        {
            m_name = name;
            m_size = size;
            m_color = color;
        }

        /**
         * Get the icon width.
         * @return The icon width, in pixels.
         */
        public int getIconWidth()
        {
            return m_size;
        }

        /**
         * Get the icon height.
         * @return The icon height, in pixels.
         */
        public int getIconHeight()
        {
            return m_size;
        }

        /**
         * Render the icon.
         * @param c The component being rendered onto.
         * @param g The graphics object to render onto.
         * @param x The X ordinate to render at.
         * @param y The Y ordinate to render at.
         */
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2d = Theme.prepare(g.create());
            Color col = m_color != null? m_color : Theme.palette().text;
            if (c != null && !c.isEnabled())
            {
                col = Theme.alpha(col, 90);
            }
            g2d.translate(x, y);
            g2d.scale(m_size / GRID, m_size / GRID);
            g2d.setColor(col);
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            paint(g2d, m_name, col);
            g2d.dispose();
        }

        /**
         * The name of the icon.
         */
        private final String m_name;

        /**
         * The width and height of the icon, in pixels.
         */
        private final int m_size;

        /**
         * The fixed colour, or null to follow the current palette.
         */
        private final Color m_color;
    }

    /**
     * A large badged icon for message dialogs.
     */
    private static class DialogIcon implements Icon
    {
        /**
         * Creates a new instance of DialogIcon.
         * @param name The name of the badge.
         * @param size The width and height of the icon, in pixels.
         */
        DialogIcon(String name, int size)
        {
            m_name = name;
            m_size = size;
        }

        /**
         * Get the icon width.
         * @return The icon width, in pixels.
         */
        public int getIconWidth()
        {
            return m_size;
        }

        /**
         * Get the icon height.
         * @return The icon height, in pixels.
         */
        public int getIconHeight()
        {
            return m_size;
        }

        /**
         * Render the icon.
         * @param c The component being rendered onto.
         * @param g The graphics object to render onto.
         * @param x The X ordinate to render at.
         * @param y The Y ordinate to render at.
         */
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Theme.Palette p = Theme.palette();
            Color tint = m_name.equals("error")?   p.danger
                       : m_name.equals("warning")? p.warning
                       : m_name.equals("question")? p.accent
                       :                            p.accent;
            String glyph = m_name.equals("error")?   "!"
                         : m_name.equals("warning")? "!"
                         : m_name.equals("question")? "?"
                         :                            "i";

            Graphics2D g2d = Theme.prepare(g.create());
            g2d.setColor(Theme.alpha(tint, 38));
            g2d.fill(new Ellipse2D.Float(x, y, m_size, m_size));
            g2d.setColor(tint);
            g2d.setStroke(new BasicStroke(m_size / 16f));
            g2d.draw(new Ellipse2D.Float(x + 1, y + 1, m_size - 2, m_size - 2));
            g2d.setFont(Theme.ui(Font.BOLD, (int)(m_size * 0.55)));
            Theme.drawCentered(g2d, glyph, x + m_size / 2.0, y + m_size / 2.0);
            g2d.dispose();
        }

        /**
         * The name of the badge.
         */
        private final String m_name;

        /**
         * The width and height of the icon, in pixels.
         */
        private final int m_size;
    }

    // --------------------------------------------------------- Path helpers

    /**
     * Build a straight line.
     * @param x1 The X ordinate of the first endpoint.
     * @param y1 The Y ordinate of the first endpoint.
     * @param x2 The X ordinate of the second endpoint.
     * @param y2 The Y ordinate of the second endpoint.
     * @return The line.
     */
    private static Shape line(double x1, double y1, double x2, double y2)
    {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    /**
     * Build a circle from its centre and radius.
     * @param cx The X ordinate of the centre.
     * @param cy The Y ordinate of the centre.
     * @param r The radius.
     * @return The circle.
     */
    private static Shape circle(double cx, double cy, double r)
    {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    /**
     * Build a rounded rectangle.
     * @param x The X ordinate of the upper-left corner.
     * @param y The Y ordinate of the upper-left corner.
     * @param w The width.
     * @param h The height.
     * @param r The corner radius.
     * @return The rounded rectangle.
     */
    private static Shape rect(double x, double y, double w, double h, double r)
    {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    /**
     * Build a closed polygon from a flat list of ordinates.
     * @param pts Alternating X and Y ordinates.
     * @return The polygon.
     */
    private static Shape poly(double... pts)
    {
        Path2D.Double p = (Path2D.Double)polyline(pts);
        p.closePath();
        return p;
    }

    /**
     * Build an open polyline from a flat list of ordinates.
     * @param pts Alternating X and Y ordinates.
     * @return The polyline.
     */
    private static Shape polyline(double... pts)
    {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2)
        {
            p.lineTo(pts[i], pts[i + 1]);
        }
        return p;
    }

    /**
     * Draw a character centred on a point, used for the handful of icons which are really glyphs.
     * @param g2d The graphics object to render onto.
     * @param s The string to render.
     * @param cx The X ordinate of the centre.
     * @param cy The Y ordinate of the centre.
     * @param size The point size, in grid units.
     */
    private static void glyph(Graphics2D g2d, String s, double cx, double cy, int size)
    {
        g2d.setFont(Theme.ui(Font.BOLD, size));
        Theme.drawCentered(g2d, s, cx, cy);
    }

    // ------------------------------------------------------------ The paths

    /**
     * Render the named icon onto a graphics object whose coordinate space has already been scaled
     * to the nominal 24x24 grid, and whose colour and stroke have already been set.
     * @param g The graphics object to render onto.
     * @param name The name of the icon.
     * @param c The colour the icon is being drawn in.
     */
    private static void paint(Graphics2D g, String name, Color c)
    {
        if (name.equals("new-machine"))
        {
            g.draw(circle(10, 13, 5.5));
            g.draw(line(16.5, 4.5, 21.5, 4.5));
            g.draw(line(19, 2, 19, 7));
        }
        else if (name.equals("open"))
        {
            g.draw(polyline(3, 19, 3, 6, 9.5, 6, 11.5, 8.5, 21, 8.5));
            g.draw(poly(3, 19, 5.5, 11.5, 22.5, 11.5, 20, 19));
        }
        else if (name.equals("save"))
        {
            g.draw(line(12, 3, 12, 15));
            g.draw(polyline(7.5, 10.5, 12, 15, 16.5, 10.5));
            g.draw(polyline(4, 16, 4, 20, 20, 20, 20, 16));
        }
        else if (name.equals("save-as"))
        {
            g.draw(line(12, 3, 12, 13));
            g.draw(polyline(8, 9, 12, 13, 16, 9));
            g.draw(polyline(4, 15, 4, 20, 20, 20, 20, 15));
            g.draw(line(17, 3.5, 21, 3.5));
            g.draw(line(19, 1.5, 19, 5.5));
        }
        else if (name.equals("tape"))
        {
            g.draw(rect(2, 8, 20, 9, 2));
            g.draw(line(8, 8, 8, 17));
            g.draw(line(14, 8, 14, 17));
        }
        else if (name.equals("new-tape"))
        {
            g.draw(rect(2, 10, 15, 9, 2));
            g.draw(line(7.5, 10, 7.5, 19));
            g.draw(line(12.5, 10, 12.5, 19));
            g.draw(line(17, 5, 22, 5));
            g.draw(line(19.5, 2.5, 19.5, 7.5));
        }
        else if (name.equals("open-tape"))
        {
            g.draw(rect(2, 12, 20, 8, 2));
            g.draw(line(8.5, 12, 8.5, 20));
            g.draw(line(15.5, 12, 15.5, 20));
            g.draw(line(12, 2, 12, 9));
            g.draw(polyline(8.5, 5.5, 12, 2, 15.5, 5.5));
        }
        else if (name.equals("save-tape"))
        {
            g.draw(rect(2, 12, 20, 8, 2));
            g.draw(line(8.5, 12, 8.5, 20));
            g.draw(line(15.5, 12, 15.5, 20));
            g.draw(line(12, 2, 12, 9));
            g.draw(polyline(8.5, 5.5, 12, 9, 15.5, 5.5));
        }
        else if (name.equals("cut"))
        {
            g.draw(circle(6.5, 18.5, 2.8));
            g.draw(circle(17.5, 18.5, 2.8));
            g.draw(line(8.5, 16.5, 18.5, 3));
            g.draw(line(15.5, 16.5, 5.5, 3));
        }
        else if (name.equals("copy"))
        {
            g.draw(rect(8.5, 8.5, 12, 12, 2.5));
            g.draw(polyline(15.5, 5.5, 15.5, 3.5, 3.5, 3.5, 3.5, 15.5, 5.5, 15.5));
        }
        else if (name.equals("paste"))
        {
            g.draw(rect(5, 4.5, 14, 16.5, 2.5));
            g.draw(rect(9, 2, 6, 4.5, 1.5));
            g.draw(line(8.5, 12, 15.5, 12));
            g.draw(line(8.5, 16, 13.5, 16));
        }
        else if (name.equals("delete"))
        {
            g.draw(line(3.5, 6.5, 20.5, 6.5));
            g.draw(polyline(5.5, 6.5, 6.5, 20.5, 17.5, 20.5, 18.5, 6.5));
            g.draw(polyline(9, 6.5, 9, 3.5, 15, 3.5, 15, 6.5));
            g.draw(line(10, 10.5, 10, 16.5));
            g.draw(line(14, 10.5, 14, 16.5));
        }
        else if (name.equals("undo"))
        {
            g.draw(new Arc2D.Double(4, 7, 16, 13, 20, 160, Arc2D.OPEN));
            g.draw(polyline(3, 4, 4.2, 10.2, 10.2, 8.4));
        }
        else if (name.equals("redo"))
        {
            g.draw(new Arc2D.Double(4, 7, 16, 13, 0, 160, Arc2D.OPEN));
            g.draw(polyline(21, 4, 19.8, 10.2, 13.8, 8.4));
        }
        else if (name.equals("alphabet"))
        {
            glyph(g, "Σ", 12, 12.5, 17);
        }
        else if (name.equals("state"))
        {
            g.draw(circle(10.5, 13.5, 6));
            g.draw(line(16.5, 5, 21.5, 5));
            g.draw(line(19, 2.5, 19, 7.5));
        }
        else if (name.equals("transition"))
        {
            g.draw(circle(5, 17.5, 3));
            g.draw(circle(19, 6.5, 3));
            g.draw(line(7.4, 15.6, 14.6, 9.9));
            g.fill(poly(16.6, 8.3, 12.6, 9.1, 14.8, 11.9));
        }
        else if (name.equals("select"))
        {
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[] { 3.2f, 3.2f }, 0f));
            g.draw(rect(3, 3, 18, 18, 2));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        else if (name.equals("eraser"))
        {
            g.draw(line(3, 20.5, 21, 20.5));
            Path2D.Double p = new Path2D.Double();
            p.moveTo(6, 16.5); p.lineTo(14.5, 8); p.lineTo(20, 13.5); p.lineTo(14.5, 19);
            p.lineTo(8.5, 19); p.closePath();
            g.draw(p);
            g.draw(line(10.5, 12, 16, 17.5));
        }
        else if (name.equals("start"))
        {
            g.draw(circle(15, 12, 5.5));
            g.draw(line(2, 12, 8, 12));
            g.fill(poly(9.6, 12, 6, 9.9, 6, 14.1));
        }
        else if (name.equals("final"))
        {
            g.draw(circle(12, 12, 8.5));
            g.draw(circle(12, 12, 5.2));
        }
        else if (name.equals("current"))
        {
            g.draw(circle(12, 12, 8.5));
            g.fill(circle(12, 12, 3.4));
        }
        else if (name.equals("validate"))
        {
            g.draw(circle(12, 12, 8.8));
            g.draw(polyline(7.8, 12.2, 10.8, 15.2, 16.4, 9));
        }
        else if (name.equals("step"))
        {
            g.fill(poly(6, 5, 15, 12, 6, 19));
            g.draw(line(18, 5, 18, 19));
        }
        else if (name.equals("run"))
        {
            g.fill(poly(7, 4.5, 19.5, 12, 7, 19.5));
        }
        else if (name.equals("pause"))
        {
            g.fill(rect(6, 5, 4, 14, 1.2));
            g.fill(rect(14, 5, 4, 14, 1.2));
        }
        else if (name.equals("stop"))
        {
            g.fill(rect(5.5, 5.5, 13, 13, 2));
        }
        else if (name.equals("tape-start"))
        {
            g.draw(line(5, 5.5, 5, 18.5));
            g.fill(poly(19, 5.5, 19, 18.5, 8.5, 12));
        }
        else if (name.equals("tape-left"))
        {
            g.draw(polyline(14.5, 5, 7.5, 12, 14.5, 19));
        }
        else if (name.equals("tape-right"))
        {
            g.draw(polyline(9.5, 5, 16.5, 12, 9.5, 19));
        }
        else if (name.equals("tape-clear"))
        {
            g.draw(rect(2, 8, 20, 9, 2));
            g.draw(line(9, 10.5, 15, 15.5));
            g.draw(line(15, 10.5, 9, 15.5));
        }
        else if (name.equals("tape-reload"))
        {
            g.draw(new Arc2D.Double(4, 4, 16, 16, 60, 260, Arc2D.OPEN));
            g.draw(polyline(15.5, 1.5, 20.2, 5.6, 14.8, 8.4));
        }
        else if (name.equals("help"))
        {
            g.draw(circle(12, 12, 8.8));
            glyph(g, "?", 12, 12.4, 12);
        }
        else if (name.equals("zoom-in"))
        {
            g.draw(circle(10.5, 10.5, 6.5));
            g.draw(line(15.4, 15.4, 20.5, 20.5));
            g.draw(line(7.5, 10.5, 13.5, 10.5));
            g.draw(line(10.5, 7.5, 10.5, 13.5));
        }
        else if (name.equals("zoom-out"))
        {
            g.draw(circle(10.5, 10.5, 6.5));
            g.draw(line(15.4, 15.4, 20.5, 20.5));
            g.draw(line(7.5, 10.5, 13.5, 10.5));
        }
        else if (name.equals("zoom-reset"))
        {
            g.draw(circle(10.5, 10.5, 6.5));
            g.draw(line(15.4, 15.4, 20.5, 20.5));
            glyph(g, "1", 10.5, 10.9, 9);
        }
        else if (name.equals("light"))
        {
            g.draw(circle(12, 12, 4.4));
            for (int i = 0; i < 8; i++)
            {
                double a = i * Math.PI / 4;
                g.draw(line(12 + Math.cos(a) * 7.2, 12 + Math.sin(a) * 7.2,
                            12 + Math.cos(a) * 9.6, 12 + Math.sin(a) * 9.6));
            }
        }
        else if (name.equals("dark"))
        {
            Area moon = new Area(circle(12, 12, 8.6));
            moon.subtract(new Area(circle(16.4, 8.4, 8.0)));
            g.fill(moon);
        }
        else if (name.equals("close"))
        {
            g.draw(line(6.5, 6.5, 17.5, 17.5));
            g.draw(line(17.5, 6.5, 6.5, 17.5));
        }
        else if (name.equals("dot"))
        {
            g.fill(circle(12, 12, 5));
        }
        else if (name.equals("plus"))
        {
            g.draw(line(12, 5, 12, 19));
            g.draw(line(5, 12, 19, 12));
        }
        else if (name.equals("chevron-down"))
        {
            g.draw(polyline(6, 9.5, 12, 15.5, 18, 9.5));
        }
        else if (name.equals("console"))
        {
            g.draw(rect(2.5, 4, 19, 16, 2.5));
            g.draw(polyline(6.5, 9.5, 10, 12.5, 6.5, 15.5));
            g.draw(line(12.5, 15.5, 17.5, 15.5));
        }
        else if (name.equals("statusbar"))
        {
            g.draw(rect(2.5, 4, 19, 16, 2.5));
            g.draw(line(2.5, 15.5, 21.5, 15.5));
        }
        else if (name.equals("machine"))
        {
            g.draw(circle(6, 8, 3.2));
            g.draw(circle(18, 16, 3.2));
            g.draw(line(8.6, 9.8, 15.4, 14.2));
        }
        else if (name.equals("dfsa"))
        {
            g.draw(circle(12, 12, 8.5));
            g.draw(circle(12, 12, 5.2));
            g.draw(line(0.5, 12, 3.4, 12));
        }
        // An unknown name renders nothing, which is preferable to throwing during a paint.
    }
}
