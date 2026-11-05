package dev.mrwavy.everwynwatch;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple Voice Chat plugin: while you're listening in the app, voices near that
 * player are decoded and passed to {@link VoiceBridge}. Only loaded if Simple
 * Voice Chat is installed.
 */
public class VoicePlugin implements VoicechatPlugin {
    private VoicechatApi api;
    private VoicechatServerApi serverApi;
    private final Map<UUID, OpusDecoder> decoders = new HashMap<>();

    @Override
    public String getPluginId() {
        return "everwyn-watch";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        VoiceBridge.svcLoaded = true;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> serverApi = e.getVoicechat());
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
    }

    private void onMic(MicrophonePacketEvent e) {
        try {
            if (!VoiceBridge.active()) {
                if (!decoders.isEmpty()) {
                    decoders.values().forEach(OpusDecoder::close);
                    decoders.clear();
                }
                return;
            }
            VoicechatConnection sender = e.getSenderConnection();
            if (sender == null) return;
            UUID speaker = sender.getPlayer().getUuid();
            UUID target = VoiceBridge.target();
            if (target == null || !VoiceBridge.inRange(speaker, e.getPacket().isWhispering())) return;

            // Group chats are private: only include them if the listened-to player is in that group
            if (sender.isInGroup() && !speaker.equals(target) && serverApi != null) {
                VoicechatConnection t = serverApi.getConnectionOf(target);
                if (t == null || !t.isInGroup() || !t.getGroup().getId().equals(sender.getGroup().getId())) return;
            }

            byte[] opus = e.getPacket().getOpusEncodedData();
            OpusDecoder dec = decoders.computeIfAbsent(speaker, k -> api.createDecoder());
            if (opus == null || opus.length == 0) {
                dec.resetState();
                return;
            }
            short[] pcm = dec.decode(opus);
            if (pcm != null && pcm.length > 0) VoiceBridge.addFrame(speaker, pcm);
        } catch (Exception ex) {
            EverwynWatch.LOG.debug("Voice relay error", ex);
        }
    }
}
