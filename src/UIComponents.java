import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public class UIComponents {
    private final JFrame frame;
    private final DataManager data;
    private final FusionCalculator calculator;
    private final TeamBuilder teamBuilder;
    private final PokedexDatabase pokedexDB;
    
    private JTextField txtRosterSearch, txtDexSearch;
    private JList<String> listSearchResults, listRoster, listDex;
    private DefaultListModel<String> modelSearchResults, modelRoster, modelDex;
    private TitledBorder borderRoster, borderSearch;
    private JTable fusionTable, teamTable;
    private DefaultTableModel fusionTableModel, teamTableModel;
    private JTextArea logArea, txtDexStats, txtAlgoInfo;
    private JLabel lblDexImage;
    private JProgressBar progressBar, calcProgress, teamProgress;
    
    // New Component: Strategy Heatmap
    private StrategyPanel strategyPanel;

    // Settings - MODIFIED: Replaced chkExhaustive with cmbAlgorithmMode
    private JCheckBox chkHiddenPenalty, chkAllowSameSpeciesInTeam, chkSharedTypes, chkSelfFusion;
    private JComboBox<String> cmbAlgorithmMode;
    private JSpinner spinNumTeams, spinIterations, spinRandomness;
    private JSlider sldStatWeight, sldTypeWeight, sldAbilityWeight, sldMoveWeight;
    private JLabel lblStatW, lblTypeW, lblAbiW, lblMoveW;
    
    private List<Fusion> calculatedFusions = new ArrayList<>();
    private Set<Fusion> pinnedFusions = new HashSet<>();
    private Map<String, ImageIcon> typeIconCache = new HashMap<>();
    private AtomicBoolean isCalculating = new AtomicBoolean(false);
    private AtomicBoolean isBuilding = new AtomicBoolean(false);
    private TaskController currentTask;
    
    private static final Font BTN_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final Color ACCENT = new Color(79, 70, 229);
    private static final Color BG_DARK = new Color(30, 30, 30);
    private static final Color BG_LIGHT = new Color(250, 250, 250);
    
    // Legendary Pokemon set
    private Set<String> legendarySet = new HashSet<>();
    
    public UIComponents(JFrame frame, DataManager data, FusionCalculator calc, TeamBuilder builder) {
        this.frame = frame;
        this.data = data;
        this.calculator = calc;
        this.teamBuilder = builder;
        this.strategyPanel = new StrategyPanel();
        this.pokedexDB = new PokedexDatabase("pokedex_data.csv");
        loadLegendaries("legendaries.csv");
        generateTypeIcons();
        loadTypeIcons();
    }
    
    private void loadLegendaries(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("Legendaries file not found: " + filename);
            return;
        }
        
        try (Scanner sc = new Scanner(file)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    legendarySet.add(line.toLowerCase());
                }
            }
            System.out.println("✓ Loaded " + legendarySet.size() + " legendaries");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        JLabel title = new JLabel("Infinite Fusion Calculator");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        
        JLabel stats = new JLabel("Database: " + data.getDatabaseStats());
        stats.setForeground(new Color(200, 200, 200));
        stats.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(BG_DARK);
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(3));
        titleBox.add(stats);
        
        panel.add(titleBox, BorderLayout.WEST);
        return panel;
    }
    
    public JPanel createLeftPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 10));
        panel.setBackground(BG_LIGHT);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 15, 0); 
        
        // 1. Roster - High WeightY to take up space
        JPanel p1 = createRosterPanel();
        gbc.weighty = 1.0; 
        panel.add(p1, gbc);
        
        // 2. Calculation - No WeightY
        JPanel p2 = createCalcSettingsPanel();
        gbc.weighty = 0.0;
        panel.add(p2, gbc);
        
        // 3. Team Settings - No WeightY
        JPanel p3 = createTeamSettingsPanel();
        gbc.weighty = 0.0;
        panel.add(p3, gbc);
        
        // 4. Sprites - No WeightY
        JPanel p4 = createSpritePanel();
        gbc.weighty = 0.0;
        panel.add(p4, gbc);
        
        return panel;
    }
    
    private JPanel createSlider(String label, JSlider slider, JLabel valueLabel) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(Color.WHITE);
        p.setMaximumSize(new Dimension(700, 50));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(150, 25));
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        p.add(lbl, BorderLayout.WEST);
        slider.setBackground(Color.WHITE);
        slider.setMajorTickSpacing(20);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.addChangeListener(e -> {
            valueLabel.setText(slider.getValue() + "%");
            updateAlgoInfo();
        });
        p.add(slider, BorderLayout.CENTER);
        valueLabel.setPreferredSize(new Dimension(50, 25));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        p.add(valueLabel, BorderLayout.EAST);
        return p;
    }

    private JPanel createRosterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(createStyledBorder("Roster Builder"));
        panel.setBackground(Color.WHITE);
        
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBackground(Color.WHITE);
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        txtRosterSearch = new JTextField();
        searchPanel.add(txtRosterSearch, BorderLayout.CENTER);
        panel.add(searchPanel, BorderLayout.NORTH);
        
        // Use GridBag for internal lists to share 50/50 space
        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        listsPanel.setBackground(Color.WHITE);
        
        modelSearchResults = new DefaultListModel<>();
        listSearchResults = new JList<>(modelSearchResults);
        listSearchResults.setCellRenderer(new PokemonListRenderer());
        setupSearchLogic(txtRosterSearch, modelSearchResults, listSearchResults, this::addToRoster);
        
        JScrollPane scrollSearch = new JScrollPane(listSearchResults);
        borderSearch = createStyledBorder("Available (0)");
        scrollSearch.setBorder(borderSearch);
        listsPanel.add(scrollSearch);
        
        modelRoster = new DefaultListModel<>();
        listRoster = new JList<>(modelRoster);
        listRoster.setCellRenderer(new PokemonListRenderer());
        listRoster.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) removeFromRoster();
            }
        });
        
        borderRoster = createStyledBorder("My Roster (0)");
        JScrollPane rosterScroll = new JScrollPane(listRoster);
        rosterScroll.setBorder(borderRoster);
        listsPanel.add(rosterScroll);
        
        panel.add(listsPanel, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        btnPanel.add(createButton("Add ->", this::addToRoster, false));
        btnPanel.add(createButton("<- Remove", this::removeFromRoster, false));
        btnPanel.add(createButton("Add All", this::addAllToRoster, false));
        btnPanel.add(createButton("Clear All", this::clearRoster, false));
        btnPanel.add(createButton("Add Legendaries", this::addAllLegendaries, false));
        btnPanel.add(createButton("Remove Legendaries", this::removeAllLegendaries, false));
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createCalcSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createStyledBorder("Calculation Settings"));
        
        chkHiddenPenalty = new JCheckBox("Apply Hidden Ability Penalty (-20%)");
        chkHiddenPenalty.setBackground(Color.WHITE);
        chkHiddenPenalty.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(chkHiddenPenalty);
        panel.add(Box.createVerticalStrut(8));
        
        JButton btn = createButton("1. Calculate All Fusions (Click again to Cancel)", this::runCalculation, true);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btn);
        panel.add(Box.createVerticalStrut(8));
        
        calcProgress = new JProgressBar(0, 100);
        calcProgress.setStringPainted(true);
        calcProgress.setString("Ready");
        calcProgress.setMaximumSize(new Dimension(400, 20));
        calcProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(calcProgress);
        
        return panel;
    }
    
    private JPanel createTeamSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createStyledBorder("Team Builder Settings"));
        
        spinNumTeams = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        spinIterations = new JSpinner(new SpinnerNumberModel(2000, 100, 50000, 500));
        spinRandomness = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        
        panel.add(createSpinnerRow("Number of Teams:", spinNumTeams));
        panel.add(Box.createVerticalStrut(5));
        panel.add(createSpinnerRow("Iterations per Team:", spinIterations));
        panel.add(Box.createVerticalStrut(5));
        panel.add(createSpinnerRow("Selection Randomness:", spinRandomness));
        panel.add(Box.createVerticalStrut(10));
        
        // NEW: Algorithm mode dropdown instead of checkbox
        JPanel algoPanel = new JPanel(new BorderLayout(10, 0));
        algoPanel.setBackground(Color.WHITE);
        algoPanel.setMaximumSize(new Dimension(400, 30));
        JLabel lblAlgo = new JLabel("Algorithm Mode:");
        lblAlgo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        algoPanel.add(lblAlgo, BorderLayout.WEST);
        
        String[] modes = {
            "Speed (Fast, Good Results)",
            "Balanced (Medium Speed, Better Results)", 
            "Quality (Slower, Great Results)",
            "Maximum (Slowest, Guaranteed Optimal)"
        };
        cmbAlgorithmMode = new JComboBox<>(modes);
        cmbAlgorithmMode.setSelectedIndex(1); // Default to Balanced
        cmbAlgorithmMode.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        algoPanel.add(cmbAlgorithmMode, BorderLayout.CENTER);
        
        panel.add(algoPanel);
        panel.add(Box.createVerticalStrut(10));
        
        chkAllowSameSpeciesInTeam = new JCheckBox("Allow Multiple of Same Species");
        chkSharedTypes = new JCheckBox("Allow Shared Types Within One Team");
        chkSelfFusion = new JCheckBox("Allow Self-Fusions");
        
        // Add checkboxes
        for (JCheckBox cb : new JCheckBox[]{chkAllowSameSpeciesInTeam, chkSharedTypes, chkSelfFusion}) {
            cb.setBackground(Color.WHITE);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cb);
            panel.add(Box.createVerticalStrut(5));
        }
        
        panel.add(Box.createVerticalStrut(5));
        JButton btnBuild = createButton("2. Build Optimal Teams", this::runTeamBuilder, true);
        btnBuild.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btnBuild);
        panel.add(Box.createVerticalStrut(8));
        
        teamProgress = new JProgressBar(0, 100);
        teamProgress.setStringPainted(true);
        teamProgress.setString("Ready");
        teamProgress.setMaximumSize(new Dimension(400, 20));
        teamProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(teamProgress);
        panel.add(Box.createVerticalStrut(8));
        
        JButton btnReset = createButton("Reset to Defaults", this::resetTeamSettings, false);
        btnReset.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btnReset);
        
        return panel;
    }
    
    private JPanel createSpritePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createStyledBorder("Sprite Manager"));
        
        JButton btn = createButton("Download Missing Sprites", this::downloadSprites, false);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btn);
        panel.add(Box.createVerticalStrut(10));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setMaximumSize(new Dimension(400, 20));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(progressBar);
        
        return panel;
    }
    
    private void showFilterDialog() {
        JDialog dlg = new JDialog(frame, "Filter Fusions", true);
        dlg.setLayout(new GridLayout(8, 2, 5, 5));
        
        JTextField txtType = new JTextField();
        JTextField txtAbility = new JTextField();
        JTextField txtHP = new JTextField("0");
        JTextField txtAtk = new JTextField("0");
        JTextField txtDef = new JTextField("0");
        JTextField txtSpA = new JTextField("0");
        JTextField txtSpD = new JTextField("0");
        JTextField txtSpe = new JTextField("0");
        
        dlg.add(new JLabel("Type contains:")); dlg.add(txtType);
        dlg.add(new JLabel("Ability contains:")); dlg.add(txtAbility);
        dlg.add(new JLabel("Min HP:")); dlg.add(txtHP);
        dlg.add(new JLabel("Min Attack:")); dlg.add(txtAtk);
        dlg.add(new JLabel("Min Defense:")); dlg.add(txtDef);
        dlg.add(new JLabel("Min Sp. Atk:")); dlg.add(txtSpA);
        dlg.add(new JLabel("Min Sp. Def:")); dlg.add(txtSpD);
        dlg.add(new JLabel("Min Speed:")); dlg.add(txtSpe);
        
        JButton apply = new JButton("Apply Filter");
        apply.addActionListener(e -> {
            FusionFilter filter = new FusionFilter();
            filter.typeConstraint = txtType.getText();
            filter.abilityConstraint = txtAbility.getText();
            try {
                filter.minHP = Integer.parseInt(txtHP.getText());
                filter.minAtk = Integer.parseInt(txtAtk.getText());
                filter.minDef = Integer.parseInt(txtDef.getText());
                filter.minSpa = Integer.parseInt(txtSpA.getText());
                filter.minSpd = Integer.parseInt(txtSpD.getText());
                filter.minSpe = Integer.parseInt(txtSpe.getText());
            } catch (Exception ignored) {}
            
            List<Fusion> filtered = filter.apply(calculatedFusions);
            updateFusionTable(filtered); 
            log("Filter applied. Showing " + filtered.size() + " fusions.");
            dlg.dispose();
        });
        
        JPanel btnP = new JPanel(); btnP.add(apply);
        dlg.add(new JLabel("")); dlg.add(btnP);
        dlg.pack();
        dlg.setLocationRelativeTo(frame);
        dlg.setVisible(true);
    }

    // --- HELPER METHODS ---
    private TitledBorder createStyledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1), title);
        border.setTitleFont(new Font("Segoe UI", Font.BOLD, 12));
        border.setTitleColor(ACCENT);
        return border;
    }
    
    private JButton createButton(String text, Runnable action, boolean primary) {
        JButton btn = new JButton(text);
        btn.setFont(BTN_FONT);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBackground(primary ? ACCENT : Color.WHITE);
        // FIX: User requested BLACK text
        btn.setForeground(primary ? Color.WHITE : Color.BLACK); 
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? ACCENT : new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        btn.addActionListener(e -> action.run());
        return btn;
    }
    
    private JPanel createSpinnerRow(String label, JSpinner spinner) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(Color.WHITE);
        p.setMaximumSize(new Dimension(400, 30));
        p.add(new JLabel(label), BorderLayout.WEST);
        p.add(spinner, BorderLayout.CENTER);
        return p;
    }
    
    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(msg + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }
    
    public JTabbedPane createTabbedPane() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabs.addTab("Rankings", createRankingsTab());
        tabs.addTab("Team Builder", createTeamTab());
        tabs.addTab("Strategy Heatmap", strategyPanel);
        tabs.addTab("Scoring", createScoringTab());
        tabs.addTab("Pokedex", createPokedexTab());
        tabs.addTab("Logs", createLogsTab());
        return tabs;
    }

    private JPanel createRankingsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        String[] cols = {"Head", "Body", "Types", "Ability", "Role", "HP", "Atk", "Def", 
                         "SpA", "SpD", "Spe", "BST", "Score"};
        fusionTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex >= 5 && columnIndex <= 11) return Integer.class;
                if (columnIndex == 12) return Double.class;
                return String.class;
            }
        };
        fusionTable = new JTable(fusionTableModel);
        fusionTable.setAutoCreateRowSorter(true);
        fusionTable.setRowHeight(26);
        fusionTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        fusionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        setupTableMenu(fusionTable, true);
        panel.add(new JScrollPane(fusionTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Color.WHITE);
        bottom.add(createButton("Filter", this::showFilterDialog, false));
        bottom.add(createButton("Copy to Clipboard", () -> copyTable(fusionTable), false));
        bottom.add(createButton("Export CSV", () -> exportData(fusionTable, "fusions.csv"), false));
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createTeamTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        String[] cols = {"Team/Score", "Head", "Body", "Types", "Ability", "Role", "Score"};
        teamTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        teamTable = new JTable(teamTableModel);
        teamTable.setRowHeight(26);
        teamTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        teamTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        teamTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        setupTableMenu(teamTable, false);
        panel.add(new JScrollPane(teamTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(Color.WHITE);
        bottom.add(createButton("Copy to Clipboard", () -> copyTable(teamTable), false));
        bottom.add(createButton("Export CSV", () -> exportData(teamTable, "teams.csv"), false));
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createScoringTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);
        txtAlgoInfo = new JTextArea(12, 60);
        txtAlgoInfo.setEditable(false);
        txtAlgoInfo.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtAlgoInfo.setBackground(new Color(248, 248, 248));
        JScrollPane scroll = new JScrollPane(txtAlgoInfo);
        scroll.setMaximumSize(new Dimension(800, 250));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(20));
        JLabel weightLabel = new JLabel("Adjust Scoring Weights:");
        weightLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        weightLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(weightLabel);
        panel.add(Box.createVerticalStrut(15));
        sldStatWeight = new JSlider(0, 100, 40);
        sldTypeWeight = new JSlider(0, 100, 30);
        sldAbilityWeight = new JSlider(0, 100, 25);
        sldMoveWeight = new JSlider(0, 100, 5);
        panel.add(createSlider("Base Stats:", sldStatWeight, lblStatW = new JLabel("40%")));
        panel.add(createSlider("Type Synergy:", sldTypeWeight, lblTypeW = new JLabel("30%")));
        panel.add(createSlider("Ability:", sldAbilityWeight, lblAbiW = new JLabel("25%")));
        panel.add(createSlider("Moveset:", sldMoveWeight, lblMoveW = new JLabel("5%")));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.add(createButton("Recalculate with New Weights", this::runCalculation, true));
        btnPanel.add(createButton("Reset to Defaults", this::resetWeights, false));
        panel.add(btnPanel);
        panel.add(Box.createVerticalGlue());
        updateAlgoInfo();
        return panel;
    }

    private JPanel createPokedexTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(350);
        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.setBorder(new EmptyBorder(10, 10, 10, 10));
        left.setBackground(Color.WHITE);
        txtDexSearch = new JTextField();
        txtDexSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        modelDex = new DefaultListModel<>();
        listDex = new JList<>(modelDex);
        listDex.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setupSearchLogic(txtDexSearch, modelDex, listDex, () -> {});
        listDex.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showPokedexEntry(listDex.getSelectedValue());
        });
        left.add(txtDexSearch, BorderLayout.NORTH);
        left.add(new JScrollPane(listDex), BorderLayout.CENTER);
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(20, 20, 20, 20));
        right.setBackground(Color.WHITE);
        lblDexImage = new JLabel("Select a Pokemon", SwingConstants.CENTER);
        lblDexImage.setPreferredSize(new Dimension(220, 220));
        lblDexImage.setMaximumSize(new Dimension(220, 220));
        lblDexImage.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        lblDexImage.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblDexImage.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblDexImage.setForeground(Color.GRAY);
        txtDexStats = new JTextArea(18, 35);
        txtDexStats.setEditable(false);
        txtDexStats.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtDexStats.setBorder(createStyledBorder("Stats & Abilities"));
        txtDexStats.setBackground(new Color(248, 248, 248));
        right.add(lblDexImage);
        right.add(Box.createVerticalStrut(15));
        right.add(new JScrollPane(txtDexStats));
        split.setLeftComponent(left);
        split.setRightComponent(right);
        panel.add(split);
        return panel;
    }

    private JPanel createLogsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void runCalculation() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            log("!!! Calculation Cancelled !!!");
            return;
        }

        if (modelRoster.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Roster is empty!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentTask = new TaskController();
        isCalculating.set(true);
        log("=== STARTING CALCULATION (Click again to Cancel) ===");
        log("Generating exhaustive ability combinations...");
        
        new Thread(() -> {
            List<Pokemon> roster = new ArrayList<>();
            for (int i = 0; i < modelRoster.getSize(); i++) {
                roster.add(data.pokemon.get(modelRoster.getElementAt(i)));
            }

            ScoringWeights weights = new ScoringWeights(
                sldStatWeight.getValue() / 100.0,
                sldTypeWeight.getValue() / 100.0,
                sldAbilityWeight.getValue() / 100.0,
                sldMoveWeight.getValue() / 100.0
            );

            FusionPool pool = new FusionPool();
            int approximatePairs = roster.size() * roster.size();

            calculator.calculateAll(roster, weights, chkHiddenPenalty.isSelected(), pool, currentTask, (count) -> {
                 SwingUtilities.invokeLater(() -> {
                     calcProgress.setValue((int)((count / (float)approximatePairs) * 100));
                     calcProgress.setString("Pairs: " + count + " / " + approximatePairs);
                 });
            });

            if (!currentTask.isCancelled()) {
                pool.sort();
                List<Fusion> results = pool.getList();
                
                SwingUtilities.invokeLater(() -> {
                    updateFusionTable(results);
                    this.calculatedFusions = results;
                    calcProgress.setValue(100);
                    calcProgress.setString("Done! Generated " + results.size() + " unique variants.");
                    log("Finished. Total variants generated: " + results.size());
                    log("Note: Table contains every possible ability combination.");
                });
            }
            
            isCalculating.set(false);
            currentTask.finish();
            
        }).start();
    }

    private void runTeamBuilder() {
        if (isBuilding.get()) {
            JOptionPane.showMessageDialog(frame, "Team building already in progress!", 
                "Busy", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (calculatedFusions.isEmpty()) {
            JOptionPane.showMessageDialog(frame, 
                "Run 'Calculate All Fusions' first!", "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Reset current task
        if (currentTask != null) currentTask.cancel();
        currentTask = new TaskController();

        isBuilding.set(true);
        teamProgress.setValue(0);
        
        TeamBuildConfig config = new TeamBuildConfig(
            (Integer) spinNumTeams.getValue(),
            (Integer) spinIterations.getValue(),
            (Integer) spinRandomness.getValue(),
            chkAllowSameSpeciesInTeam.isSelected(), 
            chkSharedTypes.isSelected(),
            chkSelfFusion.isSelected(),
            cmbAlgorithmMode.getSelectedIndex()  // 0=Speed, 1=Balanced, 2=Quality, 3=Maximum
        );
        
        new Thread(() -> {
            log("\n=== TEAM BUILDING STARTED ===");
            String[] modeNames = {"Speed", "Balanced", "Quality", "Maximum (Exhaustive)"};
            log("Algorithm: " + modeNames[config.exhaustiveMode]);
            log("Constraints: Species Dupes=" + config.allowSameSpeciesInTeam + ", Shared Types=" + config.allowSharedTypes);
            
            long start = System.currentTimeMillis();
            
            // Pass the currentTask to support cancellation if you add a Cancel button later
            List<Team> teams = teamBuilder.buildTeams(calculatedFusions, pinnedFusions, config, currentTask,
                (current, total) -> {
                    SwingUtilities.invokeLater(() -> {
                        teamProgress.setValue((int)((current / (float)total) * 100));
                        teamProgress.setString("Building team " + current + "/" + total);
                    });
                });
            
            teams.sort((a, b) -> Double.compare(b.realScore, a.realScore));
            
            SwingUtilities.invokeLater(() -> {
                teamTableModel.setRowCount(0);
                
                if (teams.isEmpty()) {
                    log("Warning: No valid teams found!"); 
                } else {
                    for (int i = 0; i < teams.size(); i++) {
                        Team t = teams.get(i);
                        String balanceInfo = t.balanceBonus != 0 ? 
                            String.format(" (div: %+.2f)", t.balanceBonus) : "";
                        
                        teamTableModel.addRow(new Object[]{
                            "Team " + (i+1) + balanceInfo, "", "", "", "", "", 
                            String.format("%.3f", t.realScore)
                        });
                        
                        // Sort members by individual score desc
                        t.members.sort((a, b) -> Double.compare(b.score, a.score));
                        
                        for (Fusion f : t.members) {
                            teamTableModel.addRow(new Object[]{
                                "", cap(f.headName), cap(f.bodyName), f.typing, 
                                f.chosenAbility, f.role, f.score
                            });
                        }
                        teamTableModel.addRow(new Object[]{"", "", "", "", "", "", ""});
                    }
                    
                    // Update Strategy Panel with best team
                    if (!teams.isEmpty()) {
                        strategyPanel.displayTeam(teams.get(0));
                    }
                }
                
                teamProgress.setValue(100);
                teamProgress.setString("Complete! " + teams.size() + " teams");
                
                long elapsed = System.currentTimeMillis() - start;
                log("Built " + teams.size() + " teams in " + elapsed + "ms");
                isBuilding.set(false);
            });
        }).start();
    }

    private void setupTableMenu(JTable table, boolean allowPin) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem detailItem = new JMenuItem("Show Scoring Details");
        detailItem.addActionListener(e -> showDetails(table));
        menu.add(detailItem);
        if (allowPin) {
            JMenuItem pinItem = new JMenuItem("Pin/Unpin Fusion (Force into Team)");
            pinItem.addActionListener(e -> togglePin());
            menu.addSeparator();
            menu.add(pinItem);
        }
        table.setComponentPopupMenu(menu);
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showDetails(table);
            }
        });
    }

    private void togglePin() {
        int row = fusionTable.getSelectedRow();
        if (row == -1) return;
        int modelRow = fusionTable.convertRowIndexToModel(row);
        if (modelRow >= calculatedFusions.size()) return;
        Fusion f = calculatedFusions.get(modelRow);
        if (pinnedFusions.contains(f)) {
            pinnedFusions.remove(f);
            log("Unpinned: " + f.getDisplayName());
        } else {
            if (pinnedFusions.size() >= 6) {
                JOptionPane.showMessageDialog(frame, "Cannot pin more than 6!");
                return;
            }
            pinnedFusions.add(f);
            log("Pinned: " + f.getDisplayName());
        }
    }

    private void showDetails(JTable table) {
        int row = table.getSelectedRow();
        if (row == -1) return;
        if (table == fusionTable) {
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= calculatedFusions.size()) return;
            Fusion f = calculatedFusions.get(modelRow);
            showDetailsPopup(f);
        } else if (table == teamTable) {
            JOptionPane.showMessageDialog(frame, "Please view details in the Rankings tab.");
        }
    }
    
    private void showDetailsPopup(Fusion f) {
        ScoringWeights weights = new ScoringWeights(
            sldStatWeight.getValue()/100.0, sldTypeWeight.getValue()/100.0,
            sldAbilityWeight.getValue()/100.0, sldMoveWeight.getValue()/100.0
        );
        String report = calculator.getDetailedBreakdown(f, weights);
        JTextArea area = new JTextArea(report);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        area.setEditable(false);
        JOptionPane.showMessageDialog(frame, new JScrollPane(area), "Fusion Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportData(JTable table, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                CSVUtils.exportTableToCSV(table, fc.getSelectedFile());
                log("Exported data to " + fc.getSelectedFile().getName());
            } catch (Exception e) {
                log("Error exporting: " + e.getMessage());
            }
        }
    }

    private void updateFusionTable(List<Fusion> list) {
        fusionTableModel.setRowCount(0);
        for (Fusion f : list) {
            fusionTableModel.addRow(new Object[]{
                cap(f.headName), cap(f.bodyName), f.typing, f.chosenAbility, f.role,
                f.hp, f.atk, f.def, f.spa, f.spd, f.spe, f.bst, f.score
            });
        }
    }

    private String cap(String s) {
        return s == null || s.isEmpty() ? s : 
            s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
    
    private String normalizeName(String name) {
        if (name == null) return "";
        String n = name.toLowerCase().trim();
        n = n.replace("'", "").replace(".", "").replace(" ", "-");
        n = n.replace("♀", "-f").replace("♂", "-m");
        n = n.replace("é", "e");
        return n;
    }

    private void downloadSprites() {
        new Thread(() -> {
            log("\n=== SPRITE DOWNLOAD STARTED ===");
            List<String> names = new ArrayList<>(data.pokemon.getAllNames());
            AtomicInteger downloaded = new AtomicInteger(0);
            AtomicInteger skipped = new AtomicInteger(0);
            for (int i = 0; i < names.size(); i++) {
                String raw = names.get(i);
                final int prog = i;
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue((int)((prog / (float)names.size()) * 100));
                    progressBar.setString("Processing: " + raw);
                });
                if (data.sprites.downloadSprite(raw)) {
                    downloaded.incrementAndGet();
                    log("Downloaded: " + raw);
                    try { Thread.sleep(50); } catch (Exception ignored) {}
                } else {
                    skipped.incrementAndGet();
                }
            }
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(100);
                progressBar.setString("Complete!");
                log("Downloaded: " + downloaded.get() + " | Skipped/Failed: " + skipped.get());
            });
        }).start();
    }
    
    private void loadSprite(String pokemonName) {
        ImageIcon icon = data.sprites.getSprite(pokemonName);
        if (icon != null) {
            lblDexImage.setIcon(icon);
            lblDexImage.setText("");
        } else {
            lblDexImage.setIcon(null);
            lblDexImage.setText("Sprite not available");
        }
    }
    
    private void generateTypeIcons() {
        File folder = new File("type_icons");
        if (!folder.exists()) folder.mkdirs();
        Map<String, Color> typeColors = new HashMap<>();
        typeColors.put("normal", new Color(168, 168, 120));
        typeColors.put("fire", new Color(240, 128, 48));
        typeColors.put("water", new Color(104, 144, 240));
        typeColors.put("electric", new Color(248, 208, 48));
        typeColors.put("grass", new Color(120, 200, 80));
        typeColors.put("ice", new Color(152, 216, 216));
        typeColors.put("fighting", new Color(192, 48, 40));
        typeColors.put("poison", new Color(160, 64, 160));
        typeColors.put("ground", new Color(224, 192, 104));
        typeColors.put("flying", new Color(168, 144, 240));
        typeColors.put("psychic", new Color(248, 88, 136));
        typeColors.put("bug", new Color(168, 184, 32));
        typeColors.put("rock", new Color(184, 160, 56));
        typeColors.put("ghost", new Color(112, 88, 152));
        typeColors.put("dragon", new Color(112, 56, 248));
        typeColors.put("dark", new Color(112, 88, 72));
        typeColors.put("steel", new Color(184, 184, 208));
        typeColors.put("fairy", new Color(238, 153, 172));
        for (Map.Entry<String, Color> entry : typeColors.entrySet()) {
            File f = new File(folder, entry.getKey() + ".png");
            if (!f.exists()) {
                try {
                    BufferedImage img = new BufferedImage(40, 16, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    GradientPaint gradient = new GradientPaint(
                        0, 0, entry.getValue(), 0, 16, entry.getValue().darker());
                    g.setPaint(gradient);
                    g.fillRoundRect(0, 0, 40, 16, 4, 4);
                    g.setColor(entry.getValue().darker().darker());
                    g.drawRoundRect(0, 0, 39, 15, 4, 4);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("SansSerif", Font.BOLD, 9));
                    String label = entry.getKey().substring(0, 3).toUpperCase();
                    FontMetrics fm = g.getFontMetrics();
                    int textX = (40 - fm.stringWidth(label)) / 2;
                    int textY = (16 + fm.getAscent() - fm.getDescent()) / 2;
                    g.setColor(new Color(0, 0, 0, 100));
                    g.drawString(label, textX + 1, textY + 1);
                    g.setColor(Color.WHITE);
                    g.drawString(label, textX, textY);
                    g.dispose();
                    ImageIO.write(img, "png", f);
                } catch (Exception ex) {
                    System.err.println("Failed to generate icon for " + entry.getKey());
                }
            }
        }
    }

    private void loadTypeIcons() {
        File folder = new File("type_icons");
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".png")) {
                        try {
                            BufferedImage img = ImageIO.read(f);
                            String typeName = f.getName().replace(".png", "");
                            typeIconCache.put(typeName, new ImageIcon(img));
                        } catch (Exception e) {
                            System.err.println("Failed to load type icon: " + f.getName());
                        }
                    }
                }
            }
        }
    }

    private void copyTable(JTable table) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < table.getColumnCount(); i++) {
            sb.append(table.getColumnName(i));
            if (i < table.getColumnCount() - 1) sb.append("\t");
        }
        sb.append("\n");
        for (int i = 0; i < table.getRowCount(); i++) {
            for (int j = 0; j < table.getColumnCount(); j++) {
                Object v = table.getValueAt(i, j);
                sb.append(v == null ? "" : v.toString());
                if (j < table.getColumnCount() - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        StringSelection sel = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        log("Copied " + table.getRowCount() + " rows to clipboard");
    }

    private void setupSearchLogic(JTextField field, DefaultListModel<String> model, JList<String> list, Runnable enterAction) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterList(field, model, list); }
            public void removeUpdate(DocumentEvent e) { filterList(field, model, list); }
            public void changedUpdate(DocumentEvent e) { filterList(field, model, list); }
        });
        field.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && model.getSize() > 0) {
                    list.requestFocus();
                    if (list.getSelectedIndex() == -1) list.setSelectedIndex(0);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && model.getSize() > 0) {
                    if (list.getSelectedIndex() == -1) list.setSelectedIndex(0);
                    enterAction.run();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    enterAction.run();
                    field.requestFocus();
                }
            }
        });
    }

    private void filterList(JTextField field, DefaultListModel<String> model, JList<String> list) {
        String query = field.getText().trim().toLowerCase();
        model.clear();
        List<String> results = data.pokemon.search(query);
        for (String m : results) {
            model.addElement(cap(m));
        }
        if (model == modelSearchResults) {
            borderSearch.setTitle("Available (" + results.size() + ")");
            listSearchResults.repaint();
        }
        if (!model.isEmpty() && list.getSelectedIndex() == -1) {
            list.setSelectedIndex(0);
        }
    }

    private void addToRoster() {
        int idx = listSearchResults.getSelectedIndex();
        if (idx == -1 && modelSearchResults.getSize() > 0) idx = 0;
        if (idx == -1) return;
        String selected = modelSearchResults.getElementAt(idx);
        if (!modelRoster.contains(selected)) {
            modelRoster.addElement(selected);
            updateRosterBorder();
            log("+ Added " + selected);
        }
    }

    private void addAllToRoster() {
        int added = 0;
        for (int i = 0; i < modelSearchResults.getSize(); i++) {
            String s = modelSearchResults.getElementAt(i);
            if (!modelRoster.contains(s)) {
                modelRoster.addElement(s);
                added++;
            }
        }
        updateRosterBorder();
        log("+ Added " + added + " Pokemon");
    }

    private void removeFromRoster() {
        int idx = listRoster.getSelectedIndex();
        if (idx >= 0) {
            String removed = modelRoster.remove(idx);
            updateRosterBorder();
            log("- Removed " + removed);
            if (idx < modelRoster.getSize()) listRoster.setSelectedIndex(idx);
        }
    }

    private void clearRoster() {
        int count = modelRoster.getSize();
        modelRoster.clear();
        updateRosterBorder();
        log("Cleared roster (" + count + " removed)");
    }

    // New methods for legendary management
    private void addAllLegendaries() {
        int added = 0;
        for (String legendary : legendarySet) {
            Pokemon p = data.pokemon.get(legendary);
            if (p != null) {
                String name = p.name;
                if (!modelRoster.contains(name)) {
                    modelRoster.addElement(name);
                    added++;
                }
            }
        }
        updateRosterBorder();
        log("+ Added " + added + " legendary Pokémon");
    }

    private void removeAllLegendaries() {
        int removed = 0;
        List<String> toRemove = new ArrayList<>();
        
        for (int i = 0; i < modelRoster.getSize(); i++) {
            String name = modelRoster.getElementAt(i);
            if (legendarySet.contains(name.toLowerCase())) {
                toRemove.add(name);
                removed++;
            }
        }
        
        for (String name : toRemove) {
            modelRoster.removeElement(name);
        }
        
        updateRosterBorder();
        log("- Removed " + removed + " legendary Pokémon");
    }

    private void updateRosterBorder() {
        borderRoster.setTitle("My Roster (" + modelRoster.size() + ")");
        listRoster.getParent().getParent().repaint();
    }

    private void resetWeights() {
        sldStatWeight.setValue(40);
        sldTypeWeight.setValue(30);
        sldAbilityWeight.setValue(25);
        sldMoveWeight.setValue(5);
        log("Weights reset to defaults");
    }

    private void resetTeamSettings() {
        spinNumTeams.setValue(5);
        spinIterations.setValue(2000);
        spinRandomness.setValue(3);
        cmbAlgorithmMode.setSelectedIndex(1); // Reset to Balanced
        chkAllowSameSpeciesInTeam.setSelected(false);
        chkSharedTypes.setSelected(false);
        chkSelfFusion.setSelected(false);
        log("Team settings reset to defaults");
    }

    private void updateAlgoInfo() {
        double total = sldStatWeight.getValue() + sldTypeWeight.getValue() + 
                       sldAbilityWeight.getValue() + sldMoveWeight.getValue();
        StringBuilder sb = new StringBuilder();
        sb.append("=======================================================\n");
        sb.append("FUSION SCORING ALGORITHM v9.1 - ENHANCED\n");
        sb.append("=======================================================\n\n");
        sb.append("COMPONENT WEIGHTS (normalized to 1.0):\n");
        sb.append(String.format("   * Base Stats:     %.2f\n", sldStatWeight.getValue() / total));
        sb.append(String.format("   * Type Synergy:   %.2f\n", sldTypeWeight.getValue() / total));
        sb.append(String.format("   * Ability:        %.2f\n", sldAbilityWeight.getValue() / total));
        sb.append(String.format("   * Moveset:        %.2f\n\n", sldMoveWeight.getValue() / total));
        sb.append("ABILITY FEATURES:\n");
        sb.append("   * All abilities ranked per fusion\n");
        sb.append("   * 40+ synergy patterns\n");
        sb.append("   * Negative synergies for bad combos\n\n");
        sb.append("TEAM BALANCE BONUSES:\n");
        sb.append("   * Role diversity: +0.03 to +0.15\n");
        sb.append("   * Coverage bonus: +0.05 to +0.10\n");
        sb.append("   * Imbalance penalty: -0.05 to -0.10\n\n");
        sb.append("DIMINISHING RETURNS:\n");
        sb.append("   * Scores above 0.85 face 70% penalty\n");
        sb.append("   * Prevents excessive 1.0 scores\n");
        sb.append("=======================================================");
        txtAlgoInfo.setText(sb.toString());
    }

    private void showPokedexEntry(String name) {
        if (name == null) return;
        Pokemon p = data.pokemon.get(name);
        if (p == null) {
            txtDexStats.setText("Pokemon not found.");
            return;
        }
        
        PokedexDatabase.PokedexInfo info = pokedexDB.getInfo(name);
        
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append(String.format("   %-32s  \n", name.toUpperCase()));
        sb.append("========================================\n");
        sb.append(String.format("   Type: %-13s / %-10s \n", cap(p.type1), cap(p.type2)));
        
        if (info != null) {
            sb.append("========================================\n");
            if (info.evolution != null && !info.evolution.isEmpty()) {
                sb.append("   Evolution: ").append(info.evolution).append("\n");
            }
            if (info.locations != null && !info.locations.isEmpty()) {
                sb.append("   Locations: ").append(info.locations).append("\n");
            }
            if (info.notes != null && !info.notes.isEmpty()) {
                sb.append("   Notes: ").append(info.notes).append("\n");
            }
        }
        
        sb.append("========================================\n");
        sb.append(String.format("   HP:  %3d    SpA: %3d    BST: %3d  \n", p.hp, p.spa, p.getBST()));
        sb.append(String.format("   Atk: %3d    SpD: %3d              \n", p.atk, p.spd));
        sb.append(String.format("   Def: %3d    Spe: %3d              \n", p.def, p.spe));
        sb.append("========================================\n");
        sb.append("   ABILITIES (with multipliers)         \n");
        sb.append("========================================\n");
        for (int i = 0; i < p.abilities.size(); i++) {
            String ab = p.abilities.get(i);
            if (ab != null && !ab.equalsIgnoreCase("None") && !ab.isEmpty()) {
                String label = (i == 2) ? "Hidden" : "Ability " + (i + 1);
                double mult = data.abilities.getScore(ab);
                sb.append(String.format("   %-10s: %-16s [%.2f]\n", label, ab, mult));
            }
        }
        sb.append("========================================");
        
        // Check if legendary
        if (legendarySet.contains(name.toLowerCase())) {
            sb.append("\n   ✨ LEGENDARY/MYTHICAL POKÉMON ✨");
        }
        
        txtDexStats.setText(sb.toString());
        loadSprite(p.name);
    }
    
    // Class for Pokemon List Renderer
    class PokemonListRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel nameLabel = new JLabel();
        private JLabel iconLabel = new JLabel();
        
        public PokemonListRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 3, 1));
            setBorder(new EmptyBorder(2, 3, 2, 3));
            setOpaque(true);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            add(nameLabel);
            add(iconLabel);
        }
        
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value);
            iconLabel.setIcon(null);
            
            Pokemon p = data.pokemon.get(value);
            if (p != null) {
                ImageIcon i1 = typeIconCache.get(p.type1.toLowerCase());
                ImageIcon i2 = p.type2.equalsIgnoreCase("None") ? null : typeIconCache.get(p.type2.toLowerCase());
                
                if (i1 != null) {
                    if (i2 != null) {
                        BufferedImage combined = new BufferedImage(80, 16, BufferedImage.TYPE_INT_ARGB);
                        Graphics g = combined.getGraphics();
                        g.drawImage(i1.getImage(), 0, 0, null);
                        g.drawImage(i2.getImage(), 40, 0, null);
                        g.dispose();
                        iconLabel.setIcon(new ImageIcon(combined));
                    } else {
                        iconLabel.setIcon(i1);
                    }
                }
            }
            
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
            }
            
            return this;
        }
    }
    
    public void initializeData() {
        for (String s : new String[]{"Kyurem", "Zekrom", "Sylveon", "Entei", "Metagross", "Salamence"}) {
            modelRoster.addElement(s);
        }
        updateRosterBorder();
        filterList(txtRosterSearch, modelSearchResults, listSearchResults);
        filterList(txtDexSearch, modelDex, listDex);
    }
}
