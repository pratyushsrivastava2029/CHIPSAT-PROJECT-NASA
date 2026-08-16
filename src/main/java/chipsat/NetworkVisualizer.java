package chipsat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class NetworkVisualizer extends JFrame {
    private final Random telemetryRandom = new Random(61L);
    private final List<ChipSat> satellites;
    private final TelemetryNetwork network;
    private final NetworkPanel networkPanel;
    private JTextArea eventLog;
    private JComboBox<String> sourceSelector;
    private JComboBox<String> failureSelector;
    private JComboBox<String> routingSelector;
    private JLabel sentValue;
    private JLabel deliveredValue;
    private JLabel droppedValue;
    private JLabel rateValue;
    private JLabel hopsValue;
    private JLabel queuedValue;
    private JLabel statusValue;
    private JButton autoButton;
    private final javax.swing.Timer autoTimer;

    private List<Integer> activeRoute = Collections.emptyList();
    private int animationHop = -1;
    private boolean autoRunning = false;

    public NetworkVisualizer() {
        super("ChipSat Telemetry Mesh");

        satellites = createSatellites();
        GroundStation ground = new GroundStation(8, 8, 26);

        network = new TelemetryNetwork(
                satellites,
                ground,
                28,
                0.035,
                61L
        );

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.setBackground(new Color(10, 15, 26));
        setContentPane(root);

        root.add(buildTopBar(), BorderLayout.NORTH);

        networkPanel = new NetworkPanel();
        root.add(networkPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(340, 0));
        rightPanel.add(buildControls(), BorderLayout.NORTH);
        rightPanel.add(buildEventPanel(), BorderLayout.CENTER);
        rightPanel.add(buildMetricsPanel(), BorderLayout.SOUTH);
        root.add(rightPanel, BorderLayout.EAST);

        autoTimer = new javax.swing.Timer(1250, e -> autoStep());

        appendLog("SYSTEM", "Network initialized with " + satellites.size() + " ChipSats.");
        appendLog("SYSTEM", "Mission policy active. Generate telemetry and watch the network decide whether to transmit, store, reroute, or wait.");
        refreshMetrics();
    }

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ChipSat Telemetry Mesh");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(new Color(241, 245, 249));

        JLabel subtitle = new JLabel("Fault-tolerant multi-hop satellite telemetry simulator");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(new Color(148, 163, 184));

        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(subtitle);

        statusValue = new JLabel("● NETWORK ONLINE");
        statusValue.setFont(new Font("Monospaced", Font.BOLD, 13));
        statusValue.setForeground(new Color(74, 222, 128));

        panel.add(titleBlock, BorderLayout.WEST);
        panel.add(statusValue, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildControls() {
        JPanel card = makeCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel heading = heading("Mission Controls");
        card.add(heading);
        card.add(Box.createVerticalStrut(12));

        sourceSelector = new JComboBox<>();
        for (ChipSat sat : satellites) {
            sourceSelector.addItem("Sat-" + sat.getId());
        }
        styleCombo(sourceSelector);

        routingSelector = new JComboBox<>(new String[]{
                "Protect Mission-Critical Data",
                "Minimize Relay Usage"
        });
        styleCombo(routingSelector);

        failureSelector = new JComboBox<>();
        for (ChipSat sat : satellites) {
            failureSelector.addItem("Sat-" + sat.getId());
        }
        styleCombo(failureSelector);

        card.add(labeled("Telemetry source", sourceSelector));
        card.add(Box.createVerticalStrut(8));
        card.add(labeled("Mission policy", routingSelector));
        card.add(Box.createVerticalStrut(6));

        JTextArea policyHelp = new JTextArea(
                "Protect Mission-Critical Data: avoids weak relays and preserves delivery reliability.\n"
                        + "Minimize Relay Usage: uses the fewest spacecraft when an immediate route exists."
        );
        policyHelp.setEditable(false);
        policyHelp.setLineWrap(true);
        policyHelp.setWrapStyleWord(true);
        policyHelp.setOpaque(false);
        policyHelp.setForeground(new Color(148, 163, 184));
        policyHelp.setFont(new Font("SansSerif", Font.PLAIN, 11));
        policyHelp.setMaximumSize(new Dimension(245, 76));
        card.add(policyHelp);
        card.add(Box.createVerticalStrut(12));

        JButton sendButton = primaryButton("GENERATE TELEMETRY");
        sendButton.addActionListener(e -> sendSelectedTelemetry());
        card.add(sendButton);

        card.add(Box.createVerticalStrut(12));
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(51, 65, 85));
        card.add(sep);
        card.add(Box.createVerticalStrut(12));

        card.add(labeled("Failure target", failureSelector));
        card.add(Box.createVerticalStrut(8));

        JPanel failureButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        failureButtons.setOpaque(false);

        JButton failButton = dangerButton("FAIL NODE");
        failButton.addActionListener(e -> failSelectedSatellite());

        JButton recoverButton = secondaryButton("RECOVER");
        recoverButton.addActionListener(e -> recoverSelectedSatellite());

        failureButtons.add(failButton);
        failureButtons.add(recoverButton);
        card.add(failureButtons);

        card.add(Box.createVerticalStrut(8));

        JButton moveButton = secondaryButton("ADVANCE ORBIT STEP");
        moveButton.addActionListener(e -> {
            network.moveSatellites();
            activeRoute = Collections.emptyList();
            animationHop = -1;
            appendLog("TOPOLOGY", "Orbital step advanced; communication graph rebuilt.");
            flushStoredTelemetry();
            networkPanel.repaint();
        });
        card.add(moveButton);

        card.add(Box.createVerticalStrut(8));

        autoButton = secondaryButton("START AUTO DEMO");
        autoButton.addActionListener(e -> toggleAuto());
        card.add(autoButton);

        return card;
    }

    private JScrollPane buildEventPanel() {
        eventLog = new JTextArea();
        eventLog.setEditable(false);
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(true);
        eventLog.setBackground(new Color(15, 23, 42));
        eventLog.setForeground(new Color(203, 213, 225));
        eventLog.setCaretColor(Color.WHITE);
        eventLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        eventLog.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(eventLog);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder()
        ));
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        return scroll;
    }

    private JPanel buildMetricsPanel() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 10));

        card.add(heading("Live Metrics"), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 3, 8, 8));
        grid.setOpaque(false);

        sentValue = new JLabel("0");
        deliveredValue = new JLabel("0");
        droppedValue = new JLabel("0");
        rateValue = new JLabel("0%");
        hopsValue = new JLabel("0.0");
        queuedValue = new JLabel("0");

        grid.add(metric("SENT", sentValue));
        grid.add(metric("DELIVERED", deliveredValue));
        grid.add(metric("DROPPED", droppedValue));
        grid.add(metric("SUCCESS", rateValue));
        grid.add(metric("RELAY LOAD", hopsValue));
        grid.add(metric("ONBOARD", queuedValue));

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JPanel labeled(String text, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        JLabel label = new JLabel(text);
        label.setForeground(new Color(148, 163, 184));
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        return panel;
    }

    private JPanel metric(String labelText, JLabel value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(22, 30, 46));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel label = new JLabel(labelText);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setForeground(new Color(100, 116, 139));
        label.setFont(new Font("SansSerif", Font.BOLD, 9));

        value.setAlignmentX(Component.CENTER_ALIGNMENT);
        value.setForeground(new Color(241, 245, 249));
        value.setFont(new Font("Monospaced", Font.BOLD, 17));

        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        panel.add(value);
        return panel;
    }

    private JPanel makeCard() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(15, 23, 42));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(226, 232, 240));
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        return label;
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(new Color(30, 41, 59));
        combo.setForeground(new Color(226, 232, 240));
        combo.setFocusable(false);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    }

    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(37, 99, 235));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        button.setPreferredSize(new Dimension(0, 38));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(30, 41, 59));
        button.setForeground(new Color(226, 232, 240));
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return button;
    }

    private JButton dangerButton(String text) {
        JButton button = secondaryButton(text);
        button.setBackground(new Color(127, 29, 29));
        button.setForeground(new Color(254, 226, 226));
        return button;
    }

    private List<ChipSat> createSatellites() {
        return Arrays.asList(
                new ChipSat(1, 18, 14, 96),
                new ChipSat(2, 34, 23, 88),
                new ChipSat(3, 50, 30, 82),
                new ChipSat(4, 67, 39, 77),
                new ChipSat(5, 49, 51, 91),
                new ChipSat(6, 30, 45, 58),
                new ChipSat(7, 74, 57, 94),
                new ChipSat(8, 55, 70, 74)
        );
    }

    private boolean useDijkstra() {
        return routingSelector.getSelectedIndex() == 0;
    }

    private int selectedSourceId() {
        return sourceSelector.getSelectedIndex() + 1;
    }

    private int selectedFailureId() {
        return failureSelector.getSelectedIndex() + 1;
    }

    private void sendSelectedTelemetry() {
        int sourceId = selectedSourceId();
        ChipSat sat = network.getSatellite(sourceId);

        if (sat == null || !sat.isOnline()) {
            appendLog("DROP", "Sat-" + sourceId + " is offline and cannot generate telemetry.");
            return;
        }

        TelemetryPacket packet = sat.generateTelemetry(telemetryRandom);

        List<Integer> route = useDijkstra()
                ? network.findLowestCostRoute(sourceId)
                : network.findShortestHopRoute(sourceId);

        activeRoute = route;
        animationHop = route.isEmpty() ? -1 : 0;
        networkPanel.repaint();

        if (!route.isEmpty()) {
            appendLog("ROUTE",
                    "Packet " + packet.getPacketId() + ": "
                            + TelemetryNetwork.formatRoute(route)
                            + " [" + (useDijkstra() ? "mission-cost route" : "minimum-relay route") + "]");
            animateRoute(packet);
        } else {
            MissionDecision decision = network.handleTelemetry(packet, useDijkstra());

            if (decision.getStatus() == MissionDecision.Status.STORED) {
                appendLog("STORE", decision.getMessage());
            } else {
                appendLog("DROP", decision.getMessage());
            }

            activeRoute = Collections.emptyList();
            animationHop = -1;
            refreshMetrics();
            networkPanel.repaint();
        }
    }

    private void animateRoute(TelemetryPacket packet) {
        if (activeRoute.size() < 2) {
            finishDelivery(packet);
            return;
        }

        javax.swing.Timer hopTimer = new javax.swing.Timer(280, null);
        hopTimer.addActionListener(e -> {
            animationHop++;
            networkPanel.repaint();

            if (animationHop >= activeRoute.size() - 1) {
                hopTimer.stop();
                finishDelivery(packet);
            }
        });
        hopTimer.start();
    }

    private void finishDelivery(TelemetryPacket packet) {
        MissionDecision decision = network.handleTelemetry(packet, useDijkstra());

        if (decision.getStatus() == MissionDecision.Status.DELIVERED) {
            appendLog("DELIVERED",
                    "Sat-" + packet.getSourceId()
                            + " telemetry reached Ground | "
                            + packet.getPriority()
                            + " | battery="
                            + String.format("%.1f%%", packet.getBatteryPercent()));
        } else if (decision.getStatus() == MissionDecision.Status.STORED) {
            appendLog("STORE", decision.getMessage());
        } else {
            appendLog("DROP", decision.getMessage());
        }

        refreshMetrics();

        javax.swing.Timer clearTimer = new javax.swing.Timer(650, e -> {
            activeRoute = Collections.emptyList();
            animationHop = -1;
            networkPanel.repaint();
        });
        clearTimer.setRepeats(false);
        clearTimer.start();
    }

    private void failSelectedSatellite() {
        int id = selectedFailureId();
        network.failSatellite(id);
        activeRoute = Collections.emptyList();
        animationHop = -1;
        appendLog("FAILURE", "Sat-" + id + " forced offline. Topology rebuilt.");

        int sourceId = selectedSourceId();
        if (network.getSatellite(sourceId).isOnline()) {
            List<Integer> newRoute = useDijkstra()
                    ? network.findLowestCostRoute(sourceId)
                    : network.findShortestHopRoute(sourceId);

            if (newRoute.isEmpty()) {
                appendLog("ROUTE", "Sat-" + sourceId + " currently has NO path to Ground.");
            } else {
                appendLog("REROUTE", "New path: " + TelemetryNetwork.formatRoute(newRoute));
                activeRoute = newRoute;
            }
        }

        statusValue.setText("● DEGRADED / REROUTING");
        statusValue.setForeground(new Color(251, 191, 36));
        networkPanel.repaint();
    }

    private void recoverSelectedSatellite() {
        int id = selectedFailureId();
        network.recoverSatellite(id);
        activeRoute = Collections.emptyList();
        animationHop = -1;
        appendLog("RECOVERY", "Sat-" + id + " restored. Topology rebuilt.");
        flushStoredTelemetry();
        statusValue.setText("● NETWORK ONLINE");
        statusValue.setForeground(new Color(74, 222, 128));
        networkPanel.repaint();
    }

    private void toggleAuto() {
        autoRunning = !autoRunning;

        if (autoRunning) {
            autoTimer.start();
            autoButton.setText("STOP AUTO DEMO");
            appendLog("SYSTEM", "Automatic telemetry simulation started.");
        } else {
            autoTimer.stop();
            autoButton.setText("START AUTO DEMO");
            appendLog("SYSTEM", "Automatic telemetry simulation stopped.");
        }
    }

    private void autoStep() {
        List<ChipSat> online = new ArrayList<>();
        for (ChipSat sat : satellites) {
            if (sat.isOnline()) {
                online.add(sat);
            }
        }

        if (online.isEmpty()) {
            return;
        }

        ChipSat source = online.get(telemetryRandom.nextInt(online.size()));
        sourceSelector.setSelectedIndex(source.getId() - 1);

        if (telemetryRandom.nextDouble() < 0.18) {
            network.moveSatellites();
            appendLog("TOPOLOGY", "Orbital motion changed neighbor links.");
        }

        sendSelectedTelemetry();
    }

    private void flushStoredTelemetry() {
        List<MissionDecision> decisions = network.flushStoredTelemetry(useDijkstra());

        for (MissionDecision decision : decisions) {
            if (decision.getStatus() == MissionDecision.Status.DELIVERED) {
                appendLog("FORWARD", decision.getMessage());
            } else if (decision.getStatus() == MissionDecision.Status.STORED) {
                appendLog("WAIT", decision.getMessage());
            } else {
                appendLog("EXPIRED", decision.getMessage());
            }
        }

        refreshMetrics();
    }

    private void refreshMetrics() {
        NetworkMetrics metrics = network.getMetrics();

        sentValue.setText(String.valueOf(metrics.getSent()));
        deliveredValue.setText(String.valueOf(metrics.getDelivered()));
        droppedValue.setText(String.valueOf(metrics.getDropped()));
        rateValue.setText(String.format("%.0f%%", metrics.getDeliveryRate()));
        hopsValue.setText(String.format("%.1f", metrics.getAverageHops()));
        queuedValue.setText(String.valueOf(network.getTotalQueuedPackets()));
    }

    private void appendLog(String category, String message) {
        String line = String.format("[%9s] %s%n", category, message);
        eventLog.append(line);
        eventLog.setCaretPosition(eventLog.getDocument().getLength());
    }

    private class NetworkPanel extends JPanel {
        private final Color bg = new Color(8, 13, 24);
        private final Color grid = new Color(25, 34, 50);
        private final Color edge = new Color(71, 85, 105);
        private final Color node = new Color(37, 99, 235);
        private final Color offline = new Color(100, 116, 139);
        private final Color route = new Color(34, 211, 238);
        private final Color groundColor = new Color(74, 222, 128);

        NetworkPanel() {
            setBackground(bg);
            setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
            setPreferredSize(new Dimension(760, 650));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            drawGrid(g2);
            drawEdges(g2);
            drawActiveRoute(g2);
            drawGround(g2);
            drawSatellites(g2);

            g2.dispose();
        }

        private void drawGrid(Graphics2D g2) {
            g2.setColor(grid);
            g2.setStroke(new BasicStroke(1f));

            int spacing = 50;
            for (int x = 0; x < getWidth(); x += spacing) {
                g2.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += spacing) {
                g2.drawLine(0, y, getWidth(), y);
            }
        }

        private void drawEdges(Graphics2D g2) {
            Map<Integer, List<Link>> graph = network.getAdjacencySnapshot();
            Set<String> drawn = new HashSet<>();

            g2.setStroke(new BasicStroke(1.5f));

            for (Map.Entry<Integer, List<Link>> entry : graph.entrySet()) {
                int from = entry.getKey();

                for (Link link : entry.getValue()) {
                    int to = link.getDestinationId();

                    String key = Math.min(from, to) + ":" + Math.max(from, to);
                    if (!drawn.add(key)) {
                        continue;
                    }

                    Point2D p1 = screenPoint(from);
                    Point2D p2 = screenPoint(to);

                    if (p1 == null || p2 == null) {
                        continue;
                    }

                    g2.setColor(edge);
                    g2.draw(new Line2D.Double(p1, p2));
                }
            }
        }

        private void drawActiveRoute(Graphics2D g2) {
            if (activeRoute == null || activeRoute.size() < 2) {
                return;
            }

            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));

            for (int i = 0; i < activeRoute.size() - 1; i++) {
                if (animationHop >= 0 && i >= animationHop) {
                    break;
                }

                Point2D p1 = screenPoint(activeRoute.get(i));
                Point2D p2 = screenPoint(activeRoute.get(i + 1));

                if (p1 != null && p2 != null) {
                    g2.setColor(route);
                    g2.draw(new Line2D.Double(p1, p2));
                }
            }

            if (animationHop >= 0 && animationHop < activeRoute.size()) {
                Point2D pulse = screenPoint(activeRoute.get(animationHop));
                if (pulse != null) {
                    g2.setColor(new Color(207, 250, 254));
                    g2.fill(new Ellipse2D.Double(
                            pulse.getX() - 7,
                            pulse.getY() - 7,
                            14,
                            14
                    ));
                }
            }
        }

        private void drawGround(Graphics2D g2) {
            Point2D point = screenPoint(GroundStation.ID);

            double x = point.getX();
            double y = point.getY();

            g2.setColor(new Color(74, 222, 128, 35));
            g2.fill(new Ellipse2D.Double(x - 38, y - 38, 76, 76));

            g2.setColor(groundColor);
            Path2D dish = new Path2D.Double();
            dish.moveTo(x - 12, y - 12);
            dish.curveTo(x - 5, y + 6, x + 10, y + 8, x + 16, y - 10);
            g2.draw(dish);
            g2.draw(new Line2D.Double(x + 3, y + 4, x + 3, y + 18));
            g2.draw(new Line2D.Double(x - 6, y + 18, x + 12, y + 18));

            drawLabel(g2, "GROUND", x, y + 34, groundColor);
        }

        private void drawSatellites(Graphics2D g2) {
            List<ChipSat> sorted = new ArrayList<>(network.getSatellites());
            sorted.sort(Comparator.comparingInt(ChipSat::getId));

            for (ChipSat sat : sorted) {
                Point2D point = screenPoint(sat.getId());
                double x = point.getX();
                double y = point.getY();

                Color body = sat.isOnline() ? node : offline;

                if (activeRoute.contains(sat.getId())) {
                    g2.setColor(new Color(34, 211, 238, 45));
                    g2.fill(new Ellipse2D.Double(x - 24, y - 24, 48, 48));
                }

                g2.setColor(body);
                RoundRectangle2D center = new RoundRectangle2D.Double(
                        x - 10, y - 8, 20, 16, 5, 5
                );
                g2.fill(center);

                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Line2D.Double(x - 24, y, x - 10, y));
                g2.draw(new Line2D.Double(x + 10, y, x + 24, y));

                g2.draw(new Rectangle2D.Double(x - 30, y - 7, 6, 14));
                g2.draw(new Rectangle2D.Double(x + 24, y - 7, 6, 14));

                Color labelColor = sat.isOnline()
                        ? new Color(226, 232, 240)
                        : new Color(148, 163, 184);

                drawLabel(g2, "SAT-" + sat.getId(), x, y + 29, labelColor);

                String battery = String.format("%.0f%%", sat.getBatteryPercent());
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                g2.setColor(sat.getBatteryPercent() < 20
                        ? new Color(248, 113, 113)
                        : new Color(148, 163, 184));

                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(battery,
                        (float) (x - fm.stringWidth(battery) / 2.0),
                        (float) (y + 43));

                if (!sat.isOnline()) {
                    g2.setColor(new Color(248, 113, 113));
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.draw(new Line2D.Double(x - 12, y - 12, x + 12, y + 12));
                    g2.draw(new Line2D.Double(x + 12, y - 12, x - 12, y + 12));
                }
            }
        }

        private Point2D screenPoint(int nodeId) {
            double worldX;
            double worldY;

            if (nodeId == GroundStation.ID) {
                GroundStation ground = network.getGroundStation();
                worldX = ground.getX();
                worldY = ground.getY();
            } else {
                ChipSat sat = network.getSatellite(nodeId);
                if (sat == null) {
                    return null;
                }
                worldX = sat.getX();
                worldY = sat.getY();
            }

            double padding = 48.0;
            double scaleX = (getWidth() - 2.0 * padding) / 90.0;
            double scaleY = (getHeight() - 2.0 * padding) / 85.0;

            double x = padding + worldX * scaleX;
            double y = getHeight() - (padding + worldY * scaleY);

            return new Point2D.Double(x, y);
        }

        private void drawLabel(Graphics2D g2, String text,
                               double x, double y, Color color) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(color);

            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text,
                    (float) (x - fm.stringWidth(text) / 2.0),
                    (float) y);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            NetworkVisualizer visualizer = new NetworkVisualizer();
            visualizer.setVisible(true);
        });
    }
}
