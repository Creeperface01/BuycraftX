package net.buycraft.plugin.execution;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import lombok.RequiredArgsConstructor;
import net.buycraft.plugin.IBuycraftPlatform;
import net.buycraft.plugin.client.ApiException;
import net.buycraft.plugin.data.QueuedCommand;
import net.buycraft.plugin.data.QueuedPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;

@RequiredArgsConstructor
public class CommandExecutor implements Callable<CommandExecutorResult>, Runnable {
    private final IBuycraftPlatform platform;
    private final QueuedPlayer fallbackQueuedPlayer;
    private final List<QueuedCommand> commands;
    private final boolean requireOnline;
    private final boolean skipDelay;

    @Override
    public CommandExecutorResult call() throws Exception {
        final List<QueuedCommand> successfullyRun = new ArrayList<>();
        ListMultimap<Integer, QueuedCommand> delayed = ArrayListMultimap.create();

        // Determine what we can run.
        for (QueuedCommand command : commands) {
            QueuedPlayer qp = command.getPlayer();

            if (qp == null) {
                qp = fallbackQueuedPlayer;
            }

            boolean playerOnline = platform.isPlayerOnline(qp);

            if (!playerOnline && requireOnline) {
                continue;
            }

            Integer requiredSlots = command.getConditions().get("slots");
            if (requiredSlots != null && requiredSlots > 0 && playerOnline) {
                int free = platform.getFreeSlots(qp);
                if (free < requiredSlots) {
                    continue;
                }
            }

            Integer delay = command.getConditions().get("delay");
            if (delay != null && delay > 0 && !skipDelay) {
                delayed.put(delay, command);
                continue;
            }

            // Run the command now.
            String finalCommand = platform.getPlaceholderManager().doReplace(qp, command);
            platform.log(Level.INFO, String.format("Dispatching command '%s' for player '%s'.", finalCommand, qp.getName()));
            try {
                platform.dispatchCommand(finalCommand);
                successfullyRun.add(command);
            } catch (Exception e) {
                platform.log(Level.SEVERE, String.format("Could not dispatch command '%s' for player '%s'. " +
                        "This is typically a plugin error, not an issue with BuycraftX.", finalCommand, qp.getName()), e);
            }
        }

        platform.executeAsync(new Runnable() {
            @Override
            public void run() {
                List<Integer> ids = new ArrayList<>();
                for (QueuedCommand command : successfullyRun) {
                    ids.add(command.getId());
                }

                try {
                    platform.getApiClient().deleteCommand(ids);
                } catch (IOException | ApiException e) {
                    platform.log(Level.SEVERE, "Unable to mark commands as completed", e);
                }
            }
        });

        return new CommandExecutorResult(successfullyRun, delayed);
    }


    public static UUID mojangUuidToJavaUuid(String id) {
        Preconditions.checkNotNull(id, "id");
        Preconditions.checkArgument(id.matches("^[a-z0-9]{32}"), "Not a valid Mojang UUID.");

        return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" +
                id.substring(16, 20) + "-" + id.substring(20, 32));
    }

    @Override
    public void run() {
        try {
            call();
        } catch (Exception e) {
            platform.log(Level.SEVERE, "Unable to execute commands", e);
        }
    }
}