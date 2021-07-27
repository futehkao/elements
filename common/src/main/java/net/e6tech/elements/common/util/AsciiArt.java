/*
Copyright 2015-2019 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.common.util;

import java.awt.*;
import java.awt.image.BufferedImage;


public class AsciiArt {

    public AsciiArt() {
    }

    public static String generate(String text, int fontSize) {
        Graphics2D graphics = null;
        StringBuilder builder = new StringBuilder();
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
            int width = 0;
            int height = 0;
            int desc = 0;
            try {
                graphics = image.createGraphics();
                graphics.setFont(font);
                width = graphics.getFontMetrics().stringWidth(text) + 5;
                height = graphics.getFontMetrics().getHeight();
                desc = graphics.getFontMetrics().getDescent();
            } finally {
                if (graphics != null)
                    graphics.dispose();
                graphics = null;
            }

            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            graphics = image.createGraphics();
            graphics.setFont(font);

            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.drawString(text, 5, height - desc);

            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (int y = 0; y < height; y++) {
                sb.setLength(0);
                for (int x = 0; x < width; x++) {
                    sb.append(image.getRGB(x, y) == -16777216 ? " " : text.charAt(i++ % text.length()));
                }
                if (sb.toString().trim().isEmpty()) {
                    continue;
                }
                builder.append(sb.toString());
                builder.append("\n");
            }
            graphics.dispose();
        } finally {
            if (graphics != null)
                graphics.dispose();
        }
        return builder.toString();

    }

}