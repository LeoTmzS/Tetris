// Tetris.java
// Compilar: javac Tetris.java
// Rodar: java Tetris
// Requisitos: Java 8+
// Autor: Java (assistente) — versão corrigida

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.*;

/**
 * Tetris completo em um arquivo. Contém:
 * - Movimento, rotação, queda lenta/rápida, hard drop
 * - Sistema de pontuação, nível, linhas
 * - Pausa, Game Over, reiniciar com Enter
 * - Painel lateral com próximo bloco
 *
 * Se ocorrer erro ao compilar/rodar, cole a mensagem de erro aqui que eu te ajudo.
 */
public class Tetris extends JFrame {
    public Tetris() {
        setTitle("Tetris — Java");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);
        
        // Configura o tamanho mínimo da janela
        setMinimumSize(new Dimension(600, 650));
        
        // Seleciona o tema
        Theme selectedTheme = showThemeDialog();
        
        GamePanel game = new GamePanel(selectedTheme);
        // Adiciona o painel com layout que permite redimensionamento
        setLayout(new BorderLayout());
        add(game, BorderLayout.CENTER);
        
        // Configura o tamanho inicial
        setSize(800, 800);
        setLocationRelativeTo(null);
        setVisible(true);

        game.startGame();
    }
    
    private Theme showThemeDialog() {
        Object[] options = {Theme.BLUE.name, Theme.WHITE.name, Theme.BLACK.name};
        int n = JOptionPane.showOptionDialog(
            null,
            "Escolha o tema da interface:",
            "Seleção de Tema",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
        
        return switch(n) {
            case 0 -> Theme.BLUE;
            case 1 -> Theme.WHITE;
            case 2 -> Theme.BLACK;
            default -> Theme.BLUE;
        };
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Tetris::new);
    }
}

/* -------------------------
   THEME SYSTEM
   ------------------------- */
enum Theme {
    BLUE("Azul", new Color(15, 35, 65), new Color(5, 20, 45), new Color(30, 70, 120), 
         new Color(10, 30, 60), new Color(150, 200, 255), new Color(100, 200, 255), new Color(120, 180, 255)),
    WHITE("Branco", new Color(240, 240, 245), new Color(250, 250, 255), new Color(200, 200, 210),
         new Color(230, 230, 240), new Color(60, 60, 80), new Color(40, 40, 60), new Color(80, 120, 180)),
    BLACK("Preto", new Color(20, 20, 25), new Color(10, 10, 15), new Color(50, 50, 60),
         new Color(30, 30, 40), new Color(200, 200, 220), new Color(150, 150, 180), new Color(180, 180, 220));
    
    final String name;
    final Color bgMain;
    final Color bgField;
    final Color gridColor;
    final Color sidePanel;
    final Color labelColor;
    final Color valueColor;
    final Color instructColor;
    
    Theme(String name, Color bgMain, Color bgField, Color gridColor, Color sidePanel,
          Color labelColor, Color valueColor, Color instructColor) {
        this.name = name;
        this.bgMain = bgMain;
        this.bgField = bgField;
        this.gridColor = gridColor;
        this.sidePanel = sidePanel;
        this.labelColor = labelColor;
        this.valueColor = valueColor;
        this.instructColor = instructColor;
    }
}

/* -------------------------
   GAME PANEL (RENDER + LOOP)
   ------------------------- */
class GamePanel extends JPanel {
    // GRID
    private final int COLS = 10;
    private final int ROWS = 20;
    private int cellSize = 30; // Não é mais final para permitir redimensionamento
    private int fieldMarginX = 20;
    private int fieldMarginY = 10;
    private int sidePanelWidth = 320;
    
    // THEME
    private Theme theme;

    // GAME STATE
    private Color[][] wall; // grid of placed blocks
    private Tetromino current;
    private Tetromino next;
    private int curRow, curCol, rotation;
    private Timer timer;
    private int dropDelay = 600; // ms, will decrease with levels
    private boolean isPaused = false;
    private boolean isGameOver = false;

    // SCORE / LEVEL
    private int score = 0;
    private int level = 1;
    private int totalLines = 0;
    // Highscores
    private java.util.List<ScoreEntry> highscores = new java.util.ArrayList<>();

    // VISUAL
    private final Font uiFont = new Font("Segoe UI", Font.BOLD, 14);
    private final Font bigFont = new Font("Segoe UI", Font.BOLD, 26);
    private final VisualEffects effects = new VisualEffects();
    private float pieceDropProgress = 0f;

    private final Random rand = new Random();

    public GamePanel() {
        this(Theme.BLUE);
    }
    
    public GamePanel(Theme selectedTheme) {
        this.theme = selectedTheme;
        setBackground(theme.bgMain);
        setFocusable(true);
        initControls();
        initGrid();
        // Adiciona listener para redimensionamento
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                handleResize();
            }
        });
    }
    
    // Calcula o tamanho ideal das células baseado no tamanho do painel
    public void handleResize() {
        int availableWidth = getWidth() - sidePanelWidth - 40;
        int availableHeight = getHeight() - 40;
        
        // Calcula o tamanho da célula baseado no espaço disponível
        int cellByWidth = availableWidth / COLS;
        int cellByHeight = availableHeight / ROWS;
        
        // Usa o menor valor para manter o aspecto quadrado
        int newCellSize = Math.max(20, Math.min(cellByWidth, cellByHeight));
        cellSize = Math.min(newCellSize, 50); // Limita o tamanho máximo
        
        // Recentraliza o campo
        int fieldW = COLS * cellSize;
        int fieldH = ROWS * cellSize;
        fieldMarginX = Math.max(10, (getWidth() - fieldW - sidePanelWidth) / 2);
        fieldMarginY = Math.max(10, (getHeight() - fieldH) / 2);
        
        repaint();
    }

    private void initGrid() {
        wall = new Color[ROWS][COLS];
    }

    public void startGame() {
        isPaused = false;
        isGameOver = false;
        score = 0;
        level = 1;
        totalLines = 0;
        dropDelay = 600;
        initGrid();
        next = Tetromino.random(rand);
        spawnPiece();
    // load highscores when game starts
    loadHighscores();

        if (timer != null && timer.isRunning()) timer.stop();
        timer = new Timer(dropDelay, e -> {
            if (!isPaused && !isGameOver) {
                dropOneRow();
            }
        });
        timer.start();
    }

    private void spawnPiece() {
        current = next != null ? next : Tetromino.random(rand);
        next = Tetromino.random(rand);
        rotation = 0;
        curRow = -current.getTopEmptyRows(rotation);
        curCol = COLS / 2 - 2;
        if (!isValidPosition(curRow, curCol, rotation)) {
            isGameOver = true;
            if (timer != null) timer.stop();
            // Trigger end-of-game flow: ask for player name and save score
            SwingUtilities.invokeLater(() -> handleGameOver());
        }
        repaint();
    }

    private void initControls() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke("LEFT"), "left");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
        im.put(KeyStroke.getKeyStroke("DOWN"), "softDrop");
        im.put(KeyStroke.getKeyStroke("UP"), "rotate");
        im.put(KeyStroke.getKeyStroke("SPACE"), "hardDrop");
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        im.put(KeyStroke.getKeyStroke("ENTER"), "restart");

        am.put("left", new AbstractAction() { public void actionPerformed(ActionEvent e) { move(-1); }});
        am.put("right", new AbstractAction() { public void actionPerformed(ActionEvent e) { move(1); }});
        am.put("softDrop", new AbstractAction() { public void actionPerformed(ActionEvent e) { softDrop(); }});
        am.put("rotate", new AbstractAction() { public void actionPerformed(ActionEvent e) { rotate(); }});
        am.put("hardDrop", new AbstractAction() { public void actionPerformed(ActionEvent e) { hardDrop(); }});
        am.put("pause", new AbstractAction() { public void actionPerformed(ActionEvent e) { togglePause(); }});
        am.put("restart", new AbstractAction() { public void actionPerformed(ActionEvent e) { if (isGameOver) startGame(); }});
    }

    private void togglePause() {
        if (isGameOver) return;
        isPaused = !isPaused;
        repaint();
    }

    private void move(int dx) {
        if (isPaused || isGameOver) return;
        if (isValidPosition(curRow, curCol + dx, rotation)) {
            curCol += dx;
            repaint();
        }
    }

    private void softDrop() {
        if (isPaused || isGameOver) return;
        dropOneRow();
    }

    private void hardDrop() {
        if (isPaused || isGameOver) return;
        while (isValidPosition(curRow + 1, curCol, rotation)) {
            curRow++;
        }
        lockPiece();
    }

    private void dropOneRow() {
        if (isValidPosition(curRow + 1, curCol, rotation)) {
            curRow++;
        } else {
            lockPiece();
        }
        repaint();
    }

    private void rotate() {
        if (isPaused || isGameOver) return;
        int newRot = (rotation + 1) % current.rotationCount();
        int[] kicks = {0, -1, 1, -2, 2};
        for (int k : kicks) {
            if (isValidPosition(curRow, curCol + k, newRot)) {
                rotation = newRot;
                curCol += k;
                repaint();
                return;
            }
        }
        if (isValidPosition(curRow - 1, curCol, newRot)) {
            rotation = newRot;
            curRow -= 1;
            repaint();
        }
    }

    private boolean isValidPosition(int r, int c, int rot) {
        for (Point p : current.getBlocks(rot)) {
            int rr = r + p.y;
            int cc = c + p.x;
            if (cc < 0 || cc >= COLS) return false;
            if (rr >= ROWS) return false;
            if (rr >= 0 && wall[rr][cc] != null) return false;
        }
        return true;
    }

    private void lockPiece() {
        for (Point p : current.getBlocks(rotation)) {
            int rr = curRow + p.y;
            int cc = curCol + p.x;
            if (rr >= 0 && rr < ROWS && cc >= 0 && cc < COLS) {
                wall[rr][cc] = current.getColor();
            } else if (rr < 0) {
                isGameOver = true;
                if (timer != null) timer.stop();
                repaint();
                // Trigger end-of-game flow: ask for player name and save score
                SwingUtilities.invokeLater(() -> handleGameOver());
                return;
            }
        }
        int cleared = clearLines();
        if (cleared > 0) {
            int points;
            switch (cleared) {
                case 1 -> points = 40;
                case 2 -> points = 100;
                case 3 -> points = 300;
                case 4 -> points = 1200;
                default -> points = cleared * 300;
            }
            score += points * level;
            totalLines += cleared;
            int newLevel = totalLines / 10 + 1;
            if (newLevel > level) {
                level = newLevel;
                dropDelay = Math.max(80, 600 - (level - 1) * 40);
                if (timer != null) timer.setDelay(dropDelay);
            }
        }
        spawnPiece();
    }

    // Called on the EDT when the game ends. Shows a dialog to enter player name and saves the score.
    private void handleGameOver() {
        // Simple input dialog for player name
        String name = JOptionPane.showInputDialog(this, "Game Over! Enter your name:", "Save Score", JOptionPane.PLAIN_MESSAGE);
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) name = "(anonymous)";
            boolean ok = saveScore(name, score);
            if (ok) {
                // refresh highscores in memory so UI updates
                loadHighscores();
                JOptionPane.showMessageDialog(this, "Score saved!", "Saved", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to save score.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Salva o score no banco de dados SQLite
    private boolean saveScore(String name, int score) {
        try (Connection conn = DatabaseManager.getConnection()) {
            // Insere o novo score
            PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO highscores (player_name, score) VALUES (?, ?)"
            );
            pstmt.setString(1, name);
            pstmt.setInt(2, score);
            pstmt.executeUpdate();
            
            // Mantém apenas os top 5 scores
            Statement stmt = conn.createStatement();
            stmt.execute("""
                DELETE FROM highscores 
                WHERE id NOT IN (
                    SELECT id FROM highscores 
                    ORDER BY score DESC 
                    LIMIT 5
                )
            """);
            
            // Atualiza a lista em memória
            loadHighscores();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Highscore helpers
    private static class ScoreEntry {
        final String name;
        final int score;
        ScoreEntry(String n, int s) { name = n; score = s; }
    }

    private void loadHighscores() {
        highscores.clear();
        try (Connection conn = DatabaseManager.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("""
                SELECT player_name, score 
                FROM highscores 
                ORDER BY score DESC 
                LIMIT 5
            """);
            
            while (rs.next()) {
                String name = rs.getString("player_name");
                int score = rs.getInt("score");
                highscores.add(new ScoreEntry(name, score));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int clearLines() {
        int cleared = 0;
        for (int r = ROWS - 1; r >= 0; r--) {
            boolean full = true;
            List<Color> lineColors = new ArrayList<>();
            
            for (int c = 0; c < COLS; c++) {
                if (wall[r][c] == null) { 
                    full = false; 
                    break; 
                }
                lineColors.add(wall[r][c]);
            }
            
            if (full) {
                cleared++;
                // Adiciona efeitos visuais para a linha eliminada
                int lineY = fieldMarginY + r * cellSize;
                effects.addLineEffect(lineY, COLS * cellSize, lineColors.toArray(new Color[0]));
                
                // Adiciona popup de pontuação
                int points = switch(cleared) {
                    case 1 -> 40;
                    case 2 -> 100;
                    case 3 -> 300;
                    case 4 -> 1200;
                    default -> cleared * 300;
                };
                
                effects.addScorePopup(
                    fieldMarginX + (COLS * cellSize) / 2,
                    lineY,
                    points
                );
                
                // Move as linhas para baixo
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(wall[rr - 1], 0, wall[rr], 0, COLS);
                }
                for (int cc = 0; cc < COLS; cc++) wall[0][cc] = null;
                r++; // recheck same index after shift
            }
        }
        return cleared;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);

        // Atualiza efeitos visuais
        effects.update();
        
        // Fundo animado
        Paint old = g2.getPaint();
        Color bgColor = effects.getBackgroundColor();
        g2.setPaint(new GradientPaint(
            0, 0, 
            bgColor.brighter(), 
            0, getHeight(), 
            bgColor.darker()
        ));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setPaint(old);
        
        // playfield
        int fieldW = COLS * cellSize;
        int fieldH = ROWS * cellSize;
        RoundRectangle2D.Float fieldBG = new RoundRectangle2D.Float(fieldMarginX - 8, fieldMarginY - 8, fieldW + 16, fieldH + 16, 16, 16);
        g2.setColor(new Color(theme.bgField.getRed(), theme.bgField.getGreen(), theme.bgField.getBlue(), 220));
        g2.fill(fieldBG);

        // grid lines
        g2.setColor(theme.gridColor);
        for (int r = 0; r <= ROWS; r++) {
            g2.drawLine(fieldMarginX, fieldMarginY + r * cellSize, fieldMarginX + fieldW, fieldMarginY + r * cellSize);
        }
        for (int c = 0; c <= COLS; c++) {
            g2.drawLine(fieldMarginX + c * cellSize, fieldMarginY, fieldMarginX + c * cellSize, fieldMarginY + fieldH);
        }

        // placed blocks
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Color col = wall[r][c];
                if (col != null) {
                    drawBlock(g2, fieldMarginX + c * cellSize, fieldMarginY + r * cellSize, col);
                }
            }
        }

        // current piece + ghost
        if (current != null && !isGameOver) {
            int ghostRow = curRow;
            while (isValidPosition(ghostRow + 1, curCol, rotation)) ghostRow++;
            for (Point p : current.getBlocks(rotation)) {
                int rr = ghostRow + p.y;
                int cc = curCol + p.x;
                if (rr >= 0) drawGhostBlock(g2, fieldMarginX + cc * cellSize, fieldMarginY + rr * cellSize, current.getColor());
            }
            for (Point p : current.getBlocks(rotation)) {
                int rr = curRow + p.y;
                int cc = curCol + p.x;
                if (rr >= 0) drawBlock(g2, fieldMarginX + cc * cellSize, fieldMarginY + rr * cellSize, current.getColor());
            }
        }

        // side panel
        int sideX = fieldMarginX + fieldW + 20;
        int sideY = fieldMarginY;
        drawSidePanel(g2, sideX, sideY);

        // overlay pause / game over
        if (isPaused || isGameOver) {
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRect(fieldMarginX, fieldMarginY, fieldW, fieldH);
            g2.setColor(Color.WHITE);
            g2.setFont(bigFont);
            String text = isGameOver ? "GAME OVER" : "PAUSED";
            int tw = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, fieldMarginX + fieldW / 2 - tw / 2, fieldMarginY + fieldH / 2 - 10);
            g2.setFont(uiFont);
            String sub = isGameOver ? "Press ENTER to restart" : "Press P to resume";
            int sw = g2.getFontMetrics().stringWidth(sub);
            g2.drawString(sub, fieldMarginX + fieldW / 2 - sw / 2, fieldMarginY + fieldH / 2 + 18);
        }

        // draw controls/instructions at bottom-right
        drawInstructionsBottomRight(g2);
        
        // Desenha os efeitos visuais por cima de tudo
        effects.draw(g2);
        
        g2.dispose();
    }

    // Draw controls/instructions at bottom-right of the panel
    private void drawInstructionsBottomRight(Graphics2D g2) {
        String[] hints = {
            "← → : mover",
            "↑ : rotacionar",
            "↓ : descer",
            "SPACE : queda rápida",
            "P : pausar",
            "ENTER : reiniciar (game over)"
        };
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(theme.instructColor);
        FontMetrics fm = g2.getFontMetrics();
        int lineH = fm.getHeight();
        int totalH = hints.length * lineH;
        int padding = 12;
        int startY = getHeight() - padding - totalH + fm.getAscent();
        for (int i = 0; i < hints.length; i++) {
            String s = hints[i];
            int w = fm.stringWidth(s);
            int x = getWidth() - padding - w;
            int y = startY + i * lineH;
            g2.drawString(s, x, y);
        }
    }

    private void drawSidePanel(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(theme.sidePanel.getRed(), theme.sidePanel.getGreen(), theme.sidePanel.getBlue(), 220));
        // Use sidePanelWidth to drive the actual visual width so it resizes consistently
        int panelWidth = Math.max(200, sidePanelWidth - 20);
        int panelHeight = 360; // slightly taller to fit highscores without overflow
        g2.fillRoundRect(x - 10, y - 8, panelWidth, panelHeight, 12, 12);

        g2.setColor(Color.WHITE);
        g2.setFont(bigFont);
        g2.drawString("TETRIS", x + 20, y + 36);

        g2.setFont(uiFont);
        g2.setColor(theme.labelColor);
        g2.drawString("Score:", x + 10, y + 80);
        g2.setFont(new Font("Consolas", Font.BOLD, 20));
        g2.setColor(theme.valueColor);
        g2.drawString(String.valueOf(score), x + 10, y + 106);

        g2.setFont(uiFont);
        g2.setColor(theme.labelColor);
        g2.drawString("Level:", x + 10, y + 140);
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(theme.valueColor);
        g2.drawString(String.valueOf(level), x + 10, y + 162);

        g2.setFont(uiFont);
        g2.setColor(theme.labelColor);
        g2.drawString("Lines:", x + 10, y + 192);
        g2.setFont(new Font("Consolas", Font.BOLD, 18));
        g2.setColor(theme.valueColor);
        g2.drawString(String.valueOf(totalLines), x + 10, y + 214);

        // Next preview
        g2.setFont(uiFont);
        g2.setColor(theme.instructColor);
        g2.drawString("Next:", x + panelWidth - 100, y + 80);
        if (next != null) {
            int previewX = x + panelWidth - 100; // place preview inside the right area of the panel
            int previewY = y + 100;
            int size = 18;
            for (Point p : next.getBlocks(0)) {
                int px = previewX + p.x * size;
                int py = previewY + p.y * size;
                drawSmallBlock(g2, px, py, next.getColor());
            }
        }

        // Highscores (top 5)
        int shown = Math.min(5, highscores.size());
        if (shown > 0) {
            int highscoresStart = y + 240; // start lower so it fits within the taller panel
            g2.setFont(uiFont);
            g2.setColor(theme.instructColor);
            g2.drawString("Highscores:", x + 10, highscoresStart);

            g2.setFont(new Font("Consolas", Font.PLAIN, 14));
            int highscoresLineHeight = 18;
            for (int i = 0; i < shown; i++) {
                ScoreEntry e = highscores.get(i);
                String line = String.format("%d. %-10s %5d", i + 1, e.name, e.score);
                g2.drawString(line, x + 10, highscoresStart + 6 + (i + 1) * highscoresLineHeight);
            }
        }
    }

    private void drawBlock(Graphics2D g2, int x, int y, Color base) {
        // Efeito de brilho
        effects.drawBlockGlow(g2, x, y, cellSize, base);
        
        // Desenho do bloco com gradiente metálico
        Color highlight = new Color(
            Math.min(255, base.getRed() + 100),
            Math.min(255, base.getGreen() + 100),
            Math.min(255, base.getBlue() + 100)
        );
        
        // Gradiente diagonal para efeito metálico
        GradientPaint gp = new GradientPaint(
            x, y, highlight,
            x + cellSize, y + cellSize, base.darker()
        );
        
        g2.setPaint(gp);
        g2.fillRoundRect(x + 1, y + 1, cellSize - 2, cellSize - 2, 8, 8);
        
        // Borda brilhante
        g2.setStroke(new BasicStroke(2));
        g2.setColor(new Color(255, 255, 255, 100));
        g2.drawRoundRect(x + 2, y + 2, cellSize - 4, cellSize - 4, 6, 6);
        
        // Reflexo
        Shape oldClip = g2.getClip();
        g2.setClip(new RoundRectangle2D.Float(x + 2, y + 2, cellSize - 4, cellSize - 4, 6, 6));
        g2.setColor(new Color(255, 255, 255, 50));
        g2.fillOval(x - 5, y - 5, cellSize, cellSize/2);
        g2.setClip(oldClip);
    }

    private void drawGhostBlock(Graphics2D g2, int x, int y, Color base) {
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 70));
        g2.fillRoundRect(x + 2, y + 2, cellSize - 4, cellSize - 4, 6, 6);
    }

    private void drawSmallBlock(Graphics2D g2, int x, int y, Color base) {
        int size = 16;
        g2.setColor(base.darker());
        g2.fillRect(x + 1, y + 1, size - 2, size - 2);
        g2.setColor(base);
        g2.fillRect(x + 3, y + 3, size - 6, size - 6);
    }
}

/* -------------------------
   DATABASE MANAGER
   ------------------------- */
class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:tetris.db";
    
    // Carrega o driver SQLite na inicialização da classe
    static {
        try {
            // Registra o driver SQLite explicitamente
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Erro ao carregar o driver SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static {
        // Inicializa o banco de dados
        try (Connection conn = getConnection()) {
            Statement stmt = conn.createStatement();
            // Cria a tabela de highscores se não existir
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS highscores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_name TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    date_achieved TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(DB_URL);
    }
}

/* -------------------------
   TETROMINO DEFINITION
   (cada rotação 4x4, 16 chars)
   ------------------------- */
enum Tetromino {
    I(new String[]{
            "...." +
            "####" +
            "...." +
            "....",
            "..#." +
            "..#." +
            "..#." +
            "..#.",
            "...." +
            "...." +
            "####" +
            "....",
            ".#.." +
            ".#.." +
            ".#.." +
            ".#.."
    }, new Color(0, 220, 220)),

    J(new String[]{
            "#..." +
            "###." +
            "...." +
            "....",
            ".##." +
            ".#.." +
            ".#.." +
            "....",
            "...." +
            "###." +
            "..#." +
            "....",
            ".#.." +
            ".#.." +
            "##.." +
            "...."
    }, new Color(0, 0, 220)),

    L(new String[]{
            "..#." +
            "###." +
            "...." +
            "....",
            ".#.." +
            ".#.." +
            ".##." +
            "....",
            "...." +
            "###." +
            "#..." +
            "....",
            "##.." +
            ".#.." +
            ".#.." +
            "...."
    }, new Color(255, 160, 0)),

    O(new String[]{
            ".##." +
            ".##." +
            "...." +
            "....",
            ".##." +
            ".##." +
            "...." +
            "....",
            ".##." +
            ".##." +
            "...." +
            "....",
            ".##." +
            ".##." +
            "...." +
            "...."
    }, new Color(220, 220, 0)),

    S(new String[]{
            ".##." +
            "##.." +
            "...." +
            "....",
            ".#.." +
            ".##." +
            "..#." +
            "....",
            ".##." +
            "##.." +
            "...." +
            "....",
            ".#.." +
            ".##." +
            "..#." +
            "...."
    }, new Color(0, 220, 0)),

    T(new String[]{
            ".#.." +
            "###." +
            "...." +
            "....",
            ".#.." +
            ".##." +
            ".#.." +
            "....",
            "...." +
            "###." +
            ".#.." +
            "....",
            ".#.." +
            "##.." +
            ".#.." +
            "...."
    }, new Color(160, 0, 220)),

    Z(new String[]{
            "##.." +
            ".##." +
            "...." +
            "....",
            "..#." +
            ".##." +
            ".#.." +
            "....",
            "##.." +
            ".##." +
            "...." +
            "....",
            "..#." +
            ".##." +
            ".#.." +
            "...."
    }, new Color(220, 0, 0));

    private final String[] rotations; // cada string length 16 (4x4)
    private final Color color;

    Tetromino(String[] rotations, Color color) {
        this.rotations = rotations;
        this.color = color;
    }

    public static Tetromino random(Random r) {
        Tetromino[] vals = values();
        return vals[r.nextInt(vals.length)];
    }

    public int rotationCount() {
        return rotations.length;
    }

    public Color getColor() {
        return color;
    }

    public Point[] getBlocks(int rotIndex) {
        String s = rotations[rotIndex % rotations.length];
        java.util.List<Point> pts = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            if (s.charAt(i) == '#') {
                int x = i % 4;
                int y = i / 4;
                pts.add(new Point(x, y));
            }
        }
        return pts.toArray(new Point[0]);
    }

    public int getTopEmptyRows(int rot) {
        String s = rotations[rot % rotations.length];
        for (int r = 0; r < 4; r++) {
            boolean any = false;
            for (int c = 0; c < 4; c++) {
                if (s.charAt(r * 4 + c) == '#') { any = true; break; }
            }
            if (any) return r;
        }
        return 4;
    }
}
