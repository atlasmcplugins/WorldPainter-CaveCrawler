package org.pepsoft.worldpainter.util.docking;

import javax.swing.*;
import java.awt.*;

/**
 * A dockable panel in the main window. This is WorldPainter's own, library independent description of a dock; it is
 * translated to whatever the installed {@link DockingController} needs by that controller.
 *
 * <p>Instances are normally created with a {@link org.pepsoft.worldpainter.util.DockableFrameBuilder}. The title and
 * icon are mutable so that panels which are renamed while the application is running (custom layer palettes, for
 * example) can be updated in place; the controller is notified through the listener installed when the panel is added.
 *
 * <p>Replaces {@code com.jidesoft.docking.DockableFrame}.
 */
public class DockPanel {
    public DockPanel(String id, String title, Component content, DockSide side, int index) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (content == null) {
            throw new NullPointerException("content");
        }
        this.id = id;
        this.title = title;
        this.content = content;
        this.side = side;
        this.index = index;
    }

    public String getId() {
        return id;
    }

    /**
     * Change the ID of this panel. Legal while the panel is docked; the controller re-registers it under the new ID,
     * keeping it in the same group. Custom layer palettes do this when they are renamed.
     */
    public void setId(String id) {
        final String oldId = this.id;
        if (oldId.equals(id)) {
            return;
        }
        this.id = id;
        if (listener != null) {
            listener.idChanged(this, oldId);
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        if (listener != null) {
            listener.titleChanged(this);
        }
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
        if (listener != null) {
            listener.iconChanged(this);
        }
    }

    public Component getContent() {
        return content;
    }

    public DockSide getSide() {
        return side;
    }

    public int getIndex() {
        return index;
    }

    public Dimension getPreferredContentSize() {
        return preferredContentSize;
    }

    public void setPreferredContentSize(Dimension preferredContentSize) {
        this.preferredContentSize = preferredContentSize;
    }

    public String getHelpKey() {
        return helpKey;
    }

    public void setHelpKey(String helpKey) {
        this.helpKey = helpKey;
    }

    /**
     * Installed by the {@link DockingController} when this panel is added, and cleared when it is removed. Package
     * private; not part of the public API of this class.
     */
    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public String toString() {
        return "DockPanel[" + id + ']';
    }

    private final Component content;
    private final DockSide side;
    private final int index;

    private String id, title, helpKey;
    private Icon icon;
    private Dimension preferredContentSize;
    private Listener listener;

    /**
     * Notified by a {@link DockPanel} when a property changes which the {@link DockingController} needs to reflect in
     * the UI.
     */
    interface Listener {
        void titleChanged(DockPanel panel);
        void iconChanged(DockPanel panel);
        void idChanged(DockPanel panel, String oldId);
    }
}
