import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class VisualEffects {
    private List<Particle> particles = new ArrayList<>();
    private List<ScorePopup> scorePopups = new ArrayList<>();
    private float backgroundHue = 0;
    
    // Partícula para efeitos visuais
    private static class Particle {
        float x, y;
        float vx, vy;
        float alpha;
        Color color;
        float size;
        
        Particle(float x, float y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            double angle = Math.random() * Math.PI * 2;
            float speed = (float) (Math.random() * 5 + 2);
            this.vx = (float) Math.cos(angle) * speed;
            this.vy = (float) Math.sin(angle) * speed;
            this.alpha = 1.0f;
            this.size = (float) (Math.random() * 6 + 4);
        }
        
        void update() {
            x += vx;
            y += vy;
            vy += 0.1f; // Gravidade
            alpha *= 0.95f;
            size *= 0.97f;
        }
        
        boolean isDead() {
            return alpha < 0.01f;
        }
        
        void draw(Graphics2D g2) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(color);
            g2.fill(new Ellipse2D.Float(x - size/2, y - size/2, size, size));
        }
    }
    
    // Popup de pontuação
    private static class ScorePopup {
        float x, y;
        float alpha = 1.0f;
        float vy = -2.0f;
        final String text;
        final Color color;
        
        ScorePopup(float x, float y, int score, Color color) {
            this.x = x;
            this.y = y;
            this.text = "+" + score;
            this.color = color;
        }
        
        void update() {
            y += vy;
            vy *= 0.95f;
            alpha *= 0.95f;
        }
        
        boolean isDead() {
            return alpha < 0.01f;
        }
        
        void draw(Graphics2D g2) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(color);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
            FontMetrics fm = g2.getFontMetrics();
            int width = fm.stringWidth(text);
            g2.drawString(text, x - width/2, y);
        }
    }
    
    // Adiciona partículas para uma linha eliminada
    public void addLineEffect(int y, int width, Color[] colors) {
        Random rand = new Random();
        for (int i = 0; i < 50; i++) {
            float x = rand.nextFloat() * width;
            Color color = colors[rand.nextInt(colors.length)];
            particles.add(new Particle(x, y, color));
        }
    }
    
    // Adiciona popup de pontuação
    public void addScorePopup(int x, int y, int score) {
        Color popupColor = new Color(255, 215, 0); // Dourado
        scorePopups.add(new ScorePopup(x, y, score, popupColor));
    }
    
    // Atualiza todos os efeitos
    public void update() {
        particles.removeIf(Particle::isDead);
        particles.forEach(Particle::update);
        
        scorePopups.removeIf(ScorePopup::isDead);
        scorePopups.forEach(ScorePopup::update);
        
        backgroundHue = (backgroundHue + 0.1f) % 360f;
    }
    
    // Desenha todos os efeitos
    public void draw(Graphics2D g2) {
        particles.forEach(p -> p.draw(g2));
        scorePopups.forEach(p -> p.draw(g2));
    }
    
    // Retorna a cor do fundo baseada na animação
    public Color getBackgroundColor() {
        return Color.getHSBColor(backgroundHue / 360f, 0.1f, 0.1f);
    }
    
    // Adiciona efeito de brilho em um bloco
    public void drawBlockGlow(Graphics2D g2, int x, int y, int size, Color color) {
        int glowSize = size + 10;
        int glowX = x - 5;
        int glowY = y - 5;
        
        // Criar gradiente radial para o brilho
        RadialGradientPaint glow = new RadialGradientPaint(
            x + size/2, y + size/2, glowSize/2,
            new float[]{0.0f, 1.0f},
            new Color[]{
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 50),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)
            }
        );
        
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2.setPaint(glow);
        g2.fillOval(glowX, glowY, glowSize, glowSize);
        g2.setComposite(oldComposite);
    }
}