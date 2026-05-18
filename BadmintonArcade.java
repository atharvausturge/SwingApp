import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class BadmintonArcade extends JPanel implements ActionListener, KeyListener {

    static final int W = 1100, H = 600;
    static final int GROUND_Y = 510;
    static final int NET_X = W / 2;
    static final int NET_TOP = 300;
    static final int WIN_SCORE = 7;

    enum State { MENU, PLAYING, POINT, GAME_OVER }
    State state = State.MENU;
    boolean botMode = true;
    int menuSelect = 0;

    // Players
    double p1x = 250, p1y = GROUND_Y, p1vy = 0;
    double p2x = 850, p2y = GROUND_Y, p2vy = 0;
    boolean p1Swing = false, p2Swing = false;
    int p1SwingT = 0, p2SwingT = 0;
    boolean p1Smash = false, p2Smash = false;

    // Shuttle
    double sx, sy, svx, svy;
    boolean shuttleLive = false;

    int score1 = 0, score2 = 0;
    int server = 1; // who serves next
    int pointTimer = 0;
    String pointMsg = "";

    boolean[] keys = new boolean[256];
    Timer timer;
    Random rng = new Random();
    int frame = 0;

    public BadmintonArcade() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(20, 10, 40));
        setFocusable(true);
        addKeyListener(this);
        timer = new Timer(16, this);
        timer.start();
        resetServe(1);
    }

    void resetServe(int who) {
        server = who;
        shuttleLive = false;
        p1x = 250; p2x = 850;
        p1y = p2y = GROUND_Y;
        p1vy = p2vy = 0;
        if (who == 1) {
            sx = p1x + 20; sy = p1y - 60;
        } else {
            sx = p2x - 20; sy = p2y - 60;
        }
        svx = 0; svy = 0;
    }

    void serve(int who) {
        if (shuttleLive) return;
        shuttleLive = true;
        if (who == 1) { svx = 6.2; svy = -9; }
        else { svx = -6.2; svy = -9; }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frame++;
        if (state == State.PLAYING) updateGame();
        else if (state == State.POINT) {
            pointTimer--;
            if (pointTimer <= 0) {
                if (score1 >= WIN_SCORE || score2 >= WIN_SCORE) state = State.GAME_OVER;
                else { state = State.PLAYING; resetServe(server); }
            }
        }
        repaint();
    }

    void updateGame() {
        // Player 1 controls: A/D move, W jump, S swing, E smash, SPACE serve
        double speed = 5.0;
        if (keys[KeyEvent.VK_A]) p1x -= speed;
        if (keys[KeyEvent.VK_D]) p1x += speed;
        if (keys[KeyEvent.VK_W] && p1y >= GROUND_Y) p1vy = -13;
        if (keys[KeyEvent.VK_S] && !p1Swing && !p1Smash) { p1Swing = true; p1SwingT = 10; }
        if (keys[KeyEvent.VK_E] && !p1Smash && !p1Swing) { p1Smash = true; p1SwingT = 10; }
        if (keys[KeyEvent.VK_SPACE] && !shuttleLive && server == 1) serve(1);

        // Player 2 / Bot — controls: ←/→ move, ↑ jump, ↓ swing, RSHIFT smash, ENTER serve
        if (botMode) {
            updateBot();
        } else {
            if (keys[KeyEvent.VK_LEFT]) p2x -= speed;
            if (keys[KeyEvent.VK_RIGHT]) p2x += speed;
            if (keys[KeyEvent.VK_UP] && p2y >= GROUND_Y) p2vy = -13;
            if (keys[KeyEvent.VK_DOWN] && !p2Swing && !p2Smash) { p2Swing = true; p2SwingT = 10; }
            if (keys[KeyEvent.VK_SHIFT] && !p2Smash && !p2Swing) { p2Smash = true; p2SwingT = 10; }
            if (keys[KeyEvent.VK_ENTER] && !shuttleLive && server == 2) serve(2);
        }

        // Physics for players
        p1vy += 0.6; p2vy += 0.6;
        p1y += p1vy; p2y += p2vy;
        if (p1y > GROUND_Y) { p1y = GROUND_Y; p1vy = 0; }
        if (p2y > GROUND_Y) { p2y = GROUND_Y; p2vy = 0; }

        // Boundaries
        p1x = clamp(p1x, 30, NET_X - 30);
        p2x = clamp(p2x, NET_X + 30, W - 30);

        if (p1SwingT > 0) p1SwingT--; else { p1Swing = false; p1Smash = false; }
        if (p2SwingT > 0) p2SwingT--; else { p2Swing = false; p2Smash = false; }

        // Shuttle physics
        if (shuttleLive) {
            svy += 0.25; // gravity (light for floaty feel)
            svx *= 0.995; // air drag
            sx += svx; sy += svy;

            // Net collision
            if (sx > NET_X - 5 && sx < NET_X + 5 && sy > NET_TOP) {
                if (svx > 0) sx = NET_X - 5; else sx = NET_X + 5;
                svx = -svx * 0.4;
            }

            // Wall bounce (sides)
            if (sx < 10) { sx = 10; svx = -svx * 0.5; }
            if (sx > W - 10) { sx = W - 10; svx = -svx * 0.5; }

            // Check racket hits
            checkHit(p1x, p1y, p1Swing, p1Smash, 1);
            checkHit(p2x, p2y, p2Swing, p2Smash, 2);

            // Ground
            if (sy >= GROUND_Y) {
                sy = GROUND_Y;
                shuttleLive = false;
                if (sx < NET_X) { score2++; pointMsg = "PLAYER 2 SCORES!"; server = 2; }
                else { score1++; pointMsg = "PLAYER 1 SCORES!"; server = 1; }
                if (botMode && pointMsg.contains("2")) pointMsg = "BOT SCORES!";
                state = State.POINT;
                pointTimer = 90;
            }
        } else {
            // Auto-serve hint: shuttle floats by server
            if (server == 1) { sx = p1x + 20; sy = p1y - 60; }
            else { sx = p2x - 20; sy = p2y - 60; }
        }
    }

    void updateBot() {
        // Target: predict shuttle landing x on bot's side
        double targetX = p2x;
        if (shuttleLive && svx > 0 || (shuttleLive && sx > NET_X)) {
            // simple predict
            double simX = sx, simY = sy, simVx = svx, simVy = svy;
            for (int i = 0; i < 120 && simY < GROUND_Y; i++) {
                simVy += 0.25; simVx *= 0.995;
                simX += simVx; simY += simVy;
                if (simX > NET_X - 5 && simX < NET_X + 5 && simY > NET_TOP) simVx = -simVx * 0.4;
            }
            targetX = simX - 25;
        }
        targetX = clamp(targetX, NET_X + 30, W - 30);
        double diff = targetX - p2x;
        if (Math.abs(diff) > 3) p2x += Math.signum(diff) * 3.5;

        // Jump if shuttle is high and close
        double dx = Math.abs(sx - p2x);
        if (shuttleLive && sy < 320 && dx < 80 && p2y >= GROUND_Y && sx > NET_X) p2vy = -12;

        // Smash if shuttle is high above us; otherwise normal swing
        if (shuttleLive && dx < 60 && !p2Swing && !p2Smash) {
            boolean highShot = sy < (p2y - 75) && sy > (p2y - 130);
            if (highShot) { p2Smash = true; p2SwingT = 10; }
            else if (Math.abs(sy - (p2y - 40)) < 60) { p2Swing = true; p2SwingT = 10; }
        }

        // Serve
        if (!shuttleLive && server == 2 && frame % 60 == 0) serve(2);
    }

    void checkHit(double px, double py, boolean swing, boolean smash, int who) {
        if (!swing && !smash) return;
        double dir = (who == 1) ? 1 : -1;
        // Smash uses overhead racket position; swing uses side
        double rx, ry;
        if (smash) {
            rx = px + dir * 12;
            ry = py - 95;
        } else {
            rx = px + dir * 30;
            ry = py - 50;
        }
        double d = Math.hypot(sx - rx, sy - ry);
        double radius = smash ? 55 : 45;
        if (d < radius) {
            if (smash) {
                // Powerful downward smash — requires shuttle near/above head
                double power = 12 + rng.nextDouble() * 2;
                svx = dir * power;
                svy = 4 + rng.nextDouble() * 2; // angled downward
            } else {
                double power = 7 + rng.nextDouble() * 2;
                svx = dir * power;
                svy = -8 - rng.nextDouble() * 2;
                if (who == 1 && p1vy < 0) svy -= 1;
                if (who == 2 && p2vy < 0) svy -= 1;
            }
            sx = rx + dir * 30;
        }
    }

    double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    // ===== RENDER =====
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        drawBackground(g);

        if (state == State.MENU) { drawMenu(g); return; }

        drawCourt(g);
        drawPlayer(g, (int)p1x, (int)p1y, new Color(255, 80, 80), p1Swing, p1Smash, 1);
        drawPlayer(g, (int)p2x, (int)p2y, new Color(80, 200, 255), p2Swing, p2Smash, 2);
        drawShuttle(g);
        drawHUD(g);

        if (state == State.POINT) {
            drawCenterText(g, pointMsg, 200, new Color(255, 230, 50), 36);
        }
        if (state == State.GAME_OVER) {
            String w = (score1 > score2) ? "PLAYER 1 WINS!" : (botMode ? "BOT WINS!" : "PLAYER 2 WINS!");
            drawCenterText(g, w, 200, new Color(255, 230, 50), 40);
            drawCenterText(g, "PRESS ENTER FOR MENU", 260, Color.WHITE, 18);
        }
    }

    void drawBackground(Graphics2D g) {
        // gradient sky
        for (int y = 0; y < H; y++) {
            float t = y / (float) H;
            int r = (int)(20 + t * 60);
            int gr = (int)(10 + t * 30);
            int b = (int)(40 + t * 80);
            g.setColor(new Color(r, gr, b));
            g.drawLine(0, y, W, y);
        }
        // stars
        Random sr = new Random(42);
        g.setColor(Color.WHITE);
        for (int i = 0; i < 60; i++) {
            int x = sr.nextInt(W);
            int y = sr.nextInt(GROUND_Y - 60);
            if ((frame / 20 + i) % 7 != 0) g.fillRect(x, y, 2, 2);
        }
        // sun
        g.setColor(new Color(255, 100, 150));
        g.fillOval(W - 150, 40, 100, 100);
        g.setColor(new Color(255, 200, 100));
        g.fillOval(W - 140, 50, 80, 80);
    }

    void drawCourt(Graphics2D g) {
        // ground stripes (arcade)
        for (int i = 0; i < 30; i++) {
            int y = GROUND_Y + i * 3;
            if (y > H) break;
            g.setColor(i % 2 == 0 ? new Color(40, 120, 60) : new Color(30, 90, 45));
            g.fillRect(0, y, W, 3);
        }
        g.setColor(new Color(20, 60, 30));
        g.fillRect(0, GROUND_Y, W, H);
        // court line
        g.setColor(Color.WHITE);
        g.fillRect(40, GROUND_Y - 2, W - 80, 3);

        // Net
        g.setColor(new Color(220, 220, 220));
        for (int y = NET_TOP; y < GROUND_Y; y += 6) {
            g.drawLine(NET_X - 3, y, NET_X + 3, y);
        }
        for (int x = NET_X - 4; x <= NET_X + 4; x += 2) {
            g.drawLine(x, NET_TOP, x, GROUND_Y);
        }
        g.setColor(Color.WHITE);
        g.fillRect(NET_X - 5, NET_TOP - 4, 10, 6);
        g.setColor(new Color(80, 50, 20));
        g.fillRect(NET_X - 2, NET_TOP, 4, GROUND_Y - NET_TOP);
    }

    void drawPlayer(Graphics2D g, int x, int y, Color c, boolean swing, boolean smash, int who) {
        // shadow
        g.setColor(new Color(0, 0, 0, 100));
        g.fillOval(x - 20, GROUND_Y - 6, 40, 10);

        // body
        g.setColor(c);
        g.fillRect(x - 10, y - 50, 20, 30); // torso
        // head
        g.setColor(new Color(255, 220, 180));
        g.fillRect(x - 8, y - 65, 16, 16);
        // legs
        g.setColor(new Color(40, 40, 80));
        g.fillRect(x - 10, y - 20, 8, 20);
        g.fillRect(x + 2, y - 20, 8, 20);
        // arm + racket
        int dir = (who == 1) ? 1 : -1;
        int ax = x + dir * 10;
        int ay = y - 40;
        int rx, ry;
        if (smash) {
            // arm raised overhead
            rx = x + dir * 12;
            ry = y - 95;
        } else if (swing) {
            rx = ax + dir * 35;
            ry = ay - 20;
        } else {
            rx = ax + dir * 20;
            ry = ay + 5;
        }
        g.setColor(c.darker());
        g.setStroke(new BasicStroke(4));
        g.drawLine(ax, ay, rx, ry);
        // racket head
        g.setColor(new Color(255, 220, 80));
        g.fillOval(rx - 8, ry - 8, 16, 16);
        g.setColor(new Color(180, 140, 30));
        g.drawOval(rx - 8, ry - 8, 16, 16);
        // strings
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE);
        for (int i = -6; i <= 6; i += 3) {
            g.drawLine(rx - 6, ry + i, rx + 6, ry + i);
            g.drawLine(rx + i, ry - 6, rx + i, ry + 6);
        }
        g.setStroke(new BasicStroke(1));
    }

    void drawShuttle(Graphics2D g) {
        int x = (int) sx, y = (int) sy;
        // feathers
        g.setColor(Color.WHITE);
        int[] xs = {x, x - 8, x + 8};
        int[] ys = {y, y - 12, y - 12};
        g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(220, 220, 240));
        g.drawLine(x, y, x - 6, y - 12);
        g.drawLine(x, y, x + 6, y - 12);
        g.drawLine(x, y, x, y - 12);
        // cork
        g.setColor(new Color(220, 180, 80));
        g.fillOval(x - 4, y - 4, 8, 8);
        g.setColor(new Color(160, 110, 40));
        g.drawOval(x - 4, y - 4, 8, 8);
    }

    void drawHUD(Graphics2D g) {
        // Score box
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(W / 2 - 100, 10, 200, 50);
        g.setColor(new Color(255, 230, 50));
        g.drawRect(W / 2 - 100, 10, 200, 50);
        g.setFont(arcadeFont(28));
        g.setColor(new Color(255, 80, 80));
        g.drawString(String.valueOf(score1), W / 2 - 60, 45);
        g.setColor(Color.WHITE);
        g.drawString("-", W / 2 - 6, 45);
        g.setColor(new Color(80, 200, 255));
        g.drawString(String.valueOf(score2), W / 2 + 40, 45);

        g.setFont(arcadeFont(12));
        g.setColor(Color.WHITE);
        g.drawString("P1: A/D move  W jump  S swing  E smash  SPACE serve", 10, H - 20);
        if (!botMode) g.drawString("P2: ←/→ move  ↑ jump  ↓ swing  RSHIFT smash  ENTER serve", W - 500, H - 20);
        else g.drawString("VS CPU", W - 90, H - 20);

        if (!shuttleLive && state == State.PLAYING) {
            String s = (server == 1) ? "P1 SERVE - press SPACE" :
                       (botMode ? "BOT SERVING..." : "P2 SERVE - press ENTER");
            drawCenterText(g, s, 130, new Color(255, 230, 50), 18);
        }
    }

    void drawMenu(Graphics2D g) {
        g.setFont(arcadeFont(48));
        g.setColor(new Color(255, 230, 50));
        drawCenterText(g, "BADMINTON", 130, new Color(255, 230, 50), 48);
        drawCenterText(g, "ARCADE  '95", 180, new Color(255, 100, 150), 32);

        String[] opts = { "1 PLAYER (VS CPU)", "2 PLAYERS" };
        for (int i = 0; i < opts.length; i++) {
            Color c = (i == menuSelect) ? new Color(255, 230, 50) : Color.WHITE;
            String prefix = (i == menuSelect) ? "> " : "  ";
            drawCenterText(g, prefix + opts[i], 280 + i * 40, c, 22);
        }
        drawCenterText(g, "UP/DOWN to choose, ENTER to start", 400, new Color(180, 180, 220), 14);
        drawCenterText(g, "FIRST TO " + WIN_SCORE + " WINS", 430, new Color(180, 180, 220), 14);

        // blinking insert coin
        if ((frame / 30) % 2 == 0)
            drawCenterText(g, "* INSERT COIN *", 460, new Color(255, 100, 100), 16);
    }

    void drawCenterText(Graphics2D g, String s, int y, Color c, int size) {
        g.setFont(arcadeFont(size));
        g.setColor(new Color(0, 0, 0, 180));
        FontMetrics fm = g.getFontMetrics();
        int x = (W - fm.stringWidth(s)) / 2;
        g.drawString(s, x + 2, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    Font arcadeFont(int size) {
        return new Font("Monospaced", Font.BOLD, size);
    }

    // ===== INPUT =====
    @Override public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k >= 0 && k < keys.length) keys[k] = true;

        if (state == State.MENU) {
            if (k == KeyEvent.VK_UP) menuSelect = (menuSelect + 1) % 2;
            if (k == KeyEvent.VK_DOWN) menuSelect = (menuSelect + 1) % 2;
            if (k == KeyEvent.VK_ENTER) {
                botMode = (menuSelect == 0);
                score1 = score2 = 0;
                state = State.PLAYING;
                resetServe(1);
            }
        } else if (state == State.GAME_OVER) {
            if (k == KeyEvent.VK_ENTER) {
                state = State.MENU;
                score1 = score2 = 0;
            }
        }
        if (k == KeyEvent.VK_ESCAPE) {
            state = State.MENU;
            score1 = score2 = 0;
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k >= 0 && k < keys.length) keys[k] = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Badminton Arcade '95");
            BadmintonArcade p = new BadmintonArcade();
            f.add(p);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setResizable(false);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}
