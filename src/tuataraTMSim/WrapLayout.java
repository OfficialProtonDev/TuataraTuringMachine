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
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * A FlowLayout which reports a preferred size that accounts for wrapping.
 *
 * FlowLayout wraps its children when it runs out of width, but still reports a preferred size as
 * though everything sat on one row, so a container using it gets clipped instead of growing. This
 * subclass measures the rows it would actually produce at the current width. It replaces the
 * hand-rolled row arithmetic the old ToolBarPanel used, which had to hard-code a fudge factor found
 * by trial and error.
 */
public class WrapLayout extends FlowLayout
{
    /**
     * Creates a new instance of WrapLayout.
     * @param align The row alignment, one of the FlowLayout constants.
     * @param hgap The horizontal gap between components.
     * @param vgap The vertical gap between rows.
     */
    public WrapLayout(int align, int hgap, int vgap)
    {
        super(align, hgap, vgap);
    }

    /**
     * Get the preferred size of the container under this layout.
     * @param target The container being laid out.
     * @return The preferred size.
     */
    public Dimension preferredLayoutSize(Container target)
    {
        return layoutSize(target, true);
    }

    /**
     * Get the minimum size of the container under this layout.
     * @param target The container being laid out.
     * @return The minimum size.
     */
    public Dimension minimumLayoutSize(Container target)
    {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    /**
     * Measure the container by walking its children and starting a new row whenever the next child
     * would not fit in the available width.
     * @param target The container being laid out.
     * @param preferred true to measure children at their preferred size, false for their minimum.
     * @return The resulting size.
     */
    private Dimension layoutSize(Container target, boolean preferred)
    {
        synchronized (target.getTreeLock())
        {
            // Use the width the container has actually been given. Before the first layout pass that
            // is zero, in which case fall back to the parent's width, and finally to unbounded.
            int targetWidth = target.getSize().width;
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null)
            {
                container = container.getParent();
            }
            targetWidth = container.getSize().width;
            if (targetWidth == 0)
            {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + getHgap() * 2;
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++)
            {
                Component m = target.getComponent(i);
                if (!m.isVisible())
                {
                    continue;
                }
                Dimension d = preferred? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth + d.width > maxWidth && rowWidth != 0)
                {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0)
                {
                    rowWidth += getHgap();
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + getVgap() * 2;

            // When nested in a scroll pane the viewport reports the width the scrollbar will leave,
            // so allow for it to avoid a feedback loop between the two.
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid())
            {
                dim.width -= (getHgap() + 1);
            }
            return dim;
        }
    }

    /**
     * Fold a completed row into the running total.
     * @param dim The running total.
     * @param rowWidth The width of the completed row.
     * @param rowHeight The height of the completed row.
     */
    private void addRow(Dimension dim, int rowWidth, int rowHeight)
    {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0)
        {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}
