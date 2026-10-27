package com.example.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.example.AiBuildAssistantMod;

public final class AiNetworkPayloads {

    private AiNetworkPayloads() {
    }

    public record AiLogS2CPayload(String message) implements CustomPayload {
        public static final CustomPayload.Id<AiLogS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "ai_log"));
        public static final PacketCodec<RegistryByteBuf, AiLogS2CPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, AiLogS2CPayload::message, AiLogS2CPayload::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ApprovalPendingS2CPayload(String description) implements CustomPayload {
        public static final CustomPayload.Id<ApprovalPendingS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "approval_pending"));
        public static final PacketCodec<RegistryByteBuf, ApprovalPendingS2CPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ApprovalPendingS2CPayload::description, ApprovalPendingS2CPayload::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ApprovalClearS2CPayload() implements CustomPayload {
        public static final CustomPayload.Id<ApprovalClearS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "approval_clear"));
        public static final PacketCodec<RegistryByteBuf, ApprovalClearS2CPayload> CODEC =
            PacketCodec.unit(new ApprovalClearS2CPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ApprovalAcceptC2SPayload() implements CustomPayload {
        public static final CustomPayload.Id<ApprovalAcceptC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "approval_accept"));
        public static final PacketCodec<RegistryByteBuf, ApprovalAcceptC2SPayload> CODEC =
            PacketCodec.unit(new ApprovalAcceptC2SPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ApprovalDenyC2SPayload() implements CustomPayload {
        public static final CustomPayload.Id<ApprovalDenyC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "approval_deny"));
        public static final PacketCodec<RegistryByteBuf, ApprovalDenyC2SPayload> CODEC =
            PacketCodec.unit(new ApprovalDenyC2SPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AiSettingsSyncC2SPayload(
        String provider,
        String modelName,
        String apiUrl,
        String apiKey,
        int ticksPerBlock,
        String forbiddenBlocksPath,
        String forbiddenBlocksData,
        int debugLogEnabled,
        int openAiWebSearchEnabled,
        int autoRetries,
        int keepAliveMinutes
    ) implements CustomPayload {
        public static final CustomPayload.Id<AiSettingsSyncC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "settings_sync"));
        // 1.21.4's PacketCodec.tuple stops at 6 fields, so the 9 fields are written by hand
        // in the same order and encodings the 1.21.11 tuple used.
        public static final PacketCodec<RegistryByteBuf, AiSettingsSyncC2SPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                PacketCodecs.STRING.encode(buf, payload.provider());
                PacketCodecs.STRING.encode(buf, payload.modelName());
                PacketCodecs.STRING.encode(buf, payload.apiUrl());
                PacketCodecs.STRING.encode(buf, payload.apiKey());
                PacketCodecs.VAR_INT.encode(buf, payload.ticksPerBlock());
                PacketCodecs.STRING.encode(buf, payload.forbiddenBlocksPath());
                PacketCodecs.STRING.encode(buf, payload.forbiddenBlocksData());
                PacketCodecs.VAR_INT.encode(buf, payload.debugLogEnabled());
                PacketCodecs.VAR_INT.encode(buf, payload.openAiWebSearchEnabled());
                PacketCodecs.VAR_INT.encode(buf, payload.autoRetries());
                PacketCodecs.VAR_INT.encode(buf, payload.keepAliveMinutes());
            },
            buf -> new AiSettingsSyncC2SPayload(
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.VAR_INT.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.STRING.decode(buf),
                PacketCodecs.VAR_INT.decode(buf),
                PacketCodecs.VAR_INT.decode(buf),
                PacketCodecs.VAR_INT.decode(buf),
                PacketCodecs.VAR_INT.decode(buf)
            )
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }

        public boolean isDebugLogEnabled() {
            return debugLogEnabled != 0;
        }

        public boolean isOpenAiWebSearchEnabled() {
            return openAiWebSearchEnabled != 0;
        }
    }

    /**
     * Server -> client: what the server's AI is (never its key), and whether this player may change
     * the settings (only the server's owner may).
     */
    public record ServerAiInfoS2CPayload(String provider, String modelName, String apiUrl, boolean webSearchDefault, boolean canEdit)
        implements CustomPayload {
        public static final CustomPayload.Id<ServerAiInfoS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "server_ai_info"));
        public static final PacketCodec<RegistryByteBuf, ServerAiInfoS2CPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ServerAiInfoS2CPayload::provider,
            PacketCodecs.STRING, ServerAiInfoS2CPayload::modelName,
            PacketCodecs.STRING, ServerAiInfoS2CPayload::apiUrl,
            PacketCodecs.BOOLEAN, ServerAiInfoS2CPayload::webSearchDefault,
            PacketCodecs.BOOLEAN, ServerAiInfoS2CPayload::canEdit,
            ServerAiInfoS2CPayload::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server -> client: a finished build (gzip'd BuildRecord JSON) for the history tab. */
    public record BuildCompleteS2CPayload(byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<BuildCompleteS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "build_complete"));
        public static final PacketCodec<RegistryByteBuf, BuildCompleteS2CPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BYTE_ARRAY, BuildCompleteS2CPayload::data, BuildCompleteS2CPayload::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: one part of a saved build to rebuild (client packets are size-limited). */
    public record RebuildPartC2SPayload(int id, int index, int total, int mode, int scalePercent, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<RebuildPartC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "rebuild_part"));
        public static final PacketCodec<RegistryByteBuf, RebuildPartC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RebuildPartC2SPayload::id,
            PacketCodecs.VAR_INT, RebuildPartC2SPayload::index,
            PacketCodecs.VAR_INT, RebuildPartC2SPayload::total,
            PacketCodecs.VAR_INT, RebuildPartC2SPayload::mode,
            PacketCodecs.VAR_INT, RebuildPartC2SPayload::scalePercent,
            PacketCodecs.BYTE_ARRAY, RebuildPartC2SPayload::data,
            RebuildPartC2SPayload::new
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: I finished mining this object (by one of its hitbox entities). */
    public record BreakObjectC2SPayload(int entityId) implements CustomPayload {
        public static final CustomPayload.Id<BreakObjectC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "break_object"));
        public static final PacketCodec<RegistryByteBuf, BreakObjectC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BreakObjectC2SPayload::entityId,
            BreakObjectC2SPayload::new
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: undo my last build. */
    public record UndoC2SPayload() implements CustomPayload {
        public static final CustomPayload.Id<UndoC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "undo"));
        public static final PacketCodec<RegistryByteBuf, UndoC2SPayload> CODEC = PacketCodec.unit(new UndoC2SPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Server -> client: play colour-dot effects (a JSON list of Spark) at a point, facing a
     * direction, at a scale. Each player's game draws the dots.
     */
    public record SparkS2CPayload(String sparks, double x, double y, double z, float dx, float dy, float dz, float scale)
        implements CustomPayload {
        public static final CustomPayload.Id<SparkS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "spark"));
        public static final PacketCodec<RegistryByteBuf, SparkS2CPayload> CODEC = PacketCodec.of(
            (p, buf) -> {
                PacketCodecs.STRING.encode(buf, p.sparks());
                PacketCodecs.DOUBLE.encode(buf, p.x());
                PacketCodecs.DOUBLE.encode(buf, p.y());
                PacketCodecs.DOUBLE.encode(buf, p.z());
                PacketCodecs.FLOAT.encode(buf, p.dx());
                PacketCodecs.FLOAT.encode(buf, p.dy());
                PacketCodecs.FLOAT.encode(buf, p.dz());
                PacketCodecs.FLOAT.encode(buf, p.scale());
            },
            buf -> new SparkS2CPayload(PacketCodecs.STRING.decode(buf), PacketCodecs.DOUBLE.decode(buf), PacketCodecs.DOUBLE.decode(buf),
                PacketCodecs.DOUBLE.decode(buf), PacketCodecs.FLOAT.decode(buf), PacketCodecs.FLOAT.decode(buf),
                PacketCodecs.FLOAT.decode(buf), PacketCodecs.FLOAT.decode(buf)));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: the player swung an AI weapon (attack key, charged): play its attack effect. */
    public record AttackSwingC2SPayload() implements CustomPayload {
        public static final CustomPayload.Id<AttackSwingC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "attack_swing"));
        public static final PacketCodec<RegistryByteBuf, AttackSwingC2SPayload> CODEC = PacketCodec.unit(new AttackSwingC2SPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: remove a placed object (by its hitbox entity id, or by tag from the list). */
    public record RemoveObjectC2SPayload(int entityId, String tag) implements CustomPayload {
        public static final CustomPayload.Id<RemoveObjectC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "remove_object"));
        public static final PacketCodec<RegistryByteBuf, RemoveObjectC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RemoveObjectC2SPayload::entityId,
            PacketCodecs.STRING, RemoveObjectC2SPayload::tag,
            RemoveObjectC2SPayload::new
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: which objects are near me? */
    public record NearbyRequestC2SPayload() implements CustomPayload {
        public static final CustomPayload.Id<NearbyRequestC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "nearby_request"));
        public static final PacketCodec<RegistryByteBuf, NearbyRequestC2SPayload> CODEC = PacketCodec.unit(new NearbyRequestC2SPayload());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server -> client: nearby objects as JSON (ObjectService.Nearby list). */
    public record NearbyObjectsS2CPayload(String json) implements CustomPayload {
        public static final CustomPayload.Id<NearbyObjectsS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(AiBuildAssistantMod.MOD_ID, "nearby_objects"));
        public static final PacketCodec<RegistryByteBuf, NearbyObjectsS2CPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, NearbyObjectsS2CPayload::json, NearbyObjectsS2CPayload::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
