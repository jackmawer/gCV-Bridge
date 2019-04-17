package uk.co.n3fs.mc.gcvbridge;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.lucko.gchat.GChat;
import me.lucko.gchat.api.GChatApi;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.slf4j.Logger;
import uk.co.n3fs.mc.gcvbridge.discord.ChatListener;
import uk.co.n3fs.mc.gcvbridge.discord.CommandListener;
import uk.co.n3fs.mc.gcvbridge.discord.ConnectionListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(
    id = "gcv-bridge",
    name = "gChat-Velocity Bridge",
    authors = "md678685",
    version = "VERSION", // filled in during build
    description = "A Discord bridge plugin for gChat for Velocity.",
    dependencies = {
        @Dependency(id = "gchat-velocity")
    }
)
public class GCVBridge {
    @Inject private ProxyServer proxy;
    @Inject private Logger logger;
    @Inject @DataDirectory private Path dataDirectory;

    private GCVBConfig config;

    private GChatApi gcApi;
    private DiscordApi dApi;

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        logger.info("Enabling gCV-Bridge v" + getDescription().getVersion().get());

        // Attempt to load config
        try {
            this.config = loadConfig();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }

        gcApi = GChat.getApi();
        startBot();
    }

    @Subscribe
    public boolean onReload(ProxyReloadEvent event) {
        return reloadConfig();
    }

    public boolean reloadConfig() {
        final String oldToken = config.getToken();
        try {
            config = loadConfig();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (!config.getToken().equals(oldToken)) {
            startBot();
        }

        return true;
    }

    private GCVBConfig loadConfig() throws Exception {
        ConfigurationNode config = YAMLConfigurationLoader.builder()
            .setFile(getBundledFile("config.yml"))
            .build()
            .load();
        return new GCVBConfig(config);
    }

    private void startBot() {
        if (dApi != null) {
            dApi.disconnect();
            dApi = null;
        }

        ConnectionListener connListener = new ConnectionListener(logger);
        CommandListener commandListener = new CommandListener(this, proxy);
        ChatListener chatListener = new ChatListener(this, proxy);

        new DiscordApiBuilder()
            .setToken(config.getToken())
            .addLostConnectionListener(connListener::onConnectionLost)
            .addReconnectListener(connListener::onReconnect)
            .addResumeListener(connListener::onResume)
            .addMessageCreateListener(commandListener::onPlayerlist)
            .addMessageCreateListener(chatListener::onMessage)
            .login().thenAccept(api -> dApi = api);
    }

    private File getBundledFile(String name) {
        File file = new File(dataDirectory.toFile(), name);

        if (!file.exists()) {
            dataDirectory.toFile().mkdir();
            try (InputStream in = GCVBridge.class.getResourceAsStream("/" + name)) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return file;
    }

    private PluginDescription getDescription() {
        return proxy.getPluginManager().getPlugin("gcv-bridge").map(PluginContainer::getDescription).orElse(null);
    }

    public GCVBConfig getConfig() {
        return config;
    }

    public GChatApi getGChatApi() {
        return gcApi;
    }

    public Logger getLogger() {
        return logger;
    }

    public DiscordApi getDApi() {
        return dApi;
    }
}
