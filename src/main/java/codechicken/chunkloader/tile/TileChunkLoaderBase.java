package codechicken.chunkloader.tile;

import codechicken.chunkloader.ChickenChunks;
import codechicken.chunkloader.api.IChunkLoader;
import codechicken.chunkloader.api.IChunkLoaderHandler;
import codechicken.chunkloader.api.MyEnergyStorage;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.packet.PacketCustom;
import net.minecraft.block.BlockState;
import net.minecraft.command.arguments.NBTCompoundTagArgument;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static codechicken.chunkloader.network.ChickenChunksNetwork.*;

public abstract class TileChunkLoaderBase extends TileEntity implements ITickableTileEntity, IChunkLoader {

    public static final int INPUT_SLOT = 1;
    public static final int CAPACITY_ENERGY = 1_000;
    public static final int GENERATE_ENERGY = 50;
    public static final int COUNT_TICK = 20;
    public static final int SEND_PER_TICK = 10;
    public UUID owner;
    public ITextComponent ownerName;
    protected boolean loaded = false;
    protected boolean powered = false;
    public RenderInfo renderInfo;
    public boolean active = false;

    private int counter;



    private ItemStackHandler itemHandler = createHandler();
    private MyEnergyStorage energyStorage = createEnergy();

    private LazyOptional<IItemHandler> handler = LazyOptional.of(() -> itemHandler);
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> energyStorage);

    public TileChunkLoaderBase(TileEntityType<?> tileEntityTypeIn) {
        super(tileEntityTypeIn);
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        tag.put("inv", itemHandler.serializeNBT());
        tag.put("energy", energyStorage.serializeNBT());
        tag.putBoolean("powered", powered);
        tag.putInt("counter", counter);
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("owner_name", ITextComponent.Serializer.toJson(ownerName));
        }
        return tag;
    }

    @Override
    public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        itemHandler.deserializeNBT(tag.getCompound("inv"));
        energyStorage.deserializeNBT(tag.getCompound("energy"));

        counter = tag.getInt("counter");

        if (tag.contains("owner")) {
            owner = tag.getUUID("owner");
            ownerName = ITextComponent.Serializer.fromJson(tag.getString("owner_name"));
        }
        if (tag.contains("powered")) {
            powered = tag.getBoolean("powered");
        }
        loaded = true;
    }


    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return handler.cast();
        }
        if (cap == CapabilityEnergy.ENERGY) {
            return energy.cast();
        }
        return super.getCapability(cap, side);
    }


    private ItemStackHandler createHandler(){
        return new ItemStackHandler(INPUT_SLOT){
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() == Items.DIAMOND;
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {

                if(stack.getItem() != Items.DIAMOND){
                    return stack;
                }

                return super.insertItem(slot, stack, simulate);
            }
        };
    }

    private MyEnergyStorage createEnergy() {
        return new MyEnergyStorage(CAPACITY_ENERGY, 0) {
            @Override
            protected void onEnergyChanged() {
                setChanged();
            }
        };
    }


    public void clearRemoved() {
        super.clearRemoved();
        if (!level.isClientSide && loaded && !powered) {
            activate();
        }

        if (level.isClientSide) {
            renderInfo = new RenderInfo();
        }
    }

    public boolean isPowered() {
        for (Direction face : Direction.BY_3D_DATA) {
            boolean isPowered = isPoweringTo(level, getBlockPos().relative(face), face);
            if (isPowered) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPoweringTo(World world, BlockPos pos, Direction side) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock().getSignal(state, world, pos, side) > 0;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!level.isClientSide) {
            deactivate();
        }
    }

    public void destroyBlock() {
        //        ModBlocks.blockChunkLoader.dropBlockAsItem(world, getPos(), world.getBlockState(pos), 0);
        //        world.setBlockToAir(getPos());
    }

//    public ChunkPos getChunkPosition() {
//        return new ChunkPos(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4);
//    }

    public void onBlockPlacedBy(LivingEntity entityliving) {
        if (entityliving instanceof PlayerEntity) {
            owner = entityliving.getUUID();
            ownerName = entityliving.getName();
        }
        //TODO
        //        if (owner.equals("")) {
        //            owner = null;
        //        }
        activate();
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    @Override
    public String getMod() {
        return ChickenChunks.MOD_ID;
    }

    @Override
    public World world() {
        return level;
    }

    @Override
    public BlockPos pos() {
        return getBlockPos();
    }

    @Override
    public void deactivate() {
        if (owner == null) {
            return;
        }
        loaded = true;
        active = false;
        IChunkLoaderHandler.getCapability(level).removeChunkLoader(this);

    }

    public void activate() {
        if (owner == null) {
            return;
        }
        loaded = true;
        active = true;
        IChunkLoaderHandler.getCapability(level).addChunkLoader(this);

    }

    public void updateState(){
        BlockState state = level.getBlockState(getBlockPos());
        level.sendBlockUpdated(getBlockPos(), state, state, 3);

    }



    @Override
    public boolean isValid() {
        return !remove;
    }

    @Override
    public void tick() {
        if (!level.isClientSide) {
            boolean nowPowered = isPowered();
            if (powered != nowPowered) {
                powered = nowPowered;
                if (powered) {
                    deactivate();

                } else {
                    activate();
                }
                updateState();
            }
            workCapacityAndEnergy();

        } else {

             renderInfo.update(this);

        }
    }

    public void workCapacityAndEnergy(){
        if (counter > 0) {
            counter--;
            if (counter <= 0) {
                energyStorage.addEnergy(GENERATE_ENERGY);
            }
            setChanged();
        }

        if (counter <= 0) {
            ItemStack stack = itemHandler.getStackInSlot(0);
            if (stack.getItem() == Items.DIAMOND) {
                itemHandler.extractItem(0, 1, false);
                counter = COUNT_TICK;
                setChanged();
            }
        }

        if (powered != counter > 0) {
            updateState();
        }

        sendOutPower();
    }

    private void sendOutPower() {
        AtomicInteger capacity = new AtomicInteger(energyStorage.getEnergyStored());
        if (capacity.get() > 0) {
            for (Direction direction : Direction.values()) {
                TileEntity te = level.getBlockEntity(getBlockPos().offset(direction.getNormal()));
                if (te != null) {
                    boolean doContinue = te.getCapability(CapabilityEnergy.ENERGY, direction).map(handler -> {
                                if (handler.canReceive()) {
                                    int received = handler.receiveEnergy(Math.min(capacity.get(), SEND_PER_TICK), false);
                                    capacity.addAndGet(-received);
                                    energyStorage.consumeEnergy(received);
                                    setChanged();
                                    return capacity.get() > 0;
                                } else {
                                    return true;
                                }
                            }
                    ).orElse(true);
                    if (!doContinue) {
                        return;
                    }
                }
            }
        }
    }



    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        PacketCustom packet = new PacketCustom(NET_CHANNEL, 1);//Dummy Index.
        writeToPacket(packet);
        return packet.toTilePacket(getBlockPos());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        PacketCustom packet = new PacketCustom(NET_CHANNEL, 1);//Dummy Index.
        writeToPacket(packet);
        return packet.writeToNBT(super.getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        readFromPacket(PacketCustom.fromTilePacket(pkt));
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        readFromPacket(PacketCustom.fromNBTTag(tag));
    }

    public void writeToPacket(MCDataOutput packet) {
        packet.writeBoolean(active);
        packet.writeBoolean(owner != null);
        if (owner != null) {
            packet.writeUUID(owner);
            packet.writeTextComponent(ownerName);
        }
    }



    public void readFromPacket(MCDataInput packet) {
        active = packet.readBoolean();
        if (packet.readBoolean()) {
            owner = packet.readUUID();
            ownerName = packet.readTextComponent();
        }
    }

    @Override
    public double getViewDistance() {
        return 65536.0D;
    }

    public static class RenderInfo {

        public int activationCounter;
        public boolean showLasers;
        public boolean activeReceive = false;


        public void update(TileChunkLoaderBase chunkLoader) {
            if (activationCounter < 20 && chunkLoader.active) {
                activationCounter++;
            } else if (activationCounter > 0 && !chunkLoader.active) {
                activationCounter--;
            }
        }
    }
}
