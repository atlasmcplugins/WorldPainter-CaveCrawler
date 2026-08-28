package org.pepsoft.worldpainter.util.docking;

import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockableStyle;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;
import io.github.andrewauclair.moderndocking.layouts.ApplicationLayout;
import io.github.andrewauclair.moderndocking.settings.Settings;
import io.github.andrewauclair.moderndocking.ui.DockingHeaderUI;
import io.github.andrewauclair.moderndocking.ui.HeaderController;
import io.github.andrewauclair.moderndocking.ui.HeaderModel;
import org.pepsoft.worldpainter.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.*;

import static io.github.andrewauclair.moderndocking.DockableTabPreference.TOP;

/**
 * A {@link DockingController} backed by <a href="https://github.com/andrewauclair/ModernDocking">Modern Docking</a>.
 *
 * <p>Modern Docking has no notion of a fixed central "workspace" the way the JIDE Docking Framework did, so the
 * workspace component is docked as an ordinary dockable which simply refuses to be closed, floated or auto hidden. It
 * is always docked first, so that everything else docks around it.
 *
 * <p>Panels are grouped by their {@link DockPanel#getSide() side} and {@link DockPanel#getIndex() index}: panels
 * sharing a side and index are tabbed together, and each new index on a side is split off below the previous one. This
 * reproduces the layout WorldPainter built with JIDE's side/index model.
 */
public class ModernDockingController implements DockingController, DockPanel.Listener {
    public ModernDockingController(JFrame frame, Container contentContainer) {
        this.frame = frame;
        docking = new Docking(frame);
        Settings.setDefaultTabPreference(TOP);
        // This constructor registers the panel with the DockingAPI itself; calling registerDockingPanel() as well
        // would fail with RootDockingPanelRegistrationFailureException.
        rootPanel = new RootDockingPanel(docking, frame);
        rootPanel.setAutoHideSupported(true);
        contentContainer.setLayout(new BorderLayout());
        contentContainer.add(rootPanel, BorderLayout.CENTER);
    }

    // DockingController

    @Override
    public void setWorkspaceComponent(Component component) {
        if (workspace != null) {
            throw new IllegalStateException("The workspace component has already been set");
        }
        workspace = new WorkspaceDockable(component);
        docking.registerDockable(workspace);
        docking.dock(workspace, frame);
    }

    @Override
    public JComponent getMainContainer() {
        return rootPanel;
    }

    @Override
    public void addPanel(DockPanel panel) {
        if (workspace == null) {
            throw new IllegalStateException("The workspace component must be set before any panels are added");
        }
        final String id = panel.getId();
        if (adapters.containsKey(id)) {
            removePanel(id);
        }
        final DockPanelAdapter adapter = new DockPanelAdapter(panel);
        adapters.put(id, adapter);
        additionOrder.add(id);
        panel.setListener(this);
        docking.registerDockable(adapter);
        dockInGroup(adapter);
    }

    @Override
    public void removePanel(String id) {
        final DockPanelAdapter adapter = adapters.remove(id);
        if (adapter == null) {
            return;
        }
        additionOrder.remove(id);
        adapter.getPanel().setListener(null);
        // Hand the group anchor over to another member of the group before removing this one, so the rest of the group
        // does not lose its docking reference.
        final GroupKey groupKey = groupKeyOf(adapter.getPanel());
        if (id.equals(groupAnchors.get(groupKey))) {
            groupAnchors.remove(groupKey);
            additionOrder.stream()
                    .map(adapters::get)
                    .filter(candidate -> groupKeyOf(candidate.getPanel()).equals(groupKey))
                    .findFirst()
                    .ifPresent(candidate -> groupAnchors.put(groupKey, candidate.getPanel().getId()));
        }
        if (docking.isDocked(adapter)) {
            docking.undock(adapter);
        }
        docking.deregisterDockable(adapter);
    }

    @Override
    public void showPanel(String id) {
        final DockPanelAdapter adapter = adapters.get(id);
        if (adapter == null) {
            return;
        }
        if (! docking.isDocked(adapter)) {
            dockInGroup(adapter);
        } else if (docking.isHidden(adapter)) {
            docking.autoShowDockable(adapter);
        }
        docking.display(adapter);
    }

    @Override
    public void activatePanel(String id) {
        final DockPanelAdapter adapter = adapters.get(id);
        if (adapter == null) {
            return;
        }
        showPanel(id);
        docking.bringToFront(adapter);
    }

    @Override
    public boolean isPanelShowing(String id) {
        final DockPanelAdapter adapter = adapters.get(id);
        return (adapter != null) && docking.isDocked(adapter) && (! docking.isHidden(adapter)) && adapter.isShowing();
    }

    @Override
    public Component getPanelComponent(String id) {
        return adapters.get(id);
    }

    @Override
    public String findPanelId(Component component) {
        Component parent = component;
        while (parent != null) {
            if (parent instanceof DockPanelAdapter) {
                return ((DockPanelAdapter) parent).getPanel().getId();
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public byte[] getLayoutData() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(LAYOUT_MAGIC);
            docking.getLayoutPersistence().saveLayoutToOutputStream(out, docking.getDockingState().getApplicationLayout());
            return out.toByteArray();
        } catch (Exception e) {
            logger.warn("Could not capture the docking layout; it will not be saved", e);
            return null;
        }
    }

    @Override
    public boolean loadLayout(byte[] layoutData) {
        if (! hasMagic(layoutData)) {
            // Most likely a layout saved by an older WorldPainter, which used the JIDE Docking Framework. There is no
            // way to translate those, so leave the current layout alone.
            logger.debug("Ignoring a stored docking layout which was not written by this docking framework");
            return false;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(layoutData, LAYOUT_MAGIC.length, layoutData.length - LAYOUT_MAGIC.length)) {
            final ApplicationLayout layout = docking.getLayoutPersistence().loadApplicationLayoutFromInputStream(in);
            docking.getDockingState().restoreApplicationLayout(layout);
            return true;
        } catch (Exception e) {
            logger.warn("Could not restore the stored docking layout; using the default layout", e);
            return false;
        }
    }

    @Override
    public void resetToDefault() {
        final List<String> ids = new ArrayList<>(additionOrder);
        for (String id: ids) {
            final DockPanelAdapter adapter = adapters.get(id);
            if (docking.isDocked(adapter)) {
                docking.undock(adapter);
            }
        }
        groupAnchors.clear();
        if (! docking.isDocked(workspace)) {
            docking.dock(workspace, frame);
        }
        for (String id: ids) {
            dockInGroup(adapters.get(id));
        }
    }

    // DockPanel.Listener

    @Override
    public void titleChanged(DockPanel panel) {
        updateTabInfo(panel);
    }

    @Override
    public void iconChanged(DockPanel panel) {
        updateTabInfo(panel);
    }

    @Override
    public void idChanged(DockPanel panel, String oldId) {
        final DockPanelAdapter adapter = adapters.remove(oldId);
        if (adapter == null) {
            return;
        }
        // Modern Docking keys its registry on the persistent ID, so the dockable has to be taken out and put back
        // under the new one. Re-docking puts it back into the same group.
        final boolean wasDocked = docking.isDocked(adapter);
        if (wasDocked) {
            docking.undock(adapter);
        }
        docking.deregisterDockable(adapter);

        final String newId = panel.getId();
        final GroupKey groupKey = groupKeyOf(panel);
        if (oldId.equals(groupAnchors.get(groupKey))) {
            groupAnchors.put(groupKey, newId);
        }
        Collections.replaceAll(additionOrder, oldId, newId);
        adapter.setRegisteredId(newId);
        adapters.put(newId, adapter);

        docking.registerDockable(adapter);
        if (wasDocked) {
            dockInGroup(adapter);
        }
    }

    private void updateTabInfo(DockPanel panel) {
        final DockPanelAdapter adapter = adapters.get(panel.getId());
        if (adapter != null) {
            docking.updateTabInfo(adapter);
        }
    }

    /**
     * Dock a panel into the group for its side and index: tabbed with the group's anchor if the group already exists,
     * split off below the previous group on that side otherwise.
     */
    private void dockInGroup(DockPanelAdapter adapter) {
        final DockPanel panel = adapter.getPanel();
        final GroupKey groupKey = groupKeyOf(panel);
        final String anchorId = groupAnchors.get(groupKey);
        if (anchorId != null) {
            final DockPanelAdapter anchor = adapters.get(anchorId);
            if ((anchor != null) && docking.isDocked(anchor)) {
                docking.dock(adapter, anchor, DockingRegion.CENTER);
                return;
            }
        }
        // This is the first panel of its group. Dock it below the last group already present on this side, or against
        // the window itself if this is the first group on that side.
        final DockPanelAdapter previousGroupAnchor = groupAnchors.entrySet().stream()
                .filter(entry -> (entry.getKey().side() == panel.getSide()) && (entry.getKey().index() < panel.getIndex()))
                .max(Comparator.comparingInt(entry -> entry.getKey().index()))
                .map(entry -> adapters.get(entry.getValue()))
                .filter(candidate -> (candidate != null) && docking.isDocked(candidate))
                .orElse(null);
        if (previousGroupAnchor != null) {
            docking.dock(adapter, previousGroupAnchor, DockingRegion.SOUTH);
        } else {
            docking.dock(adapter, frame, (panel.getSide() == DockSide.EAST) ? DockingRegion.EAST : DockingRegion.WEST);
        }
        groupAnchors.put(groupKey, panel.getId());
    }

    private static GroupKey groupKeyOf(DockPanel panel) {
        return new GroupKey(panel.getSide(), panel.getIndex());
    }

    private static boolean hasMagic(byte[] layoutData) {
        if ((layoutData == null) || (layoutData.length < LAYOUT_MAGIC.length)) {
            return false;
        }
        for (int i = 0; i < LAYOUT_MAGIC.length; i++) {
            if (layoutData[i] != LAYOUT_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private final JFrame frame;
    private final Docking docking;
    private final RootDockingPanel rootPanel;
    private final Map<String, DockPanelAdapter> adapters = new HashMap<>();
    private final Map<GroupKey, String> groupAnchors = new HashMap<>();
    /** The order in which panels were added, so that {@link #resetToDefault()} can rebuild the original layout. */
    private final List<String> additionOrder = new ArrayList<>();

    private WorkspaceDockable workspace;

    private static final Logger logger = LoggerFactory.getLogger(ModernDockingController.class);

    /**
     * Prefixed to saved layouts so that layouts written by a previous docking framework, which are stored in the same
     * configuration field, can be recognised and discarded rather than blowing up the parser.
     */
    private static final byte[] LAYOUT_MAGIC = {'W', 'P', 'D', 'O', 'C', 'K', '1', '\n'};

    /**
     * The identity of a group of panels which are tabbed together, i.e. all panels added with the same side and index.
     */
    private record GroupKey(DockSide side, int index) {}

    /**
     * Adapts a {@link DockPanel} to Modern Docking's {@link Dockable}, which must also be a {@code Component}.
     */
    private static class DockPanelAdapter extends JPanel implements Dockable {
        DockPanelAdapter(DockPanel panel) {
            super(new BorderLayout());
            this.panel = panel;
            registeredId = panel.getId();
            add(panel.getContent(), BorderLayout.CENTER);
            final Dimension preferredContentSize = panel.getPreferredContentSize();
            if (preferredContentSize != null) {
                setPreferredSize(preferredContentSize);
            }
            // App.showHelp() finds the help key by walking up the component hierarchy, so it has to live on a component
            // and not just on the DockPanel.
            if (panel.getHelpKey() != null) {
                putClientProperty(App.KEY_HELP_KEY, panel.getHelpKey());
            }
        }

        DockPanel getPanel() {
            return panel;
        }

        /**
         * Kept separate from {@code panel.getId()} because Modern Docking looks a dockable up by the ID it was
         * registered with; on a rename the old ID is needed to deregister it before the new one takes effect.
         */
        void setRegisteredId(String registeredId) {
            this.registeredId = registeredId;
        }

        @Override
        public String getPersistentID() {
            return registeredId;
        }

        @Override
        public String getTabText() {
            return panel.getTitle();
        }

        @Override
        public Icon getIcon() {
            return panel.getIcon();
        }

        @Override
        public boolean isClosable() {
            // WorldPainter manages the lifetime of its panels itself; the JIDE version deliberately hid the close
            // button because there was no way to get a closed panel back.
            return false;
        }

        @Override
        public boolean isAutoHideAllowed() {
            return true;
        }

        @Override
        public boolean isMinMaxAllowed() {
            return false;
        }

        @Override
        public boolean isWrappableInScrollpane() {
            // The builder already wraps the content in a scroll pane where that is wanted, with WorldPainter's own
            // vertical only scrolling behaviour.
            return false;
        }

        private final DockPanel panel;
        private String registeredId;

        private static final long serialVersionUID = 1L;
    }

    /**
     * The map view. Docked like any other dockable, but pinned in place: it cannot be closed, floated or auto hidden.
     */
    private static class WorkspaceDockable extends JPanel implements Dockable {
        WorkspaceDockable(Component component) {
            super(new BorderLayout());
            add(component, BorderLayout.CENTER);
        }

        @Override
        public String getPersistentID() {
            return "workspace";
        }

        @Override
        public String getTabText() {
            return "Map";
        }

        @Override
        public boolean isClosable() {
            return false;
        }

        @Override
        public boolean isFloatingAllowed() {
            return false;
        }

        @Override
        public boolean isAutoHideAllowed() {
            return false;
        }

        @Override
        public boolean isMinMaxAllowed() {
            return false;
        }

        @Override
        public DockableStyle getStyle() {
            return DockableStyle.CENTER_ONLY;
        }

        @Override
        public boolean isWrappableInScrollpane() {
            return false;
        }

        @Override
        public DockingHeaderUI createHeaderUI(HeaderController headerController, HeaderModel headerModel) {
            // The JIDE workspace had no title bar, and there is nothing useful to put in one for the map: it cannot be
            // closed, floated, auto hidden or maximised. Give it an empty header so it does not waste a strip of the
            // editor on the word "Map".
            return new EmptyHeaderUI();
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * A header that renders nothing and takes up no space, for dockables that have no controls worth showing.
     * Modern Docking adds the header to the display panel as a {@code Component}, so it has to be one.
     */
    private static class EmptyHeaderUI extends JPanel implements DockingHeaderUI {
        EmptyHeaderUI() {
            setVisible(false);
            final Dimension none = new Dimension(0, 0);
            setPreferredSize(none);
            setMinimumSize(none);
            setMaximumSize(none);
        }

        @Override
        public void update() {
            // Nothing to update
        }

        @Override
        public void displaySettingsMenu(JButton button) {
            // No settings menu
        }

        @Override
        public void setBackgroundOverride(Color color) {
            // Nothing to colour
        }

        @Override
        public void setForegroundOverride(Color color) {
            // Nothing to colour
        }

        private static final long serialVersionUID = 1L;
    }
}
