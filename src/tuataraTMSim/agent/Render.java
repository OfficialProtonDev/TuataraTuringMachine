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

package tuataraTMSim.agent;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import tuataraTMSim.Theme;
import tuataraTMSim.machine.*;

/**
 * A picture of a machine.
 *
 * The drawing is the program's own: this paints the machine into an image instead of onto the
 * screen, so what comes back is exactly what the user would see. That matters more than it sounds.
 * A layout is either legible or it is not, and no amount of structured data will show an agent
 * that two states have landed on top of each other.
 */
public final class Render
{
    /**
     * The most pixels an image may have. A machine of a thousand states would otherwise produce
     * something too large to send and too small to read.
     */
    private static final int MAX_PIXELS = 4000000;

    /**
     * Space left around the machine.
     */
    private static final int PADDING = 40;

    /**
     * Not instantiable.
     */
    private Render() { }

    /**
     * Draw a machine and return it as a PNG.
     * @param machine The machine to draw.
     * @param highlight Labels of states to pick out; may be empty.
     * @return A JSON object holding the image, its size, and a note if it had to be scaled.
     * @throws Exception If the image could not be produced.
     */
    public static Map<String, Object> png(Machine machine, List<String> highlight) throws Exception
    {
        List<State> states = new ArrayList<State>();
        for (Object o : machine.getStates())
        {
            states.add((State)o);
        }
        if (states.isEmpty())
        {
            throw new AgentException("That machine has no states, so there is nothing to draw.");
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (State s : states)
        {
            minX = Math.min(minX, s.getX());
            minY = Math.min(minY, s.getY());
            maxX = Math.max(maxX, s.getX() + State.STATE_RENDERING_WIDTH);
            maxY = Math.max(maxY, s.getY() + State.STATE_RENDERING_WIDTH);
        }
        // Arrows bow outside the states, self-loops reach above and below them, and a state whose
        // name will not fit inside the circle has it drawn underneath.
        minX -= PADDING * 2;
        minY -= PADDING * 2;
        maxX += PADDING * 2;
        maxY += PADDING * 2;

        int width = Math.max(1, maxX - minX);
        int height = Math.max(1, maxY - minY);
        double scale = 1.0;
        if ((long)width * height > MAX_PIXELS)
        {
            scale = Math.sqrt(MAX_PIXELS / (double)((long)width * height));
        }
        int imageWidth = Math.max(1, (int)(width * scale));
        int imageHeight = Math.max(1, (int)(height * scale));

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setColor(Theme.palette().canvas);
            g.fillRect(0, 0, imageWidth, imageHeight);
            g.scale(scale, scale);
            g.translate(-minX, -minY);
            Theme.prepare(g);

            // Picked-out states are drawn as though selected, which is the emphasis the program
            // already has rather than a second one invented for this.
            Set<State> selected = new HashSet<State>();
            for (String label : highlight)
            {
                State s = Machines.state(machine, label);
                if (s == null)
                {
                    throw new AgentException("There is no state called \"" + label
                            + "\" to highlight in that machine.");
                }
                selected.add(s);
            }
            paint(machine, g, selected);
        }
        finally
        {
            g.dispose();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        String encoded = base64(bytes.toByteArray());

        Map<String, Object> out = Json.object(
                "image_png_base64", encoded,
                "width", Integer.valueOf(imageWidth),
                "height", Integer.valueOf(imageHeight),
                "states", Integer.valueOf(states.size()),
                "overlapping_state_pairs", Integer.valueOf(overlaps(states)));
        if (scale < 1.0)
        {
            out.put("note", String.format(
                        "Scaled to %.0f%% to keep the image a sensible size.", scale * 100));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void paint(Machine machine, Graphics2D g, Set<State> selected)
    {
        // The drawing asks the simulator which transition would be taken next, so as to pick it
        // out. One that has not started answers "none", which is what a still picture wants.
        machine.paint(g, selected, new HashSet<Transition>(),
                Machines.simulator(machine, new CA_Tape("")));
    }

    /**
     * How many pairs of states are close enough to read as one. Reported alongside the picture,
     * because it is the thing the picture is usually being asked about.
     * @param states The states to check.
     * @return The number of overlapping pairs.
     */
    private static int overlaps(List<State> states)
    {
        int count = 0;
        for (int i = 0; i < states.size(); i++)
        {
            for (int j = i + 1; j < states.size(); j++)
            {
                double distance = Math.hypot(states.get(i).getX() - states.get(j).getX(),
                                             states.get(i).getY() - states.get(j).getY());
                if (distance < State.STATE_RENDERING_WIDTH)
                {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Base64, without a dependency on the version of Java that added it.
     * @param data The bytes to encode.
     * @return The encoded text.
     */
    private static String base64(byte[] data)
    {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        for (int i = 0; i < data.length; i += 3)
        {
            int chunk = (data[i] & 0xFF) << 16;
            int have = 1;
            if (i + 1 < data.length)
            {
                chunk |= (data[i + 1] & 0xFF) << 8;
                have++;
            }
            if (i + 2 < data.length)
            {
                chunk |= data[i + 2] & 0xFF;
                have++;
            }
            sb.append(alphabet.charAt((chunk >> 18) & 0x3F));
            sb.append(alphabet.charAt((chunk >> 12) & 0x3F));
            sb.append(have > 1? alphabet.charAt((chunk >> 6) & 0x3F) : '=');
            sb.append(have > 2? alphabet.charAt(chunk & 0x3F) : '=');
        }
        return sb.toString();
    }
}
