import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Standalone Java SE application to render the Proton VPN-Next icon.
 * This avoids Android Studio's rendering engine and ensures a clean 1024x1024 square PNG.
 */
public class IconGenerator {
    public static void main(String[] args) {
        int size = 1024;
        try {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            
            // Quality hints
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // 1. Background (Black)
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, size, size);

            // 2. Foreground Scaling
            // Original viewport is 552. Transform: scale(0.75), translate(69)
            double scaleFactor = size / 552.0;
            g2.scale(scaleFactor, scaleFactor);
            g2.translate(69, 69);
            g2.scale(0.75, 0.75);

            // Path 1 & 2 Base Shape
            // M243.637,422.72 L109.732,180.114 C95.596,154.503 116.289,123.62 145.351,126.952 
            // L417.942,158.21 C444.575,161.264 458.647,191.314 443.94,213.727 
            // L305.254,425.075 C290.38,447.741 256.738,446.455 243.637,422.72 Z
            Path2D.Double mainPath = new Path2D.Double();
            mainPath.moveTo(243.637, 422.72);
            mainPath.lineTo(109.732, 180.114);
            mainPath.curveTo(95.596, 154.503, 116.289, 123.62, 145.351, 126.952);
            mainPath.lineTo(417.942, 158.21);
            mainPath.curveTo(444.575, 161.264, 458.647, 191.314, 443.94, 213.727);
            mainPath.lineTo(305.254, 425.075);
            mainPath.curveTo(290.38, 447.741, 256.738, 446.455, 243.637, 422.72);
            mainPath.closePath();

            // Path 3 (Fold)
            Path2D.Double foldPath = new Path2D.Double();
            foldPath.moveTo(263.756, 379.083);
            foldPath.lineTo(251.447, 397.584);
            foldPath.curveTo(246.46, 405.081, 235.301, 404.619, 230.95, 396.736);
            foldPath.lineTo(252.06, 430);
            foldPath.curveTo(267.797, 443.184, 292.949, 440.825, 305.254, 422.073);
            foldPath.lineTo(443.94, 210.726);
            foldPath.curveTo(458.647, 188.313, 444.575, 158.264, 417.942, 155.21);
            foldPath.lineTo(145.351, 123.951);
            foldPath.curveTo(116.289, 120.619, 95.596, 151.503, 109.732, 177.113);
            foldPath.lineTo(342.83, 205.909);
            foldPath.curveTo(357.619, 207.62, 365.421, 224.313, 357.247, 236.756);
            foldPath.lineTo(263.756, 379.083);
            foldPath.closePath();

            // Draw Shadow
            g2.setColor(new Color(0, 0, 0, 0x35));
            g2.fill(mainPath);

            // Draw Main Body (shifted up by 3)
            AffineTransform shift = AffineTransform.getTranslateInstance(0, -3);
            Shape shiftedMain = shift.createTransformedShape(mainPath);
            
            float[] fractions = {0f, 0.33f, 0.66f, 1f};
            Color[] colors = {
                new Color(0x6A, 0x00, 0xFF),
                new Color(0xD5, 0x00, 0xF9),
                new Color(0xFF, 0x40, 0x81),
                new Color(0xFF, 0xB7, 0x4D)
            };
            g2.setPaint(new LinearGradientPaint(150, 120, 400, 450, fractions, colors));
            g2.fill(shiftedMain);
            
            // Main Body Stroke
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xD5, 0x00, 0xF9));
            g2.draw(shiftedMain);

            // Draw Fold with Gradient
            float[] foldFractions = {0f, 1f};
            Color[] foldColors = { new Color(255, 255, 255, 0), new Color(0, 0, 0, 0x25) };
            g2.setPaint(new LinearGradientPaint(200, 200, 450, 450, foldFractions, foldColors));
            g2.fill(foldPath);

            // Draw Rim (Inner Highlight)
            Path2D.Double rimPath = new Path2D.Double();
            rimPath.moveTo(243.637, 415.72);
            rimPath.lineTo(113.732, 181.114);
            rimPath.curveTo(103.596, 162.503, 116.289, 135.62, 145.351, 133.952);
            rimPath.lineTo(413.942, 165.21);
            rimPath.curveTo(434.575, 168.264, 443.647, 193.314, 433.94, 210.727);
            rimPath.lineTo(305.254, 412.075);
            rimPath.curveTo(290.38, 434.741, 266.738, 433.455, 243.637, 415.72);
            rimPath.closePath();
            
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(255, 255, 255, 0x60));
            g2.draw(rimPath);

            g2.dispose();
            
            File outputFile = new File("app_icon_square_1024.png");
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("SUCCESS: Generated " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
