package net.binaryvibrance.schematicmetablocks.blocks;

import net.binaryvibrance.schematicmetablocks.jobs.ChunkToProcess;
import net.binaryvibrance.schematicmetablocks.jobs.JobProcessor;
import net.binaryvibrance.schematicmetablocks.jobs.JobType;
import net.binaryvibrance.schematicmetablocks.library.ModBlock;
import net.binaryvibrance.schematicmetablocks.proxy.ClientProxy;
import net.binaryvibrance.schematicmetablocks.tileentity.InteriorAirMarkerTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class InteriorAirMarker extends MetaBlock
{
    public static final String NAME = "blockInteriorAirMarker";

    public InteriorAirMarker()
    {
        super(Material.glass);
        this.setBlockName(NAME);
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.15F, 1.0F);
    }

    @Override
    public boolean renderAsNormalBlock()
    {
        return false;
    }

    @Override
    public int getRenderType()
    {
        return ClientProxy.insideMetadataBlockRendererId;
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side)
    {
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        Block b = world.getBlock(x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ);
        if (b instanceof ImplicitAirBlock || b instanceof InteriorAirMarker)
        {
            return null;
        }

        return super.getIcon(world, x, y, z, side);
    }

    @Override
    public boolean isOpaqueCube()
    {
        return false;
    }

    @Override
    public void onBlockDestroyedByPlayer(World world, int x, int y, int z, int p_149664_5_)
    {
        world.setBlock(x, y, z, ModBlock.blockImplicitAir, 0, 3);
    }

    @Override
    public int getRenderBlockPass()
    {
        return 1;
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float tu, float tv, float tw, int metadata)
    {
        if (!world.isRemote)
        {
            ChunkToProcess chunkToProcess = new ChunkToProcess(world, x >> 4, z >> 4);
            JobProcessor.Instance.scheduleJob(JobType.BACKGROUND, chunkToProcess);
        }
        return metadata;
    }

    @Override
    public boolean hasTileEntity(int metadata)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata)
    {
        return new InteriorAirMarkerTileEntity();
    }

    @Override
    public boolean canRenderInPass(int pass)
    {
        ClientProxy.renderPass = pass;
        return pass != 0;
    }
}
