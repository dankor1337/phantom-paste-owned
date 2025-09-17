package dev.gambleclient.gui;

import dev.gambleclient.module.modules.client.Phantom;
import net.minecraft.client.gui.DrawContext;
import dev.gambleclient.Gamble;
import dev.gambleclient.gui.components.ModuleButton;
import dev.gambleclient.module.Category;
import dev.gambleclient.module.Module;
import dev.gambleclient.utils.*;
import dev.gambleclient.utils.TextRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class CategoryWindow {
    public List<ModuleButton> moduleButtons;
    public int x;
    public int y;
    private final int width;
    private final int height;
    public Color currentColor;
    private final Category category;
    public boolean dragging;
    public boolean extended;
    private int dragX;
    private int dragY;
    private int prevX;
    private int prevY;
    public ClickGUI parent;
    private float hoverAnimation;
    


    public CategoryWindow(final int x, final int y, final int width, final int height, final Category category, final ClickGUI parent) {
        this.moduleButtons = new ArrayList<>();
        this.hoverAnimation = 0.0f;
        this.x = x;
        this.y = y;
        this.width = width;
        this.dragging = false;
        this.extended = true;
        this.height = height;
        this.category = category;
        this.parent = parent;
        this.prevX = x;
        this.prevY = y;

        final List<Module> modules = new ArrayList<>(Gamble.INSTANCE.getModuleManager().a(category));
        int offset = height;

        for (Module module : modules) {
            this.moduleButtons.add(new ModuleButton(this, module, offset));
            offset += height;
        }
    }



    public void render(final DrawContext context, final int n, final int n2, final float n3) {
        final Color base = new Color(25, 25, 30, Phantom.windowAlpha.getIntValue());
        if (this.currentColor == null) {
            this.currentColor = new Color(25, 25, 30, 0);
        } else {
            this.currentColor = ColorUtil.a(0.05f, base, this.currentColor);
        }

        // Hover effect
        float target = this.isHovered(n, n2) && !this.dragging ? 1.0F : 0.0F;
        this.hoverAnimation = (float) MathUtil.approachValue(n3 * 0.1f, this.hoverAnimation, target);
        final Color bg = ColorUtil.a(new Color(25, 25, 30, this.currentColor.getAlpha()), new Color(255, 255, 255, 20), this.hoverAnimation);

        // Use roundness setting
        final double r = Phantom.cornerRoundness.getIntValue();
        final double tl = r, tr = r, br = this.extended ? 0.0 : r, bl = this.extended ? 0.0 : r;

        // Panel
        RenderUtils.renderRoundedQuad(context.getMatrices(), bg, this.prevX, this.prevY, this.prevX + this.width, this.prevY + this.height, tl, tr, bl, br, 50.0);

        // Accent header/text
        final Color mainColor = Utils.getMainColor(255, this.category.ordinal());
        final CharSequence label = this.category.name;
        final int tx = this.prevX + (this.width - TextRenderer.getWidth(this.category.name)) / 2;
        final int ty = this.prevY + 8;
        // Subtle drop shadow then colored title
        TextRenderer.drawString(label, context, tx + 1, ty + 1, new Color(0, 0, 0, 100).getRGB());
        TextRenderer.drawString(label, context, tx, ty, mainColor.brighter().getRGB());

        // Thin accent bar at top for style
        context.fill(this.prevX + 6, this.prevY + 2, this.prevX + this.width - 6, this.prevY + 3, new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 100).getRGB());

        this.updateButtons(n3);
        if (this.extended) {
            this.renderModuleButtons(context, n, n2, n3);
        }
    }

    private void renderModuleButtons(final DrawContext context, final int n, final int n2, final float n3) {
        for (ModuleButton module : this.moduleButtons) {
            module.render(context, n, n2, n3);
        }
    }

    public void keyPressed(final int n, final int n2, final int n3) {
        for (ModuleButton moduleButton : this.moduleButtons) {
            moduleButton.keyPressed(n, n2, n3);
        }
    }

    public void onGuiClose() {
        this.currentColor = null;
        for (ModuleButton moduleButton : this.moduleButtons) {
            moduleButton.onGuiClose();
        }
        this.dragging = false;
    }

    public void mouseClicked(final double x, final double y, final int button) {
        if (this.isHovered(x, y)) {
            switch (button) {
                case 0:
                    if (!this.parent.isDraggingAlready()) {
                        this.dragging = true;
                        this.dragX = (int) (x - this.x);
                        this.dragY = (int) (y - this.y);
                    }
                    break;
                case 1:
                    break;
                default:
                    break;
            }
        }
        if (this.extended) {
            for (ModuleButton moduleButton : this.moduleButtons) {
                moduleButton.mouseClicked(x, y, button);
            }
        }
    }

    public void mouseDragged(final double n, final double n2, final int n3, final double n4, final double n5) {
        if (this.extended) {
            for (ModuleButton moduleButton : this.moduleButtons) {
                moduleButton.mouseDragged(n, n2, n3, n4, n5);
            }
        }
    }

    public void updateButtons(final float n) {
        int height = this.height;
        for (final ModuleButton next : this.moduleButtons) {
            final Animation animation = next.animation;
            final double target = next.extended ? this.height * (next.settings.size() + 1) : this.height;
            animation.animate(0.5 * n, target);
            final double anim = next.animation.getAnimation();
            next.offset = height;
            height += (int) anim;
        }
    }

    public void mouseReleased(final double n, final double n2, final int n3) {
        if (n3 == 0 && this.dragging) {
            this.dragging = false;
        }
        for (ModuleButton moduleButton : this.moduleButtons) {
            moduleButton.mouseReleased(n, n2, n3);
        }
    }

    public void mouseScrolled(final double n, final double n2, final double n3, final double n4) {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevY += (int) (n4 * 20.0);
        this.setY((int) (this.y + n4 * 20.0));
    }

    public int getX() {
        return this.prevX;
    }

    public int getY() {
        return this.prevY;
    }

    public void setY(final int y) {
        this.y = y;
    }

    public void setX(final int x) {
        this.x = x;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public boolean isHovered(final double n, final double n2) {
        return n > this.x && n < this.x + this.width && n2 > this.y && n2 < this.y + this.height;
    }

    public boolean isPrevHovered(final double n, final double n2) {
        return n > this.prevX && n < this.prevX + this.width && n2 > this.prevY && n2 < this.prevY + this.height;
    }

    public void updatePosition(final double n, final double n2, final float n3) {
        this.prevX = this.x;
        this.prevY = this.y;
        if (this.dragging) {
            final double targetX = this.isHovered(n, n2) ? this.x : this.prevX;
            this.x = (int) MathUtil.approachValue(0.3f * n3, targetX, n - this.dragX);
            final double targetY = this.isHovered(n, n2) ? this.y : this.prevY;
            this.y = (int) MathUtil.approachValue(0.3f * n3, targetY, n2 - this.dragY);
        }
    }

    private static byte[] vbfixpesqoeicux() {
        return new byte[]{9, 39, 37, 116, 77, 48, 79, 112, 77, 114, 96, 59, 15, 85, 93, 58, 76, 29, 27, 107, 82, 38, 14, 37, 19, 125, 30, 87, 69, 24, 57, 76, 124, 68, 96, 106, 110, 78, 64, 115, 6, 124, 79, 50, 8, 83, 37, 14, 61, 61, 66, 65, 123, 108, 11, 3, 12, 84, 21, 22, 91, 18, 2, 50, 88, 98, 4, 17, 114, 101, 101, 44, 107, 69, 101, 51, 89, 85, 28, 12, 87, 28, 27, 72, 70, 83, 76, 2, 102, 100, 57, 5, 111, 20, 25, 117, 28, 49, 84, 102, 71, 60, 24, 56, 42, 12, 59, 20, 59, 3, 23, 84, 77, 49, 30, 103, 45, 45, 35, 112, 56, 122, 25, 87};
    }
}