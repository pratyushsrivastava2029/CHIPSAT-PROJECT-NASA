package chipsat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class BenchmarkVisualizer extends JFrame {
    private final List<PolicyResult> results;

    public BenchmarkVisualizer() {
        super("ChipSat Routing Policy Benchmark");
        results = BenchmarkRunner.runAll();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 720);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(new Color(9, 14, 25));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        setContentPane(root);

        JLabel title = new JLabel(
                "<html><b>ChipSat Telemetry Policy Benchmark</b><br>"
                + "<span style='font-size:10px'>Same seeded workload • same future contacts • different routing decisions</span></html>"
        );
        title.setForeground(new Color(235, 241, 250));
        title.setFont(new Font("SansSerif", Font.PLAIN, 23));
        root.add(title, BorderLayout.NORTH);

        root.add(new ChartPanel(), BorderLayout.CENTER);

        JTextArea explanation = new JTextArea();
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setBackground(new Color(15, 23, 42));
        explanation.setForeground(new Color(203, 213, 225));
        explanation.setBorder(new EmptyBorder(12, 12, 12, 12));
        explanation.setFont(new Font("SansSerif", Font.PLAIN, 13));
        explanation.setText(
                "Experiment: 180 deterministic telemetry bundles compete for finite contact windows. "
                + "Reactive BFS and current-cost routing can only use a complete route that exists at generation time. "
                + "The contact-aware policy can hold data for predicted future links, prioritizes urgent deadlines, "
                + "and reserves link capacity so later routing decisions see the bandwidth already committed."
        );
        root.add(explanation, BorderLayout.SOUTH);
    }

    private class ChartPanel extends JPanel {
        ChartPanel() {
            setBackground(new Color(9, 14, 25));
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int left = 190;
            int top = 65;
            int groupGap = 165;
            int barH = 24;
            int maxW = getWidth() - left - 80;

            drawMetricTitle(g2, "Overall delivery rate", 25);
            for (int i = 0; i < results.size(); i++) {
                PolicyResult r = results.get(i);
                int y = top + i * 38;
                drawBar(g2, r.getPolicy(), r.getDeliveryRate(), 100, left, y, maxW, barH,
                        String.format("%.1f%%", r.getDeliveryRate()));
            }

            int secondTop = top + groupGap;
            drawMetricTitle(g2, "Critical telemetry delivered before deadline", secondTop - 40);
            for (int i = 0; i < results.size(); i++) {
                PolicyResult r = results.get(i);
                int y = secondTop + i * 38;
                drawBar(g2, r.getPolicy(), r.getCriticalRate(), 100, left, y, maxW, barH,
                        String.format("%.1f%%", r.getCriticalRate()));
            }

            int thirdTop = secondTop + groupGap;
            double maxScience = 1;
            for (PolicyResult r : results) {
                maxScience = Math.max(maxScience, r.getScienceKbDelivered() / 1024.0);
            }

            drawMetricTitle(g2, "Science data delivered", thirdTop - 40);
            for (int i = 0; i < results.size(); i++) {
                PolicyResult r = results.get(i);
                double mb = r.getScienceKbDelivered() / 1024.0;
                int y = thirdTop + i * 38;
                drawBar(g2, r.getPolicy(), mb, maxScience, left, y, maxW, barH,
                        String.format("%.1f MB", mb));
            }

            g2.dispose();
        }

        private void drawMetricTitle(Graphics2D g2, String title, int y) {
            g2.setColor(new Color(226, 232, 240));
            g2.setFont(new Font("SansSerif", Font.BOLD, 15));
            g2.drawString(title, 20, y);
        }

        private void drawBar(Graphics2D g2,
                             String label,
                             double value,
                             double max,
                             int x,
                             int y,
                             int maxW,
                             int h,
                             String display) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.setColor(new Color(148, 163, 184));
            g2.drawString(label, 20, y + 17);

            g2.setColor(new Color(30, 41, 59));
            g2.fillRoundRect(x, y, maxW, h, 8, 8);

            int width = (int) Math.round(maxW * value / max);
            g2.setColor(new Color(56, 189, 248));
            g2.fillRoundRect(x, y, Math.max(2, width), h, 8, 8);

            g2.setColor(new Color(241, 245, 249));
            g2.drawString(display, x + Math.min(width + 8, maxW - 55), y + 17);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BenchmarkVisualizer().setVisible(true));
    }
}
