package org.pepsoft.worldpainter.util.docking;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.worldpainter.util.DockableFrameBuilder;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

/**
 * Covers the {@link DockingController} operations that are otherwise only reached through user interaction: renaming a
 * docked panel, removing the anchor of a tab group, restoring a saved layout and resetting to the default.
 *
 * <p>These tests need a display, and are skipped on a headless runtime.
 *
 * <p><strong>On tab groups:</strong> panels sharing a side and index are tabbed together, so at most one of them is
 * ever showing. A group member is therefore verified by checking that it is still registered and that
 * {@link DockingController#showPanel(String)} can bring it to the front, not by expecting it to be showing already.
 */
public class ModernDockingControllerTest {
    @BeforeClass
    public static void checkNotHeadless() {
        assumeFalse("Docking tests need a display", GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() throws Exception {
        onEdt(() -> {
            frame = new JFrame("ModernDockingControllerTest");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            final JPanel contentContainer = new JPanel(new BorderLayout());
            frame.getContentPane().add(contentContainer, BorderLayout.CENTER);
            controller = new ModernDockingController(frame, contentContainer);
            mapView = new JLabel("MAP VIEW");
            controller.setWorkspaceComponent(mapView);

            // The same shape as App.java's layout: WEST 1, WEST 2, three tabbed at WEST 3, EAST 1, EAST 2.
            layers = addPanel("Layers", DockSide.WEST, 3);
            terrain = addPanel("Terrain", DockSide.WEST, 3);
            biomes = addPanel("Biomes", DockSide.WEST, 3);
            addPanel("Tools", DockSide.WEST, 1);
            addPanel("Tool Settings", DockSide.WEST, 2);
            addPanel("Brushes", DockSide.EAST, 1);
            addPanel("Info", DockSide.EAST, 2);

            frame.setSize(1200, 800);
            frame.setVisible(true);
        });
    }

    @After
    public void tearDown() throws Exception {
        if (frame != null) {
            onEdt(() -> frame.dispose());
        }
    }

    @Test
    public void panelsAreDockedWhereTheyWereAdded() throws Exception {
        onEdt(() -> {
            for (String id: UNGROUPED) {
                assertTrue("should be showing: " + id, controller.isPanelShowing(id));
            }
            assertEquals("exactly one member of a tab group is showing", 1, showing(WEST_GROUP));
            for (String id: WEST_GROUP) {
                assertTrue("should be reachable: " + id, canBeBroughtToFront(id));
            }
        });
    }

    @Test
    public void findPanelIdClimbsThePastHierarchy() throws Exception {
        onEdt(() -> {
            assertEquals("tools", controller.findPanelId(deepest(controller.getPanelComponent("tools"))));
            assertNull("a component outside any panel has no panel id", controller.findPanelId(new JButton("orphan")));
            assertNull("an unknown id has no component", controller.getPanelComponent("no.such.panel"));
        });
    }

    @Test
    public void layoutRoundTrips() throws Exception {
        onEdt(() -> {
            controller.showPanel("terrain");
            final byte[] layout = controller.getLayoutData();
            assertNotNull(layout);
            assertTrue("layout is tagged so foreign layouts can be told apart", layout.length > 8);

            controller.showPanel("biomes");
            assertTrue("its own layout is accepted", controller.loadLayout(layout));
            assertEquals(1, showing(WEST_GROUP));
            for (String id: UNGROUPED) {
                assertNotNull("still registered after restore: " + id, controller.getPanelComponent(id));
            }
        });
    }

    /** Layouts saved by WorldPainter 2.27 and earlier are JIDE layouts, and have to be discarded, not parsed. */
    @Test
    public void foreignAndCorruptLayoutsAreRejectedWithoutThrowing() throws Exception {
        onEdt(() -> {
            assertFalse("null layout", controller.loadLayout(null));
            assertFalse("empty layout", controller.loadLayout(new byte[0]));
            assertFalse("a serialised JIDE layout", controller.loadLayout(new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5, 1, 2, 3}));
            assertFalse("correctly tagged but corrupt", controller.loadLayout("WPDOCK1\nnot xml".getBytes()));
            assertTrue("the live layout survives a rejected load", controller.isPanelShowing("tools"));
        });
    }

    /** Renaming a custom layer palette changes the ID of a panel which is already docked. */
    @Test
    public void aDockedPanelCanBeRenamed() throws Exception {
        onEdt(() -> {
            layers.setTitle("Renamed");
            layers.setId("customLayerPalette.Renamed");

            assertNotNull("registered under the new id", controller.getPanelComponent("customLayerPalette.Renamed"));
            assertNull("the old id is released", controller.getPanelComponent("layers"));
            assertTrue("still dockable under the new id", canBeBroughtToFront("customLayerPalette.Renamed"));
            assertEquals("customLayerPalette.Renamed",
                    controller.findPanelId(deepest(controller.getPanelComponent("customLayerPalette.Renamed"))));
            assertTrue("its tab group siblings are unaffected", canBeBroughtToFront("terrain"));
            assertTrue(canBeBroughtToFront("biomes"));
        });
    }

    /** The first panel of a group anchors the rest, so removing it has to hand that role over. */
    @Test
    public void removingTheAnchorOfATabGroupKeepsTheRest() throws Exception {
        onEdt(() -> {
            controller.removePanel("layers");
            assertNull(controller.getPanelComponent("layers"));
            assertTrue("sibling survives the anchor being removed", canBeBroughtToFront("terrain"));
            assertTrue(canBeBroughtToFront("biomes"));

            // A panel added afterwards should still join the same group rather than starting a new split.
            controller.addPanel(build("Annotations", DockSide.WEST, 3));
            assertTrue(controller.isPanelShowing("annotations"));
            assertTrue(canBeBroughtToFront("terrain"));
        });
    }

    @Test
    public void removingAndShowingUnknownPanelsAreNoOps() throws Exception {
        onEdt(() -> {
            controller.removePanel("no.such.panel");
            controller.showPanel("no.such.panel");
            controller.activatePanel("no.such.panel");
            assertTrue("the layout is untouched", controller.isPanelShowing("tools"));
        });
    }

    @Test
    public void addingAnExistingIdReplacesThePanel() throws Exception {
        onEdt(() -> {
            controller.addPanel(build("Terrain", DockSide.WEST, 3));
            assertTrue(controller.isPanelShowing("terrain"));
            assertTrue("the rest of the group is intact", canBeBroughtToFront("biomes"));
        });
    }

    @Test
    public void resetToDefaultRestoresEveryPanel() throws Exception {
        onEdt(() -> {
            controller.removePanel("terrain");
            controller.resetToDefault();

            assertTrue("the workspace survives a reset", mapView.isShowing());
            for (String id: UNGROUPED) {
                assertTrue("showing after reset: " + id, controller.isPanelShowing(id));
            }
            final List<String> remaining = WEST_GROUP.stream()
                    .filter(id -> controller.getPanelComponent(id) != null)
                    .collect(toList());
            assertEquals("the removed panel is not resurrected", 2, remaining.size());
            assertEquals(1, showing(remaining));
            for (String id: remaining) {
                assertTrue("reachable after reset: " + id, canBeBroughtToFront(id));
            }
        });
    }

    private DockPanel addPanel(String title, DockSide side, int index) {
        final DockPanel panel = build(title, side, index);
        controller.addPanel(panel);
        return panel;
    }

    private static DockPanel build(String title, DockSide side, int index) {
        final JPanel content = new JPanel();
        content.add(new JButton(title));
        return new DockableFrameBuilder(content, title, side, index).build();
    }

    private long showing(List<String> ids) {
        return ids.stream().filter(controller::isPanelShowing).count();
    }

    private boolean canBeBroughtToFront(String id) {
        if (controller.getPanelComponent(id) == null) {
            return false;
        }
        controller.showPanel(id);
        return controller.isPanelShowing(id);
    }

    private static Component deepest(Component component) {
        Component c = component;
        while ((c instanceof Container) && (((Container) c).getComponentCount() > 0)) {
            c = ((Container) c).getComponent(0);
        }
        return c;
    }

    private static void onEdt(Runnable task) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(task);
    }

    private JFrame frame;
    private DockingController controller;
    private JLabel mapView;
    private DockPanel layers, terrain, biomes;

    private static final List<String> UNGROUPED = List.of("tools", "toolSettings", "brushes", "info");
    private static final List<String> WEST_GROUP = List.of("layers", "terrain", "biomes");
}
