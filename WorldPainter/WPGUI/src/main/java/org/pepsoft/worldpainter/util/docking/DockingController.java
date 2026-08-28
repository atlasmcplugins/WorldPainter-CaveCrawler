package org.pepsoft.worldpainter.util.docking;

import javax.swing.*;
import java.awt.*;

/**
 * The docking framework operations WorldPainter needs. Implemented by {@link ModernDockingController}; kept as an
 * interface so that the docking library remains swappable and so that no WorldPainter code outside this package needs
 * to name a docking library type.
 *
 * <p>Replaces {@code com.jidesoft.docking.DockingManager}.
 */
public interface DockingController {
    /**
     * The component in the centre of the window, around which the panels are docked. WorldPainter puts the map view
     * here. Must be invoked before any panels are added.
     */
    void setWorkspaceComponent(Component component);

    /**
     * The container that hosts the docking layout, for installing key bindings on.
     */
    JComponent getMainContainer();

    /**
     * Add a panel and dock it at the side and index it specifies. If a panel with the same ID is already present, it is
     * replaced.
     */
    void addPanel(DockPanel panel);

    /**
     * Remove the panel with the specified ID. Does nothing if no such panel is present.
     */
    void removePanel(String id);

    /**
     * Make the specified panel visible, if it is hidden or auto hidden, without moving focus to it.
     */
    void showPanel(String id);

    /**
     * Make the specified panel visible and give it focus, selecting its tab if it is part of a tabbed group.
     */
    void activatePanel(String id);

    /**
     * Whether the specified panel is currently visible on screen.
     *
     * <p>Note that panels which are tabbed together are only visible one at a time, so for a group of tabbed panels
     * this returns {@code true} for at most one of them: the one whose tab is selected. This matches the
     * {@code Component.isShowing()} check that was previously made on the JIDE {@code DockableFrame}, and is what
     * callers want, since the point of asking is normally to decide whether to call {@link #showPanel(String)}.
     */
    boolean isPanelShowing(String id);

    /**
     * The on-screen component of the specified panel, for anchoring overlays to. Returns {@code null} if there is no
     * such panel, or it is not currently realised.
     */
    Component getPanelComponent(String id);

    /**
     * The ID of the panel that contains the specified component, or {@code null} if the component is not inside a
     * docked panel. Replaces walking up the parent chain looking for a {@code DockableFrame}.
     */
    String findPanelId(Component component);

    /**
     * Capture the current layout as an opaque blob, for storing in the configuration.
     *
     * @return The layout, or {@code null} if it could not be captured.
     */
    byte[] getLayoutData();

    /**
     * Restore a layout previously returned by {@link #getLayoutData()}. Layouts written by a different docking library
     * (WorldPainter releases up to 2.27 stored JIDE layouts here) are silently ignored, leaving the current layout
     * alone.
     *
     * @return {@code true} if the layout was applied.
     */
    boolean loadLayout(byte[] layoutData);

    /**
     * Restore every panel to the side and index it was added with.
     */
    void resetToDefault();
}
