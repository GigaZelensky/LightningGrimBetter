package ac.grim.grimac.checks.impl.breaking;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.storage.verbose.VerboseBuf;
import ac.grim.grimac.api.storage.verbose.VerboseFormatter;
import ac.grim.grimac.api.storage.verbose.VerboseRenderContext;
import ac.grim.grimac.api.storage.verbose.VerboseSchema;
import ac.grim.grimac.api.storage.verbose.VerboseSink;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.internal.storage.checks.CheckRegistry;
import ac.grim.grimac.internal.storage.verbose.VerboseRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BreakingVerbose {
    private BreakingVerbose() {
    }

    public static void register(@NotNull VerboseRegistry registry, @NotNull CheckRegistry checks) {
        registerStructured(registry, checks, AirLiquidBreak.class, AirLiquidBreak.V,
                formatter(AirLiquidBreak.V, (in, ctx, out) ->
                        out.text("block=").text(in.rstr())
                                .text(", type=").text(in.rstr())));
        registerStructured(registry, checks, FarBreak.class, FarBreak.V,
                formatter(FarBreak.V, (in, ctx, out) ->
                        out.text(String.format("distance=%.2f", in.rf64()))));
        registerStructured(registry, checks, FastBreak.class, FastBreak.V,
                formatter(FastBreak.V, BreakingVerbose::renderFastBreak));
        registerStructured(registry, checks, InvalidBreak.class, InvalidBreak.V,
                formatter(InvalidBreak.V, (in, ctx, out) ->
                        out.text("face=").num(in.rzz())
                                .text(", action=").text(in.rstr())));
        registerStructured(registry, checks, MultiBreak.class, MultiBreak.V,
                formatter(MultiBreak.V, BreakingVerbose::renderMultiBreak));
        registerStructured(registry, checks, PositionBreakA.class, PositionBreakA.V,
                formatter(PositionBreakA.V, (in, ctx, out) ->
                        out.text("action=").text(in.rstr())
                                .text(", face=").text(in.rstr())));
        registerStructured(registry, checks, PositionBreakB.class, PositionBreakB.V,
                formatter(PositionBreakB.V, (in, ctx, out) ->
                        out.text("lastFace=").text(in.rstr())
                                .text(", action=").text(in.rstr())));
        registerStructured(registry, checks, RotationBreak.class, RotationBreak.V,
                formatter(RotationBreak.V, BreakingVerbose::renderRotationBreak));
        registerStructured(registry, checks, WrongBreak.class, WrongBreak.V,
                formatter(WrongBreak.V, (in, ctx, out) ->
                        out.text("action=").text(in.rstr())
                                .text(", last=").text(in.rstr())
                                .text(", pos=").text(in.rstr())));
    }

    private static void renderFastBreak(
            @NotNull VerboseBuf in,
            @NotNull VerboseRenderContext ctx,
            @NotNull VerboseSink out) {
        boolean delayMode = in.rbool();
        long delay = in.rvl();
        double diff = in.rf64();
        double balance = in.rf64();
        String type = in.rstr();
        if (delayMode) {
            out.text("delay=").text(Double.toString((double) delay)).text("ms, type=").text(type);
        } else {
            out.text("diff=").num(diff).text("ms, balance=").num(balance).text("ms, type=").text(type);
        }
    }

    private static void renderMultiBreak(
            @NotNull VerboseBuf in,
            @NotNull VerboseRenderContext ctx,
            @NotNull VerboseSink out) {
        out.text("face=").text(in.rstr())
                .text(", lastFace=").text(in.rstr())
                .text(", pos=").text(in.rstr())
                .text(", lastPos=").text(in.rstr());
    }

    private static void renderRotationBreak(
            @NotNull VerboseBuf in,
            @NotNull VerboseRenderContext ctx,
            @NotNull VerboseSink out) {
        boolean preFlying = in.rbool();
        out.text(preFlying ? "pre-flying" : "post-flying")
                .text(", action=").text(in.rstr());
    }

    private static void registerStructured(
            @NotNull VerboseRegistry registry,
            @NotNull CheckRegistry checks,
            @NotNull Class<? extends Check> checkClass,
            @NotNull VerboseSchema schema,
            @NotNull VerboseFormatter formatter) {
        registerSchema(registry, checks, checkClass, schema);
        registerFormatter(registry, checkClass, formatter);
    }

    private static void registerSchema(
            @NotNull VerboseRegistry registry,
            @NotNull CheckRegistry checks,
            @NotNull Class<? extends Check> checkClass,
            @NotNull VerboseSchema schema) {
        CheckData data = checkData(checkClass);
        if (data.verboseVersion() < 1) {
            throw new IllegalStateException(checkClass.getName() + " is missing verboseVersion");
        }
        if (schema.version() != data.verboseVersion()) {
            throw new IllegalStateException(checkClass.getName() + " verbose schema v"
                    + schema.version() + " does not match @CheckData verboseVersion="
                    + data.verboseVersion());
        }

        checks.intern(data.stableKey(), data.name(), data.description(), safePluginVersion());
        registry.register(data.stableKey(), schema);
    }

    private static void registerFormatter(
            @NotNull VerboseRegistry registry,
            @NotNull Class<? extends Check> checkClass,
            @NotNull VerboseFormatter formatter) {
        CheckData data = checkData(checkClass);
        if (formatter.version() != data.verboseVersion()) {
            throw new IllegalStateException(checkClass.getName() + " verbose formatter v"
                    + formatter.version() + " does not match @CheckData verboseVersion="
                    + data.verboseVersion());
        }

        registry.registerFormatter(data.stableKey(), formatter);
    }

    private static @NotNull CheckData checkData(@NotNull Class<? extends Check> checkClass) {
        CheckData data = checkClass.getAnnotation(CheckData.class);
        if (data == null) {
            throw new IllegalStateException(checkClass.getName() + " is missing @CheckData");
        }
        if (data.stableKey().isBlank()) {
            throw new IllegalStateException(checkClass.getName() + " is missing a stableKey");
        }
        return data;
    }

    private static @Nullable String safePluginVersion() {
        try {
            return GrimAPI.INSTANCE.getExternalAPI().getGrimVersion();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @NotNull VerboseFormatter formatter(
            @NotNull VerboseSchema schema,
            @NotNull Renderer renderer) {
        return new VerboseFormatter() {
            @Override
            public int version() {
                return schema.version();
            }

            @Override
            public void render(
                    @NotNull VerboseBuf in,
                    @NotNull VerboseRenderContext ctx,
                    @NotNull VerboseSink out) {
                renderer.render(in, ctx, out);
            }
        };
    }

    private interface Renderer {
        void render(
                @NotNull VerboseBuf in,
                @NotNull VerboseRenderContext ctx,
                @NotNull VerboseSink out);
    }
}
