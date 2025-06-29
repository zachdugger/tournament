package com.blissy.tournaments.gui;

import com.blissy.tournaments.Tournaments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * Factory for creating and opening tournament containers
 */
public class ContainerFactory {

    /**
     * Create an inventory of the specified size
     */
    public static Inventory createInventory(int rows) {
        return new Inventory(rows * 9);
    }

    /**
     * Open a tournament GUI with proper click handling
     */
    public static void openTournamentGui(ServerPlayerEntity player, String title,
                                         ContainerSetupCallback setupCallback) {
        // Create provider
        INamedContainerProvider provider = new INamedContainerProvider() {
            @Override
            public ITextComponent getDisplayName() {
                return new StringTextComponent(title);
            }

            @Nullable
            @Override
            public Container createMenu(int windowId, PlayerInventory playerInventory, PlayerEntity player) {
                // Create inventory
                Inventory inventory = createInventory(6);

                // Let caller set up inventory contents
                if (setupCallback != null) {
                    setupCallback.setupContainer(inventory, (ServerPlayerEntity) player);
                }

                // Create tournament container with click handling
                return new ClickInterceptingContainer(windowId, playerInventory, inventory, 6);
            }
        };

        // Open the GUI
        NetworkHooks.openGui(player, provider);
    }

    /**
     * Callback for setting up container contents
     */
    public interface ContainerSetupCallback {
        void setupContainer(Inventory inventory, ServerPlayerEntity player);
    }

    /**
     * Container that intercepts clicks for handling tournament actions
     */
    public static class ClickInterceptingContainer extends ChestContainer {
        private final IInventory inventory;

        public ClickInterceptingContainer(int id, PlayerInventory playerInventory, IInventory inventory, int rows) {
            // In 1.16.5, ChestContainer constructor needs container type and row count
            super(
                    rows == 6 ? ContainerType.GENERIC_9x6 : ContainerType.GENERIC_9x3,
                    id,
                    playerInventory,
                    inventory,
                    rows
            );
            this.inventory = inventory;

            // Register container ID
            Tournaments.LOGGER.debug("Created click-intercepting container with ID: {}", id);
        }

        @Override
        public ItemStack clicked(int slotId, int dragType, net.minecraft.inventory.container.ClickType clickType, PlayerEntity player) {
            // Try to handle as tournament action first
            if (player instanceof ServerPlayerEntity && slotId >= 0 && slotId < slots.size()) {
                var slot = this.slots.get(slotId);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();

                    // Check if this is a border item - if so, cancel the click
                    if (stack.hasTag() && stack.getTag().contains("BorderItem")) {
                        return ItemStack.EMPTY; // Cancel the click
                    }

                    if (stack.hasTag() && stack.getTag().contains("GuiAction")) {
                        // This is a tournament GUI action
                        String action = stack.getTag().getString("GuiAction");
                        Tournaments.LOGGER.info("GUI CLICK DEBUG: Intercepted click on tournament GUI action '{}' at slot {}",
                                action, slotId);

                        // Process the action on the next tick to avoid concurrent modification
                        // In 1.16.5 we need to use a Runnable directly
                        player.getCommandSenderWorld().getServer().execute(() -> {
                            TournamentGuiHandler.processItemClick((ServerPlayerEntity) player, stack);
                        });

                        // Don't let the normal click handling happen for this item
                        return ItemStack.EMPTY;
                    }
                }
            }

            // Let parent handle if it's not a tournament action
            return super.clicked(slotId, dragType, clickType, player);
        }
    }
}