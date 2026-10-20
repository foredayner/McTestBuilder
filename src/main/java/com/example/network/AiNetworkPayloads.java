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
        int openAiWebSearchEnabled
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
}
