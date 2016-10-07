package net.binaryvibrance.schematicmetablocks.items;

import net.binaryvibrance.schematicmetablocks.Logger;
import net.binaryvibrance.schematicmetablocks.TheMod;
import net.binaryvibrance.schematicmetablocks.events.RegisterRenderingEvent;
import net.binaryvibrance.schematicmetablocks.events.TileEntityEvent;
import net.binaryvibrance.schematicmetablocks.gui.GUIs;
import net.binaryvibrance.schematicmetablocks.library.ModBlock;
import net.binaryvibrance.schematicmetablocks.tileentity.RegionTileEntity;
import net.binaryvibrance.schematicmetablocks.utility.NBTUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import java.util.List;

public class MetaToolItem extends Item
{
    private static NBTTagCompound getTagCompoundSafe(ItemStack stack)
    {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null)
        {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        return nbt;
    }

    private static MetaToolMode getMetaToolMode(ItemStack stack)
    {
        final NBTTagCompound nbt = getTagCompoundSafe(stack);
        final MetaToolMode[] modes = MetaToolMode.values();
        return modes[nbt.getInteger("Mode") % modes.length];
    }

    private static void setMetaToolMode(ItemStack stack, MetaToolMode mode)
    {
        final NBTTagCompound nbt = getTagCompoundSafe(stack);

        nbt.setInteger("Mode", mode.ordinal());
    }

    @Override
    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        if (playerIn.isSneaking()) return EnumActionResult.PASS;

        final TileEntity tileEntity = worldIn.getTileEntity(pos);
        if (tileEntity instanceof RegionTileEntity)
        {
            final MetaToolMode mode = getMetaToolMode(stack);
            final RegionTileEntity regionTileEntity = (RegionTileEntity) tileEntity;

            boolean result = false;

            switch (mode)
            {
                case PULL:
                    result = moveRegion(worldIn, pos, facing, regionTileEntity, false);
                    break;
                case PUSH:
                    result = moveRegion(worldIn, pos, facing, regionTileEntity, true);
                    break;
                case SAVE_SCHEMATIC:
                    result = saveSchematic(worldIn, playerIn, regionTileEntity);
                    break;
                case SET_NAME:
                    result = setName(worldIn, playerIn, regionTileEntity);
                    break;
                case CLEAR_METATOOL:
                    result = clearMetaTool(stack, playerIn);
                    break;
                case SET_REGION:
                    result = linkRegions(stack, worldIn, playerIn, pos, regionTileEntity);
                    break;
                case CLEAR_REGION:
                    result = clearRegion(regionTileEntity);
                    break;
            }
            return result ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
        }

        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer playerIn, EnumHand hand)
    {
        if (worldIn.isRemote)
        {
            return ActionResult.newResult(EnumActionResult.SUCCESS, itemStackIn);
        }

        if (playerIn.isSneaking())
        {
            MetaToolMode mode = getMetaToolMode(itemStackIn);

            final MetaToolMode[] modes = MetaToolMode.values();
            mode = modes[(mode.ordinal() + 1) % modes.length];
            setMetaToolMode(itemStackIn, mode);
            final String displayName = getItemStackDisplayName(itemStackIn);
            itemStackIn.setStackDisplayName(displayName);
            playerIn.addChatComponentMessage(new TextComponentString("MetaTool mode changed: " + displayName));
        } else {
            if (getMetaToolMode(itemStackIn) == MetaToolMode.CLEAR_METATOOL) {
                clearMetaTool(itemStackIn, playerIn);
            }
        }

        return ActionResult.newResult(EnumActionResult.SUCCESS, itemStackIn);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced)
    {
        final NBTTagCompound coords = stack.getTagCompound();
        if (coords != null)
        {
            final BlockPos worldBlockCoord = NBTUtils.readBlockPos(coords);
            if (worldBlockCoord != null) {
                tooltip.add(String.format("(%d, %d, %d)", worldBlockCoord.getX(), worldBlockCoord.getY(), worldBlockCoord.getZ()));
            }
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack)
    {
        final NBTTagCompound nbt = stack.getTagCompound();

        final MetaToolMode mode = getMetaToolMode(stack);
        String extra = "";
        String modeName = mode.name().toLowerCase();
        if (mode == MetaToolMode.SET_REGION)
        {
            if (nbt.hasKey("Link"))
            {
                final BlockPos worldBlockCoord = NBTUtils.readBlockPos(nbt.getCompoundTag("Link"));
                extra = String.format(" - (%d, %d, %d)", worldBlockCoord.getX(), worldBlockCoord.getY(), worldBlockCoord.getZ());
            } else
            {
                modeName = "set_region_unset";
            }
        }

        final String name = I18n.translateToLocal(
                String.format("%s.%s.name", this.getUnlocalizedNameInefficiently(stack), modeName)
        ) + extra;
        return name;
    }

    private static boolean saveSchematic(World world, EntityPlayer player, RegionTileEntity regionTileEntity)
    {
        if (world.isRemote)
        {

            if (!regionTileEntity.isPaired())
            {
                player.addChatComponentMessage(new TextComponentString("Cannot save schematic, not paired"));
                return false;
            }

            final String schematicName = regionTileEntity.getSchematicName();
            if (schematicName == null || schematicName.trim().isEmpty())
            {
                player.addChatComponentMessage(new TextComponentString("Cannot save schematic, name not specified"));
                return false;
            }

            final BlockPos location = regionTileEntity.getPos();
            final BlockPos oppositeLocation = regionTileEntity.getLinkedLocation();

            final int minX = Math.min(location.getX(), oppositeLocation.getX()) + 1;
            final int minY = Math.min(location.getY(), oppositeLocation.getY()) + 1;
            final int minZ = Math.min(location.getZ(), oppositeLocation.getZ()) + 1;

            final int maxX = Math.max(location.getX(), oppositeLocation.getX()) - 1;
            final int maxY = Math.max(location.getY(), oppositeLocation.getY()) - 1;
            final int maxZ = Math.max(location.getZ(), oppositeLocation.getZ()) - 1;

            Minecraft.getMinecraft().thePlayer.sendChatMessage(
                    String.format("/schematicaSave %d %d %d %d %d %d %s",
                            minX, minY, minZ,
                            maxX, maxY, maxZ,
                            schematicName)
            );
        }

        return true;
    }

    private static boolean setName(World world, EntityPlayer player, RegionTileEntity tileEntity)
    {
        if (world.isRemote)
        {
            final BlockPos tileEntityPos = tileEntity.getPos();
            player.openGui(TheMod.instance, GUIs.SCHEMATIC_NAME.ordinal(), world, tileEntityPos.getX(), tileEntityPos.getY(), tileEntityPos.getZ());
            return true;
        }
        return false;
    }

    private static boolean clearMetaTool(ItemStack stack, EntityPlayer player)
    {

        final NBTTagCompound nbt = getTagCompoundSafe(stack);
        nbt.removeTag("Link");
        player.addChatComponentMessage(new TextComponentString("MetaTool coordinates cleared"));
        return true;
    }

    private static boolean clearRegion(RegionTileEntity selectedTileEntity)
    {
        if (!selectedTileEntity.isPaired())
        {
            return false;
        }
        final RegionTileEntity linkedTileEntity = selectedTileEntity.getLinkedTileEntity();
        linkedTileEntity.setLinkedTileEntity(null);
        linkedTileEntity.setSchematicName(null);
        selectedTileEntity.setLinkedTileEntity(null);

        return true;
    }

    private static boolean moveRegion(World world, BlockPos oldPos, EnumFacing direction, RegionTileEntity originalTileEntity, boolean push)
    {
        if (push)
        {
            direction = direction.getOpposite();
        }

        final BlockPos newPos = oldPos.offset(direction);

        if (!world.isAirBlock(newPos))
        {
            return false;
        }

        final RegionTileEntity oldTileEntity = RegionTileEntity.tryGetTileEntity(world, oldPos);

        world.setBlockState(newPos, ModBlock.blockRegion.getDefaultState());
        final RegionTileEntity newTileEntity = RegionTileEntity.tryGetTileEntity(world, newPos);
        Logger.info("New Tile Entity %s", newTileEntity);
        if (newTileEntity != null && originalTileEntity.isPaired())
        {
            final RegionTileEntity linkedTileEntity = originalTileEntity.getLinkedTileEntity();
            Logger.info("Linked Tile Entity: %s", linkedTileEntity);
            Logger.info("Unlinking Tile Entity: %s", originalTileEntity);
            originalTileEntity.setLinkedTileEntity(null);
            Logger.info("(1/2) Linking (%s) to (%s)", linkedTileEntity, newTileEntity);
            linkedTileEntity.setLinkedTileEntity(newTileEntity);
            Logger.info("(2/2) Linking (%s) to (%s)", newTileEntity, linkedTileEntity);
            newTileEntity.setLinkedTileEntity(linkedTileEntity);
        }

        MinecraftForge.EVENT_BUS.post(new TileEntityEvent.Added(newTileEntity));
        if (oldTileEntity != null) {
            MinecraftForge.EVENT_BUS.post(new TileEntityEvent.Added(oldTileEntity));
        }
        world.setBlockToAir(oldPos);

        return true;
    }

    private boolean linkRegions(ItemStack stack, World world, EntityPlayer player, BlockPos clickedBlockCoord, RegionTileEntity selectedRegion)
    {
        if (world.isRemote)
        {
            return true;
        }

        if (selectedRegion.isPaired())
        {
            player.addChatComponentMessage(new TextComponentString("Region already paired. Clear it first if you're sure you want to do this."));
            return false;
        }

        final NBTTagCompound nbt = getTagCompoundSafe(stack);

        if (!nbt.hasKey("Link"))
        {
            Logger.info("Setting MetaTool's coordinates - %s", clickedBlockCoord);
            //Setting item nbt
            nbt.setTag("Link", NBTUtils.writeBlockPos(clickedBlockCoord));
            stack.setStackDisplayName(getItemStackDisplayName(stack));
        } else
        {
            //Applying nbt
            final BlockPos oppositeWorldCoords = NBTUtils.readBlockPos(nbt.getCompoundTag("Link"));

            if (oppositeWorldCoords.equals(clickedBlockCoord))
            {
                clearMetaTool(stack, player);
                return false;
            }

            Logger.info("Applying MetaTool Coordinates - %s", oppositeWorldCoords);

            final TileEntity tileEntity = world.getTileEntity(oppositeWorldCoords);
            if (!(tileEntity instanceof RegionTileEntity))
            {
                player.addChatComponentMessage(new TextComponentString("Linked Region Block no longer exists."));
                clearMetaTool(stack, player);
                return false;
            }
            final RegionTileEntity oppositeRegion = (RegionTileEntity) tileEntity;

            selectedRegion.setLinkedTileEntityWithReverify(oppositeRegion);
            oppositeRegion.setLinkedTileEntityWithReverify(selectedRegion);
            clearMetaTool(stack, player);
        }
        return true;
    }
}
