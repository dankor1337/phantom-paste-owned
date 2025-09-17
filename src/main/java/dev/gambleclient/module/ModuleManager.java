package dev.gambleclient.module;

import dev.gambleclient.Gamble;
import net.minecraft.client.gui.screen.ChatScreen;
import dev.gambleclient.event.EventListener;
import dev.gambleclient.event.events.KeyEvent;
import dev.gambleclient.module.modules.client.*;
import dev.gambleclient.module.modules.combat.*;
import dev.gambleclient.module.modules.donut.*;
import dev.gambleclient.module.modules.misc.*;
import dev.gambleclient.module.modules.render.*;
import dev.gambleclient.module.setting.BindSetting;
import dev.gambleclient.utils.EncryptedString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ModuleManager {
    private final List<Module> modules;

    public ModuleManager() {
        this.modules = new ArrayList<>();
        a();
        d();
    }

    public void a() {
        // Combat
        add(new ElytraSwap());
        add(new Hitbox());
        add(new MaceSwap());
        add(new StaticHitboxes());

        // Client
        add(new ConfigDebug());
        add(new Phantom());
        add(new SelfDestruct());

        // Render
        add(new Animations());
        add(new ChunkFinder());
        add(new FullBright());
        add(new HUD());
        add(new KelpESP());
        add(new NoRender());
        add(new PlayerESP());
        add(new StorageESP());
        add(new SwingSpeed());
        add(new TargetHUD());

        // Misc
        add(new AutoEat());
        add(new AutoFirework());
        add(new AutoMine());
        add(new AutoTPA());
        add(new AutoTool());
        add(new CordSnapper());
        add(new ElytraGlide());
        add(new FastPlace());
        add(new Freecam());
        add(new KeyPearl());
        add(new NameProtect());

        // Crystal
        add(new AnchorMacro());
        add(new AutoCrystal());
        add(new AutoInventoryTotem());
        add(new AutoTotem());
        add(new DoubleAnchor());
        add(new HoverTotem());

        // Donut
        add(new AntiTrap());
        add(new BStarPhantom());
        add(new AuctionSniper());
        add(new AutoSell());
        add(new AutoSpawnerSell());
        add(new BoneDropper());
        add(new NetheriteFinder());
        add(new RtpBaseFinder());
        add(new RTPEndBaseFinder());
        add(new ShulkerDropper());
        add(new TunnelBaseFinder());

        // Macro
    }

    public List<Module> b() {
        return this.modules.stream().filter(Module::isEnabled).toList();
    }

    public List<Module> c() {
        return this.modules;
    }

    public void d() {
        dev.gambleclient.Gamble.INSTANCE.getEventBus().register(this);
        for (final Module next : this.modules) {
            next.addSetting(new BindSetting(EncryptedString.of("Keybind"), next.getKeybind(), true).setDescription(EncryptedString.of("Key to enabled the module")));
        }
    }

    public List<Module> a(final Category category) {
        return this.modules.stream().filter(module -> module.getCategory() == category).toList();
    }

    public Module getModuleByClass(final Class<? extends Module> obj) {
        Objects.requireNonNull(obj);
        return this.modules.stream().filter(obj::isInstance).findFirst().orElse(null);
    }

    public void add(final Module module) {
        dev.gambleclient.Gamble.INSTANCE.getEventBus().register(module);
        this.modules.add(module);
    }

    @EventListener
    public void a(final KeyEvent keyEvent) {
        if (dev.gambleclient.Gamble.mc.player == null || dev.gambleclient.Gamble.mc.currentScreen instanceof ChatScreen) {
            return;
        }

        // Do not toggle modules while ClickGUI is open
        if (dev.gambleclient.Gamble.mc.currentScreen instanceof dev.gambleclient.gui.ClickGUI) {
            return;
        }

        this.modules.forEach(module -> {
            // Only prevent SelfDestruct from being toggled when it's active
            if (module.getKeybind() == keyEvent.key && keyEvent.mode == 1) {
                if (module instanceof SelfDestruct && SelfDestruct.isActive) {
                    return; // Skip SelfDestruct if it's already active
                }
                // Prevent DonutBBC GUI from being opened after self-destruct
                if (module instanceof Phantom && SelfDestruct.hasSelfDestructed) {
                    return; // Skip DonutBBC if self-destruct has been activated
                }
                module.toggle();
            }
        });
    }
}
