package com.raoulvdberge.refinedstorage.apiimpl.storage;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageCache;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class StorageCacheItem implements IStorageCache<ItemStack> {
    public static final Consumer<INetwork> INVALIDATE = network -> network.getItemStorageCache().invalidate();

    private INetwork network;
    private CopyOnWriteArrayList<IStorage<ItemStack>> storages = new CopyOnWriteArrayList<>();
    private IStackList<ItemStack> list = API.instance().createItemStackList();
    private List<Runnable> listeners = new LinkedList<>();

    public StorageCacheItem(INetwork network) {
        this.network = network;
    }

    @Override
    public synchronized void invalidate() {
        storages.clear();

        network.getNodeGraph().all().stream()
            .filter(node -> node.canUpdate() && node instanceof IStorageProvider)
            .forEach(node -> ((IStorageProvider) node).addItemStorages(storages));

        list.clear();

        sort();

        for (IStorage<ItemStack> storage : storages) {
            if (storage.getAccessType() == AccessType.INSERT) {
                continue;
            }

            for (ItemStack stack : storage.getStacks()) {
                if (!stack.isEmpty()) {
                    add(stack, stack.getCount(), true, false);
                }
            }
        }

        listeners.forEach(Runnable::run);

        network.sendItemStorageToClient();
    }

    @Override
    public synchronized void add(@Nonnull ItemStack stack, int size, boolean rebuilding, boolean batched) {
        list.add(stack, size);

        if (!rebuilding) {
            network.sendItemStorageDeltaToClient(stack, size, batched);

            listeners.forEach(Runnable::run);
        }
    }

    @Override
    public synchronized void remove(@Nonnull ItemStack stack, int size, boolean batched) {
        if (list.remove(stack, size)) {
            network.sendItemStorageDeltaToClient(stack, -size, batched);

            listeners.forEach(Runnable::run);
        }
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void sort() {
        storages.sort(IStorage.COMPARATOR);
    }

    @Override
    public IStackList<ItemStack> getList() {
        return list;
    }

    @Override
    public List<IStorage<ItemStack>> getStorages() {
        return storages;
    }
}
