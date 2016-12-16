package com.raoulvdberge.refinedstorage.tile;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.RSUtils;
import com.raoulvdberge.refinedstorage.api.network.security.ISecurityCard;
import com.raoulvdberge.refinedstorage.api.network.security.ISecurityCardContainer;
import com.raoulvdberge.refinedstorage.api.network.security.Permission;
import com.raoulvdberge.refinedstorage.apiimpl.network.security.SecurityCard;
import com.raoulvdberge.refinedstorage.inventory.ItemHandlerBasic;
import com.raoulvdberge.refinedstorage.inventory.ItemValidatorBasic;
import com.raoulvdberge.refinedstorage.item.ItemSecurityCard;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TileSecurityManager extends TileNode implements ISecurityCardContainer {
    private List<ISecurityCard> actualCards = new ArrayList<>();

    private ItemHandlerBasic cards = new ItemHandlerBasic(9 * 2, this, new ItemValidatorBasic(RSItems.SECURITY_CARD)) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);

            if (getWorld() != null && !getWorld().isRemote) {
                rebuildCards();
            }

            if (network != null) {
                network.getSecurityManager().rebuild();
            }
        }
    };
    private ItemHandlerBasic editCard = new ItemHandlerBasic(1, this, new ItemValidatorBasic(RSItems.SECURITY_CARD));

    @Override
    public int getEnergyUsage() {
        int usage = RS.INSTANCE.config.securityManagerUsage;

        for (int i = 0; i < cards.getSlots(); ++i) {
            if (!cards.getStackInSlot(i).isEmpty()) {
                usage += RS.INSTANCE.config.securityManagerPerSecurityCardUsage;
            }
        }

        return usage;
    }

    @Override
    public void update() {
        if (!getWorld().isRemote && ticks == 0) {
            rebuildCards();
        }

        super.update();
    }

    @Override
    public void updateNode() {
        // NO OP
    }

    private void rebuildCards() {
        actualCards.clear();

        for (int i = 0; i < cards.getSlots(); ++i) {
            ItemStack stack = cards.getStackInSlot(i);

            if (!stack.isEmpty()) {
                UUID uuid = ItemSecurityCard.getBound(stack);

                if (uuid == null) {
                    continue;
                }

                SecurityCard card = new SecurityCard(uuid);

                for (Permission permission : Permission.values()) {
                    card.getPermissions().put(permission, ItemSecurityCard.hasPermission(stack, permission));
                }

                actualCards.add(card);
            }
        }
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        RSUtils.readItems(cards, 0, tag);
        RSUtils.readItems(editCard, 1, tag);
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        RSUtils.writeItems(cards, 0, tag);
        RSUtils.writeItems(editCard, 1, tag);

        return tag;
    }

    public ItemHandlerBasic getCardsItems() {
        return cards;
    }

    public ItemHandlerBasic getEditCard() {
        return editCard;
    }

    public void updatePermission(Permission permission, boolean state) {
        ItemStack card = getEditCard().getStackInSlot(0);

        if (!card.isEmpty()) {
            ItemSecurityCard.setPermission(card, permission, state);
        }
    }

    @Override
    public List<ISecurityCard> getCards() {
        return actualCards;
    }
}
