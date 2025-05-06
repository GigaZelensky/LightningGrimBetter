package ac.grim.grimac.command.handler;

import ac.grim.grimac.command.SenderRequirement;
import ac.grim.grimac.api.platform.sender.Sender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.processors.requirements.RequirementFailureHandler;

public class GrimCommandFailureHandler implements RequirementFailureHandler<Sender, SenderRequirement> {
    @Override
    public void handleFailure(@NonNull CommandContext<Sender> context, @NonNull SenderRequirement requirement) {
        context.sender().sendMessage(requirement.errorMessage(context.sender()));
    }
}
