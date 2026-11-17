package dev.chatmodes;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatModes extends JavaPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Разовый режим для одного следующего сообщения (/g текст, /l текст). */
    private final Map<UUID, Boolean> override = new ConcurrentHashMap<>();

    private Component prefix(boolean global) {
        String tag = global
                ? "<gradient:#ff512f:#f9d423>[G]</gradient>"
                : "<gradient:#11998e:#38ef7d>[L]</gradient>";
        return MM.deserialize(tag)
                .clickEvent(ClickEvent.runCommand("/chatmode"))
                .hoverEvent(HoverEvent.showText(MM.deserialize(
                        "<yellow>Нажми, чтобы переключить режим твоего чата</yellow>\n"
                                + "<gray>! в начале = один раз в глобальный, ? = один раз в локальный</gray>")));
    }

    private boolean isGlobal(Player p) {
        boolean def = "global".equalsIgnoreCase(getConfig().getString("default-mode", "local"));
        return getConfig().getBoolean("modes." + p.getUniqueId(), def);
    }

    private void setGlobal(Player p, boolean global) {
        getConfig().set("modes." + p.getUniqueId(), global);
        saveConfig();
    }

    private void announce(Player p) {
        boolean g = isGlobal(p);
        p.sendMessage(MM.deserialize("<gray>Теперь ты пишешь в </gray>")
                .append(prefix(g))
                .append(MM.deserialize(g
                        ? " <gold>глобальный чат</gold>"
                        : " <green>локальный чат</green>")));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        for (String name : new String[]{"chatmode", "g", "l"}) {
            var cmd = getCommand(name);
            if (cmd != null) cmd.setExecutor(this);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());

        boolean global;
        Boolean once = override.remove(p.getUniqueId());
        if (once != null) {
            global = once;
        } else if (plain.length() > 1 && plain.startsWith("!")) {
            global = true;
            e.message(Component.text(plain.substring(1).stripLeading()));
        } else if (plain.length() > 1 && plain.startsWith("?")) {
            global = false;
            e.message(Component.text(plain.substring(1).stripLeading()));
        } else {
            global = isGlobal(p);
        }

        if (!global) {
            Location loc = p.getLocation();
            double r = getConfig().getDouble("local-radius", 100);
            double r2 = r * r;
            e.viewers().removeIf(a -> a instanceof Player v
                    && !v.equals(p)
                    && !v.hasPermission("chatmodes.spy")
                    && !(v.getWorld().equals(loc.getWorld()) && v.getLocation().distanceSquared(loc) <= r2));

            long heard = e.viewers().stream().filter(a -> a instanceof Player v && !v.equals(p)).count();
            if (heard == 0) {
                p.sendActionBar(MM.deserialize("<gray>Рядом никого нет. Напиши <gold>!</gold> в начале, чтобы услышали все</gray>"));
            }
        }

        final boolean g = global;
        e.renderer(ChatRenderer.viewerUnaware((source, name, msg) ->
                Component.empty()
                        .append(prefix(g))
                        .append(Component.text(" "))
                        .append(name.colorIfAbsent(g ? NamedTextColor.GOLD : NamedTextColor.GREEN))
                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                        .append(msg.colorIfAbsent(g ? NamedTextColor.WHITE : NamedTextColor.GRAY))));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Только для игроков.");
            return true;
        }
        String name = c.getName();

        if (name.equals("chatmode")) {
            setGlobal(p, !isGlobal(p));
            announce(p);
            return true;
        }

        boolean global = name.equals("g");
        if (a.length == 0) {
            setGlobal(p, global);
            announce(p);
        } else {
            // одно сообщение в нужный чат, режим не меняется
            override.put(p.getUniqueId(), global);
            p.chat(String.join(" ", a));
        }
        return true;
    }
}
