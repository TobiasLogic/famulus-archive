package dev.famulus.fabric;

import dev.famulus.core.MaterialList;
import dev.famulus.core.MaterialRequirement;
import dev.famulus.core.PlannedTask;
import dev.famulus.core.TaskPlan;
import dev.famulus.jev.CredentialStore;
import dev.famulus.jev.JevConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The in-game control panel.
 *
 * <p>Built entirely from widgets. Minecraft 26.2 replaced immediate-mode drawing with render-state
 * extraction and {@code GuiGraphics} no longer has text methods at all, so every line on screen is a
 * {@link StringWidget} whose message is refreshed in {@link #tick()}.
 */
public final class FamulusScreen extends Screen {
    private static final int TAB_BAR_HEIGHT = 24;
    private static final int ROW_WIDTH = 380;
    private static final int ROW_HEIGHT = 11;
    private static final int LOG_ROWS = 8;
    private static final int MATERIAL_ROWS = 7;

    private final FamulusAgent agent;
    private final CredentialStore credentials;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);

    private AgentTab agentTab;
    private BuildTab buildTab;
    private SettingsTab settingsTab;
    private MenuTabBar tabBar;

    private final int initialTab;

    public FamulusScreen(FamulusAgent agent, CredentialStore credentials) {
        this(agent, credentials, 0);
    }

    /** {@code initialTab} lets the client test photograph each tab without simulating clicks. */
    public FamulusScreen(FamulusAgent agent, CredentialStore credentials, int initialTab) {
        super(Component.literal("Famulus"));
        this.agent = agent;
        this.credentials = credentials;
        this.initialTab = initialTab;
    }

    @Override
    protected void init() {
        agentTab = new AgentTab();
        buildTab = new BuildTab();
        settingsTab = new SettingsTab();
        tabBar = MenuTabBar.builder(tabManager, width)
                .addTabs(agentTab, buildTab, settingsTab)
                .build();
        addRenderableWidget(tabBar);
        tabBar.selectTab(Math.max(0, Math.min(initialTab, 2)), false);
        repositionElements();
        refresh();
    }

    @Override
    public void repositionElements() {
        if (tabBar == null) {
            return;
        }
        tabBar.setWidth(width);
        tabBar.arrangeElements(width);
        tabManager.setTabArea(new ScreenRectangle(0, TAB_BAR_HEIGHT, width, height - TAB_BAR_HEIGHT));
    }

    @Override
    public void tick() {
        refresh();
    }

    private void refresh() {
        if (agentTab != null) {
            agentTab.refresh();
        }
        if (settingsTab != null) {
            settingsTab.refresh();
        }
    }

    /** Fits a line to the panel so a long message cannot spill outside it. */
    private static Component row(String text) {
        String trimmed = text == null ? "" : text;
        return Component.literal(trimmed.length() <= 58 ? trimmed : trimmed.substring(0, 55) + "...");
    }

    private StringWidget line(GridLayout grid, int rowIndex, String text) {
        StringWidget widget = new StringWidget(ROW_WIDTH, ROW_HEIGHT, row(text), font);
        grid.addChild(widget, rowIndex, 0);
        return widget;
    }

    /** What the agent is doing, and why. */
    private final class AgentTab extends GridLayoutTab {
        private final StringWidget status;
        private final StringWidget policy;
        private final List<StringWidget> logLines = new ArrayList<>();

        AgentTab() {
            super(Component.literal("Agent"));
            layout.spacing(2);
            int nextRow = 0;
            status = line(layout, nextRow++, "no plan");
            policy = line(layout, nextRow++, "policy");
            line(layout, nextRow++, "-- recent --");
            for (int i = 0; i < LOG_ROWS; i++) {
                logLines.add(line(layout, nextRow++, ""));
            }
            layout.addChild(Button.builder(Component.literal("Stop"),
                    button -> agent.stop("Stopped from the screen")).width(80).build(), nextRow, 0);
        }

        void refresh() {
            status.setMessage(row(agent.status()));
            policy.setMessage(row("policy: " + agent.policyState()));
            List<String> recent = agent.recentLog();
            int from = Math.max(0, recent.size() - LOG_ROWS);
            for (int i = 0; i < LOG_ROWS; i++) {
                int index = from + i;
                logLines.get(i).setMessage(row(index < recent.size() ? recent.get(index) : ""));
            }
        }
    }

    /** Blueprints, their material shortfall, and starting a collection run. */
    private final class BuildTab extends GridLayoutTab {
        private final StringWidget selection;
        private final StringWidget summary;
        private final List<StringWidget> materialLines = new ArrayList<>();
        private List<Path> schematics = List.of();
        private int index;

        BuildTab() {
            super(Component.literal("Build"));
            layout.spacing(2);
            int nextRow = 0;
            selection = line(layout, nextRow++, "no blueprints found");
            summary = line(layout, nextRow++, "");
            for (int i = 0; i < MATERIAL_ROWS; i++) {
                materialLines.add(line(layout, nextRow++, ""));
            }
            GridLayout buttons = new GridLayout().spacing(4);
            buttons.addChild(Button.builder(Component.literal("Rescan"), b -> rescan()).width(64).build(), 0, 0);
            buttons.addChild(Button.builder(Component.literal("Next"), b -> cycle()).width(64).build(), 0, 1);
            buttons.addChild(Button.builder(Component.literal("Materials"), b -> showMaterials()).width(80).build(), 0, 2);
            buttons.addChild(Button.builder(Component.literal("Collect"), b -> collect()).width(72).build(), 0, 3);
            layout.addChild(buttons, nextRow, 0);
            rescan();
        }

        private void rescan() {
            try {
                schematics = SchematicAnalyzer.listSchematics();
            } catch (Exception failure) {
                schematics = List.of();
                summary.setMessage(row("could not read the schematic folder: " + failure.getMessage()));
            }
            index = 0;
            updateSelection();
        }

        private void cycle() {
            if (!schematics.isEmpty()) {
                index = (index + 1) % schematics.size();
                updateSelection();
            }
        }

        private void updateSelection() {
            if (schematics.isEmpty()) {
                selection.setMessage(row("no blueprints in config/famulus/schematics"));
                summary.setMessage(row("formats: "
                        + String.join(", ", SchematicAnalyzer.supportedExtensions())));
                return;
            }
            selection.setMessage(row("[" + (index + 1) + "/" + schematics.size() + "] "
                    + schematics.get(index).getFileName()));
        }

        private MaterialList analyse() throws Exception {
            SchematicSummary parsed = SchematicAnalyzer.analyze(schematics.get(index));
            summary.setMessage(row(parsed.dimensions() + ", " + parsed.totalBlocks() + " blocks, "
                    + parsed.itemCounts().size() + " item types"));
            return MaterialList.of(parsed.itemCounts(),
                    MinecraftObserver.countAll(minecraft, parsed.itemCounts().keySet()));
        }

        private void showMaterials() {
            if (schematics.isEmpty()) {
                return;
            }
            clearMaterials();
            try {
                MaterialList list = analyse();
                List<MaterialRequirement> requirements = list.requirements();
                for (int i = 0; i < Math.min(MATERIAL_ROWS, requirements.size()); i++) {
                    MaterialRequirement requirement = requirements.get(i);
                    materialLines.get(i).setMessage(row(String.format("%s  need %d  have %d  short %d",
                            requirement.itemId(), requirement.needed(),
                            requirement.have(), requirement.shortfall())));
                }
                if (requirements.size() > MATERIAL_ROWS) {
                    materialLines.get(MATERIAL_ROWS - 1).setMessage(
                            row("... and " + (requirements.size() - MATERIAL_ROWS + 1) + " more"));
                }
            } catch (Exception failure) {
                summary.setMessage(row(failure.getMessage()));
            }
        }

        private void collect() {
            if (schematics.isEmpty()) {
                return;
            }
            clearMaterials();
            try {
                MaterialList list = analyse();
                if (list.isSatisfied()) {
                    summary.setMessage(row("everything needed is already held"));
                    return;
                }
                List<MaterialRequirement> blocked = list.unobtainable(GatherCatalog::supports);
                if (!blocked.isEmpty()) {
                    summary.setMessage(row("no gather path for "
                            + blocked.get(0).itemId() + (blocked.size() > 1
                            ? " and " + (blocked.size() - 1) + " more" : "")));
                    return;
                }
                List<PlannedTask> tasks = new ArrayList<>();
                int number = 0;
                for (MaterialRequirement requirement : list.gatherable(GatherCatalog::supports)) {
                    if (requirement.needed() > 2304) {
                        summary.setMessage(row(requirement.itemId() + " needs more than an inventory holds"));
                        return;
                    }
                    tasks.add(new PlannedTask.Gather("t" + (++number),
                            requirement.itemId(), requirement.needed()));
                }
                agent.start(new TaskPlan("collect materials for "
                        + schematics.get(index).getFileName(), tasks));
                summary.setMessage(row("collecting: " + tasks.size() + " tasks"));
            } catch (Exception failure) {
                summary.setMessage(row(failure.getMessage()));
            }
        }

        private void clearMaterials() {
            materialLines.forEach(widget -> widget.setMessage(Component.empty()));
        }
    }

    /** Credentials and how confidently the policy must answer before it is obeyed. */
    private final class SettingsTab extends GridLayoutTab {
        private final EditBox keyField;
        private final StringWidget keyStatus;
        private final StringWidget note;

        SettingsTab() {
            super(Component.literal("Settings"));
            layout.spacing(3);
            int nextRow = 0;
            line(layout, nextRow++, "OpenRouter API key (used for Jev and the planner)");
            keyField = new EditBox(font, ROW_WIDTH, 18, Component.literal("API key"));
            keyField.setMaxLength(200);
            keyField.setHint(Component.literal("paste a key, then Save"));
            layout.addChild(keyField, nextRow++, 0);
            keyStatus = line(layout, nextRow++, "");

            GridLayout buttons = new GridLayout().spacing(4);
            buttons.addChild(Button.builder(Component.literal("Save"), b -> saveKey()).width(72).build(), 0, 0);
            buttons.addChild(Button.builder(Component.literal("Clear"), b -> clearKey()).width(72).build(), 0, 1);
            layout.addChild(buttons, nextRow++, 0);

            note = line(layout, nextRow++, "");
            line(layout, nextRow++, "Saved owner-only to config/famulus/" + CredentialStore.FILE_NAME);
            line(layout, nextRow++, "The " + JevConfig.API_KEY_VARIABLE + " variable overrides it.");
            line(layout, nextRow++, "Without a key the agent runs; failures just retry.");
        }

        private void saveKey() {
            String typed = keyField.getValue().trim();
            if (typed.isEmpty()) {
                note.setMessage(row("type a key first"));
                return;
            }
            try {
                credentials.save(typed);
                // Clear immediately: a key left in a text box is a key on someone's stream.
                keyField.setValue("");
                agent.reloadPolicy();
                note.setMessage(row("saved; policy reloaded"));
            } catch (Exception failure) {
                note.setMessage(row("could not save: " + failure.getMessage()));
            }
        }

        private void clearKey() {
            try {
                credentials.clear();
                keyField.setValue("");
                agent.reloadPolicy();
                note.setMessage(row("cleared"));
            } catch (Exception failure) {
                note.setMessage(row("could not clear: " + failure.getMessage()));
            }
        }

        void refresh() {
            String stored = credentials.stored().map(CredentialStore::mask).orElse("not set");
            keyStatus.setMessage(row("stored key: " + stored
                    + (credentials.isOverriddenByEnvironment()
                    ? "  (environment variable is overriding it)" : "")));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
